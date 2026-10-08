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
   // Await completion directly; no periodic polling delay for each sentence.
   // SDK tasks cannot be interrupted: cancellation is checked before publishing/continuing.
   String value=Tasks.await(engine.translate(part),15,TimeUnit.SECONDS);
   cancel.check();if(!TranslationOutput.clean(part,value))throw new IllegalStateException("译文含未知字符，保留原文");cache.put(key,value);while(cache.size()>256)cache.remove(cache.keySet().iterator().next());return value;
  },cancel);
 }
 public void close(){for(Translator t:engines.values())t.close();engines.clear();cache.clear();}
}
