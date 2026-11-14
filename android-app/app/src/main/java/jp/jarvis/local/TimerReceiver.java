package jp.jarvis.local;

import android.app.*;
import android.content.*;

public class TimerReceiver extends BroadcastReceiver {
    public static final String CHANNEL="jarvis_timers";
    @Override public void onReceive(Context c,Intent i){long id=i.getLongExtra("timer_id",-1);String label=i.getStringExtra("label");NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(android.os.Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"JARVISタイマー",NotificationManager.IMPORTANCE_HIGH));nm.notify((int)Math.max(1,id),new Notification.Builder(c,CHANNEL).setContentTitle("JARVISタイマー").setContentText(label==null?"タイマーが終了しました":label).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setAutoCancel(true).build());}
}
