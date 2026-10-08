package io.github.jared.xlowerseek;
import android.content.*;import android.net.Uri;import android.os.*;import java.util.*;import java.util.concurrent.*;
/** Main-thread display state; context is part of identity and all inference is offline. */
final class LocalTranslationClient implements AutoCloseable {
 interface Listener {void changed();}
 private static final class Entry {
  String text,source,background,requestId;long lastSeen;long revision,retryAt;volatile boolean pending;boolean failed,retryable;volatile int attempts;int failures;Runnable timeout;
  final Set<Listener> listeners=Collections.newSetFromMap(new WeakHashMap<>());
  final java.util.concurrent.atomic.AtomicReference<ContentProviderClient> provider=new java.util.concurrent.atomic.AtomicReference<>();
  final java.util.concurrent.atomic.AtomicReference<Runnable> lease=new java.util.concurrent.atomic.AtomicReference<>();
 }
 private final TranslationPresence presence;
 private final Context context;private final RemoteSettings settings;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService transport=Executors.newSingleThreadExecutor(r->new Thread(r,"XUP-local-IPC"));
 private final LinkedHashMap<String,Entry> cache=new LinkedHashMap<>(16,.75f,true);private final Set<Entry> active=new HashSet<>();
 private final String clientId=UUID.randomUUID().toString();private long requestSequence;
 private volatile int generation;private int inFlight;private boolean foreground=true;
 LocalTranslationClient(Context context,RemoteSettings settings){this.context=context;this.settings=settings;presence=new TranslationPresence(context,clientId);}
 void setForeground(boolean visible){presence.setVisible(visible&&settings.translate&&settings.feature(Feature.LOCAL_TEXT));if(visible&&!foreground)for(Entry e:cache.values())if(e.failed&&e.retryable&&!e.pending){e.failed=false;e.failures=0;e.retryAt=0;}foreground=visible;}
 String lookup(String source,Listener listener){return lookup(source,"",listener);}
 String lookup(String source,String background,Listener listener){
  if(!settings.translate||!settings.feature(Feature.LOCAL_TEXT)||source.length()>8192||!PostTranslationPolicy.eligible(null,source))return source;
  String key=settings.engine+":"+settings.localRevision+":"+background.length()+":"+background+source;Entry entry=cache.get(key);
  if(entry==null){if(cache.size()>=256)cache.remove(cache.keySet().iterator().next());entry=new Entry();entry.source=source;entry.background=background;entry.revision=settings.localRevision;cache.put(key,entry);}
  entry.lastSeen=SystemClock.uptimeMillis();if(listener!=null)entry.listeners.add(listener);if(entry.text!=null)return entry.text;
  if(foreground&&!entry.pending&&!entry.failed&&SystemClock.uptimeMillis()>=entry.retryAt&&inFlight<2)request(key,entry);
  return source;
 }
 private void request(String key,Entry entry){
  entry.requestId=clientId+":"+(++requestSequence);entry.pending=true;inFlight++;active.add(entry);int epoch=generation,attempt=++entry.attempts;final String requestId=entry.requestId,selectedEngine=settings.engine;
  ResultReceiver receiver=new ResultReceiver(main){protected void onReceiveResult(int code,Bundle data){finish(key,entry,epoch,attempt,code,data);}};
  entry.timeout=()->finish(key,entry,epoch,attempt,1,null);main.postDelayed(entry.timeout,45000);
  transport.execute(()->{
   try{
    if(epoch!=generation||attempt!=entry.attempts||!entry.pending||!settings.translate)return;
    ContentProviderClient provider=context.getContentResolver().acquireUnstableContentProviderClient(Uri.parse("content://io.github.jared.xlowerseek.translation"));if(provider==null)throw new IllegalStateException();
    entry.provider.set(provider);entry.lease.set(TranslationLease.acquire(context));
    if(epoch!=generation||attempt!=entry.attempts||!entry.pending||!settings.translate){closeResources(entry);return;}
    Bundle args=new Bundle();args.putString("id",requestId);args.putString("engine",selectedEngine);args.putLong("revision",entry.revision);args.putString("text",entry.source);args.putString("context",entry.background);args.putParcelable("receiver",ResultReceiverTransport.remote(receiver));provider.call("translate",null,args);
   }catch(Throwable unavailable){receiver.send(1,null);}
  });
 }
 private static void closeResources(Entry entry){ContentProviderClient provider=entry.provider.getAndSet(null);try{if(provider!=null)provider.close();}finally{Runnable lease=entry.lease.getAndSet(null);if(lease!=null)lease.run();}}
 private void finish(String key,Entry entry,int epoch,int attempt,int code,Bundle data){
  if(epoch!=generation||attempt!=entry.attempts||!entry.pending)return;
  entry.pending=false;active.remove(entry);inFlight=Math.max(0,inFlight-1);String completedId=entry.requestId;transport.execute(()->{try{ContentProviderClient p=entry.provider.get();if(code!=0&&p!=null)p.call("cancel",completedId,null);}catch(Exception ignored){}finally{closeResources(entry);}});main.removeCallbacks(entry.timeout);
  if(!settings.translate||!settings.feature(Feature.LOCAL_TEXT)){clear();return;}
  if(entry.revision!=settings.localRevision)return;
  String text=data==null?null:data.getString("text");
  if(code==0&&text!=null&&!text.isEmpty())entry.text=text;
  else{entry.retryable=code==1;if(entry.retryable&&++entry.failures<=1)entry.retryAt=SystemClock.uptimeMillis()+1000;else entry.failed=true;}
  Set<Listener> notify=new HashSet<>();for(Entry e:cache.values())notify.addAll(e.listeners);if(foreground)for(Listener l:notify)changed(l);
  if(entry.text==null&&!entry.failed)main.postDelayed(()->{if(foreground&&epoch==generation&&cache.get(key)==entry)for(Listener l:new ArrayList<>(entry.listeners))changed(l);},1000);
 }
 private static void changed(Listener l){try{l.changed();}catch(RuntimeException ignored){}}
 void retryFailures(){for(Entry e:cache.values())if(!e.pending&&e.text==null){e.failed=false;e.failures=0;e.retryAt=0;}}
 private void cancelEntry(Entry entry){
  entry.pending=false;entry.attempts++;active.remove(entry);inFlight=Math.max(0,inFlight-1);main.removeCallbacks(entry.timeout);
  String id=entry.requestId;transport.execute(()->{ContentProviderClient p=entry.provider.get();try{if(p!=null)p.call("cancel",id,null);}catch(Exception ignored){}finally{closeResources(entry);}});
 }
 void pruneInvisible(){long now=SystemClock.uptimeMillis();for(Entry e:new ArrayList<>(active))if(now-e.lastSeen>2500)cancelEntry(e);}
 void suspend(){foreground=false;for(Entry e:new ArrayList<>(active))cancelEntry(e);}
 void clear(){generation++;main.removeCallbacksAndMessages(null);for(Entry e:new ArrayList<>(active))cancelEntry(e);cache.clear();inFlight=0;}
 public void close(){presence.close();clear();transport.shutdown();}
}
