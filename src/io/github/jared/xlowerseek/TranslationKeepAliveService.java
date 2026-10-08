package io.github.jared.xlowerseek;
import android.app.Service;import android.content.Intent;import android.os.*;
/** A bound-only service follows the visible client's importance and consumes no data-sync quota. */
public final class TranslationKeepAliveService extends Service {
 private final IBinder binder=new Binder();
 public IBinder onBind(Intent intent){return binder;}
 public int onStartCommand(Intent intent,int flags,int id){stopSelf(id);return START_NOT_STICKY;}
}
