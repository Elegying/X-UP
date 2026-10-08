package io.github.jared.xlowerseek;
import java.lang.ref.WeakReference;
import java.util.*;
/** X normally polls this callback only while playing; notify it after our paused seeks too. */
final class PlayerProgressSync {
    private final Map<Object,WeakReference<Object>> callbacks=Collections.synchronizedMap(new WeakHashMap<>());
    void install(ClassLoader loader){
        Hooks.hookAllConstructors(Reflect.findClass("com.x.media.playback.t0",loader),new HookCallback(){
            @Override protected void afterHookedMethod(HookParam p){
                if(p.args.length==11&&p.args[0]!=null&&p.args[2]!=null)callbacks.put(p.args[0],new WeakReference<>(p.args[2]));
            }
        });
    }
    void refresh(Object player){
        WeakReference<Object> ref=callbacks.get(player);Object state=ref==null?null:ref.get();
        if(state==null)return;
        try{Object callback=Reflect.callMethod(state,"getValue");Reflect.callMethod(callback,"invoke");}
        catch(Throwable e){android.util.Log.w("XLowerSeek","Progress refresh unavailable");}
    }
    void release(Object player){callbacks.remove(player);}
}
