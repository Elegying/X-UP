package io.github.jared.xlowerseek;

import android.app.*;
import android.os.*;
import android.util.Log;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.*;

/** Uses X's translation events, without changing account/server language preferences. */
final class AutoTranslateHook {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<Object,TranslationGate> visited=new WeakHashMap<>();
    private final RemoteSettings settings;
    private final NativeTranslationTargets targets;
    private int resumed;
    private AutoTranslateHook(RemoteSettings settings,NativeTranslationTargets targets){this.settings=settings;this.targets=targets;}
    static void install(Application app,TargetResolver resolver,RemoteSettings settings)throws Exception {
        NativeTranslationTargets targets=new NativeTranslationTargets(resolver);
        AutoTranslateHook hook=new AutoTranslateHook(settings,targets);
        hook.attach(targets.standardRender,targets.standardEvent,false);
        hook.attach(targets.grokRender,targets.grokEvent,true);
        // This lazy belongs only to the native presenter, never an account preference.
        Hooks.hookAllConstructors(targets.grokRender.getDeclaringClass(),new HookCallback(){
            @Override protected void afterHookedMethod(HookParam p)throws Throwable {
                if(p.hasThrowable()||!hook.enabled())return;
                Object lazy=targets.localeLazy.get(p.thisObject);
                targets.localeValue.set(lazy,Locale.SIMPLIFIED_CHINESE);
                targets.localeInitializer.set(lazy,null);
            }
        });
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
            public void onActivityResumed(Activity a){hook.resumed++;}
            public void onActivityPaused(Activity a){hook.resumed=Math.max(0,hook.resumed-1);}
            public void onActivityCreated(Activity a,Bundle b){} public void onActivityStarted(Activity a){}
            public void onActivityStopped(Activity a){} public void onActivityDestroyed(Activity a){}
            public void onActivitySaveInstanceState(Activity a,Bundle b){}
        });
    }
    private boolean enabled(){return settings.translate&&!settings.feature(Feature.LOCAL_TEXT)&&settings.feature(Feature.NATIVE_TRANSLATE);}
    private void attach(Method method,Object event,boolean grok){
        Hooks.hookMethod(method,new HookCallback(){
            @Override protected void afterHookedMethod(HookParam p){
                if(p.hasThrowable()||p.getResult()==null||Looper.myLooper()!=Looper.getMainLooper())return;
                try{
                    Object state=p.getResult();boolean available,terminal;
                    if(grok){
                        boolean idle=targets.idle.isInstance(targets.translationState.get(state));
                        available=!targets.showTranslation.getBoolean(state)&&idle;terminal=!idle;
                    }else{
                        available=targets.available.isInstance(state);terminal=!available;
                    }
                    TranslationGate gate=visited.get(p.thisObject);
                    if(gate==null){gate=new TranslationGate();visited.put(p.thisObject,gate);}
                    if(!gate.observe(available,enabled(),terminal))return;
                    WeakReference<Object> sink=new WeakReference<>((grok?targets.grokSink:targets.standardSink).get(state));
                    WeakReference<Object> owner=new WeakReference<>(p.thisObject);
                    TranslationGate pending=gate;
                    main.postDelayed(()->{
                        Object callback=sink.get();
                        if(owner.get()==null||callback==null||resumed==0||!enabled()){pending.defer();return;}
                        if(!pending.claim())return;
                        try{Reflect.callMethod(callback,"invoke",event);Log.i("XLowerSeek","autoTranslate requested engine="+(grok?"grok":"standard"));}
                        catch(Throwable e){Log.w("XLowerSeek","Auto translation unavailable; manual action retained");}
                    },150);
                }catch(Throwable e){Log.w("XLowerSeek","Translation state unavailable; original retained");}
            }
        });
    }
}
