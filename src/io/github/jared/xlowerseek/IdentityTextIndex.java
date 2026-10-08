package io.github.jared.xlowerseek;
import java.util.LinkedHashMap;

/** Bounded exact identities, never substring matching against post bodies. */
final class IdentityTextIndex {
 private final LinkedHashMap<String,Boolean> names=new LinkedHashMap<>(64,.75f,true);
 synchronized void remember(String value){
  if(value==null||value.trim().isEmpty()||value.length()>256)return;
  names.put(value.trim(),Boolean.TRUE);while(names.size()>2048)names.remove(names.keySet().iterator().next());
 }
 synchronized boolean contains(String value){return value!=null&&names.get(value.trim())!=null;}
}
