package io.github.jared.xlowerseek;
import com.google.mlkit.nl.translate.*;
import com.google.android.gms.tasks.*;
import java.util.*;import java.util.concurrent.*;
final class MlKitTranslator implements OfflineEngine {
 private final Map<Boolean,Translator> engines=new HashMap<>();
 private final LinkedHashMap<String,String> cache=new LinkedHashMap<>(16,.75f,true);
 MlKitTranslator()throws Exception{MlKitModels.verifyDownloaded();}
 public String translate(String text,String context,TranslationCancellation cancel)throws Exception{
  return SmallModelText.translate(text,(part,ja)->{
   cancel.check();String key=ja+":"+part;String hit=cache.get(key);if(hit!=null)return hit;
   Translator engine=engines.computeIfAbsent(ja,j->Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(j?TranslateLanguage.JAPANESE:TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.CHINESE).build()));
   Task<String> task=engine.translate(part);long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
   // SDK tasks cannot be interrupted. Finish the one active sentence before reusing/closing its engine.
   while(!task.isComplete()){if(System.nanoTime()>deadline)throw new TimeoutException("Google 离线翻译超时");Thread.sleep(20);}
   cancel.check();if(!task.isSuccessful())throw new IllegalStateException("Google 离线翻译失败",task.getException());
   String value=task.getResult();cache.put(key,value);while(cache.size()>256)cache.remove(cache.keySet().iterator().next());return value;
  },cancel);
 }
 public void close(){for(Translator t:engines.values())t.close();engines.clear();cache.clear();}
}
