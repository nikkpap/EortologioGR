package gr.eortologio.plus;

import android.app.*;
import android.content.*;
import android.os.Build;
import java.time.*;
import java.util.*;

public class ReminderReceiver extends android.content.BroadcastReceiver {
    public static final String CHANNEL="eortologio_events";
    public static void schedule(Context ctx){
        AlarmManager am=(AlarmManager)ctx.getSystemService(Context.ALARM_SERVICE);if(am==null)return;
        PendingIntent p=PendingIntent.getBroadcast(ctx,41,new Intent(ctx,ReminderReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        am.cancel(p);
        android.content.SharedPreferences sp=ctx.getSharedPreferences("settings",Context.MODE_PRIVATE);
        if(!sp.getBoolean("notifications",true))return;
        LocalDateTime now=LocalDateTime.now();int hour=sp.getInt("hour",9);
        LocalDateTime next=now.toLocalDate().atTime(hour,0);
        if(!next.isAfter(now))next=next.plusDays(1);
        long when=next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        am.setWindow(AlarmManager.RTC_WAKEUP,when,60L*60L*1000L,p);
    }
    @Override public void onReceive(Context ctx,Intent intent){
        try {
            android.content.SharedPreferences sp=ctx.getSharedPreferences("settings",Context.MODE_PRIVATE);
            if(!sp.getBoolean("notifications",true))return;
            LocalDate today=LocalDate.now();int lead=sp.getInt("lead",1);
            List<String> hits=new ArrayList<>();for(EventStore.Event e:new EventStore(ctx).all(ctx)){
                if(e.personal || sp.getBoolean("allNames",false)) {
                    LocalDate next=e.next(today);long days=java.time.temporal.ChronoUnit.DAYS.between(today,next);
                    if(days==0||days==lead)hits.add((days==0?"Σήμερα: ":"Σε "+days+" ημέρα/ες: ")+e.title);
                }
            }
            if(!hits.isEmpty()){
                NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
                nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Γιορτές και υπενθυμίσεις",NotificationManager.IMPORTANCE_DEFAULT));
                if(Build.VERSION.SDK_INT<33||ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS")==android.content.pm.PackageManager.PERMISSION_GRANTED){
                    Intent open=new Intent(ctx,MainActivity.class);
                    PendingIntent pi=PendingIntent.getActivity(ctx,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
                    String title=hits.size()==1?"Εορτολόγιο+ · Υπενθύμιση":"Εορτολόγιο+ · "+hits.size()+" υπενθυμίσεις";
                    Notification.Builder b=new Notification.Builder(ctx,CHANNEL).setSmallIcon(gr.eortologio.plus.R.drawable.ic_event).setContentTitle(title).setContentText(hits.get(0)).setStyle(new Notification.BigTextStyle().bigText(String.join("\n",hits))).setAutoCancel(true).setContentIntent(pi);
                    nm.notify(41,b.build());
                }
            }
        }catch(Exception ignored){}finally{schedule(ctx);}
    }
}
