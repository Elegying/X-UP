package io.github.jared.xlowerseek;
import java.nio.charset.StandardCharsets;import java.util.*;
final class OpusTranslator implements OfflineEngine {
 static {System.loadLibrary("xup_opus");}
 private long handle;private final LinkedHashMap<String,String> cache=new LinkedHashMap<>(16,.75f,true);
 OpusTranslator(String path){handle=open(path);if(handle==0)throw new IllegalStateException("OPUS 无法加载");}
 public String translate(String text,String context,TranslationCancellation cancelled)throws Exception{
  return SmallModelText.translate(text,(part,ja)->{String key=ja+":"+part;String hit=cache.get(key);if(hit!=null)return hit;
   long epoch=ticket(handle);cancelled.check();String value=new String(run(handle,part.getBytes(StandardCharsets.UTF_8),ja,epoch),StandardCharsets.UTF_8).trim();cancelled.check();cache.put(key,value);while(cache.size()>256)cache.remove(cache.keySet().iterator().next());return value;
  },cancelled);
 }
 public synchronized void cancel(){if(handle!=0)cancel(handle);}
 public synchronized void close(){if(handle!=0){close(handle);handle=0;}cache.clear();}
 private static native long open(String path);private static native byte[] run(long handle,byte[] input,boolean japanese,long ticket);
 private static native long ticket(long handle);private static native void cancel(long handle);private static native void close(long handle);
}
