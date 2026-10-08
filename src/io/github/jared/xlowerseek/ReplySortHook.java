package io.github.jared.xlowerseek;

import android.os.*;
import android.util.Log;
import java.lang.ref.WeakReference;
import java.util.*;

/** Defaults new threads to latest, and uses X's own selection action for live setting changes. */
final class ReplySortHook {
    static void install(ClassLoader loader,RemoteSettings settings)throws Exception{
        Class<?> sort=Reflect.findClass("com.x.models.ff",loader);
        Object recency=Reflect.getStaticObjectField(sort,"Recency"),relevance=Reflect.getStaticObjectField(sort,"Relevance");
        Class<?> repository=Reflect.findClass("com.x.repositories.post.t",loader);
        Handler main=new Handler(Looper.getMainLooper());
        ThreadLocal<Object> rendering=new ThreadLocal<>();
        Map<Object,WeakReference<Object>> states=Collections.synchronizedMap(new WeakHashMap<>());
        final WeakReference<?>[] active={new WeakReference<>(null)};
        Hooks.hookAllMethods(Reflect.findClass("com.x.clock.d",loader),"g",new HookCallback(){
            protected void beforeHookedMethod(HookParam p){if(p.args.length==7&&Looper.myLooper()==Looper.getMainLooper()){rendering.set(p.args[5]);active[0]=new WeakReference<>(p.args[5]);}}
            protected void afterHookedMethod(HookParam p){rendering.remove();}
        });
        Hooks.hookAllConstructors(repository,new HookCallback(){
            protected void beforeHookedMethod(HookParam p){
                settings.refresh();if(!settings.latest)return;
                for(int i=0;i<p.args.length;i++)if(sort.isInstance(p.args[i])){p.args[i]=recency;Log.i("XLowerSeek","replySort initial=Recency");}
            }
        });
        Hooks.findAndHookMethod(Reflect.findClass("com.x.ui.common.v2",loader),"invoke",new HookCallback(){
            protected void afterHookedMethod(HookParam p){
                if(p.hasThrowable()||Reflect.getIntField(p.thisObject,"a")!=14)return;
                Object action=rendering.get();if(action!=null)states.put(action,new WeakReference<>(p.getResult()));
                if(settings.latest)Reflect.callMethod(p.getResult(),"setValue",recency);
            }
        });
        // Saveable Compose state may be restored without running its initializer.
        Hooks.hookAllConstructors(Reflect.findClass("androidx.compose.foundation.text.t",loader),new HookCallback(){
            protected void afterHookedMethod(HookParam p){if(!p.hasThrowable()&&p.args.length==4&&Integer.valueOf(1).equals(p.args[3]))states.put(p.args[0],new WeakReference<>(p.args[2]));}
        });
        final boolean[] last={settings.latest};
        settings.addListener(()->main.post(()->{
            if(last[0]==settings.latest)return;last[0]=settings.latest;
            Object action=active[0].get();if(action==null)return;
            Object mode=settings.latest?recency:relevance;
            try{
                Reflect.callMethod(action,"invoke",mode);
                WeakReference<Object> ref=states.get(action);Object state=ref==null?null:ref.get();
                if(state!=null)Reflect.callMethod(state,"setValue",mode);
                Log.i("XLowerSeek","replySort live="+mode);
            }catch(RuntimeException e){Log.w("XLowerSeek","Live reply sort unavailable; next thread uses saved setting");}
        }));
    }
}
