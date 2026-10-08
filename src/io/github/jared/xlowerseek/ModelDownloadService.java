package io.github.jared.xlowerseek;
import android.app.*;import android.content.*;import android.os.*;import java.net.URL;import java.util.concurrent.atomic.AtomicBoolean;
public final class ModelDownloadService extends Service {
 private static final String CHANNEL="model_download";private static final int NOTICE=46;
 static volatile boolean running,verifying;static volatile long received;static volatile String status="";
 private final AtomicBoolean pause=new AtomicBoolean();private Thread worker;private long lastUpdate;
 public IBinder onBind(Intent i){return null;}
 public void onCreate(){super.onCreate();getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"翻译模型下载",NotificationManager.IMPORTANCE_LOW));}
 private Notification notice(){
  PendingIntent cancel=PendingIntent.getService(this,46,new Intent(this,ModelDownloadService.class).setAction("pause"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("X-UP 本地模型").setContentText(status).setOnlyAlertOnce(true).setOngoing(true).setProgress(100,(int)(received*100/LocalModelSpec.BYTES),verifying).addAction(new Notification.Action.Builder(null,"暂停",cancel).build()).build();
 }
 public int onStartCommand(Intent intent,int flags,int id){
  if(intent!=null&&"pause".equals(intent.getAction())){pause.set(true);if(!running)stopSelf();return START_NOT_STICKY;}
  if(running)return START_NOT_STICKY;
  pause.set(false);running=true;verifying=false;received=ModelFiles.partial(this);status="正在下载 · 模型约 462 MB";
  try{startForeground(NOTICE,notice());}catch(RuntimeException e){running=false;status="无法启动下载任务，请重新打开插件重试";stopSelf();return START_NOT_STICKY;}
  worker=new Thread(()->{
   try{
    String[] sources={LocalModelSpec.URL,LocalModelSpec.FALLBACK_URL};
    for(int source=0;source<sources.length;source++){
     try{ModelTransfer.download(new URL(sources[source]),ModelFiles.model(this),LocalModelSpec.BYTES,LocalModelSpec.SHA256,pause,(bytes,checking)->{
     received=bytes;verifying=checking;status=checking?"正在校验完整性…":String.format(java.util.Locale.ROOT,"%.1f / 461.9 MB",bytes/1000000.0);
     long now=SystemClock.elapsedRealtime();if(now-lastUpdate>=350||checking){lastUpdate=now;getSystemService(NotificationManager.class).notify(NOTICE,notice());}
    });break;
     }catch(ModelTransfer.Paused e){throw e;}
     catch(java.io.IOException e){if(pause.get())throw new ModelTransfer.Paused();if(source==sources.length-1)throw e;status="正在尝试另一个腾讯官方下载地址…";}
    }
    ModelFiles.activate(this);status="模型已下载并通过校验，可以离线翻译";
   }catch(ModelTransfer.Paused e){status=e.getMessage();}
   catch(Exception e){status=e instanceof java.io.IOException?e.getMessage():"模型下载失败，请检查网络或存储空间后重试";}
   finally{running=false;verifying=false;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
  },"XUP-model-download");worker.start();return START_NOT_STICKY;
 }
 public void onDestroy(){pause.set(true);super.onDestroy();}
 public void onTimeout(int id,int type){pause.set(true);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
}
