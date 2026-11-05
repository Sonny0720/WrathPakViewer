package com.wrath.pakviewer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
  private static final int OPEN=10, SAVE=11;
  private byte[] data;
  private String fileName="unknown.pak", report="";
  private TextView output, status;
  private EditText filter, hexOffset;
  private Spinner mode;
  private final ArrayList<String> strings=new ArrayList<>();
  private final ArrayList<Asset> waves=new ArrayList<>();
  private String pendingType;
  private byte[] pendingBytes;
  private static class Asset { int offset, length, format; Asset(int o,int l,int f){offset=o;length=l;format=f;} }

  @Override public void onCreate(Bundle state){super.onCreate(state);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(16,12,16,12);
    TextView title=new TextView(this);title.setText("Wrath Unleashed PAK Inspector · v0.2");title.setTextSize(20);root.addView(title);
    Button open=button(root,"OPEN .PAK FILE");open.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,OPEN);});
    status=new TextView(this);status.setText("No PAK loaded");root.addView(status);
    mode=new Spinner(this);mode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Audio assets","Readable strings"}));root.addView(mode);
    filter=new EditText(this);filter.setSingleLine(true);filter.setHint("Search names / offsets / strings");root.addView(filter);
    button(root,"SEARCH / LIST").setOnClickListener(v->showResults());
    button(root,"EXPORT ALL (ZIP)").setOnClickListener(v->requestSave("all","wrath_all_assets.zip","application/zip"));
    button(root,"EXPORT AUDIO (ZIP)").setOnClickListener(v->requestSave("audio","wrath_audio.zip","application/zip"));
    button(root,"EXPORT STRING INDEX (CSV)").setOnClickListener(v->requestSave("csv","wrath_strings.csv","text/csv"));
    LinearLayout hexRow=new LinearLayout(this);hexRow.setOrientation(LinearLayout.HORIZONTAL);
    hexOffset=new EditText(this);hexOffset.setSingleLine(true);hexOffset.setHint("Hex offset e.g. 44DE11");hexRow.addView(hexOffset,new LinearLayout.LayoutParams(0,-2,1));
    Button hex=button(hexRow,"VIEW HEX");hex.setOnClickListener(v->showHex());root.addView(hexRow);
    output=new TextView(this);output.setTypeface(Typeface.MONOSPACE);output.setTextSize(12);output.setText("Open a PAK file. Export All preserves the original PAK and exports verified embedded WAVE resources; other assets require format research.");
    ScrollView scroll=new ScrollView(this);scroll.addView(output);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){showResults();}});
  }
  private Button button(LinearLayout parent,String label){Button b=new Button(this);b.setText(label);parent.addView(b);return b;}
  private void requestSave(String type,String name,String mime){if(data==null){Toast.makeText(this,"Open a PAK first",Toast.LENGTH_SHORT).show();return;}pendingType=type;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType(mime);i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,SAVE);}
  @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);if(result!=RESULT_OK||intent==null||intent.getData()==null)return;
    if(request==OPEN){Uri uri=intent.getData();fileName="selected.pak";try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())fileName=c.getString(0);}catch(Exception ignored){}
      status.setText("Reading...");new Thread(()->{try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1){bytes.write(buf,0,n);if(bytes.size()>150_000_000)throw new IOException("PAK exceeds 150 MB memory limit");}data=bytes.toByteArray();scan();runOnUiThread(()->{status.setText(fileName+" · "+data.length+" bytes");showResults();});}catch(Exception e){runOnUiThread(()->status.setText("Open failed: "+e.getMessage()));}}).start();
    }else if(request==SAVE){Uri uri=intent.getData();String type=pendingType;status.setText("Exporting...");new Thread(()->{try(OutputStream raw=getContentResolver().openOutputStream(uri)){if(raw==null)throw new IOException("Cannot open destination");if("csv".equals(type)){writeCsv(raw);}else{try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(raw))){writeZip(zip,"all".equals(type));}}runOnUiThread(()->status.setText("Export complete: "+type));}catch(Exception e){runOnUiThread(()->status.setText("Export failed: "+e.getMessage()));}}).start();}
  }
  private boolean has(int at,String magic){if(at<0||at+magic.length()>data.length)return false;for(int j=0;j<magic.length();j++)if((data[at+j]&255)!=magic.charAt(j))return false;return true;}
  private long u32(int p){return ((long)data[p]&255)|(((long)data[p+1]&255)<<8)|(((long)data[p+2]&255)<<16)|(((long)data[p+3]&255)<<24);}
  private int u16(int p){return (data[p]&255)|((data[p+1]&255)<<8);}
  private void scan(){strings.clear();waves.clear();int i=0;while(i<data.length){int start=i;while(i<data.length&&(data[i]&255)>=32&&(data[i]&255)<=126)i++;if(i-start>=4){strings.add(String.format(Locale.US,"0x%08X  %s",start,new String(data,start,Math.min(i-start,300),StandardCharsets.US_ASCII)));}i++;}
    for(i=0;i+12<=data.length;i++){if(!has(i,"RIFF")||!has(i+8,"WAVE"))continue;long riffSize=u32(i+4);long end=(long)i+8+riffSize;if(riffSize<4||end>data.length||end<=i)continue;
      boolean fmt=false,chunkOK=true;int format=0;long cursor=(long)i+12;int count=0;
      while(cursor+8<=end&&count++<10000){int pos=(int)cursor;long len=u32(pos+4);long next=cursor+8+len+(len&1);if(next>end||next<=cursor){chunkOK=false;break;}if(has(pos,"fmt ")&&len>=16){format=u16(pos+8);fmt=true;}cursor=next;}
      if(fmt&&chunkOK&&cursor==end){waves.add(new Asset(i,(int)(end-i),format));i=(int)end-1;}
    }
    report="File: "+fileName+"\nSize: "+data.length+" bytes\nReadable strings: "+strings.size()+"\nValidated RIFF/WAVE containers: "+waves.size()+"\n\nWAVE format tags may require special decoders.\nModels, animations, and textures are not yet decoded.\nExport All includes the untouched original PAK so unknown data is preserved.\n\n";
  }
  private void showResults(){if(output==null||data==null)return;String query=filter.getText().toString().toLowerCase(Locale.ROOT);StringBuilder sb=new StringBuilder(report);int shown=0;
    if(mode.getSelectedItemPosition()==0){for(int j=0;j<waves.size();j++){Asset a=waves.get(j);String line=String.format(Locale.US,"audio_%03d.wav  offset 0x%08X  size %d  format 0x%04X",j+1,a.offset,a.length,a.format);if(line.toLowerCase(Locale.ROOT).contains(query)){sb.append(line).append('\n');if(++shown>=400)break;}}}
    else{for(String s:strings){if(s.toLowerCase(Locale.ROOT).contains(query)){sb.append(s).append('\n');if(++shown>=400)break;}}}
    if(shown>=400)sb.append("\nShowing first 400 results; narrow the search.");output.setText(sb.toString());}
  private void showHex(){if(data==null)return;try{String s=hexOffset.getText().toString().trim().replaceFirst("(?i)^0x","");long val=Long.parseLong(s,16);if(val<0||val>=data.length)throw new IllegalArgumentException("Offset outside PAK");int off=(int)val;StringBuilder sb=new StringBuilder(String.format(Locale.US,"Hex at 0x%08X\n\n",off));for(int p=off;p<Math.min(data.length,off+512);p+=16){sb.append(String.format(Locale.US,"%08X  ",p));for(int k=0;k<16;k++)sb.append(p+k<data.length?String.format(Locale.US,"%02X ",data[p+k]&255):"   ");sb.append("  ");for(int k=0;k<16&&p+k<data.length;k++){int c=data[p+k]&255;sb.append(c>=32&&c<=126?(char)c:'.');}sb.append('\n');}output.setText(sb.toString());}catch(Exception e){Toast.makeText(this,"Enter a valid hex offset",Toast.LENGTH_SHORT).show();}}
  private void writeCsv(OutputStream out)throws IOException{out.write("offset_hex,text\n".getBytes(StandardCharsets.UTF_8));for(String line:strings){int split=line.indexOf("  ");if(split<0)continue;String escaped=line.substring(split+2).replace("\"","\"\"");out.write((line.substring(0,split)+",\""+escaped+"\"\n").getBytes(StandardCharsets.UTF_8));}}
  private void addText(ZipOutputStream zip,String path,String text)throws IOException{zip.putNextEntry(new ZipEntry(path));zip.write(text.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
  private void writeZip(ZipOutputStream zip,boolean all)throws IOException{
    StringBuilder index=new StringBuilder("filename,offset_hex,length_bytes,wave_format_tag\n");for(int j=0;j<waves.size();j++){Asset a=waves.get(j);String name=String.format(Locale.US,"audio/audio_%03d.wav",j+1);zip.putNextEntry(new ZipEntry(name));zip.write(data,a.offset,a.length);zip.closeEntry();index.append(String.format(Locale.US,"%s,0x%08X,%d,0x%04X\n",name,a.offset,a.length,a.format));}
    addText(zip,"audio_index.csv",index.toString());
    if(all){zip.putNextEntry(new ZipEntry("original/"+fileName.replace('/','_').replace('\\','_')));zip.write(data);zip.closeEntry();addText(zip,"README.txt","Wrath PAK Inspector v0.2\nAudio entries are validated RIFF/WAVE containers, not necessarily playable PCM.\nThe original PAK is preserved byte-for-byte, including all models, animations, textures and unidentified data.\nIndividual non-audio resources are not yet identified or decoded.\n");}
  }
}