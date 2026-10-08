package io.github.jared.xlowerseek;

import android.os.*;
import java.lang.reflect.*;
import java.util.*;

/** Filter the common URT list boundary, while X retains layout, pagination and click handling. */
final class AdBlockHook {
    static void install(ClassLoader loader,RemoteSettings settings)throws Exception {
        AdTimelineFilter filter=new AdTimelineFilter(loader);
        Class<?> immutable=Reflect.findClass("kotlinx.collections.immutable.b",loader);
        Class<?> vector=Reflect.findClass("kotlinx.collections.immutable.implementations.immutableList.h",loader);
        AdImmutableList lists=new AdImmutableList(vector);
        AdPresentation presentation=new AdPresentation(source->{List<?> out=filter.filter((List<?>)source);return out==source?source:lists.copy(out);});
        Class<?> ui=Reflect.findClass("com.x.urt.ui.b",loader);
        Method render=null;int composer=-1;
        for(Method m:ui.getDeclaredMethods()){
            Class<?>[] p=m.getParameterTypes();
            if(m.getName().equals("f")&&p.length==26&&p[0]==immutable&&p[22].getName().equals("androidx.compose.runtime.Composer")&&m.getReturnType()==void.class){render=m;composer=22;break;}
        }
        if(render==null)throw new NoSuchMethodException("URT timeline render");
        Method mutable=Reflect.findMethodExact(Reflect.findClass("androidx.compose.runtime.i",loader),"r",Object.class);
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
