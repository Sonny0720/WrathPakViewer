package com.wrath.pakviewer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Observed Xbox character and static environment geometry layouts. */
public final class WrathGeometry {
    private WrathGeometry() {}
    private static final int[] FLOAT_FIELDS={0,4,8,16,20};
    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z_0-9]*:[A-Za-z][A-Za-z_0-9]*");
    private static final Pattern VARIANT = Pattern.compile("(C_[^:]+):(C_[A-Z]{2}_[^_]+)_(.+)");
    private static long u32(byte[] b, int p) {
        return (b[p]&255L)|((b[p+1]&255L)<<8)|((b[p+2]&255L)<<16)|((b[p+3]&255L)<<24);
    }
    private static float f32(byte[] b, int p) { return Float.intBitsToFloat((int)u32(b,p)); }
    private static boolean bytes(byte[] b,int p,String s) {
        if(p<0||p+s.length()>b.length)return false;
        for(int i=0;i<s.length();i++)if((b[p+i]&255)!=s.charAt(i))return false;
        return true;
    }
    private static final class Invalid extends IOException {
        Invalid(String message) { super(message); }
    }
    private static final class Reader {
        final byte[] data; final int end; int pos;
        Reader(byte[] b,int p,int e) { data=b;pos=p;end=e; }
        int uint() throws Invalid {
            if(pos+4>end)throw new Invalid("Truncated uint32");
            long n=u32(data,pos);pos+=4;
            if(n>Integer.MAX_VALUE)throw new Invalid("Unsupported integer");
            return (int)n;
        }
        int count(int maximum) throws Invalid {
            int cap=uint(),n=uint();
            if(n>cap||cap>maximum)throw new Invalid("Invalid allocation/count");
            return n;
        }
        int[] indices() throws Invalid {
            int n=count(100000);
            if((long)pos+2L*n>end)throw new Invalid("Truncated uint16 array");
            int[] ids=new int[n];
            for(int i=0;i<n;i++,pos+=2)ids[i]=(data[pos]&255)|((data[pos+1]&255)<<8);
            return ids;
        }
    }
    public static final class Group {
        public String name; public int offset,end,batches,listIndices,stripIndices,paletteEntries;
        public final List<int[]> faces=new ArrayList<>();
    }
    public static final class Buffer {
        public int offset,end,count,baseCount,stride; public boolean world; public String source,variant;
        public final List<Group> groups=new ArrayList<>();
    }
    public static final class Result {
        private final byte[] data;
        public final List<Buffer> buffers=new ArrayList<>();
        public final List<String> rejected=new ArrayList<>();
        private Result(byte[] b) { data=b; }
        public String audit() {
            StringBuilder s=new StringBuilder("Xbox geometry only. Y up; UV V flipped for OBJ.\n"
                +"Rig, animation, source shader behavior, PS2 layout and completeness are not validated. The category exporter adds native textures and MTL files.\n"
                +"Open models/primary_model.obj. parts/ variants may contain attachments only. Appended vertices are rebased; OBJ winding is CCW; normals are generated.\n");
            for(Buffer b:buffers) {
                Set<Integer> used=new HashSet<>();int triangles=0;
                for(Group g:b.groups)for(int[] t:g.faces){triangles++;for(int i:t)used.add(i);}
                s.append(String.format(Locale.US,"buffer=0x%X stride=%d world=%s vertices=%d base=%d appended=%d referenced=%d triangles=%d source=%s variant=%s\n",
                    b.offset,b.stride,b.world,b.count,b.baseCount,b.count-b.baseCount,used.size(),triangles,b.source,b.variant));
                for(Group g:b.groups)s.append(String.format(Locale.US,
                    "  name=%s offset=0x%X end=0x%X batches=%d list_indices=%d strip_indices=%d palette_entries=%d triangles=%d\n",
                    g.name,g.offset,g.end,g.batches,g.listIndices,g.stripIndices,g.paletteEntries,g.faces.size()));
            }
            for(String reason:rejected)s.append("REJECTED ").append(reason).append('\n');
            return s.toString();
        }
        public void writeObj(List<Buffer> selection,Writer writer) throws IOException {
            int base=0;
            writer.write("# Xbox geometry; Y up.\nmtllib materials.mtl\n");
            for(Buffer b:selection) {
                for(int i=0;i<b.count;i++) {
                    int p=b.offset+8+b.stride*i;
                    writer.write("v "+Float.toString(f32(data,p))+" "+Float.toString(f32(data,p+4))+" "+Float.toString(f32(data,p+8))+"\n");
                }
                for(int i=0;i<b.count;i++) {
                    int p=b.offset+8+b.stride*i;
                    writer.write("vt "+Float.toString(f32(data,p+16))+" "+Double.toString(1.0-f32(data,p+20))+"\n");
                }
                double[][] normals=normals(b);
                for(double[] normal:normals)writer.write("vn "+normal[0]+" "+normal[1]+" "+normal[2]+"\n");
                for(Group g:b.groups) {
                    writer.write("g "+g.name.replaceAll("[^A-Za-z0-9_]","_")+"_"+Integer.toHexString(g.offset)+"\n");
                    writer.write("usemtl "+g.name.substring(g.name.indexOf(':')+1).toLowerCase(Locale.ROOT).replaceAll("[^A-Za-z0-9_-]","_")+"\n");
                    for(int[] t:g.faces) {
                        writer.write("f");
                        for(int v:t){int n=base+v+1;writer.write(" "+n+"/"+n+"/"+n);}
                        writer.write("\n");
                    }
                }
                base+=b.count;
            }
            writer.flush();
        }
        private double[][] normals(Buffer buffer) {
            double[][] result=new double[buffer.count][3];
            for(Group group:buffer.groups)for(int[] face:group.faces) {
                double[][] v=new double[3][3];
                for(int j=0;j<3;j++)for(int k=0;k<3;k++)v[j][k]=f32(data,buffer.offset+8+buffer.stride*face[j]+4*k);
                double ux=v[1][0]-v[0][0],uy=v[1][1]-v[0][1],uz=v[1][2]-v[0][2];
                double vx=v[2][0]-v[0][0],vy=v[2][1]-v[0][1],vz=v[2][2]-v[0][2];
                for(int index:face){result[index][0]+=uy*vz-uz*vy;result[index][1]+=uz*vx-ux*vz;result[index][2]+=ux*vy-uy*vx;}
            }
            for(double[] n:result){double length=Math.sqrt(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]);
                if(length>0)for(int k=0;k<3;k++)n[k]/=length;else n[1]=1;}
            return result;
        }
        public void writeZip(ZipOutputStream zip) throws IOException {
            if(buffers.isEmpty())throw new IOException("No supported Xbox geometry found");
            for(int i=0;i<buffers.size();i++) {
                zip.putNextEntry(new ZipEntry(String.format(Locale.US,"parts/buffer_%02d.obj",i)));
                writeObj(Collections.singletonList(buffers.get(i)),new OutputStreamWriter(zip,StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            Map<String,List<Buffer>> assemblies=new TreeMap<>();
            for(Buffer b:buffers)if(b.variant!=null) {
                String source=b.source.substring(b.source.lastIndexOf('\\')+1);
                String key=(b.variant+"_"+source).replaceAll("[^A-Za-z0-9_]","_");
                assemblies.computeIfAbsent(key,k->new ArrayList<>()).add(b);
            }
            for(Map.Entry<String,List<Buffer>> e:assemblies.entrySet()) {
                zip.putNextEntry(new ZipEntry("parts/variant_"+e.getKey()+".obj"));
                writeObj(e.getValue(),new OutputStreamWriter(zip,StandardCharsets.UTF_8));zip.closeEntry();
            }
            Buffer first=buffers.get(0);List<Buffer> primary=new ArrayList<>();
            for(Buffer b:buffers)if(b.world)primary.add(b);
            if(primary.isEmpty())for(Buffer b:buffers)
                if(b==first||(first.variant!=null&&first.variant.equals(b.variant)&&first.source.equals(b.source)))primary.add(b);
            zip.putNextEntry(new ZipEntry("models/primary_model.obj"));
            writeObj(primary,new OutputStreamWriter(zip,StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new ZipEntry("README.txt"));
            zip.write(("WrathPakViewer v2.5\nOpen models/primary_model.obj first.\n"
                +"parts/ contains individual buffers and character variants. For levels, the primary OBJ contains recovered world meshes; full terrain coverage and placement of standalone props remain unverified.\n"
                +"OBJ uses Y up, counterclockwise faces and generated vertex normals.\n"
                +"Models export includes PNG textures and MTL bindings. Source shader behavior, rigs, animation and PS2 support remain unfinished. See geometry_audit.txt.\n").getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new ZipEntry("geometry_audit.txt"));
            zip.write(audit().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
    }
    private static void triangle(Group g,int a,int b,int c,int vertices) throws Invalid {
        if(a>=vertices||b>=vertices||c>=vertices)throw new Invalid("Index exceeds vertex buffer");
        if(a!=b&&a!=c&&b!=c)g.faces.add(new int[]{a,c,b});
    }
    private static Group container(Reader r,int vertices) throws Invalid {
        Group g=new Group();int count=r.count(64);
        g.batches=count;
        for(int j=0;j<count;j++) {
            int[] list=r.indices();
            if(list.length%3!=0)throw new Invalid("Triangle list count is not divisible by three");
            g.listIndices+=list.length;
            for(int i=0;i<list.length;i+=3)triangle(g,list[i],list[i+1],list[i+2],vertices);
            int strips=r.count(64);
            for(int k=0;k<strips;k++) {
                int[] strip=r.indices();g.stripIndices+=strip.length;
                // Preserve parity across degenerate stitching vertices.
                for(int i=2;i<strip.length;i++)triangle(g,strip[i%2==0?i-2:i-1],strip[i%2==0?i-1:i-2],strip[i],vertices);
            }
            g.paletteEntries+=r.indices().length;
            if(r.uint()!=0)throw new Invalid("Unsupported batch trailer");
        }
        return g;
    }
    private static Group resource(byte[] data,int offset,int end,int base,int vertices) throws Invalid {
        Reader r=new Reader(data,offset,end);
        Group secondary=container(r,vertices-base),regular=container(r,base);
        for(int[] face:secondary.faces)for(int i=0;i<3;i++)face[i]+=base;
        secondary.faces.addAll(regular.faces);secondary.batches+=regular.batches;
        secondary.listIndices+=regular.listIndices;secondary.stripIndices+=regular.stripIndices;
        secondary.paletteEntries+=regular.paletteEntries;
        if(secondary.faces.isEmpty())throw new Invalid("Empty geometry");
        secondary.end=r.pos;return secondary;
    }
    private static String variant(String name) {
        Matcher m=VARIANT.matcher(name);
        if(!m.matches())return null;
        return m.group(1)+"_"+m.group(2)+(m.group(3).startsWith("L_V")?"_low":"_main");
    }
    private static final class Marker {
        int start,end;String name;
        Marker(int p,int q,String n){start=p;end=q;name=n;}
    }
    public static Result decode(byte[] data) throws IOException {
        if(data.length<28||u32(data,0)!=0x52049)throw new IOException("Unsupported PAK header; expected observed Xbox layout");
        Result result=new Result(data);List<Marker> markers=new ArrayList<>(),names=new ArrayList<>(),worlds=new ArrayList<>();
        List<Buffer> candidates=new ArrayList<>();
        for(int p=0;p+8<=data.length;p++) {
            if(bytes(data,p,"meshes\\")) {
                int q=p;while(q<data.length&&q-p<128&&(data[q]&255)>=32&&(data[q]&255)<=126)q++;
                String name=new String(data,p,q-p,StandardCharsets.US_ASCII);
                if(name.endsWith(".msh"))markers.add(new Marker(p,q,name));
            }
            if(p>=16&&bytes(data,p,"levels\\")) {
                long n=u32(data,p-4), size=u32(data,p-16), payload=u32(data,p-12);
                long end=p-16+size;
                if(n>=12&&n<=128&&p+n<=data.length&&end<=data.length&&size==payload+n+32) {
                    String name=new String(data,p,(int)n,StandardCharsets.US_ASCII).trim();
                    if(name.matches("levels\\\\[A-Za-z0-9_./\\\\-]+[.]sls"))worlds.add(new Marker(p,(int)end,name));
                }
            }
            if(p>=4&&data[p-3]==0&&data[p-2]==0&&data[p-1]==0
                    && ((data[p]>='A'&&data[p]<='Z')||(data[p]>='a'&&data[p]<='z'))) {
                long n=u32(data,p-4);
                if(n>=4&&n<=160&&p+n<=data.length) {
                    String name=new String(data,p,(int)n,StandardCharsets.US_ASCII);
                    if(NAME.matcher(name).matches())names.add(new Marker(p,p+(int)n,name));
                }
            }
            long cap=u32(data,p);
            if(cap<3||cap>65536)continue;
            boolean powerOfTwo=(cap&(cap-1))==0;
            Marker currentWorld=worlds.isEmpty()?null:worlds.get(worlds.size()-1);
            boolean inWorld=currentWorld!=null&&p<currentWorld.end;
            long n=u32(data,p+4);
            if(n<3||n>cap)continue;
            for(int stride=32;stride>=28;stride-=4) {
                if(stride==32&&!powerOfTwo||stride==28&&(!inWorld||cap!=n))continue;
                int padding=stride==32?16:8;
                long end=(long)p+8+stride*n;
                if(end+padding+8>data.length)continue;
                int e=(int)end;
                if(u32(data,e+padding+4)!=n)continue;
                long base=u32(data,e+padding);
                if(base==0||base>n)continue;
                boolean valid=true;
                for(int i=e;i<e+padding;i++)if(data[i]!=0){valid=false;break;}
                if(!valid)continue;
                for(int i=0;i<n&&valid;i++)for(int f:FLOAT_FIELDS) {
                    float value=f32(data,p+8+stride*i+f);
                    if(Float.isNaN(value)||Float.isInfinite(value)||Math.abs(value)>=100000){valid=false;break;}
                }
                if(valid) {
                    Buffer b=new Buffer();
                    b.offset=p;b.end=e;b.count=(int)n;b.baseCount=(int)base;b.stride=stride;
                    candidates.add(b);
                }
            }
        }
        int previous=0;
        for(Buffer b:candidates) {
            Marker marker=null;
            for(Marker m:markers){if(m.start>=b.offset)break;marker=m;}
            Marker world=null;
            for(Marker m:worlds)if(m.start<b.offset&&b.end<=m.end)world=m;
            boolean character=world==null&&marker!=null&&marker.name.startsWith("meshes\\chars\\");
            boolean prop=marker!=null&&marker.name.startsWith("meshes\\props\\");
            if(character&&(b.stride!=32||b.count<=100||u32(data,b.offset)<128))continue;
            if(!character&&!prop&&world==null)continue;
            if(b.offset<previous)continue;
            b.world=world!=null;
            b.source=b.world?world.name:marker.name;
            int floor=Math.max(previous,b.world?world.start:marker.end);
            int consumed=floor;Set<String> variants=new HashSet<>();
            for(Marker name:names) {
                if(name.start<consumed||name.start>=b.offset||(character&&!name.name.startsWith("C_")))continue;
                try {
                    Group g=resource(data,name.end,b.offset,b.baseCount,b.count);
                    g.name=name.name;g.offset=name.start;b.groups.add(g);consumed=g.end;variants.add(variant(g.name));
                } catch(Invalid invalid) {result.rejected.add(name.name+" @0x"+Integer.toHexString(name.start)+": "+invalid.getMessage());}
            }
            if(!b.groups.isEmpty()) {
                previous=b.end;
                b.variant=variants.size()==1?variants.iterator().next():null;
                result.buffers.add(b);
            }
        }
        return result;
    }
}
