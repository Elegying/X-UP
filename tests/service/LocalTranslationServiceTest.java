package io.github.jared.xlowerseek;
import android.content.*;import android.os.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public final class LocalTranslationServiceTest {
 static int checks;static final Context app=new Context(){public ContentResolver getContentResolver(){return null;}};
 static void check(boolean b){checks++;if(!b)throw new AssertionError("service case "+checks);}
 static void until(java.util.function.BooleanSupplier done)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!done.getAsBoolean()&&System.nanoTime()<end){Handler.drain();Thread.sleep(2);}check(done.getAsBoolean());}
 static void request(int owner,String id,String text,AtomicInteger result){LocalTranslationService.request(app,owner,id,SettingsStore.selected.id,1,text,"",new ResultReceiver(new Handler(Looper.getMainLooper())){protected void onReceiveResult(int code,Bundle b){result.set(code);}});}
 public static void main(String[]args)throws Exception{try{
  LocalTranslationService.foreground(app,7,"page",true);until(()->LocalModels.loads.get()==1);
  LocalTranslationService.foreground(app,7,"page",false);Handler.advance(119999);Thread.sleep(20);check(LocalModels.closes.get()==0);
  LocalTranslationService.foreground(app,7,"page",true);Handler.advance(1);Thread.sleep(20);check(LocalModels.loads.get()==1&&LocalModels.closes.get()==0);
  LocalTranslationService.foreground(app,7,"page",false);Handler.advance(120000);until(()->LocalModels.closes.get()==1);
  LocalTranslationService.foreground(app,7,"page",true);until(()->LocalModels.loads.get()==2);
  AtomicInteger first=new AtomicInteger(-1),second=new AtomicInteger(-1);LocalModels.blocking=true;
  request(7,"one","same source",first);check(LocalModels.started.await(2,TimeUnit.SECONDS));request(7,"two","same source",second);
  LocalTranslationService.cancel(8,"one");check(LocalModels.cancels.get()==0);LocalTranslationService.cancel(7,"one");check(LocalModels.cancels.get()==0);
  LocalModels.blocking=false;until(()->second.get()==0);check(first.get()==-1&&LocalModels.calls.get()==1);
  AtomicInteger cached=new AtomicInteger(-1);request(7,"cached","same source",cached);until(()->cached.get()==0);check(LocalModels.calls.get()==1);
  LocalModels.started=new CountDownLatch(1);LocalModels.blocking=true;AtomicInteger cancelled=new AtomicInteger(-1);request(7,"cancel","obsolete source",cancelled);check(LocalModels.started.await(2,TimeUnit.SECONDS));LocalTranslationService.cancel(7,"cancel");until(()->LocalModels.cancels.get()==1);LocalModels.blocking=false;
  SettingsStore.selected=LocalEngine.OPUS;LocalTranslationService.settingsChanged(app);until(()->LocalModels.loads.get()==3);check(LocalModels.closes.get()==2);
  AtomicInteger opus=new AtomicInteger(-1);request(7,"new","same source",opus);until(()->opus.get()==0);check(LocalModels.calls.get()==3);
  SettingsStore.enabled=false;LocalTranslationService.settingsChanged(app);Handler.drain();until(()->LocalModels.closes.get()==3);
  System.out.println("LocalTranslationService: "+checks+" assertions passed (queue, dedup, caller isolation, cancellation, engine switching, 2-minute residency)");
 }finally{java.lang.reflect.Field f=LocalTranslationService.class.getDeclaredField("worker");f.setAccessible(true);((ExecutorService)f.get(null)).shutdownNow();}}
}
