package io.github.jared.xlowerseek;

import android.os.*;
import android.util.Log;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.enums.UsingType;
import org.luckypray.dexkit.query.matchers.*;

/** Defaults new threads to latest; host selection/state is reused for a live switch. */
final class ReplySortHook {
    static void install(TargetResolver resolver,RemoteSettings settings)throws Exception {
        Class<?> sort=resolver.type("replies.sort",ClassMatcher.create().usingEqStrings("Recency","Relevance","Likes"),Class::isEnum);
        Object recency=sort.getField("Recency").get(null),relevance=sort.getField("Relevance").get(null);
        Method initializer=resolver.method("replies.stateInitializer",MethodMatcher.create().name("invoke").paramCount(0).returnType(Object.class)
            .addUsingField(new UsingFieldMatcher(sort.getField("Relevance"),UsingType.Read)),
            method->!Modifier.isStatic(method.getModifiers()));
        Method render=resolver.method("replies.sortRender",MethodMatcher.create().returnType(void.class)
            .paramTypes("int","androidx.compose.runtime.Composer","androidx.compose.ui.Modifier","java.lang.String",
                "kotlin.jvm.functions.Function0","kotlin.jvm.functions.Function1","boolean")
            .addInvoke(MethodMatcher.create().declaredClass(initializer.getDeclaringClass()).name("<init>")),
            method->Modifier.isStatic(method.getModifiers()));
        Class<?> repository=resolver.type("replies.repository",ClassMatcher.create()
            .className("com.x.repositories.post.",StringMatchType.StartsWith)
            .addMethod(MethodMatcher.create().name("<init>").paramCount(6)),type->{
                for(Constructor<?> c:type.getDeclaredConstructors()){
                    Class<?>[] p=c.getParameterTypes();if(p.length==6&&p[2]==long.class&&p[3]==sort)return true;
                }return false;
            });
        Class<?> stateType=ComposeTargets.mutableState(resolver).getReturnType();
        Method read=Reflect.findMethodExact(stateType,"getValue");
        Handler main=new Handler(Looper.getMainLooper());
        ThreadLocal<Object> rendering=new ThreadLocal<>();
        Map<Object,WeakReference<Object>> states=Collections.synchronizedMap(new WeakHashMap<>());
        final WeakReference<?>[] active={new WeakReference<>(null)};
        Hooks.hookMethod(render,new HookCallback(){
            protected void beforeHookedMethod(HookParam p){if(Looper.myLooper()==Looper.getMainLooper()){rendering.set(p.args[5]);active[0]=new WeakReference<>(p.args[5]);}}
            protected void afterHookedMethod(HookParam p){rendering.remove();}
        });
        Hooks.hookAllConstructors(repository,new HookCallback(){
            protected void beforeHookedMethod(HookParam p){
                settings.refresh();if(!settings.latest)return;
                for(int i=0;i<p.args.length;i++)if(sort.isInstance(p.args[i])){p.args[i]=recency;Log.i("XLowerSeek","replySort initial=Recency");}
            }
        });
        Hooks.hookMethod(initializer,new HookCallback(){
            protected void afterHookedMethod(HookParam p){
                if(p.hasThrowable()||rendering.get()==null||!stateType.isInstance(p.getResult()))return;
                Object state=p.getResult();
                // R8 may regroup this lambda; inspect its typed result instead of a case number.
                if(Reflect.callMethod(state,"getValue")!=relevance)return;
                states.put(rendering.get(),new WeakReference<>(state));
                if(settings.latest)Reflect.callMethod(state,"setValue",recency);
            }
        });
        Hooks.hookMethod(read,new HookCallback(){
            protected void afterHookedMethod(HookParam p){
                Object action=rendering.get();
                if(!p.hasThrowable()&&action!=null&&sort.isInstance(p.getResult()))states.put(action,new WeakReference<>(p.thisObject));
            }
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
