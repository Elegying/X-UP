package io.github.jared.xlowerseek;
import java.util.concurrent.*;import java.util.concurrent.atomic.*;
final class LocalModels {
 static final AtomicInteger loads=new AtomicInteger(),closes=new AtomicInteger(),calls=new AtomicInteger(),cancels=new AtomicInteger();
 static volatile boolean blocking;static volatile CountDownLatch started=new CountDownLatch(1);
 static long revision(android.content.Context c){return 1;}static boolean ready(android.content.Context c,LocalEngine e){return true;}
 static OfflineEngine open(android.content.Context c,LocalEngine e){loads.incrementAndGet();return new OfflineEngine(){public String translate(String t,String b,TranslationCancellation cancel)throws Exception{calls.incrementAndGet();started.countDown();while(blocking){cancel.check();Thread.sleep(2);}cancel.check();return "译文："+e.id+t;}public void cancel(){cancels.incrementAndGet();}public void close(){closes.incrementAndGet();}};}
}
