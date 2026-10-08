package io.github.jared.xlowerseek;

import android.app.*;
import android.content.Intent;
import android.os.*;

/** A short, user-visible foreground lease for network translation, owned by X's binding. */
public final class TranslationKeepAliveService extends Service {
    private static final String CHANNEL="translation";
    private final IBinder binder=new Binder();
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean bound;
    private final Runnable expiry=()->{stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();};
    @Override public void onCreate(){
        super.onCreate();getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"翻译任务",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        try{startForeground(44,new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("X-UP 正在翻译")
            .setContentText("完成后自动结束").setOnlyAlertOnce(true).setOngoing(true).build());}
        catch(RuntimeException e){stopSelf();return START_NOT_STICKY;}
        main.removeCallbacks(expiry);main.postDelayed(expiry,190000);
        main.postDelayed(()->{if(!bound){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}},3000);
        return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent intent){bound=true;return binder;}
    @Override public boolean onUnbind(Intent intent){bound=false;main.removeCallbacks(expiry);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return false;}
    @Override public void onDestroy(){main.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override public void onTimeout(int id,int type){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
}
