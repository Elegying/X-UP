package io.github.jared.xlowerseek;
import android.content.Context;
final class LocalModels {
 static boolean ready(Context c,LocalEngine engine){switch(engine){case MLKIT:return MlKitModels.ready;case OPUS:return OpusFiles.ready(c);default:return ModelFiles.ready(c);}}
 static long revision(Context c){return c.getSharedPreferences("model_catalog",0).getLong("revision",0)+ModelFiles.revision(c);}
 static synchronized void changed(Context c){android.content.SharedPreferences p=c.getSharedPreferences("model_catalog",0);p.edit().putLong("revision",p.getLong("revision",0)+1).commit();SettingsStore.publishModelState(c);}
 static OfflineEngine open(Context c,LocalEngine engine)throws Exception{switch(engine){case MLKIT:return new MlKitTranslator();case OPUS:return new OpusTranslator(OpusFiles.directory(c).getAbsolutePath());default:return new TencentTranslator(ModelFiles.model(c).getAbsolutePath());}}
}
