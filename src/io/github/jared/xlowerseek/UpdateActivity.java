package io.github.jared.xlowerseek;

import android.app.*;import android.content.*;import android.net.*;import android.os.*;import android.provider.Settings;import android.widget.*;
import java.util.concurrent.*;

public final class UpdateActivity extends Activity {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor();
 private final Handler main=new Handler(Looper.getMainLooper());
 private TextView state,notes;private Button check,action,cancel;private Switch beta;private ProgressBar progress;
 private UpdateRelease candidate;private boolean checking,verifying,resumed,permissionPending,verificationFailed;
 private final Runnable tick=new Runnable(){public void run(){if(resumed){refresh();main.postDelayed(this,600);}}};
 public void onCreate(Bundle saved){
  super.onCreate(saved);permissionPending=saved!=null&&saved.getBoolean("permissionPending");
  SettingsUi ui=new SettingsUi(this,"检查更新","当前版本 "+BuildConfig.VERSION_NAME,true);
  LinearLayout channel=ui.section("更新渠道");beta=ui.toggle(channel,"接收测试版","关闭后只检查正式版。");beta.setChecked(UpdateDownload.prefs(this).getBoolean("beta",BuildConfig.VERSION_NAME.contains("-")));
  beta.setOnCheckedChangeListener((v,on)->{UpdateDownload.prefs(this).edit().putBoolean("beta",on).apply();candidate=null;check();});
  LinearLayout card=ui.section("版本更新");state=ui.status(card,"从 GitHub 检查新版本。");progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);card.addView(progress);
  check=ui.button(card,"检查更新",false);check.setOnClickListener(v->check());action=ui.button(card,"下载更新",true);action.setEnabled(false);action.setOnClickListener(v->act());
  cancel=ui.button(card,"取消下载",false);cancel.setEnabled(false);cancel.setOnClickListener(v->{UpdateDownload.cancel(this);verificationFailed=false;state.setText("已取消，可重新下载。");refresh();});
  notes=ui.status(ui.section("更新说明"),"检查后显示更新内容。");notes.setTextIsSelectable(true);
  ui.navigation(ui.body,"打开 GitHub 发布页","网络受限时可在浏览器下载。",()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(UpdateRelease.REPO+"/releases")));}catch(RuntimeException e){state.setText("无法打开浏览器，请检查默认浏览器设置。");}});
  ui.status(ui.body,"下载可在后台继续。安装前会校验完整性、包名和签名。覆盖安装保留模型与设置；更新后请重新启动 X。");
  UpdateRelease pending=UpdateDownload.pending(this);if(pending!=null&&UpdateRelease.compare(pending.version,BuildConfig.VERSION_NAME)<=0){UpdateDownload.cancel(this);state.setText("已更新至 "+BuildConfig.VERSION_NAME+"，模型与设置已保留。");}
  if(UpdateDownload.id(this)==-1)check();
 }
 protected void onResume(){super.onResume();resumed=true;main.post(tick);if(permissionPending){permissionPending=false;if(getPackageManager().canRequestPackageInstalls())prepare(true);else state.setText("尚未允许安装，点击安装更新可重试。");}}
 protected void onPause(){resumed=false;main.removeCallbacks(tick);super.onPause();}
 protected void onSaveInstanceState(Bundle out){out.putBoolean("permissionPending",permissionPending);super.onSaveInstanceState(out);}
 private void check(){
  if(checking||verifying||UpdateDownload.id(this)!=-1)return;
  checking=true;candidate=null;action.setEnabled(false);check.setEnabled(false);beta.setEnabled(false);progress.setVisibility(android.view.View.VISIBLE);progress.setIndeterminate(true);state.setText("正在检查更新…");boolean channel=beta.isChecked();
  IO.execute(()->{try{UpdateRelease result=UpdateClient.check(BuildConfig.VERSION_NAME,channel);main.post(()->{if(isDestroyed())return;checking=false;candidate=result;state.setText(result==null?"当前已是所选渠道的最新版本。":"发现新版本 "+result.version+" · "+mb(result.size));notes.setText(result==null?"暂无可用更新。":result.notes);progress.setIndeterminate(false);refresh();});}
   catch(Exception e){main.post(()->{if(isDestroyed())return;checking=false;progress.setIndeterminate(false);state.setText("检查失败："+message(e)+"。可点击检查更新重试。");refresh();});}});
 }
 private void refresh(){
  if(checking||verifying)return;
  long id=UpdateDownload.id(this);UpdateRelease pending=UpdateDownload.pending(this);boolean has=id!=-1&&pending!=null;
  action.setVisibility(has||candidate!=null?android.view.View.VISIBLE:android.view.View.GONE);cancel.setVisibility(has?android.view.View.VISIBLE:android.view.View.GONE);progress.setVisibility(has?android.view.View.VISIBLE:android.view.View.GONE);
  check.setEnabled(!has);beta.setEnabled(!has);cancel.setEnabled(has);action.setEnabled(candidate!=null);action.setAlpha(1f);action.setText("下载更新");
  if(!has){progress.setIndeterminate(false);progress.setProgress(0);return;}candidate=pending;notes.setText(pending.notes);UpdateDownload.State s;
  try{s=UpdateDownload.state(this);}catch(RuntimeException e){state.setText("无法读取系统下载状态，可取消后重试。");action.setEnabled(false);return;}
  progress.setIndeterminate(s.total<=0&&s.status!=DownloadManager.STATUS_SUCCESSFUL&&s.status!=DownloadManager.STATUS_FAILED);
  if(s.total>0)progress.setProgress((int)Math.min(100,s.bytes*100/s.total));
  if(s.status==DownloadManager.STATUS_SUCCESSFUL){
   progress.setIndeterminate(false);progress.setProgress(100);action.setText(verificationFailed?"重新下载":"安装更新");action.setEnabled(true);
   if(verificationFailed)return;
   if(UpdateDownload.prefs(this).getLong("verified_id",-2)!=id||!UpdateDownload.ready(this).exists()){prepare(false);return;}
   state.setText("版本 "+pending.version+" 已校验，可安装。安装取消后仍可重试。");
  }else if(s.status==DownloadManager.STATUS_FAILED||s.status==0){state.setText("下载失败或文件已移除，请重新下载。");action.setText("重新下载");action.setEnabled(true);}
  else{action.setEnabled(false);action.setAlpha(.45f);action.setText("下载中…");state.setText((s.status==DownloadManager.STATUS_PAUSED?"等待网络，系统将自动继续":"正在下载 "+pending.version)+" · "+mb(s.bytes)+(s.total>0?" / "+mb(s.total):""));}
 }
 private void act(){
  if(candidate==null||checking||verifying)return;
  if(UpdateDownload.id(this)!=-1&&!verificationFailed&&UpdateDownload.state(this).status==DownloadManager.STATUS_SUCCESSFUL){prepare(true);return;}
  ConnectivityManager cm=getSystemService(ConnectivityManager.class);
  if(cm!=null&&cm.isActiveNetworkMetered())new AlertDialog.Builder(this).setTitle("使用流量下载？").setMessage("安装包约 "+mb(candidate.size)+"，将使用当前计费网络。").setPositiveButton("继续下载",(d,w)->download()).setNegativeButton("取消",null).show();else download();
 }
 private void download(){try{UpdateDownload.start(this,candidate);verificationFailed=false;progress.setProgress(0);refresh();}catch(Exception e){state.setText("无法开始下载："+message(e));}}
 private void prepare(boolean install){
  if(verifying)return;verifying=true;check.setEnabled(false);beta.setEnabled(false);action.setEnabled(false);cancel.setEnabled(false);state.setText("正在校验安装包…");progress.setIndeterminate(true);long id=UpdateDownload.id(this);Context app=getApplicationContext();
  IO.execute(()->{try{UpdateDownload.verify(app,id);main.post(()->{if(isDestroyed())return;verifying=false;progress.setIndeterminate(false);refresh();if(install&&resumed)install();});}
   catch(Exception e){main.post(()->{if(isDestroyed())return;verifying=false;verificationFailed=true;progress.setIndeterminate(false);refresh();state.setText("校验失败："+message(e));});}});
 }
 private void install(){
  try{
   if(!getPackageManager().canRequestPackageInstalls()){permissionPending=true;state.setText("请允许 X-UP 安装更新，然后返回此页。");startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));return;}
   Uri uri=UpdateDownload.uri(this);Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("X-UP 更新",uri));startActivity(i);
  }catch(RuntimeException e){permissionPending=false;state.setText("无法打开系统安装界面，请检查安装权限后重试。");}
 }
 private static String mb(long bytes){return String.format(java.util.Locale.CHINA,"%.1f MB",Math.max(0,bytes)/1000000d);}
 private static String message(Exception e){if(e instanceof java.net.SocketTimeoutException||e instanceof java.net.UnknownHostException||e instanceof java.net.ConnectException)return "无法连接 GitHub，请检查网络后重试";String m=e.getMessage();return m==null||m.isEmpty()?"请检查网络后重试":m.length()>160?m.substring(0,160):m;}
}
