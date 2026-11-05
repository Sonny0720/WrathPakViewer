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
  private final ArrayList<Asset> textures=new ArrayList<>();
  private final ArrayList<Asset> regions=new ArrayList<>();
  private final ArrayList<String> signatures=new ArrayList<>();
  private final ArrayList<String> indexedNames=new ArrayList<>();
  private final ArrayList<String> assetClues=new ArrayList<>();
  private final ArrayList<String> nameReferences=new ArrayList<>();
  private final ArrayList<String> blockMap=new ArrayList<>();
  private final ArrayList<String> audioDetails=new ArrayList<>();
  private String pendingType;
  private byte[] pendingBytes;
  private static class Asset { int offset, length, format; Asset(int o,int l,int f){offset=o;length=l;format=f;} }

  @Override public void onCreate(Bundle state){super.onCreate(state);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(16,12,16,12);
    TextView title=new TextView(this);title.setText("Wrath Unleashed PAK Inspector · v1.4");title.setTextSize(20);root.addView(title);
    Button open=button(root,"OPEN .PAK FILE");open.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,OPEN);});
    status=new TextView(this);status.setText("No PAK loaded");root.addView(status);
    mode=new Spinner(this);mode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Audio assets","Non-audio binary regions","Other signatures","Readable strings","PAK indexed names","Asset name clues","PAK structure","Validated textures","Name references","Binary block map","WAVE format details","Reference contexts","Float triplet candidates"}));root.addView(mode);
    filter=new EditText(this);filter.setSingleLine(true);filter.setHint("Search names / offsets / strings");root.addView(filter);
    button(root,"SEARCH / LIST").setOnClickListener(v->showResults());
    button(root,"EXPORT FLOAT TRIPLETS (CSV)").setOnClickListener(v->requestSave("triplets","wrath_float_triplet_candidates.csv","text/csv"));
    button(root,"EXPORT REFERENCE CONTEXTS (CSV)").setOnClickListener(v->requestSave("contexts","wrath_reference_contexts.csv","text/csv"));
    button(root,"EXPORT WAVE DETAILS (CSV)").setOnClickListener(v->requestSave("wavedetails","wrath_wave_details.csv","text/csv"));
    button(root,"EXPORT BINARY BLOCK MAP (CSV)").setOnClickListener(v->requestSave("blocks","wrath_binary_blocks.csv","text/csv"));
    button(root,"EXPORT NAME REFERENCES (CSV)").setOnClickListener(v->requestSave("references","wrath_name_references.csv","text/csv"));
    button(root,"EXPORT TEXTURES (ZIP)").setOnClickListener(v->requestSave("textures","wrath_textures.zip","application/zip"));
    button(root,"EXPORT ASSET CLUES (CSV)").setOnClickListener(v->requestSave("clues","wrath_asset_clues.csv","text/csv"));
    button(root,"EXPORT INDEXED NAMES (CSV)").setOnClickListener(v->requestSave("names","wrath_indexed_names.csv","text/csv"));
    button(root,"EXPORT FORMAT RESEARCH (ZIP)").setOnClickListener(v->requestSave("research","wrath_format_research.zip","application/zip"));
    button(root,"EXPORT READABLE ASSETS (ZIP)").setOnClickListener(v->requestSave("clean","wrath_readable_assets.zip","application/zip"));
    button(root,"EXPORT ALL (ZIP)").setOnClickListener(v->requestSave("all","wrath_all_assets.zip","application/zip"));
    button(root,"EXPORT NON-AUDIO RAW REGIONS (ZIP)").setOnClickListener(v->requestSave("raw","wrath_raw_regions.zip","application/zip"));
    button(root,"EXPORT AUDIO (ZIP)").setOnClickListener(v->requestSave("audio","wrath_audio.zip","application/zip"));
    button(root,"EXPORT STRING INDEX (CSV)").setOnClickListener(v->requestSave("csv","wrath_strings.csv","text/csv"));
    LinearLayout hexRow=new LinearLayout(this);hexRow.setOrientation(LinearLayout.HORIZONTAL);
    hexOffset=new EditText(this);hexOffset.setSingleLine(true);hexOffset.setHint("Hex offset e.g. 44DE11");hexRow.addView(hexOffset,new LinearLayout.LayoutParams(0,-2,1));
    Button hex=button(hexRow,"VIEW HEX");hex.setOnClickListener(v->showHex());root.addView(hexRow);
    output=new TextView(this);output.setTypeface(Typeface.MONOSPACE);output.setTextSize(12);output.setText("Open a PAK file. Readable Export creates WAV and TXT files without fake conversions. Export All preserves the original PAK and raw regions. Raw regions are NOT decoded models or animations.");
    ScrollView scroll=new ScrollView(this);scroll.addView(output);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){showResults();}});
  }
  private Button button(LinearLayout parent,String label){Button b=new Button(this);b.setText(label);parent.addView(b);return b;}
  private void requestSave(String type,String name,String mime){if(data==null){Toast.makeText(this,"Open a PAK first",Toast.LENGTH_SHORT).show();return;}pendingType=type;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType(mime);i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,SAVE);}
  @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);if(result!=RESULT_OK||intent==null||intent.getData()==null)return;
    if(request==OPEN){Uri uri=intent.getData();fileName="selected.pak";try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())fileName=c.getString(0);}catch(Exception ignored){}
      status.setText("Reading...");new Thread(()->{try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1){bytes.write(buf,0,n);if(bytes.size()>150_000_000)throw new IOException("PAK exceeds 150 MB memory limit");}data=bytes.toByteArray();scan();runOnUiThread(()->{status.setText(fileName+" · "+data.length+" bytes");showResults();});}catch(Exception e){runOnUiThread(()->status.setText("Open failed: "+e.getMessage()));}}).start();
    }else if(request==SAVE){Uri uri=intent.getData();String type=pendingType;status.setText("Exporting...");new Thread(()->{try(OutputStream raw=getContentResolver().openOutputStream(uri)){if(raw==null)throw new IOException("Cannot open destination");if("csv".equals(type)){writeCsv(raw);}else if("names".equals(type)){writeIndexedNames(raw);}else if("clues".equals(type)){writeAssetClues(raw);}else if("references".equals(type)){writeNameReferences(raw);}else if("blocks".equals(type)){writeBlockMap(raw);}else if("wavedetails".equals(type)){writeWaveDetails(raw);}else if("contexts".equals(type)){writeReferenceContexts(raw);}else if("triplets".equals(type)){writeFloatTriplets(raw);}else{try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(raw))){if("research".equals(type))writeResearchZip(zip);else if("clean".equals(type))writeCleanZip(zip);else if("textures".equals(type))writeTexturesZip(zip);else writeZip(zip,"all".equals(type),"raw".equals(type));}}runOnUiThread(()->status.setText("Export complete: "+type));}catch(Exception e){runOnUiThread(()->status.setText("Export failed: "+e.getMessage()));}}).start();}
  }
  private boolean has(int at,String magic){if(at<0||at+magic.length()>data.length)return false;for(int j=0;j<magic.length();j++)if((data[at+j]&255)!=magic.charAt(j))return false;return true;}
  private long u32(int p){return ((long)data[p]&255)|(((long)data[p+1]&255)<<8)|(((long)data[p+2]&255)<<16)|(((long)data[p+3]&255)<<24);}
  private int u16(int p){return (data[p]&255)|((data[p+1]&255)<<8);}
  private void scan(){strings.clear();waves.clear();textures.clear();regions.clear();signatures.clear();indexedNames.clear();assetClues.clear();nameReferences.clear();blockMap.clear();audioDetails.clear();parseIndexedNames();scanNameReferences();int i=0;while(i<data.length){int start=i;while(i<data.length&&(data[i]&255)>=32&&(data[i]&255)<=126)i++;if(i-start>=4){strings.add(String.format(Locale.US,"0x%08X  %s",start,new String(data,start,Math.min(i-start,300),StandardCharsets.US_ASCII)));}i++;}
    for(i=0;i+12<=data.length;i++){if(!has(i,"RIFF")||!has(i+8,"WAVE"))continue;long riffSize=u32(i+4);long end=(long)i+8+riffSize;if(riffSize<4||end>data.length||end<=i)continue;
      boolean fmt=false,chunkOK=true;int format=0;long cursor=(long)i+12;int count=0;
      while(cursor+8<=end&&count++<10000){int pos=(int)cursor;long len=u32(pos+4);long next=cursor+8+len+(len&1);if(next>end||next<=cursor){chunkOK=false;break;}if(has(pos,"fmt ")&&len>=16){format=u16(pos+8);fmt=true;}cursor=next;}
      if(fmt&&chunkOK&&cursor==end){waves.add(new Asset(i,(int)(end-i),format));audioDetails.add(describeWave(i,(int)(end-i),format));i=(int)end-1;}
    }
    scanTextures();
    scanBinaryBlocks();
    // Non-audio regions are exact complementary byte ranges, NOT verified individual models.
    int previous=0;
    for(Asset a:waves){if(a.offset>previous)regions.add(new Asset(previous,a.offset-previous,0));previous=a.offset+a.length;}
    if(previous<data.length)regions.add(new Asset(previous,data.length-previous,0));
    String[] magic={"DDS ","OggS","PNG","JFIF","ID3","XPR0","XPR1","XPR2","BM","MThd"};
    for(int pos=0;pos<data.length;pos++){
      for(String m:magic){if(has(pos,m)){
        // Candidate only: signatures can occur inside unrelated payloads.
        if(signatures.size()<3000)signatures.add(String.format(Locale.US,"0x%08X  %s (unverified)",pos,m));
      }}
    }
    report="File: "+fileName+"\nSize: "+data.length+" bytes\nReadable strings: "+strings.size()+"\nValidated RIFF/WAVE containers: "+waves.size()+"\nNon-audio raw regions: "+regions.size()+"\nOther signature candidates: "+signatures.size()+"\nValidated embedded PNG/DDS textures: "+textures.size()+"\nIndexed resource names: "+indexedNames.size()+"\nAsset name clues: "+assetClues.size()+"\nCandidate name references: "+nameReferences.size()+"\nBinary blocks analyzed: "+blockMap.size()+"\n\nWAVE format tags may require special decoders.\nNon-audio regions may contain multiple models, animations, textures or metadata.\nThey are not yet individually decoded.\nExport All includes the untouched original PAK so unknown data is preserved.\n\n";
  }
  private void showResults(){if(output==null||data==null)return;String query=filter.getText().toString().toLowerCase(Locale.ROOT);StringBuilder sb=new StringBuilder(report);int shown=0;
    if(mode.getSelectedItemPosition()==0){for(int j=0;j<waves.size();j++){Asset a=waves.get(j);String line=String.format(Locale.US,"audio_%03d.wav  offset 0x%08X  size %d  format 0x%04X",j+1,a.offset,a.length,a.format);if(line.toLowerCase(Locale.ROOT).contains(query)){sb.append(line).append('\n');if(++shown>=400)break;}}}
    else if(mode.getSelectedItemPosition()==1){for(int j=0;j<regions.size();j++){Asset a=regions.get(j);String line=String.format(Locale.US,"region_%03d.bin  offset 0x%08X  size %d (unidentified raw data)",j+1,a.offset,a.length);if(line.toLowerCase(Locale.ROOT).contains(query)){sb.append(line).append('\n');if(++shown>=400)break;}}}
    else if(mode.getSelectedItemPosition()==6){sb.append(structureReport());}
    else if(mode.getSelectedItemPosition()==12){for(String line:floatTripletPreview()){if(line.toLowerCase(Locale.ROOT).contains(query)){sb.append(line).append('\n');if(++shown>=400)break;}}}
    else if(mode.getSelectedItemPosition()==11){for(String line:referenceContextPreview()){if(line.toLowerCase(Locale.ROOT).contains(query)){sb.append(line).append('\n');if(++shown>=400)break;}}}
    else if(mode.getSelectedItemPosition()==10){for(String line:audioDetails){if(line.toLowerCase(Locale.ROOT).contains(query)){sb.append(line).append('\n');if(++shown>=400)break;}}}
    else if(mode.getSelectedItemPosition()==9){for(String line:blockMap){if(line.toLowerCase(Locale.ROOT).contains(query)){sb.append(line).append('\n');if(++shown>=400)break;}}}
    else if(mode.getSelectedItemPosition()==8){for(String line:nameReferences){if(line.toLowerCase(Locale.ROOT).contains(query)){sb.append(line).append('\n');if(++shown>=400)break;}}}
    else if(mode.getSelectedItemPosition()==7){for(Asset a:textures){String line=String.format(Locale.US,"PNG at 0x%08X (%d bytes)",a.offset,a.length);if(line.toLowerCase(Locale.ROOT).contains(query)){sb.append(line).append('\n');if(++shown>=400)break;}}}
    else{for(String s:(mode.getSelectedItemPosition()==2?signatures:mode.getSelectedItemPosition()==4?indexedNames:mode.getSelectedItemPosition()==5?assetClues:strings)){if(s.toLowerCase(Locale.ROOT).contains(query)){sb.append(s).append('\n');if(++shown>=400)break;}}}
    if(shown>=400)sb.append("\nShowing first 400 results; narrow the search.");output.setText(sb.toString());}
  // Verified name-index layout in the supplied character and level PAK samples.
  // This indexes names, NOT payload offsets or decoded resource types.
  private void parseIndexedNames(){
    if(data.length<32||u32(0)!=335945)return;
    long count=u32(24), namesEnd=u32(8);
    if(count<1||count>100000||28L+4L*count>=data.length||namesEnd>data.length)return;
    long base=28L+4L*count;
    long prev=-1;
    for(int i=0;i<count;i++){
      long relative=u32(28+i*4), start=base+relative;
      if(relative<prev||start>=namesEnd||start<base){indexedNames.clear();return;}
      prev=relative;
      int end=(int)start;
      while(end<namesEnd&&data[end]!=0&&end-start<256)end++;
      if(end>=namesEnd||data[end]!=0){indexedNames.clear();return;}
      boolean printable=true;
      for(int k=(int)start;k<end;k++)if((data[k]&255)<32||(data[k]&255)>126){printable=false;break;}
      if(!printable){indexedNames.clear();return;}
      String name=new String(data,(int)start,end-(int)start,StandardCharsets.US_ASCII);
      indexedNames.add(String.format(Locale.US,"%04d  0x%08X  %s",i,start,name));
      String category=classifyName(name);
      if(!"unknown".equals(category))assetClues.add(String.format(Locale.US,"%04d  %-12s  0x%08X  %s",i,category,start,name));
    }
  }
  // Look for 32-bit values referencing name strings in either file-relative or
  // name-table-relative form. These are hypotheses, not proven pointers.
  private void scanNameReferences(){
    if(indexedNames.isEmpty())return;
    long count=u32(24), base=28L+4L*count, namesEnd=u32(8);
    HashMap<Long,String> absolute=new HashMap<>(),relative=new HashMap<>();
    for(String entry:indexedNames){
      try{String[] parts=entry.split("  ",3);long off=Long.parseLong(parts[1].substring(2),16);
        absolute.put(off,parts[2]);relative.put(off-base,parts[2]);
      }catch(Exception ignored){}
    }
    // Skip the index itself: those values are known string offsets, not resource links.
    int start=(int)Math.min(data.length,Math.max(namesEnd,base));
    for(int p=(start+3)&~3;p+4<=data.length;p+=4){
      long value=u32(p);String name=absolute.get(value);String kind="absolute";
      if(name==null){name=relative.get(value);kind="relative_to_names";}
      if(name!=null && nameReferences.size()<20000)
        nameReferences.add(String.format(Locale.US,"0x%08X  0x%08X  %s  %s",p,value,kind,name));
    }
  }
  // Fixed-size diagnostic map, not an asset decoder. Entropy is Shannon bits/byte.
  // Count potential IEEE754 values conservatively: magnitude between 0.0001 and 100000,
  // excluding NaN/Inf. Such values can occur in unrelated compressed or binary data.
  private void scanBinaryBlocks(){
    final int block=4096;
    int namesEnd=(data.length>=12)?(int)Math.min(data.length,u32(8)):0;
    for(int start=0;start<data.length;start+=block){
      int end=Math.min(data.length,start+block),length=end-start;
      int[] freq=new int[256];int zero=0,ascii=0;
      for(int p=start;p<end;p++){
        int b=data[p]&255;freq[b]++;if(b==0)zero++;if(b>=32&&b<=126)ascii++;
      }
      double entropy=0;
      for(int f:freq)if(f!=0){double q=(double)f/length;entropy-=q*(Math.log(q)/Math.log(2));}
      int plausible=0,words=0,offsets=0;
      for(int p=(start+3)&~3;p+4<=end;p+=4){
        long v=u32(p);float f=Float.intBitsToFloat((int)v);words++;
        if(!Float.isNaN(f)&&!Float.isInfinite(f)&&Math.abs(f)>=0.0001f&&Math.abs(f)<=100000f)plausible++;
        if(v>=namesEnd&&v<data.length&&(v&3)==0)offsets++;
      }
      String region=start<namesEnd?"header_or_name_index":"after_name_index";
      blockMap.add(String.format(Locale.US,"0x%08X,%d,%s,%.3f,%.2f,%.2f,%d,%d,%d",start,length,region,entropy,100.0*zero/length,100.0*ascii/length,plausible,words,offsets));
    }
  }
  // Read chunk boundaries only after the RIFF parser has validated the container.
  // WAVE format tags are retained as found; this does not decode Xbox ADPCM.
  private String describeWave(int offset,int length,int format){
    int channels=-1,rate=-1,bits=-1,blockAlign=-1;long payload=0;
    long end=(long)offset+length,cursor=(long)offset+12;
    while(cursor+8<=end){
      int pos=(int)cursor;long len=u32(pos+4);
      if(len>end-cursor-8)break;
      if(has(pos,"fmt ")&&len>=16){
        channels=u16(pos+10);rate=(int)u32(pos+12);
        blockAlign=u16(pos+20);bits=u16(pos+22);
      }
      if(has(pos,"data"))payload+=len;
      cursor+=8+len+(len&1);
    }
    return String.format(Locale.US,"0x%08X,%d,0x%04X,%d,%d,%d,%d,%d",offset,length,format,channels,rate,bits,blockAlign,payload);
  }
  private void writeWaveDetails(OutputStream out)throws IOException{
    out.write("offset_hex,container_bytes,format_tag,channels,sample_rate_hz,bits_per_sample,block_align,data_bytes\n".getBytes(StandardCharsets.UTF_8));
    for(String row:audioDetails)out.write((row+"\n").getBytes(StandardCharsets.UTF_8));
  }
  private void writeBlockMap(OutputStream out)throws IOException{
    out.write("offset_hex,length,region,entropy_bits_per_byte,zero_pct,printable_ascii_pct,plausible_float32_words,total_aligned_words,possible_file_offsets\n".getBytes(StandardCharsets.UTF_8));
    for(String line:blockMap)out.write((line+"\n").getBytes(StandardCharsets.UTF_8));
  }
  private void writeNameReferences(OutputStream out)throws IOException{
    out.write("location_hex,value_hex,interpretation,name\n".getBytes(StandardCharsets.UTF_8));
    for(String line:nameReferences){String[] parts=line.split("  ",4);
      out.write((parts[0]+","+parts[1]+","+parts[2]+",\""+parts[3].replace("\"","\"\"")+"\"\n").getBytes(StandardCharsets.UTF_8));
    }
  }
  // Classification is based on name text only; it is NOT proof of a decoded asset.
  private String classifyName(String name){
    String n=name.toLowerCase(Locale.ROOT);
    if(n.contains("skinmesh")||n.contains("mesh")||n.contains("model")||n.contains("vertex"))return "mesh-clue";
    if(n.contains("bone")||n.contains("joint")||n.contains("skeleton"))return "rig-clue";
    if(n.contains("anim")||n.contains("idle")||n.contains("walk")||n.contains("run")||n.contains("attack")||n.contains("atk")||n.contains("dodge"))return "motion-clue";
    if(n.contains("texture")||n.contains("tex_")||n.contains("diffuse")||n.contains("normalmap"))return "texture-clue";
    if(n.contains("sound")||n.contains("music")||n.contains("voice")||n.contains("sfx"))return "audio-clue";
    return "unknown";
  }
  private void writeAssetClues(OutputStream out)throws IOException{
    out.write("index,category,name_offset_hex,name,confidence\n".getBytes(StandardCharsets.UTF_8));
    for(String row:assetClues){
      String[] parts=row.trim().split("\\s+",4);
      if(parts.length==4){String line=Integer.parseInt(parts[0])+","+parts[1]+","+parts[2]+",\""+parts[3].replace("\"","\"\"")+"\",name_only\n";
        out.write(line.getBytes(StandardCharsets.UTF_8));}
    }
  }
  private void writeIndexedNames(OutputStream out)throws IOException{
    out.write("index,name_offset_hex,name\n".getBytes(StandardCharsets.UTF_8));
    for(String row:indexedNames){
      String[] parts=row.split("  ",3);
      if(parts.length==3)out.write((Integer.parseInt(parts[0])+","+parts[1]+",\""+parts[2].replace("\"","\"\"")+"\"\n").getBytes(StandardCharsets.UTF_8));
    }
  }
  private void showHex(){if(data==null)return;try{String s=hexOffset.getText().toString().trim().replaceFirst("(?i)^0x","");long val=Long.parseLong(s,16);if(val<0||val>=data.length)throw new IllegalArgumentException("Offset outside PAK");int off=(int)val;StringBuilder sb=new StringBuilder(String.format(Locale.US,"Hex at 0x%08X\n\n",off));for(int p=off;p<Math.min(data.length,off+512);p+=16){sb.append(String.format(Locale.US,"%08X  ",p));for(int k=0;k<16;k++)sb.append(p+k<data.length?String.format(Locale.US,"%02X ",data[p+k]&255):"   ");sb.append("  ");for(int k=0;k<16&&p+k<data.length;k++){int c=data[p+k]&255;sb.append(c>=32&&c<=126?(char)c:'.');}sb.append('\n');}output.setText(sb.toString());}catch(Exception e){Toast.makeText(this,"Enter a valid hex offset",Toast.LENGTH_SHORT).show();}}
  private void writeCsv(OutputStream out)throws IOException{out.write("offset_hex,text\n".getBytes(StandardCharsets.UTF_8));for(String line:strings){int split=line.indexOf("  ");if(split<0)continue;String escaped=line.substring(split+2).replace("\"","\"\"");out.write((line.substring(0,split)+",\""+escaped+"\"\n").getBytes(StandardCharsets.UTF_8));}}
  private void addText(ZipOutputStream zip,String path,String text)throws IOException{zip.putNextEntry(new ZipEntry(path));zip.write(text.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
  // Clean export never invents model/animation formats or renames arbitrary data.
  // Research export: inspect plausible header/offset fields without claiming asset types.
  // Describes confirmed header/name-table boundaries without assuming mesh layouts.
  private String structureReport(){
    if(data==null||data.length<28)return "PAK too small for header\n";
    long declared=u32(4), namesEnd=u32(8), count=u32(24);
    long tableStart=28, tableEnd=tableStart+count*4L;
    StringBuilder sb=new StringBuilder();
    sb.append(String.format(Locale.US,"PAK structure (observations, not a decoded asset directory)\nMagic: 0x%08X\nActual length: %d\nHeader length field: %d (%s)\nName pointer table: 0x%08X..0x%08X\nName count: %d\nName bytes end field: 0x%08X\nIndexed names validated: %d\n",u32(0),data.length,declared,declared==data.length?"matches":"DIFFERS",tableStart,tableEnd,count,namesEnd,indexedNames.size()));
    sb.append("\nThese offsets identify the NAME INDEX, not model/texture/animation payloads.\n");
    if(tableEnd>data.length||namesEnd>data.length||tableEnd>namesEnd)sb.append("WARNING: name table boundaries inconsistent\n");
    sb.append("\nHeader words (little-endian):\n");
    for(int off=0;off<28&&off+4<=data.length;off+=4)sb.append(String.format(Locale.US,"0x%02X: 0x%08X (%d)\n",off,u32(off),u32(off)));
    return sb.toString();
  }
  private void writeJsonIndex(ZipOutputStream zip)throws IOException{
    StringBuilder sb=new StringBuilder();
    sb.append("{\n  \"schema\": \"wrath-pak-research-v1\",\n  \"fileSize\": ").append(data.length).append(",\n  \"nameIndexOnly\": true,\n  \"names\": [\n");
    for(int i=0;i<indexedNames.size();i++){
      String row=indexedNames.get(i), name=row.length()>17?row.substring(17):"";
      int off=(int)(28+4L*u32(24)+u32(28+4*i));
      sb.append("    {\"index\":").append(i).append(",\"nameOffset\":").append(off).append(",\"name\":\"");
      for(int j=0;j<name.length();j++){char c=name.charAt(j);if(c=='\\'||c=='\"')sb.append('\\');sb.append(c);}
      sb.append("\"}");if(i+1<indexedNames.size())sb.append(',');sb.append('\n');
    }
    sb.append("  ]\n}\n");addText(zip,"research/name_index.json",sb.toString());
  }
  // Inspect 32-bit words surrounding candidate references. These are contextual
  // observations only; they are not proven resource payload offsets.
  private ArrayList<String> referenceContextPreview(){
    ArrayList<String> lines=new ArrayList<>();
    for(String ref:nameReferences){
      if(lines.size()>=400)break;
      try{
        int p=(int)Long.parseLong(ref.substring(2,10),16);
        StringBuilder line=new StringBuilder(ref).append(" | surrounding u32:");
        for(int delta=-16;delta<=16;delta+=4){int q=p+delta;
          if(q>=0&&q+4<=data.length)line.append(String.format(Locale.US," [%+d]=0x%08X",delta,u32(q)));
        }
        lines.add(line.toString());
      }catch(Exception ignored){}
    }
    return lines;
  }
  private void writeReferenceContexts(OutputStream out)throws IOException{
    out.write("reference_offset_hex,reference_value_hex,reference_kind,name,delta_bytes,nearby_word_hex,nearby_word_decimal,word_points_inside_pak\n".getBytes(StandardCharsets.UTF_8));
    for(String ref:nameReferences){
      try{
        String[] parts=ref.split("  ",4);if(parts.length<4)continue;
        int p=(int)Long.parseLong(parts[0].substring(2),16);
        String name=parts[3].replace("\"","\"\"");
        for(int delta=-32;delta<=32;delta+=4){int q=p+delta;if(q<0||q+4>data.length)continue;
          long value=u32(q);
          String row=String.format(Locale.US,"%s,%s,%s,\"%s\",%d,0x%08X,%d,%s\n",parts[0],parts[1],parts[2],name,delta,value,value,(value<data.length));
          out.write(row.getBytes(StandardCharsets.UTF_8));
        }
      }catch(Exception ignored){}
    }
  }
  // Potential little-endian XYZ float triplets. These are NOT verified vertices.
  // Search only non-audio regions, skip the known name index, and reject
  // non-finite, denormal, and extremely large values. Use CSV for inspection.
  private boolean candidateFloat(int at){
    if(at<0||at+4>data.length)return false;
    int bits=(int)u32(at);float v=Float.intBitsToFloat(bits);
    return Float.isFinite(v)&&Math.abs(v)<=100000f&&(v==0f||Math.abs(v)>=0.000001f);
  }
  private ArrayList<String> floatTripletPreview(){
    ArrayList<String> lines=new ArrayList<>();
    if(data==null)return lines;
    int endIndex=(int)Math.min(data.length,u32(8));
    for(Asset r:regions){
      int start=(Math.max(r.offset,endIndex)+3)&~3;
      int end=r.offset+r.length;
      for(int p=start;p+12<=end&&lines.size()<400;p+=4){
        if(candidateFloat(p)&&candidateFloat(p+4)&&candidateFloat(p+8)){
          float x=Float.intBitsToFloat((int)u32(p));float y=Float.intBitsToFloat((int)u32(p+4));float z=Float.intBitsToFloat((int)u32(p+8));
          if(Math.abs(x)+Math.abs(y)+Math.abs(z)<0.001f)continue;
          lines.add(String.format(Locale.US,"0x%08X  XYZ? %.5g, %.5g, %.5g (unverified)",p,x,y,z));
        }
      }
      if(lines.size()>=400)break;
    }
    return lines;
  }
  private void writeFloatTriplets(OutputStream out)throws IOException{
    out.write("offset_hex,x_float32_le,y_float32_le,z_float32_le,verified_vertex\n".getBytes(StandardCharsets.UTF_8));
    int endIndex=(int)Math.min(data.length,u32(8));
    int rows=0;
    for(Asset r:regions){
      int start=(Math.max(r.offset,endIndex)+3)&~3;
      int end=r.offset+r.length;
      for(int p=start;p+12<=end&&rows<200000;p+=4){
        if(!candidateFloat(p)||!candidateFloat(p+4)||!candidateFloat(p+8))continue;
        float x=Float.intBitsToFloat((int)u32(p));float y=Float.intBitsToFloat((int)u32(p+4));float z=Float.intBitsToFloat((int)u32(p+8));
        if(Math.abs(x)+Math.abs(y)+Math.abs(z)<0.001f)continue;
        String row=String.format(Locale.US,"0x%08X,%.9g,%.9g,%.9g,false\n",p,x,y,z);
        out.write(row.getBytes(StandardCharsets.UTF_8));rows++;
      }
    }
  }
  private void writeResearchZip(ZipOutputStream zip)throws IOException{
    StringBuilder header=new StringBuilder("byte_offset_hex,value_u32_le,value_hex\n");
    for(int off=0;off+4<=Math.min(data.length,8192);off+=4){
      long v=u32(off);
      header.append(String.format(Locale.US,"0x%08X,%d,0x%08X\n",off,v,v));
    }
    addText(zip,"research/header_u32.csv",header.toString());
    addText(zip,"research/structure_report.txt",structureReport());
    writeJsonIndex(zip);
    java.io.ByteArrayOutputStream indexed=new java.io.ByteArrayOutputStream();writeIndexedNames(indexed);
    addText(zip,"research/indexed_names.csv",indexed.toString("UTF-8"));
    java.io.ByteArrayOutputStream clues=new java.io.ByteArrayOutputStream();writeAssetClues(clues);addText(zip,"research/asset_clues.csv",clues.toString("UTF-8"));
    StringBuilder candidates=new StringBuilder("byte_offset_hex,value_u32_le,points_inside_pak,aligned_4,aligned_16\n");
    for(int off=32;off+4<=Math.min(data.length,65536);off+=4){
      long v=u32(off);
      if(v>=32&&v<data.length){
        candidates.append(String.format(Locale.US,"0x%08X,%d,true,%s,%s\n",off,v,(v%4==0),(v%16==0)));
      }
    }
    addText(zip,"research/possible_offsets.csv",candidates.toString());
    StringBuilder names=new StringBuilder("byte_offset_hex,text\n");
    for(String line:strings){
      int sep=line.indexOf("  ");if(sep<0)continue;
      String value=line.substring(sep+2);
      if(value.length()>180)continue;
      String lower=value.toLowerCase(Locale.ROOT);
      if(lower.contains("mesh")||lower.contains("bone")||lower.contains("skin")||lower.contains("anim")||lower.contains("texture")||lower.contains("model")||lower.contains("vertex")||lower.contains("demon")||lower.contains("centaur")||lower.contains("cyclops")){
        names.append(line.substring(0,sep)).append(",\"").append(value.replace("\"","\"\"")).append("\"\n");
      }
    }
    addText(zip,"research/asset_name_clues.csv",names.toString());
    StringBuilder audio=new StringBuilder("offset_hex,size_bytes,format_tag\n");
    for(Asset a:waves)audio.append(String.format(Locale.US,"0x%08X,%d,0x%04X\n",a.offset,a.length,a.format));
    addText(zip,"research/verified_audio_ranges.csv",audio.toString());
    StringBuilder tex=new StringBuilder("offset_hex,size_bytes,format\n");for(Asset a:textures)tex.append(String.format(Locale.US,"0x%08X,%d,PNG\n",a.offset,a.length));addText(zip,"research/verified_texture_ranges.csv",tex.toString());
    StringBuilder region=new StringBuilder("offset_hex,size_bytes,meaning\n");
    for(Asset a:regions)region.append(String.format(Locale.US,"0x%08X,%d,unidentified_non_audio_range\n",a.offset,a.length));
    addText(zip,"research/unidentified_ranges.csv",region.toString());
    ByteArrayOutputStream refs=new ByteArrayOutputStream();writeNameReferences(refs);addText(zip,"research/name_reference_candidates.csv",refs.toString("UTF-8"));
    ByteArrayOutputStream contexts=new ByteArrayOutputStream();writeReferenceContexts(contexts);addText(zip,"research/reference_contexts.csv",contexts.toString("UTF-8"));
    ByteArrayOutputStream blocks=new ByteArrayOutputStream();writeBlockMap(blocks);addText(zip,"research/binary_block_map.csv",blocks.toString("UTF-8"));
    ByteArrayOutputStream wd=new ByteArrayOutputStream();writeWaveDetails(wd);addText(zip,"research/wave_format_details.csv",wd.toString("UTF-8"));
    ByteArrayOutputStream floats=new ByteArrayOutputStream();writeFloatTriplets(floats);addText(zip,"research/float_triplet_candidates.csv",floats.toString("UTF-8"));
    addText(zip,"README.txt","Wrath PAK format research export.\nThese tables are diagnostic clues, not decoded mesh, texture or animation records.\nPossible offsets may be integers with unrelated meanings.\nNo model, animation, or texture conversion is claimed.\nCompare reports from multiple PAK files to determine real record structures.\n");
  }

  // Only accept complete PNGs with valid chunk CRCs and required structure.
  private void scanTextures(){
    for(int p=0;p+24<data.length;p++){
      if((data[p]&255)==137 && has(p+1,"PNG") && (data[p+4]&255)==13 && (data[p+5]&255)==10 && (data[p+6]&255)==26 && (data[p+7]&255)==10){
        long cursor=p+8;boolean ihdr=false,idat=false,valid=true,ended=false;int chunks=0;
        while(cursor+12<=data.length && chunks++<100000){
          int off=(int)cursor;long size=((long)(data[off]&255)<<24)|((long)(data[off+1]&255)<<16)|((long)(data[off+2]&255)<<8)|(data[off+3]&255);
          if(size>data.length-cursor-12){valid=false;break;}
          int type=off+4;java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(data,type,(int)size+4);
          int check=type+4+(int)size;long expected=((long)(data[check]&255)<<24)|((long)(data[check+1]&255)<<16)|((long)(data[check+2]&255)<<8)|(data[check+3]&255);
          if(crc.getValue()!=expected){valid=false;break;}
          if(chunks==1){if(!has(type,"IHDR")||size!=13){valid=false;break;}ihdr=true;}
          if(has(type,"IDAT"))idat=true;
          cursor+=12+size;
          if(has(type,"IEND")){ended=size==0;break;}
        }
        if(valid&&ihdr&&idat&&ended&&cursor-p<=Integer.MAX_VALUE){textures.add(new Asset(p,(int)(cursor-p),1));p=(int)cursor-1;}
      }
    }
  }
  private void writeTexturesZip(ZipOutputStream zip)throws IOException{
    StringBuilder index=new StringBuilder("filename,offset_hex,length_bytes,format\n");
    for(int j=0;j<textures.size();j++){
      Asset a=textures.get(j);String name=String.format(Locale.US,"textures/texture_%03d.png",j+1);
      zip.putNextEntry(new ZipEntry(name));zip.write(data,a.offset,a.length);zip.closeEntry();
      index.append(String.format(Locale.US,"%s,0x%08X,%d,PNG\n",name,a.offset,a.length));
    }
    addText(zip,"textures/index.csv",index.toString());
    addText(zip,"README.txt","Only complete, CRC-validated embedded PNG images are exported. This is not a decoder for Wrath's proprietary texture encoding. Zero PNG files means no supported embedded PNG images were found. No binary data was renamed.\n");
  }

  private void writeCleanZip(ZipOutputStream zip)throws IOException{
    StringBuilder index=new StringBuilder("filename,offset_hex,length_bytes,wave_format_tag\n");
    for(int j=0;j<waves.size();j++){
      Asset a=waves.get(j);
      String name=String.format(Locale.US,"audio/audio_%03d.wav",j+1);
      zip.putNextEntry(new ZipEntry(name));zip.write(data,a.offset,a.length);zip.closeEntry();
      index.append(String.format(Locale.US,"%s,0x%08X,%d,0x%04X\n",name,a.offset,a.length,a.format));
    }
    addText(zip,"audio/index.csv",index.toString());
    ByteArrayOutputStream wd=new ByteArrayOutputStream();writeWaveDetails(wd);addText(zip,"audio/wave_format_details.csv",wd.toString("UTF-8"));
    for(int j=0;j<textures.size();j++){Asset a=textures.get(j);zip.putNextEntry(new ZipEntry(String.format(Locale.US,"textures/texture_%03d.png",j+1)));zip.write(data,a.offset,a.length);zip.closeEntry();}
    java.io.ByteArrayOutputStream indexed=new java.io.ByteArrayOutputStream();writeIndexedNames(indexed);
    addText(zip,"metadata/indexed_names.csv",indexed.toString("UTF-8"));
    addText(zip,"metadata/structure_report.txt",structureReport());
    ByteArrayOutputStream refs=new ByteArrayOutputStream();writeNameReferences(refs);addText(zip,"metadata/name_reference_candidates.csv",refs.toString("UTF-8"));
    java.io.ByteArrayOutputStream clues=new java.io.ByteArrayOutputStream();writeAssetClues(clues);addText(zip,"metadata/asset_clues.csv",clues.toString("UTF-8"));
    StringBuilder text=new StringBuilder();
    for(String line:strings)text.append(line).append('\n');
    addText(zip,"metadata/readable_strings.txt",text.toString());
    addText(zip,"metadata/header.txt",String.format(Locale.US,
      "Original name: %s\nFile length: %d bytes\nHeader magic: 0x%08X\nHeader declared length: %d\nHeader field at 0x08: %d\nHeader field at 0x14: %d\nHeader field at 0x18: %d\nWAVE containers: %d\n\nHeader field meanings are not fully established.\n",
      fileName,data.length,u32(0),u32(4),u32(8),u32(20),u32(24),waves.size()));
    addText(zip,"README.txt","Wrath PAK Inspector v1.0 readable export\n\n"+
      "Audio files are validated WAVE containers. Some require Xbox ADPCM decoding to play.\n"+
      "Readable strings and header information are exported as text.\n"+
      "No OBJ/STL/PNG or animation files are created without a verified decoder.\n"+
      "Unidentified data is intentionally excluded from this clean export.\n"+
      "Use Export All to preserve every original byte and raw regions for later decoding.\n");
  }
  private void writeZip(ZipOutputStream zip,boolean all,boolean rawOnly)throws IOException{
    StringBuilder index=new StringBuilder("filename,offset_hex,length_bytes,wave_format_tag\n");if(!rawOnly)for(int j=0;j<waves.size();j++){Asset a=waves.get(j);String name=String.format(Locale.US,"audio/audio_%03d.wav",j+1);zip.putNextEntry(new ZipEntry(name));zip.write(data,a.offset,a.length);zip.closeEntry();index.append(String.format(Locale.US,"%s,0x%08X,%d,0x%04X\n",name,a.offset,a.length,a.format));}
    addText(zip,"audio_index.csv",index.toString());
    if(all||rawOnly){
      StringBuilder rawIndex=new StringBuilder("filename,offset_hex,length_bytes,classification\n");
      for(int j=0;j<regions.size();j++){Asset a=regions.get(j);String name=String.format(Locale.US,"raw/region_%03d.bin",j+1);zip.putNextEntry(new ZipEntry(name));zip.write(data,a.offset,a.length);zip.closeEntry();rawIndex.append(String.format(Locale.US,"%s,0x%08X,%d,unidentified\n",name,a.offset,a.length));}
      addText(zip,"raw_index.csv",rawIndex.toString());
      addText(zip,"other_signature_candidates.txt",String.join("\n",signatures));
      addText(zip,"IMPORTANT.txt","Raw regions are contiguous portions outside verified RIFF/WAVE containers. They are NOT individual decoded models, animations, or textures. Original offsets are in raw_index.csv.\n");
    }
    if(all){zip.putNextEntry(new ZipEntry("original/"+fileName.replace('/','_').replace('\\','_')));zip.write(data);zip.closeEntry();addText(zip,"README.txt","Wrath PAK Inspector v0.3\nAudio entries are validated RIFF/WAVE containers, not necessarily playable PCM.\nThe original PAK is preserved byte-for-byte, including all models, animations, textures and unidentified data.\nUnidentified non-audio regions are exported separately as raw binary ranges; these are not verified individual assets.\n");}
  }
}