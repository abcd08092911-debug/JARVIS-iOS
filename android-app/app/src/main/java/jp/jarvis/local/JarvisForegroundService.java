package jp.jarvis.local;

import android.app.*;
import android.content.Intent;
import android.os.IBinder;

public class JarvisForegroundService extends Service {
    public static final String CHANNEL="jarvis_runtime";
    @Override public void onCreate(){super.onCreate();NotificationManager nm=getSystemService(NotificationManager.class);if(android.os.Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"JARVIS常駐サービス",NotificationManager.IMPORTANCE_LOW));Notification n=new Notification.Builder(this,CHANNEL).setContentTitle("JARVIS").setContentText("親機8765・中継9000を維持中").setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).build();startForeground(8765,n);}
    @Override public int onStartCommand(Intent i,int f,int id){return START_STICKY;}
    @Override public IBinder onBind(Intent i){return null;}
}
