package gr.eortologio.plus;
import android.content.*;
import org.json.*;
import java.io.*;
import java.nio.charset.Charset;
import java.time.*;
import java.util.*;

public final class EventStore {
  static class Event {
    String id,title,category; int day,month,offset,birthYear; boolean movable,personal;
    Event(String id,String title,String category,int day,int month,int offset,int birthYear,boolean movable,boolean personal) {
      this.id=id;this.title=title;this.category=category;this.day=day;this.month=month;this.offset=offset;this.birthYear=birthYear;this.movable=movable;this.personal=personal;
    }
    LocalDate onYear(int year) {
      if(movable)return orthodoxEaster(year).plusDays(offset);
      return LocalDate.of(year,month,day==29&&month==2&&!Year.isLeap(year)?28:day);
    }
    LocalDate next(LocalDate from) {
      for(int y=from.getYear()-1;y<=from.getYear()+2;y++) {LocalDate d=onYear(y);if(!d.isBefore(from))return d;}
      return onYear(from.getYear()+2);
    }
    JSONObject json()throws JSONException {JSONObject x=new JSONObject();x.put("id",id);x.put("title",title);x.put("category",category);x.put("day",day);x.put("month",month);x.put("offset",offset);x.put("birthYear",birthYear);x.put("movable",movable);return x;}
    static Event parse(JSONObject o){return new Event(o.optString("id"),o.optString("title"),o.optString("category","Προσωπικό"),o.optInt("day",1),o.optInt("month",1),o.optInt("offset"),o.optInt("birthYear"),o.optBoolean("movable"),true);}
  }
  private final SharedPreferences prefs;
  EventStore(Context ctx){prefs=ctx.getSharedPreferences("events",Context.MODE_PRIVATE);}
  List<Event> editable(){List<Event> r=new ArrayList<>();try{JSONArray a=new JSONArray(prefs.getString("personal","[]"));for(int i=0;i<a.length();i++)r.add(Event.parse(a.getJSONObject(i)));}catch(Exception ignored){}return r;}
  void save(Event e){List<Event> es=editable();boolean replaced=false;for(int i=0;i<es.size();i++)if(es.get(i).id.equals(e.id)){es.set(i,e);replaced=true;break;}if(!replaced)es.add(e);write(es);}
  void delete(String id){List<Event> es=editable();es.removeIf(e->e.id.equals(id));write(es);}
  private void write(List<Event> es){JSONArray a=new JSONArray();for(Event e:es)try{a.put(e.json());}catch(Exception ignored){}prefs.edit().putString("personal",a.toString()).apply();}
  List<Event> all(Context ctx){List<Event> out=new ArrayList<>();load(ctx,"TODAY.DAT",false,out);load(ctx,"CUSTOM.INI",true,out);out.addAll(editable());return out;}
  private void load(Context ctx,String filename,boolean personal,List<Event> out){
    try(BufferedReader br=new BufferedReader(new InputStreamReader(ctx.getAssets().open(filename),java.nio.charset.StandardCharsets.UTF_8))){
      String line;int n=0;
      while((line=br.readLine())!=null){n++;String s=line.trim();if(s.isEmpty()||s.startsWith(";"))continue;String[] p=s.split("\\s+",2);if(p.length!=2)continue;
        try{boolean movable=p[0].startsWith("+")||p[0].startsWith("-");int day=1,month=1,offset=0;
          if(movable)offset=Integer.parseInt(p[0]);else{String[] dm=p[0].split("/");day=Integer.parseInt(dm[0]);month=Integer.parseInt(dm[1]);LocalDate.of(2024,month,day);}
          out.add(new Event(filename+":"+n,p[1],personal?"Προσωπικό":"Ονομαστική γιορτή",day,month,offset,0,movable,personal));
        }catch(Exception ignored){}
      }
    }catch(Exception ignored){}
  }
  static LocalDate orthodoxEaster(int year){
    int a=year%4,b=year%7,c=year%19,d=(19*c+15)%30,e=(2*a+4*b-d+34)%7;
    return LocalDate.of(year,3,22).plusDays(d+e+13); // Julian to Gregorian for 1900-2099
  }
}
