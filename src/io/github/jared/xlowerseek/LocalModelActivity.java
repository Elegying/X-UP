package io.github.jared.xlowerseek;
import android.app.Activity;import android.content.Intent;import android.os.*;import android.widget.*;
public final class LocalModelActivity extends Activity {
 private final java.util.Map<Feature,Switch> switches=new java.util.EnumMap<>(Feature.class);
 private final java.util.Map<LocalEngine,TextView> engineStates=new java.util.EnumMap<>(LocalEngine.class);
 private ImageView googleBadge;private TextView status,tests,modelInfo;private ProgressBar progress;private Button download,pause,test;private RadioGroup engines;
 private final Handler main=new Handler(Looper.getMainLooper());private boolean binding,testing;private String testId;
 private final Runnable refresh=()->{refresh();main.postDelayed(this.refresh,500);};
 public void onCreate(Bundle state){
  super.onCreate(state);SettingsUi ui=new SettingsUi(this,"翻译方式","按手机性能选择，已下载模型会保留",true);
  LinearLayout modes=ui.section("功能");
  for(Feature f:new Feature[]{Feature.LOCAL_TEXT,Feature.NATIVE_TRANSLATE,Feature.LOCAL_CONTEXT}){
   Switch control=ui.toggle(modes,f.title,f.description);switches.put(f,control);control.setOnCheckedChangeListener((v,on)->{
    if(binding)return;if(!SettingsStore.setFeature(this,f,on)){bindModes();status.setText("保存失败，请重试。");return;}bindModes();
    if((f==Feature.NATIVE_TRANSLATE&&!on||f==Feature.LOCAL_TEXT&&on)&&!LocalModels.ready(this,SettingsStore.engine(this)))promptDownload();
   });
  }
  LinearLayout choose=ui.section("本地引擎");engines=new RadioGroup(this);choose.addView(engines);
  for(LocalEngine engine:LocalEngine.values()){
   RadioButton radio=new RadioButton(this);radio.setId(100+engine.ordinal());radio.setText(engine.title);radio.setTextColor(ui.ink);radio.setMinHeight(ui.dp(48));engines.addView(radio);
   engineStates.put(engine,radio);
  }
  engines.setOnCheckedChangeListener((group,id)->{if(binding||id<100||id>=100+LocalEngine.values().length)return;LocalEngine selected=LocalEngine.values()[id-100];if(!SettingsStore.setEngine(this,selected)){bindModes();return;}cancelTest();refresh();if(SettingsStore.feature(this,Feature.LOCAL_TEXT)&&!LocalModels.ready(this,selected))promptDownload();});
  LinearLayout model=ui.section("模型下载");modelInfo=ui.status(model,"");status=ui.status(model,"");progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);model.addView(progress);
  download=ui.button(model,"下载所选模型",true);pause=ui.button(model,"暂停下载",false);download.setOnClickListener(v->startDownload());
  pause.setOnClickListener(v->{startService(new Intent(this,ModelDownloadService.class).setAction("pause"));status.setText("正在暂停，已下载部分会保留。");});
  LinearLayout check=ui.section("测试");googleBadge=new ImageView(this);try(java.io.InputStream badge=getAssets().open("branding/google-translate.png")){googleBadge.setImageBitmap(android.graphics.BitmapFactory.decodeStream(badge));}catch(java.io.IOException ignored){}googleBadge.setAdjustViewBounds(true);googleBadge.setContentDescription("由 Google Translate 自动翻译");check.addView(googleBadge,new LinearLayout.LayoutParams(ui.dp(172),ui.dp(28)));tests=ui.status(check,"测试当前引擎的英语、日语译文与实际等待时间。");test=ui.button(check,"测试英语与日语",true);test.setOnClickListener(v->test());
  ui.button(check,"重试未完成翻译",false).setOnClickListener(v->tests.setText(SettingsStore.retryTranslations(this)?"已通知 X 重试。":"设置服务未连接，请先启用模块。"));
  ui.navigation(ui.section("说明"),"模型与升级说明","",()->ui.details("模型与升级说明","腾讯：支持上级回复和引用上下文，性能要求较高。\n\nGoogle ML Kit：语言包约每个 30 MB，实际由 Google 决定；日译中经英语中转。\n\nOPUS-MT：INT8 英日模型包约 136 MB，英语直接译中文，日语先译英语再译中文。适合日常短句，俚语、反讽和省略句可能失真。\n\n只有腾讯引擎使用帖子上下文。另两种引擎仅翻译英语、日语，逐句分流；中文夹字母及不支持的文字保留原文，表情原样保留。链接、账号与原排版受保护。\n\n模型均需在线下载，下载完成后正文在本机处理。Google 模型下载需要能连接 Google 下载服务，SDK 不提供精确进度与暂停。腾讯和 OPUS 支持暂停续传。\n\nX 在前台时异步预热所选引擎；进入后台停止翻译，模型保留 2 分钟后释放。两分钟内返回直接复用，下载文件不会删除。\n\n正常覆盖安装保留已下载模型；切换引擎不会删除其他模型。卸载或清除应用数据会移除模型。\n\n三个本地引擎与 X 原生联网翻译互斥，不会自动联网发送正文。"));
  ui.navigation(ui.body,"Google Translate 使用说明","",()->ui.details("Google Translate","选择 Google ML Kit 时，离线译文由 Google Translate 提供。自动翻译可能存在错误，Google 不对译文的准确性、可靠性或适用性作保证。\n\nhttps://cloud.google.com/translate\nhttps://developers.google.com/ml-kit/language/translation"));
  ui.navigation(ui.body,"第三方许可","",()->{try(java.io.InputStream in=getAssets().open("licenses/NOTICE.txt")){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);ui.details("第三方许可",out.toString("UTF-8"));}catch(java.io.IOException e){ui.details("第三方许可","请查看项目 licenses 目录。");}});
  bindModes();
 }
 protected void onResume(){super.onResume();MlKitModels.refresh(this);main.post(refresh);}protected void onPause(){main.removeCallbacks(refresh);super.onPause();}
 protected void onDestroy(){cancelTest();super.onDestroy();}
 private void promptDownload(){new android.app.AlertDialog.Builder(this).setTitle("下载所选模型").setMessage("当前选择 "+SettingsStore.engine(this).title+"。下载完成前保留原文；已经下载的其他模型会继续保留。").setPositiveButton("下载",(d,w)->startDownload()).setNegativeButton("稍后",null).show();}
 private void startDownload(){
  LocalEngine engine=SettingsStore.engine(this);if(LocalModels.ready(this,engine)||ModelDownloadService.running)return;
  android.net.ConnectivityManager network=getSystemService(android.net.ConnectivityManager.class);android.net.NetworkCapabilities caps=network==null?null:network.getNetworkCapabilities(network.getActiveNetwork());
  boolean metered=network!=null&&(network.isActiveNetworkMetered()||caps!=null&&caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR));
  if(metered){String size=engine==LocalEngine.MLKIT?"中文、日语语言包每个约 30 MB，以 Google 实际下载为准":engine==LocalEngine.OPUS?"完整模型包约 136 MB":"完整模型约 462 MB";
   new android.app.AlertDialog.Builder(this).setTitle("使用流量下载？").setMessage("当前为移动数据或计费网络。"+size+"。已有有效进度会复用；校验失败或服务器不支持续传时可能重新下载。").setPositiveButton("继续下载",(d,w)->beginDownload(engine)).setNegativeButton("取消",null).show();return;}
  beginDownload(engine);
 }
 private void beginDownload(LocalEngine engine){try{startForegroundService(new Intent(this,ModelDownloadService.class).putExtra("engine",engine.id));refresh();}catch(RuntimeException e){status.setText("无法启动下载，请重新打开页面重试。");}}
 private void bindModes(){binding=true;LocalEngine engine=SettingsStore.engine(this);for(java.util.Map.Entry<Feature,Switch> e:switches.entrySet())e.getValue().setChecked(SettingsStore.feature(this,e.getKey()));switches.get(Feature.LOCAL_CONTEXT).setEnabled(SettingsStore.feature(this,Feature.LOCAL_TEXT)&&engine==LocalEngine.TENCENT);engines.check(100+engine.ordinal());binding=false;}
 private void refresh(){
  bindModes();LocalEngine engine=SettingsStore.engine(this);boolean ready=LocalModels.ready(this,engine),running=ModelDownloadService.running,same=running&&ModelDownloadService.downloadingEngine==engine;
  for(LocalEngine e:LocalEngine.values())engineStates.get(e).setText(e.title+" · "+(LocalModels.ready(this,e)?"已下载":"未就绪")+"\n"+e.description);
  googleBadge.setVisibility(engine==LocalEngine.MLKIT?android.view.View.VISIBLE:android.view.View.GONE);test.setText(engine==LocalEngine.MLKIT?"使用 Google Translate 测试英语与日语":"测试英语与日语");
  modelInfo.setText(engine.description+(engine==LocalEngine.TENCENT?"\n可开启帖子上下文":"\n当前引擎按句翻译，不使用上下文提示词"));
  progress.setIndeterminate(same&&(ModelDownloadService.verifying||engine==LocalEngine.MLKIT));progress.setProgress(ready?100:same&&ModelDownloadService.total>0?(int)Math.min(100,ModelDownloadService.received*100/ModelDownloadService.total):0);
  status.setText(same?engine==LocalEngine.MLKIT?MlKitModels.status:ModelDownloadService.status:ready?"模型已就绪 · 可离线使用":running?"正在下载 "+ModelDownloadService.downloadingEngine.title:engine==LocalEngine.MLKIT?MlKitModels.status:ModelDownloadService.downloadingEngine==engine&&!ModelDownloadService.status.isEmpty()?ModelDownloadService.status:"尚未下载，完成前保留原文");
  download.setEnabled(!running&&!ready);download.setText(ready?"模型已下载，无需重复下载":"下载所选模型");pause.setEnabled(same&&engine!=LocalEngine.MLKIT);test.setEnabled(ready&&!testing&&SettingsStore.translate(this)&&SettingsStore.feature(this,Feature.LOCAL_TEXT));
 }
 private void cancelTest(){if(testId!=null){LocalTranslationService.cancel(android.os.Process.myUid(),testId);testId=null;}testing=false;}
 private void test(){testing=true;test.setEnabled(false);tests.setText("正在测试，首次加载可能较慢…");sample(0,new StringBuilder(),SettingsStore.engine(this));}
 private void sample(int index,StringBuilder results,LocalEngine engine){
  if(index==2||isDestroyed()||engine!=SettingsStore.engine(this)){testing=false;testId=null;refresh();return;}
  String[] texts={"The launch made the impossible look routine.","今日は雨なので、傘を忘れないでください。"};String id=java.util.UUID.randomUUID().toString();testId=id;long started=SystemClock.elapsedRealtime();
  LocalTranslationService.request(getApplicationContext(),android.os.Process.myUid(),id,engine.id,LocalModels.revision(this),texts[index],"",new ResultReceiver(main){protected void onReceiveResult(int code,Bundle data){
   if(isDestroyed()||!id.equals(testId))return;results.append(index==0?"英语：":"日语：").append(code==0&&data!=null?data.getString("text"):"未完成，请检查模型与翻译开关").append("\n等待 ").append(SystemClock.elapsedRealtime()-started).append(" 毫秒\n\n");tests.setText(results.toString());sample(index+1,results,engine);
  }});
 }
}
