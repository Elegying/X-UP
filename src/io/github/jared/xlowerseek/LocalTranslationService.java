package io.github.jared.xlowerseek;
import android.content.Context;import android.os.*;import java.util.*;import java.util.concurrent.*;
/** Bounded deduplicated queue. Cancellation removes subscriptions and stops work with no consumers. */
final class LocalTranslationService {
 private static final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->new Thread(r,"XUP-offline-translation"));
 private static final Map<String,Job> jobs=new HashMap<>(),requests=new HashMap<>();
 private static final LinkedHashMap<String,String> cache=new LinkedHashMap<>(16,.75f,true);
 private static final Handler main=new Handler(Looper.getMainLooper());
 private static OfflineEngine engine;private static LocalEngine loadedEngine;private static long loadedRevision=-1;
 private static long releaseAt=-1;
 private static final Map<String,Long> visibleClients=new HashMap<>();
 private static final Runnable reap=new Runnable(){public void run(){synchronized(LocalTranslationService.class){visibleClients.values().removeIf(t->SystemClock.elapsedRealtime()-t>20000);if(visibleClients.isEmpty()){scheduleRelease();}else main.postDelayed(this,5000);}}};
 private static Job active;private static volatile String status="请选择引擎并下载对应模型。";
 private static final Runnable releaseIdle=()->{try{worker.execute(()->{synchronized(LocalTranslationService.class){if(!jobs.isEmpty()||!visibleClients.isEmpty())return;if(engine!=null)engine.close();engine=null;loadedEngine=null;releaseAt=-1;}});}catch(RejectedExecutionException ignored){}};
 static {worker.allowCoreThreadTimeOut(true);}
 private static void scheduleRelease(){if(releaseAt<0)releaseAt=SystemClock.uptimeMillis()+120000;main.removeCallbacks(releaseIdle);main.postDelayed(releaseIdle,Math.max(0,releaseAt-SystemClock.uptimeMillis()));}
 static String status(){return status;}
 static void foreground(Context c,int owner,String id,boolean visible){
  synchronized(LocalTranslationService.class){String key=owner+":"+id;if(visible){boolean first=visibleClients.isEmpty();visibleClients.put(key,SystemClock.elapsedRealtime());releaseAt=-1;main.removeCallbacks(releaseIdle);main.removeCallbacks(reap);main.postDelayed(reap,5000);if(first)warm(c.getApplicationContext());}
   else{visibleClients.remove(key);if(visibleClients.isEmpty()){main.removeCallbacks(reap);scheduleRelease();}}}
 }
 private static void warm(Context app){try{worker.execute(()->{
  LocalEngine selected=SettingsStore.engine(app);long rev=LocalModels.revision(app);if(!SettingsStore.translate(app)||!SettingsStore.feature(app,Feature.LOCAL_TEXT)||!LocalModels.ready(app,selected))return;
  synchronized(LocalTranslationService.class){if(visibleClients.isEmpty()||!jobs.isEmpty())return;}
  try{ensureEngine(app,selected,rev);}catch(Exception|LinkageError ignored){}
 });}catch(RejectedExecutionException ignored){}}
 private static void ensureEngine(Context app,LocalEngine choice,long revision)throws Exception{
  if(engine!=null&&loadedEngine==choice&&loadedRevision==revision)return;
  OfflineEngine old;synchronized(LocalTranslationService.class){old=engine;engine=null;}if(old!=null)old.close();
  OfflineEngine opened=LocalModels.open(app,choice);synchronized(LocalTranslationService.class){engine=opened;loadedEngine=choice;loadedRevision=revision;}
 }

 static void request(Context c,String text,String background,ResultReceiver receiver){request(c,android.os.Process.myUid(),UUID.randomUUID().toString(),SettingsStore.engine(c).id,LocalModels.revision(c),text,background,receiver);}
 static void request(Context context,int owner,String requestId,String selected,long revision,String text,String background,ResultReceiver receiver){
  Context app=context.getApplicationContext();LocalEngine choice=SettingsStore.engine(app);
  if(!SettingsStore.translate(app)||!SettingsStore.feature(app,Feature.LOCAL_TEXT)||!choice.id.equals(selected)||revision!=LocalModels.revision(app)){send(receiver,2,null);return;}
  if(!LocalModels.ready(app,choice)){status="所选模型尚未就绪，请打开模型页面检查。";send(receiver,2,null);return;}
  String effective=choice==LocalEngine.TENCENT&&SettingsStore.feature(app,Feature.LOCAL_CONTEXT)?background:"";
  String key=choice.id+":"+revision+":"+effective.length()+":"+effective+text;String id=owner+":"+requestId;
  synchronized(LocalTranslationService.class){
   String hit=cache.get(key);if(hit!=null){send(receiver,0,hit);return;}
   if(requests.containsKey(id)){send(receiver,2,null);return;}
   Job job=jobs.get(key);if(job==null){if(jobs.size()>=8){send(receiver,1,null);return;}job=new Job(app,choice,revision,key,text,effective);jobs.put(key,job);}
   if(job.waiting.size()>=8){send(receiver,1,null);return;}
   job.waiting.put(id,receiver);requests.put(id,job);main.removeCallbacks(releaseIdle);
   if(!job.submitted){job.submitted=true;try{worker.execute(job);}catch(RejectedExecutionException busy){complete(job,1,null);}}
  }
 }
 static synchronized void cancel(int owner,String requestId){
  String id=owner+":"+requestId;Job job=requests.remove(id);if(job==null)return;job.waiting.remove(id);
  if(job.waiting.isEmpty()){job.cancelled.cancel();jobs.remove(job.key,job);worker.remove(job);if(active==job&&engine!=null)engine.cancel();}
  if(jobs.isEmpty()&&visibleClients.isEmpty()){scheduleRelease();}
 }
 static void settingsChanged(Context c){
  if(c==null)return;LocalEngine choice=SettingsStore.engine(c);long revision=LocalModels.revision(c);boolean enabled=SettingsStore.translate(c)&&SettingsStore.feature(c,Feature.LOCAL_TEXT);
  synchronized(LocalTranslationService.class){for(Job job:new ArrayList<>(jobs.values()))if(!enabled||job.choice!=choice||job.revision!=revision){job.cancelled.cancel();worker.remove(job);if(active==job&&engine!=null)engine.cancel();complete(job,2,null);}if(enabled&&!visibleClients.isEmpty())warm(c.getApplicationContext());else if(!enabled){visibleClients.clear();
   // A cancelled job may finish before releaseIdle runs; keep release due immediately.
   releaseAt=SystemClock.uptimeMillis();main.removeCallbacks(releaseIdle);main.post(releaseIdle);}}
 }
 private static final class Job implements Runnable {
  final Context app;final LocalEngine choice;final long revision,enqueued=SystemClock.elapsedRealtime();final String key,text,background;
  final Map<String,ResultReceiver> waiting=new LinkedHashMap<>();final TranslationCancellation cancelled=new TranslationCancellation();boolean submitted;
  Job(Context c,LocalEngine e,long r,String k,String t,String b){app=c;choice=e;revision=r;key=k;text=t;background=b;}
  public void run(){
   int code=2;String result=null;
   try{
    cancelled.check();if(SystemClock.elapsedRealtime()-enqueued>15000){code=1;return;}
    if(!SettingsStore.translate(app)||!SettingsStore.feature(app,Feature.LOCAL_TEXT)||choice!=SettingsStore.engine(app)||revision!=LocalModels.revision(app))return;
    synchronized(LocalTranslationService.class){active=this;}
    ensureEngine(app,choice,revision);
    cancelled.check();long started=SystemClock.elapsedRealtime();String translated=engine.translate(text,background,cancelled);cancelled.check();
    if(!TranslationOutput.valid(text,translated)){status="译文不完整，已保留原文。";return;}
    if(choice!=SettingsStore.engine(app)||revision!=LocalModels.revision(app)||!SettingsStore.translate(app))return;
    code=0;result=translated;status=choice.title+" · "+(SystemClock.elapsedRealtime()-started)+" 毫秒";
    android.util.Log.i("XUP-local","engine="+choice.id+" chars="+text.length()+" elapsedMs="+(SystemClock.elapsedRealtime()-enqueued));
   }catch(InterruptedException cancelledWork){code=2;}
   catch(Exception|LinkageError failure){status="本地翻译未完成，请在模型页面测试或切换引擎。";code=1;}
   finally{synchronized(LocalTranslationService.class){if(active==this)active=null;complete(this,cancelled.cancelled()?2:code,result);}}
  }
 }
 private static synchronized void complete(Job job,int code,String text){
  jobs.remove(job.key,job);if(code==0){cache.put(job.key,text);while(cache.size()>256)cache.remove(cache.keySet().iterator().next());}
  for(Map.Entry<String,ResultReceiver> entry:job.waiting.entrySet()){requests.remove(entry.getKey(),job);send(entry.getValue(),code,text);}job.waiting.clear();
  if(jobs.isEmpty()&&visibleClients.isEmpty()){scheduleRelease();}
 }
 private static void send(ResultReceiver r,int code,String text){try{Bundle out=new Bundle();if(text!=null&&code==0)out.putString("text",text);r.send(code,out);}catch(RuntimeException ignored){}}
}
