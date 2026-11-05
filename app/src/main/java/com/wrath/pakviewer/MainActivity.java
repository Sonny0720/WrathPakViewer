package com.wrath.pakviewer;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
  private static final int OPEN=10, SAVE=11;
  private byte[] data;
  private String report="";
  private TextView output;
  private EditText filter;
  private ArrayList<String> entries=new ArrayList<>();
  private byte[] pending;
  @Override public void onCreate(Bundle b){super.onCreate(b);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(20,20,20,20);
    TextView title=new TextView(this);title.setText("Wrath Unleashed PAK Inspector · v0.1");title.setTextSize(20);root.addView(title);
    Button open=new Button(this);open.setText("Open .pak file");root.addView(open);open.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,OPEN);});
    filter=new EditText(this);filter.setSingleLine(true);filter.setHint("Filter detected strings");root.addView(filter);
    Button search=new Button(this);search.setText("Search");root.addView(search);search.setOnClickListener(v->showResults());
    Button export=new Button(this);export.setText("Export string index (.csv)");root.addView(export);export.setOnClickListener(v->{if(data==null)return;StringBuilder sb=new StringBuilder("offset_hex,text\n");for(String e:entries){int pos=e.indexOf("  ");if(pos>0)sb.append(e.substring(0,pos)).append(",\"").append(e.substring(pos+2).replace("\"","\"\"")).append("\"\n");}save(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),"wrath_strings.csv","text/csv");});
    output=new TextView(this);output.setTypeface(Typeface.MONOSPACE);output.setTextSize(12);output.setText("Open a PAK file to inspect readable strings and embedded signatures. Model and texture decoding is not yet supported.");ScrollView sc=new ScrollView(this);sc.addView(output);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
  }
  private void save(byte[] bytes,String name,String mime){pending=bytes;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType(mime);i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,SAVE);}
  @Override protected void onActivityResult(int req,int res,Intent intent){super.onActivityResult(req,res,intent);if(res!=RESULT_OK||intent==null)return;
    if(req==OPEN){output.setText("Reading file...");Uri u=intent.getData();new Thread(()->{try(InputStream in=getContentResolver().openInputStream(u);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>150_000_000)throw new IOException("File exceeds 150 MB safety limit");}data=out.toByteArray();scan();runOnUiThread(this::showResults);}catch(Exception ex){runOnUiThread(()->output.setText("Error: "+ex.getMessage()));}}).start();}
    if(req==SAVE){try(OutputStream o=getContentResolver().openOutputStream(intent.getData())){o.write(pending);Toast.makeText(this,"Saved",Toast.LENGTH_SHORT).show();}catch(Exception ex){output.setText("Save failed: "+ex.getMessage());}}
  }
  private void scan(){entries.clear();int i=0;while(i<data.length){int start=i;while(i<data.length && data[i]>=32 && data[i]<=126)i++;if(i-start>=4){String s=new String(data,start,Math.min(i-start,300),java.nio.charset.StandardCharsets.US_ASCII);entries.add(String.format(java.util.Locale.US,"0x%08X  %s",start,s));}i++;}
    int riff=0,png=0,dds=0;for(i=0;i<data.length-4;i++){if(matches(i,"RIFF"))riff++;if(matches(i,"DDS "))dds++;if(matches(i,"\u0089PNG"))png++;}
    report="Size: "+data.length+" bytes\nReadable strings: "+entries.size()+"\nRIFF signatures: "+riff+"\nDDS signatures: "+dds+"\n\nNote: signatures and strings are not confirmed asset boundaries.\nNo model/texture extraction yet.\n\n";
  }
  private boolean matches(int off,String s){for(int j=0;j<s.length();j++)if((data[off+j]&255)!=s.charAt(j))return false;return true;}
  private void showResults(){if(data==null)return;String q=filter.getText().toString().toLowerCase(java.util.Locale.ROOT);StringBuilder sb=new StringBuilder(report);int shown=0;for(String s:entries){if(s.toLowerCase(java.util.Locale.ROOT).contains(q)){sb.append(s).append('\n');if(++shown>=400){sb.append("\nShowing first 400 matches. Narrow your search.");break;}}}output.setText(sb.toString());}
}
