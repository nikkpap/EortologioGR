package gr.eortologio.plus;
import android.app.*;import android.content.*;import android.net.Uri;import org.json.*;import java.net.*;import java.io.*;
final class OtaUpdater {
 static void check(Activity a,boolean manual){
  new Thread(()->{try{
   HttpURLConnection c=(HttpURLConnection)new URL("https://api.github.com/repos/nikkpap/EortologioGR/releases/latest").openConnection();
   c.setConnectTimeout(10000);c.setReadTimeout(10000);c.setRequestProperty("User-Agent","EortologioGR");
   if(c.getResponseCode()!=200)throw new IOException("Δεν βρέθηκε δημοσιευμένη ενημέρωση");
   ByteArrayOutputStream o=new ByteArrayOutputStream();try(InputStream s=c.getInputStream()){byte[] buf=new byte[4096];int n;while((n=s.read(buf))!=-1)o.write(buf,0,n);}
   JSONObject j=new JSONObject(o.toString("UTF-8"));String version=j.optString("tag_name"),url=j.optString("html_url");
   a.runOnUiThread(()->new AlertDialog.Builder(a).setTitle("Έκδοση "+version).setMessage("Άνοιγμα της επίσημης σελίδας GitHub Releases για λήψη και εγκατάσταση της υπογεγραμμένης ενημέρωσης.").setNegativeButton("Άκυρο",null).setPositiveButton("GitHub",(d,w)->a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)))).show());
  }catch(Exception e){if(manual)a.runOnUiThread(()->new AlertDialog.Builder(a).setMessage(e.getMessage()).setPositiveButton("OK",null).show());}}).start();
 }
}
