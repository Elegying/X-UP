package io.github.jared.xlowerseek;
import android.content.Context;import java.io.File;import java.util.concurrent.atomic.AtomicBoolean;
final class OpusFiles {
 static final String ID="opus-int8-en-ja-zh-v1";
 static final long BYTES=135806124L;
 static final String SHA256="7362cbd5bbfa3a38bed1a1b97c34412636cb2da4dd62d1e95335068443d4194a";
 static final String URL="https://github.com/Elegying/X-UP/releases/download/models-opus-v1/xup-opus-en-ja-zh-int8-v1.zip";
 static final ModelBundle.Entry[] ENTRIES={
  new ModelBundle.Entry("en-zh/config.json",223L,"8f6496adfc930cbfecbe8281112197705c488fab47d34b4829b06d7f478909af"),
  new ModelBundle.Entry("en-zh/model.bin",79567635L,"327584c20bb83c7e89d595bcfa30b6ef3771c10816f707e892c4bbb1f808a8fb"),
  new ModelBundle.Entry("en-zh/shared_vocabulary.json",1303887L,"37314a6abb25ed8f8497498aeeb31fcea98de892bf00ff7c2e8c966b26fe0b82"),
  new ModelBundle.Entry("en-zh/source.spm",806435L,"5775ddc9e3ff2fae91554da56468ad35ff56edaba870fea74447bc7234bfdaa8"),
  new ModelBundle.Entry("en-zh/target.spm",804600L,"81dc94efa84e4025ef38d25d5d07429fe41e3eb29d44003f1db6fe98487b0052"),
  new ModelBundle.Entry("ja-en/config.json",223L,"8f6496adfc930cbfecbe8281112197705c488fab47d34b4829b06d7f478909af"),
  new ModelBundle.Entry("ja-en/model.bin",77339435L,"94ea549b9aaca8b4899d8406f8323c1c558874534ba629659af9874c63bbdf63"),
  new ModelBundle.Entry("ja-en/shared_vocabulary.json",1208862L,"c6e7f9988e5fe15c59ed331ce2d7c3dd551e70add8efc09f839f5a48168da4d6"),
  new ModelBundle.Entry("ja-en/source.spm",781853L,"d0b5c3b10b5959f056ff2c86e2f2356129242ff1fc72d3a4a34d6a8c0eee4e57"),
  new ModelBundle.Entry("ja-en/target.spm",801883L,"35d89c704e270d441fade716a3931d49c43179400682edcca7f180694982a2f6")
 };
 static File directory(Context c){return new File(c.getFilesDir(),"translation-model/"+ID);}
 static File archive(Context c){return new File(c.getFilesDir(),"translation-model/"+ID+".zip");}
 static boolean ready(Context c){
  android.content.SharedPreferences p=c.getSharedPreferences(ID,0);if(!SHA256.equals(p.getString("bundle","")))return false;
  for(ModelBundle.Entry e:ENTRIES){File f=new File(directory(c),e.path);if(!f.isFile()||f.length()!=e.size||f.lastModified()!=p.getLong(e.path,-1))return false;}return true;
 }
 static void install(Context c,AtomicBoolean pause)throws Exception{
  ModelBundle.install(archive(c),directory(c),ENTRIES,pause);
  android.content.SharedPreferences.Editor p=c.getSharedPreferences(ID,0).edit().putString("bundle",SHA256);for(ModelBundle.Entry e:ENTRIES)p.putLong(e.path,new File(directory(c),e.path).lastModified());
  if(!p.commit())throw new java.io.IOException("无法保存模型状态，请重试");LocalModels.changed(c);archive(c).delete();
 }
}
