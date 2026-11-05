package com.wrath.pakviewer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import java.io.*;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
  private static final int OPEN=10, SAVE=11, REPACK_OPEN=12, REPACK_SAVE=13;
  private byte[] data;
  private String fileName="unknown.pak";
  private TextView output,status;
  private String pendingType;
  private boolean busy;
  private WrathRepack.Result pendingRepack;

  @Override public void onCreate(Bundle state){super.onCreate(state);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(16,12,16,12);
    TextView title=new TextView(this);title.setText("WrathPakViewer · v2.5");title.setTextSize(22);root.addView(title);
    Button open=button(root,"OPEN PAK FILE");open.setOnClickListener(v->{
      if(busy){Toast.makeText(this,"Wait for the current operation",Toast.LENGTH_SHORT).show();return;}
      busy=true;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,OPEN);
    });
    status=new TextView(this);status.setText("No PAK loaded");root.addView(status);
    button(root,"EXPORT EVERYTHING").setOnClickListener(v->requestSave("all","wrath_everything.zip","application/zip"));
    button(root,"EXPORT MODELS").setOnClickListener(v->requestSave("models","wrath_models.zip","application/zip"));
    button(root,"EXPORT TEXTURES").setOnClickListener(v->requestSave("textures","wrath_textures.zip","application/zip"));
    button(root,"EXPORT RIGS").setOnClickListener(v->requestSave("rigs","wrath_rigs.zip","application/zip"));
    button(root,"EXPORT ANIMATION DATA").setOnClickListener(v->requestSave("animation","wrath_animation.zip","application/zip"));
    button(root,"EXPORT AUDIO").setOnClickListener(v->requestSave("audio","wrath_audio.zip","application/zip"));
    button(root,"REPACK ZIP TO PAK").setOnClickListener(v->{
      if(busy){Toast.makeText(this,"Wait for the current operation",Toast.LENGTH_SHORT).show();return;}
      busy=true;
      Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);
      startActivityForResult(i,REPACK_OPEN);
    });
    output=new TextView(this);output.setText("Models include OBJ, MTL and matching textures. Textures export as PNG and DDS. Rigs export skeleton tables and previews. Animation exports JSON with packed tracks. Audio exports WAV files. Export Everything preserves audio, metadata and the original archive.");root.addView(output);
    ScrollView page=new ScrollView(this);page.addView(root);setContentView(page);
  }
  private Button button(LinearLayout parent,String label){Button b=new Button(this);b.setText(label);parent.addView(b);return b;}
  private void requestSave(String type,String name,String mime){
    if(busy){Toast.makeText(this,"Wait for the current operation",Toast.LENGTH_SHORT).show();return;}
    if(data==null){Toast.makeText(this,"Open a PAK first",Toast.LENGTH_SHORT).show();return;}
    busy=true;pendingType=type;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType(mime);i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,SAVE);
  }
  @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);
    if(result!=RESULT_OK||intent==null||intent.getData()==null){pendingRepack=null;busy=false;return;}
    if(request==REPACK_OPEN){prepareRepack(intent.getData());return;}
    if(request==REPACK_SAVE){saveRepack(intent.getData());return;}
    if(request==OPEN){data=null;Uri uri=intent.getData();fileName="selected.pak";
      try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){String name=c.getString(0);if(name!=null)fileName=name;}}catch(Exception ignored){}
      status.setText("Reading...");new Thread(()->{
        try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
          if(in==null)throw new IOException("Cannot open source");byte[] buf=new byte[65536];int n;
          while((n=in.read(buf))!=-1){bytes.write(buf,0,n);if(bytes.size()>150_000_000)throw new IOException("PAK exceeds 150 MB memory limit");}
          byte[] loaded=bytes.toByteArray();int count=WrathTextures.find(loaded).size();data=loaded;
          runOnUiThread(()->{busy=false;status.setText(fileName+" · "+data.length+" bytes · "+count+" native textures");});
        }catch(Exception e){runOnUiThread(()->{busy=false;status.setText("Open failed: "+e.getMessage());});}
      }).start();
    }else if(request==SAVE){Uri uri=intent.getData();String type=pendingType;final byte[] exportData=data;final String sourceName=fileName;
      status.setText("Exporting "+type+"...");new Thread(()->{
        try(OutputStream raw=getContentResolver().openOutputStream(uri)){
          if(raw==null)throw new IOException("Cannot open destination");
          try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(raw))){WrathExports.write(exportData,sourceName,type,zip);}
          runOnUiThread(()->{busy=false;status.setText("Export complete: "+type);});
        }catch(Exception e){runOnUiThread(()->{busy=false;status.setText("Export failed: "+e.getMessage());});}
      }).start();
    }else busy=false;
  }
  private void prepareRepack(Uri uri){
    status.setText("Reading export ZIP...");
    new Thread(()->{
      File temporary=null;
      try {
        temporary=File.createTempFile("wrath-repack-", ".zip", getCacheDir());
        try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temporary)){
          if(in==null)throw new IOException("Cannot open ZIP");
          byte[] buffer=new byte[65536];
          long total=0;
          for(int count;(count=in.read(buffer))!=-1;){
            total+=count;
            if(total>512_000_000)throw new IOException("ZIP exceeds the 512 MB limit");
            out.write(buffer,0,count);
          }
        }
        WrathRepack.Result repacked=WrathRepack.read(temporary.toPath());
        runOnUiThread(()->{
          pendingRepack=repacked;
          Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/octet-stream");
          i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,repacked.name);
          startActivityForResult(i,REPACK_SAVE);
        });
      }catch(Exception error){
        runOnUiThread(()->{busy=false;status.setText("Repack failed: "+error.getMessage());});
      }finally{if(temporary!=null)temporary.delete();}
    }).start();
  }

  private void saveRepack(Uri uri){
    WrathRepack.Result repacked=pendingRepack;
    pendingRepack=null;
    if(repacked==null){busy=false;status.setText("Open the export ZIP again to repack it.");return;}
    status.setText("Saving PAK...");
    new Thread(()->{
      try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
        if(out==null)throw new IOException("Cannot open destination");
        out.write(repacked.data);
      }catch(Exception error){
        runOnUiThread(()->{busy=false;status.setText("Save failed: "+error.getMessage());});
        return;
      }
      runOnUiThread(()->{busy=false;status.setText("PAK saved. DDS textures changed: "+repacked.changedTextures);});
    }).start();
  }

}
