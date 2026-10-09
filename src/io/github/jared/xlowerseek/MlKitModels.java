package io.github.jared.xlowerseek;
import android.content.Context;
import com.google.mlkit.common.model.*;
import com.google.mlkit.nl.translate.*;
import com.google.android.gms.tasks.*;
import java.util.*;
/** Google's SDK owns model files; never delete them during app upgrades or engine changes. */
final class MlKitModels {
 static volatile boolean checked,ready,downloading;static volatile String status="正在检查语言包…";
 // Entry points and default Task listeners run on the main thread. Separate SDK tasks
 // may finish out of order, so only the current inventory request can publish state.
 private static long inventoryVersion;
 static final String[] LANGUAGES={TranslateLanguage.CHINESE,TranslateLanguage.JAPANESE};
 static TranslateRemoteModel model(String language){return new TranslateRemoteModel.Builder(language).build();}
 static void refresh(Context context){
  if(downloading)return;
  long version=++inventoryVersion;
  RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel.class).addOnSuccessListener(models->{
   if(version!=inventoryVersion)return;
   boolean all=true;for(String language:LANGUAGES){boolean found=false;for(TranslateRemoteModel m:models)if(language.equals(m.getLanguage()))found=true;all&=found;}
   boolean changed=ready!=all;ready=all;checked=true;if(!downloading)status=all?"语言包已就绪 · Google 离线翻译":"需要下载中文、日语语言包";
   if(changed)LocalModels.changed(context);
  }).addOnFailureListener(e->{if(version!=inventoryVersion)return;checked=true;status="无法检查 Google 语言包，请检查 Google 服务或稍后重试";});
 }
 static void download(Context context,Runnable finished){
  if(downloading)return;++inventoryVersion;downloading=true;status="正在从 Google 下载语言包…";
  List<Task<Void>> tasks=new ArrayList<>();DownloadConditions conditions=new DownloadConditions.Builder().build();
  for(String language:LANGUAGES)tasks.add(RemoteModelManager.getInstance().download(model(language),conditions));
  Tasks.whenAll(tasks).addOnCompleteListener(task->{downloading=false;checked=true;ready=task.isSuccessful();status=ready?"语言包已就绪 · Google 离线翻译":"Google 语言包下载失败，请检查网络后重试";if(ready)LocalModels.changed(context);finished.run();});
 }
 static void verifyDownloaded()throws Exception{
  for(String language:LANGUAGES)if(!Tasks.await(RemoteModelManager.getInstance().isModelDownloaded(model(language)),10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("请先下载 Google 语言包");
 }
}
