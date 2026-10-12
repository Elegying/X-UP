package io.github.jared.xlowerseek;

import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.enums.StringMatchType;

/** Filter the common URT list boundary, while X retains layout, pagination and click handling. */
final class AdBlockHook {
    static void install(TargetResolver resolver,RemoteSettings settings)throws Exception {
        AdTimelineFilter filter=new AdTimelineFilter(new ContentTargets.Ads(resolver));
        Class<?> vector=resolver.type("ads.smallImmutableList",ClassMatcher.create()
            .className("kotlinx.collections.immutable.implementations.immutableList.",StringMatchType.StartsWith)
            .addMethod(MethodMatcher.create().name("<init>").paramTypes(Object[].class)),
            type->List.class.isAssignableFrom(type));
        AdImmutableList lists=new AdImmutableList(vector);
        AdPresentation presentation=new AdPresentation(source->{List<?> out=filter.filter((List<?>)source);return out==source?source:lists.copy(out);});
        Class<?> ui=resolver.type("ads.timelineUi",ClassMatcher.create().usingEqStrings(" module=","(entryId=",", sortIndex="));
        Method render=resolver.method("ads.timelineRender",MethodMatcher.create().declaredClass(ui).returnType(void.class),method->{
            Class<?>[] p=method.getParameterTypes();int index=p.length-4;
            return Modifier.isStatic(method.getModifiers())&&p.length>8&&List.class.isAssignableFrom(p[0])
                &&p[1].isEnum()&&p[2]==boolean.class&&p[3]==boolean.class
                &&p[index].getName().equals("androidx.compose.runtime.Composer")
                &&p[index+1]==int.class&&p[index+2]==int.class&&p[index+3]==int.class;
        });
        int composer=render.getParameterCount()-4;
        Method mutable=ComposeTargets.mutableState(resolver);
        Object revision=mutable.invoke(null,0);Handler main=new Handler(Looper.getMainLooper());
        boolean[] enabled={settings.feature(Feature.AD_BLOCK)};int[] version={0};
        settings.addListener(()->main.post(()->{boolean next=settings.feature(Feature.AD_BLOCK);if(next!=enabled[0]){enabled[0]=next;Reflect.callMethod(revision,"setValue",++version[0]);}}));
        final int flags=composer+1;
        Hooks.hookMethod(render,new HookCallback(){
            private boolean reported;
            @Override protected void beforeHookedMethod(HookParam p){
                if(Looper.myLooper()!=Looper.getMainLooper()||!(p.args[0] instanceof List))return;
                try{
                    Reflect.callMethod(revision,"getValue"); // subscribe the active Compose scope to the switch
                    Object original=p.args[0],result=presentation.render(original,settings.feature(Feature.AD_BLOCK));
                    if(result!=original){p.args[0]=result;p.args[flags]=(((Integer)p.args[flags])&~14)|5;}
                }catch(Exception failure){if(!reported){reported=true;Hooks.log("X-UP: ad adapter unavailable; retaining original timeline");}}
            }
        });
    }
}
