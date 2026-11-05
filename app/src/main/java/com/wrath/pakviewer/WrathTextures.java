package com.wrath.pakviewer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Native MTX texture records observed in the Xbox PAKs: BC1 and BC3 mip chains. */
public final class WrathTextures {
    private WrathTextures() {}
    static long u32(byte[] b,int p){return (b[p]&255L)|((b[p+1]&255L)<<8)|((b[p+2]&255L)<<16)|((b[p+3]&255L)<<24);}
    static boolean has(byte[] b,int p,String s){if(p<0||p+s.length()>b.length)return false;for(int i=0;i<s.length();i++)if((b[p+i]&255)!=s.charAt(i))return false;return true;}
    static boolean power(int n){return n>0&&n<=4096&&(n&(n-1))==0;}
    public static final class Level { public int width,height,offset,size; }
    public static final class Texture {
        public String name,source;public int record,format;
        public final List<Level> levels=new ArrayList<>();
        public String stem(){return name.replaceAll("[^A-Za-z0-9_-]","_")+"_"+Integer.toHexString(record);}
    }
    public static List<Texture> find(byte[] data){
        List<Texture> result=new ArrayList<>();Set<Integer> seen=new HashSet<>();
        for(int p=0;p+4<data.length;p++){
            if(!has(data,p,".mtx"))continue;
            int begin=p;while(begin>0&&p-begin<125&&(data[begin-1]&255)>=32&&(data[begin-1]&255)<=126)begin--;
            int q=p+4;if(q+4>data.length)continue;long length=u32(data,q);
            if(length<1||length>31||q+8+length+71>data.length)continue;
            int n=(int)length,r=q+8+n;
            String name=new String(data,q+4,n,StandardCharsets.US_ASCII);
            if(!name.matches("[A-Za-z0-9_ .-]+")||!has(data,r,name)||data[r+n]!=0||u32(data,q+4+n)!=0)continue;
            Texture t=new Texture();t.name=name;t.source=new String(data,begin,p+4-begin,StandardCharsets.US_ASCII);t.record=r;
            int previousWidth=0,previousHeight=0;
            while(r+71<=data.length&&has(data,r,name)&&data[r+n]==0){
                int w=(int)u32(data,r+32),h=(int)u32(data,r+36),format=(int)u32(data,r+63);
                long size=u32(data,r+55);
                if(!power(w)||!power(h)||(format!=65&&format!=69)||u32(data,r+59)!=8||u32(data,r+67)!=1)break;
                long expected=((w+3L)/4)*((h+3L)/4)*(format==65?8:16);
                if(size!=expected||r+71L+size>data.length)break;
                if(!t.levels.isEmpty()&&(data[r-1]!=1||format!=t.format||w!=Math.max(1,previousWidth/2)||h!=Math.max(1,previousHeight/2)))break;
                t.format=format;Level level=new Level();level.width=w;level.height=h;level.offset=r+71;level.size=(int)size;t.levels.add(level);
                previousWidth=w;previousHeight=h;r+=72+(int)size;
                if(w==1&&h==1)break;
            }
            if(!t.levels.isEmpty()&&seen.add(t.record))result.add(t);
        }
        return result;
    }
    private static void le(OutputStream out,int n)throws IOException{for(int i=0;i<4;i++)out.write(n>>>(8*i));}
    public static void dds(byte[] data,Texture t,OutputStream out)throws IOException{
        Level top=t.levels.get(0);out.write(new byte[]{'D','D','S',' '});le(out,124);le(out,t.levels.size()>1?0xA1007:0x81007);
        le(out,top.height);le(out,top.width);le(out,top.size);le(out,0);le(out,t.levels.size());
        for(int i=0;i<11;i++)le(out,0);le(out,32);le(out,4);out.write((t.format==65?"DXT1":"DXT5").getBytes(StandardCharsets.US_ASCII));
        for(int i=0;i<5;i++)le(out,0);le(out,t.levels.size()>1?0x401008:0x1000);for(int i=0;i<4;i++)le(out,0);
        for(Level level:t.levels)out.write(data,level.offset,level.size);
    }
    private static int[] rgb(int value){int r=(value>>>11)&31,g=(value>>>5)&63,b=value&31;return new int[]{(r<<3)|(r>>>2),(g<<2)|(g>>>4),(b<<3)|(b>>>2),255};}
    public static byte[] rgba(byte[] data,Texture t){
        Level top=t.levels.get(0);int w=top.width,h=top.height;byte[] pixels=new byte[w*h*4];int p=top.offset;
        for(int by=0;by<h;by+=4)for(int bx=0;bx<w;bx+=4){
            int[] alpha=new int[8];long abits=0;
            if(t.format==69){alpha[0]=data[p]&255;alpha[1]=data[p+1]&255;for(int i=0;i<6;i++)abits|=(data[p+2+i]&255L)<<(8*i);
                if(alpha[0]>alpha[1])for(int i=2;i<8;i++)alpha[i]=((8-i)*alpha[0]+(i-1)*alpha[1])/7;
                else{for(int i=2;i<6;i++)alpha[i]=((6-i)*alpha[0]+(i-1)*alpha[1])/5;alpha[6]=0;alpha[7]=255;}p+=8;}
            int c0=(data[p]&255)|((data[p+1]&255)<<8),c1=(data[p+2]&255)|((data[p+3]&255)<<8);
            int[][] colors={rgb(c0),rgb(c1),new int[4],new int[4]};
            if(c0>c1||t.format==69){for(int k=0;k<3;k++){colors[2][k]=(2*colors[0][k]+colors[1][k])/3;colors[3][k]=(colors[0][k]+2*colors[1][k])/3;}colors[2][3]=colors[3][3]=255;}
            else{for(int k=0;k<3;k++)colors[2][k]=(colors[0][k]+colors[1][k])/2;colors[2][3]=255;}
            long bits=u32(data,p+4);p+=8;
            for(int i=0;i<16;i++){int x=bx+(i&3),y=by+(i>>>2);if(x>=w||y>=h)continue;int[] c=colors[(int)((bits>>>(2*i))&3)];int target=4*(y*w+x);
                for(int k=0;k<3;k++)pixels[target+k]=(byte)c[k];pixels[target+3]=(byte)(t.format==69?alpha[(int)((abits>>>(3*i))&7)]:c[3]);}
        }return pixels;
    }
    private static void be(OutputStream out,int n)throws IOException{for(int i=3;i>=0;i--)out.write(n>>>(8*i));}
    private static void chunk(OutputStream out,String type,byte[] bytes)throws IOException{
        be(out,bytes.length);byte[] tag=type.getBytes(StandardCharsets.US_ASCII);out.write(tag);out.write(bytes);CRC32 crc=new CRC32();crc.update(tag);crc.update(bytes);be(out,(int)crc.getValue());
    }
    public static void png(byte[] data,Texture t,OutputStream out)throws IOException{png(data,t,out,false);}
    public static void png(byte[] data,Texture t,OutputStream out,boolean opaque)throws IOException{
        Level top=t.levels.get(0);byte[] pixels=rgba(data,t);if(opaque)for(int i=3;i<pixels.length;i+=4)pixels[i]=(byte)255;out.write(new byte[]{(byte)137,80,78,71,13,10,26,10});
        ByteArrayOutputStream header=new ByteArrayOutputStream();be(header,top.width);be(header,top.height);header.write(new byte[]{8,6,0,0,0});chunk(out,"IHDR",header.toByteArray());
        ByteArrayOutputStream compressed=new ByteArrayOutputStream();try(DeflaterOutputStream z=new DeflaterOutputStream(compressed)){for(int y=0;y<top.height;y++){z.write(0);z.write(pixels,y*top.width*4,top.width*4);}}
        chunk(out,"IDAT",compressed.toByteArray());chunk(out,"IEND",new byte[0]);
    }
    public static void writeZip(byte[] data,List<Texture> textures,ZipOutputStream zip)throws IOException{
        StringBuilder index=new StringBuilder("name,record_offset,width,height,format,mip_levels,png,dds\n");
        for(Texture t:textures){String stem="textures/"+t.stem();zip.putNextEntry(new ZipEntry(stem+".png"));png(data,t,zip);zip.closeEntry();
            zip.putNextEntry(new ZipEntry(stem+".dds"));dds(data,t,zip);zip.closeEntry();Level l=t.levels.get(0);
            index.append(t.name).append(',').append(t.record).append(',').append(l.width).append(',').append(l.height).append(',').append(t.format==65?"BC1":"BC3").append(',').append(t.levels.size()).append(',').append(stem).append(".png,").append(stem).append(".dds\n");}
        zip.putNextEntry(new ZipEntry("textures/index.csv"));zip.write(index.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
    public static void materials(byte[] data,List<Texture> textures,WrathGeometry.Result geometry,ZipOutputStream zip)throws IOException{
        Map<String,Texture> names=new HashMap<>();for(Texture t:textures)if(!names.containsKey(t.name.toLowerCase(Locale.ROOT)))names.put(t.name.toLowerCase(Locale.ROOT),t);
        Set<String> materials=new TreeSet<>();for(WrathGeometry.Buffer b:geometry.buffers)for(WrathGeometry.Group g:b.groups)materials.add(material(g.name));
        StringBuilder mtl=new StringBuilder("# Native MTX textures matched by exact serialized material name.\n");
        Set<String> written=new HashSet<>();
        for(String name:materials){mtl.append("newmtl ").append(name).append("\nKa 0.2 0.2 0.2\nKd 1 1 1\nKs 0 0 0\nd 1\nillum 1\n");Texture t=names.get(name);if(t!=null){String diffuse=t.stem()+"_diffuse.png";if(written.add(diffuse)){zip.putNextEntry(new ZipEntry("textures/"+diffuse));png(data,t,zip,true);zip.closeEntry();}mtl.append("map_Kd ../textures/").append(diffuse).append("\n");}mtl.append('\n');}
        for(String dir:new String[]{"models/","parts/"}){zip.putNextEntry(new ZipEntry(dir+"materials.mtl"));zip.write(mtl.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
    }
    public static String material(String name){return name.substring(name.indexOf(':')+1).toLowerCase(Locale.ROOT).replaceAll("[^A-Za-z0-9_-]","_");}
}
