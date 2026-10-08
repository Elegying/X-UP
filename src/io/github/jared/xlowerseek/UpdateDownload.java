package io.github.jared.xlowerseek;
import android.app.DownloadManager;import android.content.*;import android.content.pm.*;import android.database.Cursor;import android.net.Uri;
import java.io.*;import java.nio.file.*;import java.security.*;import java.util.*;

/** System-managed transfer, app-private verified APK, and no silent installation. */
final class UpdateDownload {
 static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("updates",0);}
 static File ready(Context c){return new File(c.getCacheDir(),"verified-update.apk");}
 static File file(Context c){return new File(c.getExternalFilesDir("updates"),"update.apk");}
 static long id(Context c){return prefs(c).getLong("download_id",-1);}
 static UpdateRelease pending(Context c){try{return UpdateRelease.decode(prefs(c).getString("release",""));}catch(Exception e){return null;}}
 static synchronized void start(Context c,UpdateRelease r)throws Exception{
  cancel(c);if(c.getExternalFilesDir("updates")==null)throw new IOException("下载存储不可用");
  DownloadManager dm=c.getSystemService(DownloadManager.class);if(dm==null)throw new IOException("系统下载服务不可用");
  DownloadManager.Request request=new DownloadManager.Request(Uri.parse(r.url)).setTitle("X-UP "+r.version).setDescription("更新下载完成后，回到 X-UP 校验并安装")
    .setMimeType("application/vnd.android.package-archive").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
    .setAllowedOverMetered(true).setAllowedOverRoaming(true).setDestinationUri(Uri.fromFile(file(c)));
  long id=dm.enqueue(request);
  if(!prefs(c).edit().putLong("download_id",id).putString("release",r.encode()).commit()){dm.remove(id);throw new IOException("保存下载状态失败，请重试");}
 }
 static synchronized void cancel(Context c){long id=id(c);if(id!=-1){DownloadManager dm=c.getSystemService(DownloadManager.class);if(dm!=null)dm.remove(id);}prefs(c).edit().remove("download_id").remove("release").remove("verified_id").commit();ready(c).delete();File root=c.getExternalFilesDir("updates");if(root!=null)new File(root,"update.apk").delete();}
 static final class State {int status;long bytes,total;}
 static State state(Context c){
  State s=new State();DownloadManager dm=c.getSystemService(DownloadManager.class);if(dm==null||id(c)==-1)return s;
  try(Cursor cursor=dm.query(new DownloadManager.Query().setFilterById(id(c)))){if(cursor!=null&&cursor.moveToFirst()){s.status=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));s.bytes=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));s.total=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));}}return s;
 }
 static synchronized void verify(Context c,long expected)throws Exception{
  UpdateRelease r=pending(c);if(expected!=id(c)||r==null)throw new IOException("下载已变更，请重试");
  if(state(c).status!=DownloadManager.STATUS_SUCCESSFUL)throw new IOException("下载尚未完成");
  File incoming=file(c),copy=new File(c.getCacheDir(),"verifying-update.apk");
  try{
   if(incoming.length()!=r.size)throw new IOException("安装包大小不匹配，请重新下载");
   Files.copy(incoming.toPath(),copy.toPath(),StandardCopyOption.REPLACE_EXISTING);
   MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(copy)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}
   StringBuilder hex=new StringBuilder();for(byte b:md.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(!r.sha.contentEquals(hex))throw new IOException("安装包完整性校验失败，请重新下载");
   PackageManager pm=c.getPackageManager();PackageInfo apk=pm.getPackageArchiveInfo(copy.getPath(),PackageManager.GET_SIGNING_CERTIFICATES),installed=pm.getPackageInfo(c.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
   if(apk==null||!c.getPackageName().equals(apk.packageName)||!r.version.equals(apk.versionName)||apk.getLongVersionCode()<=installed.getLongVersionCode()||UpdateRelease.compare(apk.versionName,installed.versionName)<=0)throw new IOException("安装包版本或包名不匹配");
   if(!signers(apk).equals(signers(installed))||signers(apk).isEmpty())throw new IOException("安装包签名不一致，已阻止安装");
   Files.move(copy.toPath(),ready(c).toPath(),StandardCopyOption.REPLACE_EXISTING);
   if(!prefs(c).edit().putLong("verified_id",expected).commit()){ready(c).delete();throw new IOException("无法保存校验结果");}
  }finally{copy.delete();}
 }
 private static Set<String> signers(PackageInfo p){Set<String> out=new HashSet<>();if(p.signingInfo!=null)for(android.content.pm.Signature s:p.signingInfo.getApkContentsSigners())out.add(s.toCharsString());return out;}
 static Uri uri(Context c){return Uri.parse("content://"+c.getPackageName()+".updates/apk/"+id(c));}
 private UpdateDownload(){}
}
