import base64
import json
import csv
import io
import os
from pathlib import Path
import random
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile
from PIL import Image, ImageChops

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'research'))
from wrath_textures import scan,dds


def record(name,w,h,fmt,payload):
    header=bytearray(71);header[:len(name)]=name
    struct.pack_into('<II',header,32,w,h)
    struct.pack_into('<4I',header,55,len(payload),8,fmt,1)
    return bytes(header)+payload


def fixture(fmt=65,payload=None):
    name=b'test_texture'
    if payload is None:payload=struct.pack('<HHI',0xf800,0x07e0,0)*4 if fmt==65 else bytes((255,0))+bytes(6)+struct.pack('<HHI',0xf800,0x07e0,0)
    w=8 if fmt==65 else 4
    return struct.pack('<I',0x52049)+bytes(24)+b'meshes\\chars\\test.mtx'+struct.pack('<I',len(name))+name+bytes(4)+record(name,w,w,fmt,payload)


class TextureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory();cls.base=Path(cls.temp.name);cls.classes=cls.base/'classes';cls.classes.mkdir()
        sources=[ROOT/'app/src/main/java/com/wrath/pakviewer'/f'{n}.java' for n in ('WrathGeometry','WrathTextures','WrathMotion','WrathExports')]+[ROOT/'research/ExportCli.java']
        subprocess.run(['java','com.sun.tools.javac.Main','--release','8','-d',str(cls.classes),*map(str,sources)],check=True,capture_output=True)

    @classmethod
    def tearDownClass(cls):cls.temp.cleanup()

    def export(self,data,category='textures'):
        p=self.base/'source.pak';p.write_bytes(data);out=self.base/'out.zip'
        subprocess.run(['java','-cp',str(self.classes),'ExportCli',str(p),category,str(out)],check=True,capture_output=True)
        return zipfile.ZipFile(io.BytesIO(out.read_bytes()))

    def compare(self,data):
        expected=scan(data)
        with self.export(data) as z:
            rows=list(csv.DictReader(io.StringIO(z.read('textures/index.csv').decode())))
            self.assertEqual(len(rows),len(expected))
            for t,row in zip(expected,rows):
                self.assertEqual(int(row['mip_levels']),len(t.levels))
                self.assertEqual(z.read(row['dds']),dds(data,t))
                actual=Image.open(io.BytesIO(z.read(row['png']))).convert('RGBA')
                reference=Image.open(io.BytesIO(dds(data,t))).convert('RGBA')
                delta=ImageChops.difference(actual,reference)
                self.assertTrue(all(high<=1 for low,high in delta.getextrema()),(t.name,delta.getextrema()))
            return len(rows)

    def test_static_world_obj_assembles_buffers_and_rebases_faces(self):
        def mesh(name, x):
            vertices=b''.join(struct.pack('<3fI2fI',*point,0,0.25,0.5,0)
                              for point in ((x,0,0),(x+1,0,0),(x,0,1)))
            batches=struct.pack('<6I3H3I',0,0,1,1,3,3,0,1,2,0,0,0)+bytes(8)
            return (struct.pack('<I',len(name))+name+batches+bytes(24)
                    +struct.pack('<2I',3,3)+vertices+bytes(8)+struct.pack('<2I',3,3))

        path=b'levels\\test.sls'+bytes(2)
        world=bytes(16)+mesh(b'Rock:Sand',0)+mesh(b'Ruin:Stone',10)
        payload_size=len(world)-16
        header=struct.pack('<4I',payload_size+len(path)+32,payload_size,0,len(path))
        archive=struct.pack('<I',0x52049)+bytes(24)+header+path+world
        with self.export(archive,'models') as z:
            obj=z.read('models/primary_model.obj').decode()
            self.assertEqual(sum(line.startswith('v ') for line in obj.splitlines()),6)
            self.assertIn('v 10.0 0.0 0.0',obj)
            self.assertIn('f 1/1/1 3/3/3 2/2/2',obj)
            self.assertIn('f 4/4/4 6/6/6 5/5/5',obj)
            self.assertIn('usemtl sand',obj)
            self.assertIn('usemtl stone',obj)

    def test_bc1_known_red(self):
        data=fixture();self.assertEqual(self.compare(data),1)
        with self.export(data) as z:
            name=next(n for n in z.namelist() if n.endswith('.png'))
            image=Image.open(io.BytesIO(z.read(name)));self.assertEqual(image.getpixel((0,0)),(255,0,0,255))

    def test_bc1_transparent_mode(self):
        self.compare(fixture(payload=struct.pack('<HHI',0,0xffff,0xffffffff)*4))

    def test_bc3_both_alpha_interpolation_modes(self):
        for a,b in ((240,30),(20,230)):
            bits=sum((i%8)<<(3*i) for i in range(16));payload=bytes((a,b))+bits.to_bytes(6,'little')+struct.pack('<HHI',0xffff,0,0xe4e4e4e4)
            self.compare(fixture(69,payload))

    def test_random_blocks_match_independent_dds_decoder(self):
        randomizer=random.Random(21)
        for fmt in (65,69):
            for _ in range(6):
                size=32 if fmt==65 else 16;self.compare(fixture(fmt,randomizer.randbytes(size)))

    def test_mip_chain_retained(self):
        data=fixture()+b'\1'+record(b'test_texture',4,4,65,bytes(8))
        self.assertEqual(len(scan(data)[0].levels),2);self.compare(data)

    def test_truncated_or_mis_sized_top_level_rejected(self):
        self.assertEqual(self.compare(fixture()[:-1]),0)
        self.assertEqual(self.compare(fixture(payload=bytes(8))),0)

    def test_everything_includes_all_categories_and_original_bytes(self):
        data=fixture()
        with self.export(data,'all') as outer:
            self.assertEqual(len(outer.namelist()),len(set(outer.namelist())))
            for category in ('models','textures','rigs','animation','other'):
                with zipfile.ZipFile(io.BytesIO(outer.read(category+'.zip'))) as child:
                    self.assertIsNone(child.testzip())
                    if category=='other':self.assertEqual(child.read('original/source.pak'),data)
                    if category == 'animation':self.assertIn(b'not implemented',child.read('README.txt'))
                    if category=='models':self.assertIn('EXPORT_ERROR.txt',child.namelist())

    @unittest.skipUnless(os.environ.get('WRATH_PAK_DIR'),'Original archives not committed')
    def test_original_texture_png_and_dds_parity(self):
        expected={'cyclops':102,'centaur':111,'archdemon':111}
        for key,count in expected.items():
            paths=list(Path(os.environ['WRATH_PAK_DIR']).glob('*'+key+'*.pak'));self.assertEqual(len(paths),1)
            with self.subTest(archive=key):self.assertEqual(self.compare(paths[0].read_bytes()),count)

    @unittest.skipUnless(os.environ.get('WRATH_PAK_DIR'),'Original archives not committed')
    def test_model_material_references_resolve(self):
        for key in ('cyclops','centaur','archdemon'):
            data=next(Path(os.environ['WRATH_PAK_DIR']).glob('*'+key+'*.pak')).read_bytes()
            with self.subTest(archive=key),self.export(data,'models') as z:
                obj=z.read('models/primary_model.obj').decode();mtl=z.read('models/materials.mtl').decode()
                self.assertIn('mtllib materials.mtl',obj)
                materials={line.split()[1] for line in mtl.splitlines() if line.startswith('newmtl ')}
                for line in obj.splitlines():
                    if line.startswith('usemtl '):self.assertIn(line.split()[1],materials)
                mapped=set();current=None
                for line in mtl.splitlines():
                    if line.startswith('newmtl '):current=line.split()[1]
                    if line.startswith('map_Kd '):mapped.add(current)
                for line in obj.splitlines():
                    if line.startswith('usemtl '):self.assertIn(line.split()[1],mapped)
                maps=[line.split(' ',1)[1] for line in mtl.splitlines() if line.startswith('map_Kd ')]
                self.assertTrue(maps)
                for path in maps:
                    member=path.removeprefix('../');self.assertIn(member,z.namelist())
                    diffuse=Image.open(io.BytesIO(z.read(member))).convert('RGBA')
                    self.assertEqual(diffuse.getextrema()[3],(255,255))
                    original=Image.open(io.BytesIO(z.read(member.replace('_diffuse.png','.png')))).convert('RGB')
                    self.assertIsNone(ImageChops.difference(diffuse.convert('RGB'),original).getbbox())

    @unittest.skipUnless(os.environ.get('WRATH_PAK_DIR'),'Original archives not committed')
    def test_everything_with_supported_character(self):
        data=next(Path(os.environ['WRATH_PAK_DIR']).glob('*cyclops*.pak')).read_bytes()
        with self.export(data,'all') as outer:
            for category in ('models','textures','rigs','animation','other'):
                with zipfile.ZipFile(io.BytesIO(outer.read(category+'.zip'))) as child:
                    self.assertIsNone(child.testzip())
                    self.assertNotIn('EXPORT_ERROR.txt',child.namelist())
                    if category=='models':
                        self.assertIn('models/primary_model.obj',child.namelist())
                        self.assertIn('models/materials.mtl',child.namelist())
                    if category == 'rigs':
                        tables = [name for name in child.namelist() if name.endswith('.csv')]
                        self.assertEqual(len(tables), 1)
                        joints = list(csv.DictReader(io.StringIO(child.read(tables[0]).decode())))
                        self.assertEqual(len(joints), 98)
                        self.assertEqual(joints[0]['parent'], '-1')
                        self.assertEqual(joints[1]['parent'], '0')
                    if category == 'animation':
                        clips = list(csv.DictReader(io.StringIO(child.read('clips.csv').decode())))
                        self.assertEqual(len(clips), 66)
                        for clip in clips:
                            start, length = int(clip['record_offset']), int(clip['record_bytes'])
                            entry = clip['file']
                            self.assertTrue(entry.endswith('.json'))
                            parsed = json.loads(child.read(entry))
                            packed = base64.b64decode(parsed['packed_base64'], validate=True)
                            self.assertEqual(packed, data[start+length-8-len(packed):start+length-8])
                            self.assertEqual(len(parsed['tracks']), parsed['track_count'])
                            for track in parsed['tracks']:
                                self.assertLessEqual(track['offset']+track['bytes'], len(packed))
                    if category=='other':self.assertEqual(child.read('original/source.pak'),data)

    @unittest.skipUnless(os.environ.get('WRATH_PAK_DIR'),'Original archives not committed')
    def test_audio_only_export_preserves_source_waves(self):
        data = next(Path(os.environ['WRATH_PAK_DIR']).glob('*cyclops*.pak')).read_bytes()
        with self.export(data, 'audio') as archive:
            rows = list(csv.DictReader(io.StringIO(archive.read('audio/index.csv').decode())))
            self.assertEqual(len(rows), 116)
            for row in rows:
                start, size = int(row['offset']), int(row['length'])
                wave = archive.read(row['file'])
                self.assertEqual(wave[:4], b'RIFF')
                self.assertEqual(wave[8:12], b'WAVE')
                self.assertEqual(wave, data[start:start+size])
            self.assertFalse(any(name.startswith('original/') for name in archive.namelist()))
