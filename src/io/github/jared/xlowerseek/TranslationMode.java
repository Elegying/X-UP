package io.github.jared.xlowerseek;
/** One stored mode makes native/local mutual exclusion atomic across processes. */
final class TranslationMode {
 static final String KEY="translation_mode";
 static final int OFF=0,NATIVE=1,LOCAL=2;
 static int normalize(int mode){return mode==OFF||mode==NATIVE?mode:LOCAL;}
 static boolean enabled(int mode,Feature feature){return feature==Feature.NATIVE_TRANSLATE?normalize(mode)==NATIVE:normalize(mode)==LOCAL;}
 static int switchTo(int current,Feature feature,boolean enabled){
  if(feature==Feature.NATIVE_TRANSLATE)return enabled?NATIVE:LOCAL;
  if(feature==Feature.LOCAL_TEXT)return enabled?LOCAL:NATIVE;
  return normalize(current);
 }
}
