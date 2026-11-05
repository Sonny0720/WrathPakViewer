package com.wrath.pakviewer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** The same category writer is used for individual exports and Export Everything. */
public final class WrathExports {
    private WrathExports() {}
    public static final String[] CATEGORIES={"models","textures","rigs","animation","other"};
    static void text(ZipOutputStream zip,String name,String value)throws IOException{
        zip.putNextEntry(new ZipEntry(name));zip.write(value.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
    public static void write(byte[] data,String fileName,String category,ZipOutputStream zip)throws IOException{
        if("all".equals(category)){
            for(String name:CATEGORIES){
                zip.putNextEntry(new ZipEntry(name+".zip"));
                OutputStream nonClosing=new FilterOutputStream(zip){@Override public void write(byte[] b,int off,int length)throws IOException{out.write(b,off,length);}@Override public void close()throws IOException{flush();}};
                try(ZipOutputStream child=new ZipOutputStream(nonClosing)){
                    try{write(data,fileName,name,child);}catch(IOException error){text(child,"EXPORT_ERROR.txt",error.getMessage()+"\nOriginal bytes are preserved in other.zip.\n");}
                }
                zip.closeEntry();
            }
            text(zip,"README.txt","WrathPakViewer 2.2\nModels, textures, rigs and animation also have individual export buttons.\nOpen models.zip/models/primary_model.obj with its MTL and textures in place.\nRigs contain skeleton data. Animation contains indexed raw records.\nOther.zip contains audio, metadata and the untouched original archive, including undecoded data.\n");
        }else if("models".equals(category)){
            WrathGeometry.Result geometry=WrathGeometry.decode(data);Set<String> needed=new HashSet<>();
            for(WrathGeometry.Buffer b:geometry.buffers)for(WrathGeometry.Group g:b.groups)needed.add(WrathTextures.material(g.name));
            List<WrathTextures.Texture> textures=new ArrayList<>();for(WrathTextures.Texture t:WrathTextures.find(data))if(needed.contains(WrathTextures.material(t.name)))textures.add(t);
            geometry.writeZip(zip);WrathTextures.writeZip(data,textures,zip);WrathTextures.materials(data,textures,geometry,zip);
        }else if("textures".equals(category)){
            List<WrathTextures.Texture> textures=WrathTextures.find(data);WrathTextures.writeZip(data,textures,zip);
            text(zip,"README.txt","Native Xbox MTX textures: "+textures.size()+"\nPNG files contain decoded full-resolution RGBA images. DDS files retain validated BC1/BC3 mip chains.\nOnly supported named records with matching dimensions, block sizes and bounds are exported.\nZero textures means this serialization is unsupported; no arbitrary bytes are renamed as images.\n");
        }else if("rigs".equals(category)){
            WrathMotion.rigs(data,zip);
        }else if("animation".equals(category)){
            WrathMotion.animations(data,zip);
        }else if("other".equals(category)){
            String safe=fileName.replaceAll("[^A-Za-z0-9._-]","_");
            zip.putNextEntry(new ZipEntry("original/"+safe));zip.write(data);zip.closeEntry();
            text(zip,"metadata/readable_strings.csv",strings(data));
            StringBuilder header=new StringBuilder("offset,value_u32_le\n");for(int p=0;p+4<=Math.min(8192,data.length);p+=4)header.append(p).append(',').append(WrathTextures.u32(data,p)).append('\n');
            text(zip,"metadata/header.csv",header.toString());audio(data,zip);
            text(zip,"README.txt","Audio files are bounded, chunk-validated RIFF/WAVE containers; some use Xbox ADPCM.\nThe original archive preserves every byte, including undecoded rigs, animation, textures and unknown resources.\nMetadata strings are evidence, not decoded asset boundaries.\n");
        }else throw new IOException("Unknown export category: "+category);
    }
    private static String strings(byte[] data){
        StringBuilder s=new StringBuilder("offset,text\n");int hits=0;
        for(int p=0;p<data.length&&hits<100000;){int start=p;while(p<data.length&&(data[p]&255)>=32&&(data[p]&255)<=126)p++;
            if(p-start>=4){String value=new String(data,start,Math.min(p-start,300),StandardCharsets.US_ASCII);
                s.append(start).append(",\"").append(value.replace("\"","\"\"")).append("\"\n");
                hits++;}
            p++;}
        return s.toString();
    }
    private static void audio(byte[] data,ZipOutputStream zip)throws IOException{
        StringBuilder index=new StringBuilder("file,offset,length,format_tag\n");int number=0;
        for(int p=0;p+12<=data.length;p++){
            if(!WrathTextures.has(data,p,"RIFF")||!WrathTextures.has(data,p+8,"WAVE"))continue;
            long end=p+8L+WrathTextures.u32(data,p+4);if(end>data.length||end<p+12)continue;long cursor=p+12;boolean fmt=false,valid=true;int format=0,chunks=0;
            while(cursor+8<=end&&chunks++<10000){int at=(int)cursor;long n=WrathTextures.u32(data,at+4),next=cursor+8+n+(n&1);
                if(next>end||next<=cursor){valid=false;break;}if(WrathTextures.has(data,at,"fmt ")&&n>=16){fmt=true;format=(data[at+8]&255)|((data[at+9]&255)<<8);}cursor=next;}
            if(valid&&fmt&&cursor==end){String name=String.format(Locale.US,"audio/audio_%03d.wav",++number);zip.putNextEntry(new ZipEntry(name));zip.write(data,p,(int)end-p);zip.closeEntry();index.append(name).append(',').append(p).append(',').append(end-p).append(',').append(format).append('\n');p=(int)end-1;}
        }text(zip,"audio/index.csv",index.toString());
    }
}
