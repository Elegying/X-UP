package io.github.jared.xlowerseek;
import android.content.Context;import android.os.*;import java.util.*;import java.util.concurrent.*;
/** Shared, warm native model with bounded deduplication and memory-only translations. */
final class LocalTranslationService {
 private static final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->new Thread(r,"XUP-offline-translation"));
 private static final Map<String,List<ResultReceiver>> pending=new HashMap<>();
 private static final LinkedHashMap<String,String> cache=new LinkedHashMap<>(16,.75f,true);
 private static NativeTranslator engine;private static long loadedRevision=-1;
 private static volatile String status="下载模型后即可使用本地翻译。";
 static {worker.allowCoreThreadTimeOut(true);}
 static String status(){return status;}
 static void request(Context context,String text,String background,ResultReceiver receiver){
  Context app=context.getApplicationContext();
  if(!SettingsStore.translate(app)||!SettingsStore.feature(app,Feature.LOCAL_TEXT)){send(receiver,2,null);return;}
  if(!ModelFiles.ready(app)){status="模型尚未下载完成，请在本地翻译页面下载。";send(receiver,2,null);return;}
  long revision=ModelFiles.revision(app);String key=revision+":"+background.length()+":"+background+text;
  synchronized(LocalTranslationService.class){
   String hit=cache.get(key);if(hit!=null){send(receiver,0,hit);return;}
   List<ResultReceiver> joined=pending.get(key);if(joined!=null){if(joined.size()<8)joined.add(receiver);else send(receiver,1,null);return;}
   pending.put(key,new ArrayList<>(Collections.singletonList(receiver)));
  }
  long enqueued=SystemClock.elapsedRealtime();
  try{worker.execute(()->{
   int code=2;String result=null;
   try{
    if(!SettingsStore.translate(app)||!SettingsStore.feature(app,Feature.LOCAL_TEXT)||revision!=ModelFiles.revision(app))return;
    if(SystemClock.elapsedRealtime()-enqueued>30000){code=1;return;}
    if(engine==null||loadedRevision!=revision){if(engine!=null)engine.close();engine=null;engine=new NativeTranslator(ModelFiles.model(app).getAbsolutePath());loadedRevision=revision;synchronized(LocalTranslationService.class){cache.clear();}}
    long started=SystemClock.elapsedRealtime();String translated=engine.translate(LocalTranslationPrompt.build(text,background));
    if(!TranslationOutput.valid(text,translated)){status="本次译文格式不完整，已保留原文。";return;}
    code=0;result=translated;status="本地翻译完成 · "+(SystemClock.elapsedRealtime()-started)+" 毫秒";
    android.util.Log.i("XUP-local","completed chars="+text.length()+" contextChars="+background.length()+" elapsedMs="+(SystemClock.elapsedRealtime()-enqueued));
   }catch(Exception|LinkageError failure){status="本地翻译暂不可用，请在模型页面测试；内存不足时可关闭其他应用。";code=1;}
   finally{
    List<ResultReceiver> waiting;
    synchronized(LocalTranslationService.class){
     waiting=pending.remove(key);if(!SettingsStore.translate(app)||!SettingsStore.feature(app,Feature.LOCAL_TEXT)||revision!=ModelFiles.revision(app)){code=2;result=null;}
     if(code==0){cache.put(key,result);while(cache.size()>128)cache.remove(cache.keySet().iterator().next());}
    }
    if(waiting!=null)for(ResultReceiver r:waiting)send(r,code,result);
   }
  });}catch(RejectedExecutionException busy){List<ResultReceiver> waiting; synchronized(LocalTranslationService.class){waiting=pending.remove(key);}if(waiting!=null)for(ResultReceiver r:waiting)send(r,1,null);}
 }
 private static void send(ResultReceiver r,int code,String text){try{Bundle out=new Bundle();if(text!=null)out.putString("text",text);r.send(code,out);}catch(RuntimeException ignored){}}
}
