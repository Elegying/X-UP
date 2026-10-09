package android.app;import android.content.*;import android.os.*;public abstract class Service extends Context {
 public static final int START_NOT_STICKY=2,STOP_FOREGROUND_REMOVE=1;public boolean stopped;public abstract IBinder onBind(Intent intent);public void onCreate(){}public int onStartCommand(Intent i,int f,int id){return 2;}public void onDestroy(){}public void onTimeout(int id,int type){}public void startForeground(int id,Notification n){}public void stopForeground(int flags){}public void stopSelf(){stopped=true;}public void stopSelf(int id){stopped=true;}
}
