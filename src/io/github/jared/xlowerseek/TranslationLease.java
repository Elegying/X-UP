package io.github.jared.xlowerseek;

import android.content.*;
import android.os.IBinder;
import java.util.concurrent.atomic.AtomicBoolean;

/** The foreground client owns a finite translation task; no root or battery exemption. */
final class TranslationLease {
    static Runnable acquire(Context context){
        ServiceConnection connection=new ServiceConnection(){
            public void onServiceConnected(ComponentName name,IBinder binder){}
            public void onServiceDisconnected(ComponentName name){}
        };
        Intent intent=new Intent().setClassName("io.github.jared.xlowerseek","io.github.jared.xlowerseek.TranslationKeepAliveService");
        try{context.startForegroundService(intent);}catch(RuntimeException unavailable){/* The provider lease remains a fallback. */}
        boolean bound;
        try{bound=context.bindService(intent,connection,Context.BIND_AUTO_CREATE|Context.BIND_IMPORTANT);}
        catch(RuntimeException e){bound=false;}
        if(!bound)return ()->{};
        AtomicBoolean closed=new AtomicBoolean();
        return ()->{if(closed.compareAndSet(false,true))try{context.unbindService(connection);}catch(RuntimeException ignored){}};
    }
}
