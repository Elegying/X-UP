package io.github.jared.xlowerseek;

import android.app.*;
import android.os.*;
import android.util.Log;
import java.lang.ref.WeakReference;
import java.util.*;

/** Uses X's translation events, without changing account/server language preferences. */
final class AutoTranslateHook {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<Object,TranslationGate> visited=new WeakHashMap<>();
    private final RemoteSettings settings;
    private int resumed;
    private AutoTranslateHook(RemoteSettings settings){this.settings=settings;}
    static void install(Application app,ClassLoader loader,RemoteSettings settings){
        AutoTranslateHook hook=new AutoTranslateHook(settings);
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
            public void onActivityResumed(Activity a){hook.resumed++;}
            public void onActivityPaused(Activity a){hook.resumed=Math.max(0,hook.resumed-1);}
            public void onActivityCreated(Activity a,Bundle b){} public void onActivityStarted(Activity a){}
            public void onActivityStopped(Activity a){} public void onActivityDestroyed(Activity a){}
            public void onActivitySaveInstanceState(Activity a,Bundle b){}
        });
        Object ordinary=Reflect.getStaticObjectField(Reflect.findClass("com.x.urt.items.post.translate.f",loader),"a");
        Object grok=Reflect.getStaticObjectField(Reflect.findClass("com.x.urt.items.post.translate.grok.h",loader),"a");
        hook.attach(loader,"com.x.urt.items.post.translate.i","a",ordinary,false);
        hook.attach(loader,"com.x.urt.items.post.translate.grok.o","d",grok,true);
        // Translation language is local to the native presenter, never an account preference.
        Hooks.hookAllConstructors(Reflect.findClass("com.x.urt.items.post.translate.grok.o",loader),new HookCallback(){
            @Override protected void afterHookedMethod(HookParam p)throws Throwable{
                if(p.hasThrowable()||(!settings.translate||settings.feature(Feature.LOCAL_TEXT)||!settings.feature(Feature.NATIVE_TRANSLATE)))return;
                Object lazy=Reflect.getObjectField(p.thisObject,"i");
                java.lang.reflect.Field value=lazy.getClass().getDeclaredField("b");value.setAccessible(true);
                value.set(lazy,Locale.SIMPLIFIED_CHINESE);
                java.lang.reflect.Field initializer=lazy.getClass().getDeclaredField("a");initializer.setAccessible(true);
                initializer.set(lazy,null);
            }
        });
    }
    private void attach(ClassLoader loader,String type,String method,Object event,boolean grok){
        Hooks.hookAllMethods(Reflect.findClass(type,loader),method,new HookCallback(){
            @Override protected void afterHookedMethod(HookParam p){
                if(p.hasThrowable()||p.getResult()==null||Looper.myLooper()!=Looper.getMainLooper())return;
                try{
                    Object state=p.getResult();boolean available,terminal;
                    if(grok){
                        Object translation=Reflect.getObjectField(state,"b");
                        boolean idle=translation.getClass().getName().equals("com.x.groktranslate.l");
                        available=!Reflect.getBooleanField(state,"a")&&idle;
                        terminal=!idle;
                    }else{
                        available=state.getClass().getName().equals("com.x.urt.items.post.translate.j");
                        terminal=!available;
                    }
                    TranslationGate gate=visited.get(p.thisObject);
                    if(gate==null){gate=new TranslationGate();visited.put(p.thisObject,gate);}
                    if(!gate.observe(available,settings.translate&&!settings.feature(Feature.LOCAL_TEXT)&&settings.feature(Feature.NATIVE_TRANSLATE),terminal))return;
                    WeakReference<Object> sink=new WeakReference<>(Reflect.getObjectField(state,grok?"g":"a"));
                    WeakReference<Object> owner=new WeakReference<>(p.thisObject);
                    TranslationGate pending=gate;
                    main.postDelayed(()->{
                        Object callback=sink.get();
                        if(owner.get()==null||callback==null||resumed==0||(!settings.translate||settings.feature(Feature.LOCAL_TEXT)||!settings.feature(Feature.NATIVE_TRANSLATE))){pending.defer();return;}
                        if(!pending.claim())return;
                        try{Reflect.callMethod(callback,"invoke",event);Log.i("XLowerSeek","autoTranslate requested engine="+(grok?"grok":"standard"));}
                        catch(Throwable e){Log.w("XLowerSeek","Auto translation unavailable; manual action retained");}
                    },150);
                }catch(Throwable e){Log.w("XLowerSeek","Translation state unavailable; original retained");}
            }
        });
    }
}
