package com.wrath.pakviewer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Observed Xbox character layout. Geometry only: no rig, textures or PS2 claim. */
public final class WrathGeometry {
    private WrathGeometry() {}
    private static final int[] FLOAT_FIELDS={0,4,8,16,20};
    private static final Pattern NAME = Pattern.compile("C_[A-Za-z_0-9]+:[A-Za-z][A-Za-z_0-9]*");
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
        public int offset,end,count; public String source,variant;
        public final List<Group> groups=new ArrayList<>();
    }
    public static final class Result {
        private final byte[] data;
        public final List<Buffer> buffers=new ArrayList<>();
        public final List<String> rejected=new ArrayList<>();
        private Result(byte[] b) { data=b; }
        public String audit() {
            StringBuilder s=new StringBuilder("Xbox geometry only. Y up; UV V flipped for OBJ.\n"
                +"Rig, textures, animation, PS2 layout and completeness are not validated.\n"
                +"Variant assembly follows serialized names; individual buffers are preserved.\n");
            for(Buffer b:buffers) {
                Set<Integer> used=new HashSet<>();int triangles=0;
                for(Group g:b.groups)for(int[] t:g.faces){triangles++;for(int i:t)used.add(i);}
                s.append(String.format(Locale.US,"buffer=0x%X vertices=%d referenced=%d triangles=%d source=%s variant=%s\n",
                    b.offset,b.count,used.size(),triangles,b.source,b.variant));
                for(Group g:b.groups)s.append(String.format(Locale.US,
                    "  name=%s offset=0x%X end=0x%X batches=%d list_indices=%d strip_indices=%d palette_entries=%d triangles=%d\n",
                    g.name,g.offset,g.end,g.batches,g.listIndices,g.stripIndices,g.paletteEntries,g.faces.size()));
            }
            for(String reason:rejected)s.append("REJECTED ").append(reason).append('\n');
            return s.toString();
        }
        public void writeObj(List<Buffer> selection,Writer writer) throws IOException {
            int base=0;
            writer.write("# Xbox geometry; Y up. Rig/materials not recovered.\n");
            for(Buffer b:selection) {
                for(int i=0;i<b.count;i++) {
                    int p=b.offset+8+32*i;
                    writer.write("v "+Float.toString(f32(data,p))+" "+Float.toString(f32(data,p+4))+" "+Float.toString(f32(data,p+8))+"\n");
                }
                for(int i=0;i<b.count;i++) {
                    int p=b.offset+8+32*i;
                    writer.write("vt "+Float.toString(f32(data,p+16))+" "+Double.toString(1.0-f32(data,p+20))+"\n");
                }
                for(Group g:b.groups) {
                    writer.write("g "+g.name.replaceAll("[^A-Za-z0-9_]","_")+"_"+Integer.toHexString(g.offset)+"\n");
                    for(int[] t:g.faces) {
                        writer.write("f");
                        for(int v:t){int n=base+v+1;writer.write(" "+n+"/"+n);}
                        writer.write("\n");
                    }
                }
                base+=b.count;
            }
            writer.flush();
        }
        public void writeZip(ZipOutputStream zip) throws IOException {
            if(buffers.isEmpty())throw new IOException("No supported Xbox character geometry found");
            for(int i=0;i<buffers.size();i++) {
                zip.putNextEntry(new ZipEntry(String.format(Locale.US,"buffers/buffer_%02d.obj",i)));
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
                zip.putNextEntry(new ZipEntry("assembled/"+e.getKey()+".obj"));
                writeObj(e.getValue(),new OutputStreamWriter(zip,StandardCharsets.UTF_8));zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("geometry_audit.txt"));
            zip.write(audit().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
    }
    private static void triangle(Group g,int a,int b,int c,int vertices) throws Invalid {
        if(a>=vertices||b>=vertices||c>=vertices)throw new Invalid("Index exceeds vertex buffer");
        if(a!=b&&a!=c&&b!=c)g.faces.add(new int[]{a,b,c});
    }
    private static Group container(Reader r,int vertices) throws Invalid {
        Group g=new Group();int count=r.count(64);
        if(count==0)throw new Invalid("Empty draw container");
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
    private static Group resource(byte[] data,int offset,int end,int vertices) throws Invalid {
        Reader r=new Reader(data,offset,end);
        if(offset+8<=end&&u32(data,offset)==4&&u32(data,offset+4)==0)r.pos+=8;
        Group g=container(r,vertices);
        while(r.pos+8<=end&&u32(data,r.pos)==4&&u32(data,r.pos+4)>=1&&u32(data,r.pos+4)<=4) {
            Reader next=new Reader(data,r.pos,end);Group extra;
            extra=container(next,vertices);
            g.faces.addAll(extra.faces);g.batches+=extra.batches;g.listIndices+=extra.listIndices;
            g.stripIndices+=extra.stripIndices;g.paletteEntries+=extra.paletteEntries;r.pos=next.pos;
        }
        if(g.faces.isEmpty())throw new Invalid("Empty geometry");
        g.end=r.pos;return g;
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
        Result result=new Result(data);List<Marker> markers=new ArrayList<>(),names=new ArrayList<>();
        List<Buffer> candidates=new ArrayList<>();
        for(int p=0;p+8<=data.length;p++) {
            if(bytes(data,p,"meshes\\")) {
                int q=p;while(q<data.length&&q-p<128&&(data[q]&255)>=32&&(data[q]&255)<=126)q++;
                String name=new String(data,p,q-p,StandardCharsets.US_ASCII);
                if(name.endsWith(".msh"))markers.add(new Marker(p,q,name));
            }
            if(p>=4&&bytes(data,p,"C_")) {
                long n=u32(data,p-4);
                if(n>=4&&n<=160&&p+n<=data.length) {
                    String name=new String(data,p,(int)n,StandardCharsets.US_ASCII);
                    if(NAME.matcher(name).matches())names.add(new Marker(p,p+(int)n,name));
                }
            }
            long cap=u32(data,p);
            if(cap<128||cap>65536||(cap&(cap-1))!=0)continue;
            long n=u32(data,p+4),end=(long)p+8+32*n;
            if(n<=100||n>cap||end+24>data.length)continue;
            int e=(int)end;boolean valid=true;
            for(int i=e;i<e+16;i++)if(data[i]!=0){valid=false;break;}
            if(!valid||u32(data,e+20)!=n)continue;
            for(int i=0;i<n&&valid;i++)for(int f:FLOAT_FIELDS) {
                float value=f32(data,p+8+32*i+f);
                if(Float.isNaN(value)||Float.isInfinite(value)||Math.abs(value)>=100000){valid=false;break;}
            }
            if(valid){Buffer b=new Buffer();b.offset=p;b.end=e;b.count=(int)n;candidates.add(b);}
        }
        int previous=0;
        for(Buffer b:candidates) {
            Marker marker=null;for(Marker m:markers){if(m.start>=b.offset)break;marker=m;}
            int floor=Math.max(previous,marker==null?0:marker.end);previous=b.end;
            if(marker==null||!marker.name.toLowerCase(Locale.ROOT).startsWith("meshes\\chars\\"))continue;
            b.source=marker.name;int consumed=floor;Set<String> variants=new HashSet<>();
            for(Marker name:names) {
                if(name.start<consumed||name.start>=b.offset)continue;
                try {
                    Group g=resource(data,name.end,b.offset,b.count);
                    g.name=name.name;g.offset=name.start;b.groups.add(g);consumed=g.end;variants.add(variant(g.name));
                } catch(Invalid invalid) {result.rejected.add(name.name+" @0x"+Integer.toHexString(name.start)+": "+invalid.getMessage());}
            }
            if(!b.groups.isEmpty()) {
                b.variant=variants.size()==1?variants.iterator().next():null;
                result.buffers.add(b);
            }
        }
        return result;
    }
}
