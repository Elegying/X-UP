package io.github.jared.xlowerseek;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

/** Discover the native progress callback without relying on a coroutine's parameter index. */
final class PlayerProgressSync {
    private final Map<Object,WeakReference<Object>> callbacks=Collections.synchronizedMap(new WeakHashMap<>());
    void install(TargetResolver resolver,VideoTargets targets)throws Exception {
        Class<?> function0=Reflect.findClass("kotlin.jvm.functions.Function0",resolver.loader);
        Method loop=resolver.method("video.progress-loop",MethodMatcher.create().name("invokeSuspend")
            .declaredClass(ClassMatcher.create().className("com.x.media.playback.",StringMatchType.StartsWith)
                .superClass("kotlin.coroutines.jvm.internal.SuspendLambda").addFieldForType(targets.exo))
            .addInvoke(MethodMatcher.create().name("isPlaying").paramCount(0).returnType(boolean.class))
            .addInvoke(MethodMatcher.create().declaredClass(function0).name("invoke").paramCount(0)),
            method->{
                Constructor<?>[] choices=method.getDeclaringClass().getDeclaredConstructors();
                return choices.length==1&&choices[0].getParameterCount()>=8&&choices[0].getParameterTypes()[0]==targets.exo;
            });
        Constructor<?> constructor=loop.getDeclaringClass().getDeclaredConstructors()[0];
        Hooks.hookMethod(constructor,new HookCallback(){
            @Override protected void afterHookedMethod(HookParam p){
                if(p.hasThrowable()||p.args[0]==null)return;
                Object selected=null;
                for(int i=1;i<p.args.length;i++) {
                    Object state=p.args[i];if(state==null)continue;
                    Method getter=Reflect.findMethodExactIfExists(state.getClass(),"getValue");
                    if(getter==null)continue;
                    try {
                        if(!function0.isInstance(getter.invoke(state)))continue;
                        if(selected!=null){callbacks.remove(p.args[0]);return;}
                        selected=state;
                    }catch(ReflectiveOperationException ignored){}
                }
                if(selected!=null)callbacks.put(p.args[0],new WeakReference<>(selected));
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
