package io.github.jared.xlowerseek;
import android.app.*;import android.content.*;import android.os.*;import java.io.File;import java.net.URL;import java.util.concurrent.atomic.AtomicBoolean;
public final class ModelDownloadService extends Service {
 private static final String CHANNEL="model_download";private static final int NOTICE=46;
 static volatile boolean running,verifying;static volatile long received,total;static volatile String status="";static volatile LocalEngine downloadingEngine=LocalEngine.DEFAULT;
 private static long sequence;private long token;private boolean destroyed,finished;private final Handler main=new Handler(Looper.getMainLooper());
 private final AtomicBoolean pause=new AtomicBoolean();private long lastUpdate;
 public IBinder onBind(Intent i){return null;}
 public void onCreate(){super.onCreate();getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"翻译模型下载",NotificationManager.IMPORTANCE_LOW));}
 private Notification notice(){
  Notification.Builder n=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("X-UP · "+downloadingEngine.title).setContentText(status).setOnlyAlertOnce(true).setOngoing(true).setProgress(100,total>0?(int)Math.min(100,received*100/total):0,verifying||total<=0);
  if(downloadingEngine!=LocalEngine.MLKIT){PendingIntent cancel=PendingIntent.getService(this,46,new Intent(this,ModelDownloadService.class).setAction("pause"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);n.addAction(new Notification.Action.Builder(null,"暂停",cancel).build());}return n.build();
 }
 public int onStartCommand(Intent intent,int flags,int id){
  if(intent!=null&&"pause".equals(intent.getAction())){pause.set(true);if(!running)stopSelf();return START_NOT_STICKY;}
  if(running){if(token==0)stopSelf();return START_NOT_STICKY;}
  LocalEngine choice=LocalEngine.parse(intent==null?null:intent.getStringExtra("engine"));
  if(LocalModels.ready(this,choice)){stopSelf();return START_NOT_STICKY;}
  pause.set(false);running=true;token=++sequence;downloadingEngine=choice;verifying=false;received=0;total=choice==LocalEngine.TENCENT?LocalModelSpec.BYTES:choice==LocalEngine.OPUS?OpusFiles.BYTES:0;status="正在准备下载…";
  try{startForeground(NOTICE,notice());}catch(RuntimeException e){running=false;status="无法启动下载任务，请重新打开插件重试";stopSelf();return START_NOT_STICKY;}
  if(choice==LocalEngine.MLKIT){MlKitModels.download(this,()->{status=MlKitModels.status;finish(token);});return START_NOT_STICKY;}
  final long task=token;new Thread(()->{
   try{
    File target=choice==LocalEngine.TENCENT?ModelFiles.model(this):OpusFiles.archive(this);
    String digest=choice==LocalEngine.TENCENT?LocalModelSpec.SHA256:OpusFiles.SHA256;
    String[] sources=choice==LocalEngine.TENCENT?new String[]{LocalModelSpec.URL,LocalModelSpec.FALLBACK_URL}:new String[]{OpusFiles.URL};
    for(int source=0;source<sources.length;source++)try{
     ModelTransfer.download(new URL(sources[source]),target,total,digest,pause,(bytes,checking)->{
      if(pause.get())return;received=bytes;verifying=checking;status=checking?"正在校验完整性…":String.format(java.util.Locale.ROOT,"%.1f / %.1f MB",bytes/1000000.0,total/1000000.0);
      long now=SystemClock.elapsedRealtime();if(now-lastUpdate>=350||checking){lastUpdate=now;main.post(()->{if(!destroyed&&running&&sequence==task)getSystemService(NotificationManager.class).notify(NOTICE,notice());});}
     });break;
    }catch(ModelTransfer.Paused e){throw e;}catch(java.io.IOException e){if(pause.get())throw new ModelTransfer.Paused();if(source==sources.length-1)throw e;status="正在尝试另一个官方下载地址…";}
    if(pause.get())throw new ModelTransfer.Paused();
    if(choice==LocalEngine.TENCENT)ModelFiles.activate(this);else{status="正在安装并校验 OPUS 模型…";OpusFiles.install(this,pause);}
    status="模型已就绪，可以离线翻译";
   }catch(ModelTransfer.Paused e){status=e.getMessage();}catch(Exception e){status=e instanceof java.io.IOException?e.getMessage():"模型下载失败，请检查网络或存储空间后重试";}
   finally{main.post(()->finish(task));}
  },"XUP-model-download").start();return START_NOT_STICKY;
 }
 private void finish(long task){if(sequence!=task)return;verifying=false;finished=true;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();if(destroyed)running=false;}
 public void onDestroy(){destroyed=true;pause.set(true);if(finished&&sequence==token)running=false;super.onDestroy();}
 public void onTimeout(int id,int type){pause.set(true);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
}
