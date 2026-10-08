package io.github.jared.xlowerseek;
import android.app.Activity;import android.content.Intent;import android.os.*;import android.widget.*;
public final class LocalModelActivity extends Activity {
 private final java.util.Map<Feature,Switch> switches=new java.util.EnumMap<>(Feature.class);
 private TextView status,tests;private ProgressBar progress;private Button download,pause,test;private final Handler main=new Handler(Looper.getMainLooper());private boolean binding,testing;
 private final Runnable refresh=()->{refresh();main.postDelayed(this.refresh,500);};
 public void onCreate(Bundle state){
  super.onCreate(state);SettingsUi ui=new SettingsUi(this,"翻译方式","腾讯本地翻译为主，X 原生翻译备用",true);
  LinearLayout modes=ui.section("功能");
  for(Feature f:new Feature[]{Feature.LOCAL_TEXT,Feature.NATIVE_TRANSLATE,Feature.LOCAL_CONTEXT}){
   Switch control=ui.toggle(modes,f.title,f.description);switches.put(f,control);control.setChecked(SettingsStore.feature(this,f));control.setOnCheckedChangeListener((v,on)->{
    if(binding)return;
    if(!SettingsStore.setFeature(this,f,on)){bindModes();status.setText("保存失败，请重试。");return;}
    bindModes();
    if((f==Feature.NATIVE_TRANSLATE&&!on||f==Feature.LOCAL_TEXT&&on)&&!ModelFiles.ready(this)){
     new android.app.AlertDialog.Builder(this).setTitle("下载本地翻译模型").setMessage("已切换到腾讯本地翻译。首次使用需要下载约 462 MB 模型；完成前保留原文。英语、日语共用一份模型。").setPositiveButton("下载模型",(dialog,which)->startDownload()).setNegativeButton("稍后",null).show();
    }
   });
  }
  LinearLayout model=ui.section("翻译模型");ui.info(model,LocalModelSpec.NAME,"462 MB · 下载后无需联网 · 权重不包含在安装包内");
  status=ui.status(model,"");progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);model.addView(progress);
  download=ui.button(model,"下载模型 · 462 MB",true);pause=ui.button(model,"暂停下载",false);
  download.setOnClickListener(v->startDownload());
  pause.setOnClickListener(v->{startService(new Intent(this,ModelDownloadService.class).setAction("pause"));status.setText("正在暂停，已下载部分会保留。");});
  LinearLayout check=ui.section("检查");tests=ui.status(check,"模型会参考同一帖子已加载的上级回复与引用，整段翻译。");test=ui.button(check,"测试英语与日语",true);test.setOnClickListener(v->test());
  ui.button(check,"重试未完成翻译",false).setOnClickListener(v->tests.setText(SettingsStore.retryTranslations(this)?"已通知 X 重试未完成内容。":"设置服务未连接，请先启用模块。"));
  ui.navigation(ui.section("说明"),"模型来源与使用说明","",()->ui.details("本地翻译说明","模型由腾讯发布，支持英语、日语等语言翻译为中文，以及上下文翻译。\n\n下载需联网，可暂停并续传；只有大小与 SHA-256 校验通过才启用。模型文件约 462 MB，小于 500 MB；实际运行内存会大于文件体积。\n\n默认使用腾讯本地翻译，帖子和私信文字在手机上处理。手动切换到 X 原生备用模式后，将使用 X 的在线翻译服务。速度取决于手机和文本长度，不保证所有内容在 2 秒内完成。输入框、图片不处理，特殊自绘内容可能保留原文。\n\n模型遵循 Apache-2.0 许可证；推理内核 llama.cpp 为 MIT 许可。"));
  ui.navigation(ui.body,"第三方许可","",()->{try{java.io.InputStream in=getAssets().open("licenses/NOTICE.txt");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);in.close();ui.details("第三方许可",out.toString("UTF-8"));}catch(java.io.IOException e){ui.details("第三方许可","请查看项目 licenses 目录。");}});
  ui.navigation(ui.body,"模型官方页面","",()->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(LocalModelSpec.PAGE))));
 }
 protected void onResume(){super.onResume();main.post(refresh);}protected void onPause(){main.removeCallbacks(refresh);super.onPause();}
 private void startDownload(){
  long remaining=Math.max(0,LocalModelSpec.BYTES-ModelFiles.partial(this));
  android.net.ConnectivityManager network=getSystemService(android.net.ConnectivityManager.class);
  android.net.NetworkCapabilities caps=network==null?null:network.getNetworkCapabilities(network.getActiveNetwork());
  boolean cellular=caps!=null&&caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR);
  if(remaining>0&&network!=null&&(cellular||network.isActiveNetworkMetered())){
   new android.app.AlertDialog.Builder(this).setTitle("使用流量下载？").setMessage(String.format(java.util.Locale.ROOT,"当前为移动数据或计费网络，模型还需下载约 %.1f MB。可以继续下载，也可以稍后连接 Wi-Fi。",remaining/1000000.0)).setPositiveButton("继续下载",(dialog,which)->beginDownload()).setNegativeButton("取消",null).show();
   return;
  }
  beginDownload();
 }
 private void beginDownload(){try{startForegroundService(new Intent(this,ModelDownloadService.class));refresh();}catch(RuntimeException e){status.setText("无法启动下载，请重新打开页面重试。");}}
 private void bindModes(){binding=true;for(java.util.Map.Entry<Feature,Switch> e:switches.entrySet())e.getValue().setChecked(SettingsStore.feature(this,e.getKey()));Switch context=switches.get(Feature.LOCAL_CONTEXT);context.setEnabled(SettingsStore.feature(this,Feature.LOCAL_TEXT));binding=false;}
 private void refresh(){
  bindModes();
  boolean running=ModelDownloadService.running,ready=ModelFiles.ready(this);long bytes=running?ModelDownloadService.received:ready?LocalModelSpec.BYTES:ModelFiles.partial(this);
  progress.setIndeterminate(running&&ModelDownloadService.verifying);progress.setProgress((int)(bytes*100/LocalModelSpec.BYTES));
  status.setText(running?ModelDownloadService.status:ready?"模型文件已就绪 · 可测试本地翻译":ModelDownloadService.status.isEmpty()?"本地模型尚未下载 · 下载完成前保留原文":ModelDownloadService.status);
  download.setEnabled(!running&&!ready);download.setText(ready?"模型已下载":bytes>0?"继续下载模型":"下载模型 · 462 MB");pause.setEnabled(running);test.setEnabled(ready&&!testing&&SettingsStore.feature(this,Feature.LOCAL_TEXT));
 }
 private void test(){
  testing=true;test.setEnabled(false);tests.setText("正在本地测试，首次加载模型可能较慢…");
  android.content.Context app=getApplicationContext();java.lang.ref.WeakReference<LocalModelActivity> screen=new java.lang.ref.WeakReference<>(this);StringBuilder results=new StringBuilder();
  String[] samples={"The launch made the impossible look routine.","今日は雨なので、傘を忘れないでください。"};int[] remaining={2};
  for(int i=0;i<samples.length;i++){
   final String label=i==0?"英语":"日语";long start=SystemClock.elapsedRealtime();
   LocalTranslationService.request(app,samples[i],"",new ResultReceiver(main){protected void onReceiveResult(int code,Bundle data){
    results.append(label).append("：").append(code==0?data.getString("text"):"未完成，请检查本地翻译开关与模型状态").append("\n耗时 ").append(SystemClock.elapsedRealtime()-start).append(" 毫秒\n\n");
    LocalModelActivity a=screen.get();if(a==null||a.isDestroyed())return;a.tests.setText(results.toString());if(--remaining[0]==0){a.testing=false;a.refresh();}
   }});
  }
 }
}
