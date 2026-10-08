package io.github.jared.xlowerseek;
import android.content.Context;import java.io.File;
final class ModelFiles {
 static File model(Context c){return new File(new File(c.getFilesDir(),"translation-model"),"hy-mt2-1.25bit.gguf");}
 static boolean ready(Context c){File f=model(c);return f.isFile()&&f.length()==LocalModelSpec.BYTES&&c.getSharedPreferences("model",0).getLong("verified_mtime",-1)==f.lastModified()&&LocalModelSpec.SHA256.equals(c.getSharedPreferences("model",0).getString("sha256",""));}
 static long revision(Context c){return c.getSharedPreferences("model",0).getLong("revision",0);}
 static void activate(Context c)throws java.io.IOException{
  if(!c.getSharedPreferences("model",0).edit().putString("sha256",LocalModelSpec.SHA256).putLong("verified_mtime",model(c).lastModified()).putLong("revision",revision(c)+1).commit())throw new java.io.IOException("无法保存模型状态，请重试");
  SettingsStore.publishModelState(c);
 }
 static long partial(Context c){return new File(model(c).getPath()+".part").length();}
}
