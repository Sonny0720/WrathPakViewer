"""Topology, bounds, relocation, cross-runtime and optional original-archive checks."""
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'research'))
from wrath_geometry import Reader, FormatError, decode, draw_container, triangles, write_obj


def uints(*values):
    return struct.pack('<'+'I'*len(values), *values)


def array(values):
    return uints(max(4, len(values)), len(values))+struct.pack('<'+'H'*len(values), *values)


def container(ids=(0, 1, 2), strip=(2, 2, 3, 4, 4, 5, 6)):
    return uints(4, 1)+array(ids)+uints(4, 1)+array(strip)+array((0,))+uints(0)


def fixture(padding=0, corrupt=False, continuation=False, mesh='chars'):
    blob = bytearray(uints(0x52049)+bytes(24)+bytes(padding))
    blob += f'meshes\\{mesh}\\c_test.msh'.encode()+b'\0'
    name = b'C_Test:C_DC_Test_Body_V1'
    blob += uints(len(name))+name+uints(4, 0)
    blob += container((0, 1, 101) if corrupt else (0, 1, 2))
    if continuation:
        blob += container((7, 8, 9), (7, 8, 9))
    blob += bytes(16)+uints(128, 101)
    for i in range(101):
        blob += struct.pack('<3fI2fII', i % 10, i // 10, i % 3, 0, .25, .75, 0, 0)
    blob += bytes(16)+uints(101, 101)
    return bytes(blob)


class GeometryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.classes = Path(cls.temp.name)/'classes'
        cls.classes.mkdir()
        subprocess.run(['java', 'com.sun.tools.javac.Main', '--release', '8', '-d', str(cls.classes),
            str(ROOT/'app/src/main/java/com/wrath/pakviewer/WrathGeometry.java'),
            str(ROOT/'research/GeometryCli.java')], check=True, capture_output=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def android(self, blob):
        base = Path(self.temp.name)
        (base/'test.pak').write_bytes(blob)
        result = subprocess.run(['java', '-cp', str(self.classes), 'GeometryCli',
            str(base/'test.pak'), str(base/'test.zip')], capture_output=True, text=True)
        return result, base/'test.zip'

    def test_strip_parity_survives_degenerate_stitches(self):
        self.assertEqual(triangles((0, 1, 2, 2, 3, 4), True), [(0, 1, 2), (3, 2, 4)])

    def test_list_rejects_remainder_instead_of_dropping_indices(self):
        with self.assertRaises(FormatError):
            triangles((0, 1, 2, 3))

    def test_truncated_arrays(self):
        with self.assertRaises(FormatError):
            Reader(uints(128, 100)+bytes(2), 0).indices()

    def test_invalid_allocation(self):
        with self.assertRaises(FormatError):
            Reader(uints(2, 3), 0).indices()

    def test_unsupported_batch_trailer(self):
        value = container()[:-4]+uints(1)
        with self.assertRaises(FormatError):
            draw_container(Reader(value, 0))

    def test_shifted_unaligned_buffers_and_names(self):
        for padding in (0, 1, 19, 8193):
            buffers, rejected = decode(fixture(padding))
            self.assertEqual(len(buffers), 1)
            self.assertEqual(rejected, [])
            self.assertEqual(buffers[0]['groups'][0]['faces'], [(0, 1, 2), (3, 2, 4), (4, 5, 6)])

    def test_containers_are_all_retained(self):
        buffers, _ = decode(fixture(continuation=True))
        self.assertEqual(len(buffers[0]['groups'][0]['batches']), 2)
        self.assertEqual(len(buffers[0]['groups'][0]['faces']), 5)

    def test_bad_index_rejects_resource(self):
        buffers, rejected = decode(fixture(corrupt=True))
        self.assertEqual(buffers, [])
        self.assertEqual(len(rejected), 1)
        result, _ = self.android(fixture(corrupt=True))
        self.assertNotEqual(result.returncode, 0)

    def test_noncharacter_mesh_not_assigned_to_character(self):
        self.assertEqual(decode(fixture(mesh='weapons'))[0], [])

    def test_nonfinite_vertex_rejected(self):
        blob = bytearray(fixture())
        offset = blob.find(uints(128, 101))
        struct.pack_into('<f', blob, offset+8, float('nan'))
        self.assertEqual(decode(blob)[0], [])

    def test_unsupported_header(self):
        with self.assertRaises(FormatError):
            decode(bytes(100))

    def test_android_matches_reference_and_obj_rebasing(self):
        blob = fixture(19, continuation=True)
        buffers, _ = decode(blob)
        result, path = self.android(blob)
        self.assertEqual(result.returncode, 0, result.stderr)
        with zipfile.ZipFile(path) as archive:
            obj = archive.read('buffers/buffer_00.obj').decode()
            faces = [tuple(int(v.split('/')[0])-1 for v in line.split()[1:])
                     for line in obj.splitlines() if line.startswith('f ')]
            self.assertEqual(faces, buffers[0]['groups'][0]['faces'])
            self.assertIn('vt 0.25 0.25', obj)
        path = Path(self.temp.name)/'merged.obj'
        write_obj(buffers*2, path)
        faces = [tuple(int(v.split('/')[0]) for v in line.split()[1:])
                 for line in path.read_text().splitlines() if line.startswith('f ')]
        self.assertEqual(faces[5], (102, 103, 104))

    @unittest.skipUnless(os.environ.get('WRATH_PAK_DIR'), 'Original archives not included in repository')
    def test_original_archives_and_android_parity(self):
        expected = json.loads((ROOT/'research/validated_archives.json').read_text())
        for entry in expected:
            with self.subTest(archive=entry['archive']):
                blob = (Path(os.environ['WRATH_PAK_DIR'])/entry['archive']).read_bytes()
                self.assertEqual(hashlib.sha256(blob).hexdigest(), entry['sha256'])
                buffers, rejected = decode(blob)
                self.assertEqual(rejected, [])
                metrics = [[len(b['records']), sum(len(g['faces']) for g in b['groups'])] for b in buffers]
                self.assertEqual(metrics, entry['buffers_vertices_triangles'])
                result, path = self.android(blob)
                self.assertEqual(result.returncode, 0, result.stderr)
                with zipfile.ZipFile(path) as archive:
                    for i, buffer in enumerate(buffers):
                        text = archive.read(f'buffers/buffer_{i:02d}.obj').decode()
                        faces = [tuple(int(v.split('/')[0])-1 for v in line.split()[1:])
                                 for line in text.splitlines() if line.startswith('f ')]
                        self.assertEqual(faces, [t for g in buffer['groups'] for t in g['faces']])
                        coords = [tuple(map(float, line.split()[1:])) for line in text.splitlines() if line.startswith('v ')]
                        for actual, row in zip(coords, buffer['records']):
                            for a, b in zip(actual, row[:3]):
                                self.assertAlmostEqual(a, b, places=4)


if __name__ == '__main__':
    unittest.main()
