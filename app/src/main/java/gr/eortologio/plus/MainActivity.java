package gr.eortologio.plus;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
  final int ink=0xff172342,accent=0xff6657dd,sub=0xff626b7f;
  LinearLayout root,body; EventStore store; String page="today";LocalDate chosen=LocalDate.now();
  int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  TextView label(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);t.setPadding(dp(8),dp(10),dp(8),dp(10));return t;}
  LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
  void showCard(String title,String detail,Runnable action){
    LinearLayout c=column();GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(18));c.setBackground(bg);c.setPadding(dp(14),dp(8),dp(14),dp(8));
    c.addView(label(title,17,ink));c.addView(label(detail,13,sub));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);body.addView(c,p);
    if(action!=null)c.setOnClickListener(v->action.run());
  }
  @Override public void onCreate(Bundle b){super.onCreate(b);store=new EventStore(this);ReminderReceiver.schedule(this);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},12);render();}
  void render(){
    root=column();root.setBackgroundColor(0xfff4f5fa);setContentView(root);
    LinearLayout head=column();head.setBackgroundColor(ink);head.setPadding(dp(18),dp(20),dp(18),dp(20));
    head.addView(label("ΕΟΡΤΟΛΟΓΙΟ +",13,0xffa9a1ff));head.addView(label("Οι σημαντικές σου μέρες",24,Color.WHITE));head.addView(label(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy",new Locale("el","GR"))),14,0xffe0e5ff));root.addView(head);
    ScrollView scroll=new ScrollView(this);body=column();body.setPadding(dp(16),dp(16),dp(16),dp(12));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    if(page.equals("today"))today();else if(page.equals("calendar"))calendar();else if(page.equals("personal"))personal();else settings();
    LinearLayout nav=new LinearLayout(this);nav.setBackgroundColor(Color.WHITE);
    String[] ids={"today","calendar","personal","settings"},names={"Σήμερα","Ημερολόγιο","Δικά μου","Ρυθμίσεις"};
    for(int i=0;i<4;i++){String id=ids[i];TextView b=label(names[i],12,page.equals(id)?accent:sub);b.setGravity(Gravity.CENTER);nav.addView(b,new LinearLayout.LayoutParams(0,dp(62),1));b.setOnClickListener(v->{page=id;render();});}root.addView(nav);
  }
  List<EventStore.Event> on(LocalDate day){List<EventStore.Event> out=new ArrayList<>();for(EventStore.Event e:store.all(this))try{if(e.onYear(day.getYear()).equals(day))out.add(e);}catch(Exception ignored){}return out;}
  void today(){body.addView(label("Σήμερα γιορτάζουν",21,ink));for(EventStore.Event e:on(LocalDate.now()))showCard(e.title,e.category,null);
    body.addView(label("Έρχονται σύντομα",20,ink));List<EventStore.Event> es=store.all(this);LocalDate today=LocalDate.now();es.sort(Comparator.comparing(e->e.next(today)));int count=0;
    for(EventStore.Event e:es){LocalDate d=e.next(today);if(d.equals(today))continue;showCard(e.title,d.format(DateTimeFormatter.ofPattern("d MMMM",new Locale("el","GR")))+" · "+e.category,null);if(++count>=25)break;}
  }
  void calendar(){LinearLayout bar=new LinearLayout(this);TextView prev=label("‹",24,accent),next=label("›",24,accent),mid=label(chosen.format(DateTimeFormatter.ofPattern("MMMM yyyy",new Locale("el","GR"))),20,ink);
    bar.addView(prev);bar.addView(mid,new LinearLayout.LayoutParams(0,-2,1));bar.addView(next);body.addView(bar);
    prev.setOnClickListener(v->{chosen=chosen.minusMonths(1);render();});next.setOnClickListener(v->{chosen=chosen.plusMonths(1);render();});
    for(int d=1;d<=chosen.lengthOfMonth();d++){LocalDate date=chosen.withDayOfMonth(d);List<EventStore.Event> es=on(date);if(!es.isEmpty()){StringBuilder s=new StringBuilder();for(EventStore.Event e:es){if(s.length()>0)s.append(" · ");s.append(e.title);}showCard(date.getDayOfMonth()+" "+date.getMonth().getDisplayName(java.time.format.TextStyle.FULL,new Locale("el","GR")),s.toString(),null);}}
  }
  void personal(){TextView add=label("+ Προσθήκη γεγονότος",19,accent);body.addView(add);add.setOnClickListener(v->edit(null));for(EventStore.Event e:store.editable())showCard(e.title,e.day+"/"+e.month+" · "+e.category,()->edit(e));}
  void edit(EventStore.Event e){boolean fresh=e==null;EditText name=new EditText(this),day=new EditText(this),month=new EditText(this);
    name.setHint("Όνομα ή γεγονός");day.setHint("Ημέρα (1-31)");month.setHint("Μήνας (1-12)");
    day.setInputType(2);month.setInputType(2);if(e!=null){name.setText(e.title);day.setText(""+e.day);month.setText(""+e.month);}
    LinearLayout form=column();form.setPadding(dp(16),0,dp(16),0);form.addView(name);form.addView(day);form.addView(month);
    AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(fresh?"Νέο γεγονός":"Επεξεργασία").setView(form).setNegativeButton("Άκυρο",null);
    if(!fresh)b.setNeutralButton("Διαγραφή",(d,w)->{store.delete(e.id);render();});
    b.setPositiveButton("Αποθήκευση",(d,w)->{try{int dd=Integer.parseInt(day.getText().toString()),mm=Integer.parseInt(month.getText().toString());LocalDate.of(2024,mm,dd);String title=name.getText().toString().trim();if(title.isEmpty())throw new Exception();
      store.save(new EventStore.Event(fresh?"user:"+UUID.randomUUID():e.id,title,"Προσωπικό",dd,mm,0,0,false,true));ReminderReceiver.schedule(this);render();
    }catch(Exception ex){Toast.makeText(this,"Μη έγκυρη ημερομηνία ή όνομα",Toast.LENGTH_LONG).show();}}).show();
  }
  void settings(){android.content.SharedPreferences p=getSharedPreferences("settings",0);
    Switch sw=new Switch(this);sw.setText("Ενεργές ειδοποιήσεις");sw.setChecked(p.getBoolean("notifications",true));body.addView(sw);
    sw.setOnCheckedChangeListener((v,on)->{p.edit().putBoolean("notifications",on).apply();ReminderReceiver.schedule(this);});
    showCard("Ώρα ειδοποίησης",p.getInt("hour",9)+":00",()->new TimePickerDialog(this,(v,h,m)->{p.edit().putInt("hour",h).apply();ReminderReceiver.schedule(this);render();},p.getInt("hour",9),0,true).show());
    showCard("Έλεγχος νέας έκδοσης","GitHub Releases · OTA",()->OtaUpdater.check(this,true));
  }
}
