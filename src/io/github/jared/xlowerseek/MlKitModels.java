package io.github.jared.xlowerseek;
import android.content.Context;
import com.google.mlkit.common.MlKitException;
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
 private static final List<Runnable> downloadObservers=new ArrayList<>();
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
  if(!downloadObservers.contains(finished))downloadObservers.add(finished);
  if(downloading)return;++inventoryVersion;downloading=true;status="正在从 Google 下载语言包…\n若系统提示流量保护，请在系统窗口允许下载；选择等待 WLAN 会由系统暂停。";
  Context app=context.getApplicationContext();
  List<Task<Void>> tasks=new ArrayList<>();DownloadConditions conditions=new DownloadConditions.Builder().build();
  for(String language:LANGUAGES)tasks.add(RemoteModelManager.getInstance().download(model(language),conditions));
  Tasks.whenAll(tasks).addOnCompleteListener(task->{
   downloading=false;checked=true;ready=task.isSuccessful();status="语言包已就绪 · Google 离线翻译";
   if(!ready){StringBuilder errors=new StringBuilder("Google 语言包下载失败：");for(int i=0;i<tasks.size();i++)if(!tasks.get(i).isSuccessful()){
    if(errors.charAt(errors.length()-1)!='：')errors.append("；");errors.append(i==0?"中文":"日语").append(" · ").append(downloadError(tasks.get(i).getException()));
   }status=errors.append("。已下载的语言包会保留。").toString();}
   if(ready)LocalModels.changed(app);
   List<Runnable> observers=new ArrayList<>(downloadObservers);downloadObservers.clear();
   for(Runnable observer:observers)observer.run();
  });
 }
 static void stopObservingDownload(Runnable observer){downloadObservers.remove(observer);}
 private static String downloadError(Exception failure){
  Throwable cause=failure;
  for(int depth=0;cause!=null&&depth<8;depth++,cause=cause.getCause())if(cause instanceof MlKitException){
   int code=((MlKitException)cause).getErrorCode();String reason;
   switch(code){
    case MlKitException.NOT_ENOUGH_SPACE:reason="存储空间不足，请释放空间后重试";break;
    case MlKitException.MODEL_HASH_MISMATCH:reason="模型完整性校验失败，请重新下载";break;
    case MlKitException.MODEL_INCOMPATIBLE_WITH_TFLITE:case MlKitException.UNSUPPORTED:reason="当前设备或运行库不支持此模型，请选择其他引擎";break;
    case MlKitException.PERMISSION_DENIED:case MlKitException.FAILED_PRECONDITION:reason="系统未允许下载，请检查下载管理器和应用的后台限制";break;
    case MlKitException.UNAVAILABLE:reason="Google 模型服务暂不可用，请稍后重试";break;
    case MlKitException.NETWORK_ISSUE:reason="连接 Google 模型服务失败，请稍后重试";break;
    default:reason="Google 下载任务失败，请重试";
   }return reason+"（ML Kit "+code+"）";
  }
  return "下载任务未完成，请重试";
 }
 static void verifyDownloaded()throws Exception{
  for(String language:LANGUAGES)if(!Tasks.await(RemoteModelManager.getInstance().isModelDownloaded(model(language)),10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("请先下载 Google 语言包");
 }
}
