package io.github.jared.xlowerseek;
import android.content.*;import android.database.Cursor;import android.net.Uri;import android.os.*;
/** Only X and this module can submit visible text. No network translation path exists. */
public final class LocalTranslationProvider extends ContentProvider {
 public static final String AUTHORITY="io.github.jared.xlowerseek.translation";
 public boolean onCreate(){return true;}
 private boolean allowed(){int uid=Binder.getCallingUid();if(uid==android.os.Process.myUid())return true;String[] packages=getContext().getPackageManager().getPackagesForUid(uid);if(packages!=null)for(String p:packages)if("com.twitter.android".equals(p))return true;return false;}
 public Bundle call(String method,String arg,Bundle extras){
  if(!allowed())throw new SecurityException("Caller not allowed");Bundle out=new Bundle();
  if("foreground".equals(method)){if(arg!=null&&arg.length()<=128&&extras!=null)LocalTranslationService.foreground(getContext(),Binder.getCallingUid(),arg,extras.getBoolean("visible",false));return out;}
  if("cancel".equals(method)){if(arg!=null&&arg.length()<=128)LocalTranslationService.cancel(Binder.getCallingUid(),arg);return out;}
  if("status".equals(method)){out.putString("status",LocalTranslationService.status());out.putBoolean("ready",LocalModels.ready(getContext(),SettingsStore.engine(getContext())));return out;}
  if(!"translate".equals(method)||extras==null)return out;
  ResultReceiver receiver=android.os.Build.VERSION.SDK_INT>=33?extras.getParcelable("receiver",ResultReceiver.class):extras.getParcelable("receiver");if(receiver==null)return out;
  String text=extras.getString("text"),background=extras.getString("context","");
  if(text==null||text.trim().isEmpty()||text.length()>8192||background==null||background.length()>1800){receiver.send(2,null);return out;}
  String id=extras.getString("id");String engine=extras.getString("engine");if(id==null||id.length()>128||engine==null){receiver.send(2,null);return out;}
  LocalTranslationService.request(getContext(),Binder.getCallingUid(),id,engine,extras.getLong("revision",-1),text,background,receiver);return out;
 }
 public Cursor query(Uri u,String[] p,String s,String[] a,String sort){return null;}public String getType(Uri u){return null;}
 public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
