package io.github.jared.xlowerseek;
import android.content.*;import android.net.Uri;import android.os.*;import java.util.concurrent.*;
/** Visible X owns a bound lease. Heartbeats expire if X dies without a lifecycle callback. */
final class TranslationPresence implements AutoCloseable {
 private final Context context;private final String id;private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService ipc=Executors.newSingleThreadExecutor(r->new Thread(r,"XUP-translation-presence"));
 private volatile boolean visible,closed;private Runnable release;
 private final Runnable beat=new Runnable(){public void run(){if(visible&&!closed){send(true);main.postDelayed(this,5000);}}};
 private final Runnable releaseDelayed=()->ipc.execute(this::release);
 TranslationPresence(Context c,String id){context=c;this.id=id;}
 void setVisible(boolean value){if(closed||visible==value)return;visible=value;main.removeCallbacks(beat);main.removeCallbacks(releaseDelayed);if(value)main.post(beat);else{send(false);main.postDelayed(releaseDelayed,120000);}}
 private void send(boolean foreground){ipc.execute(()->{try{
  if(foreground&&release==null)release=TranslationLease.acquire(context);
  Bundle b=new Bundle();b.putBoolean("visible",foreground);context.getContentResolver().call(Uri.parse("content://io.github.jared.xlowerseek.translation"),"foreground",id,b);
 }catch(RuntimeException ignored){}});}
 private void release(){if(release!=null){release.run();release=null;}}
 public void close(){if(closed)return;setVisible(false);closed=true;main.removeCallbacksAndMessages(null);ipc.execute(this::release);ipc.shutdown();}
}
