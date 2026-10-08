package io.github.jared.xlowerseek;
import android.content.Context;
/** Upgrade removes retired API credentials; never decrypts or logs them. */
final class LegacyTranslationCleanup {
 static void run(Context c){
  c.deleteSharedPreferences("llm_private");
  c.getSharedPreferences("settings_local",0).edit().remove("fast_translation").remove("review_translation").commit();
  try{java.security.KeyStore store=java.security.KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias("xup.llm.api-key"))store.deleteEntry("xup.llm.api-key");}catch(Exception ignored){}
 }
}
