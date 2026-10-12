package io.github.jared.xlowerseek;

import android.os.Looper;
import android.util.Log;
import java.lang.reflect.*;
import java.util.Map;
import java.util.WeakHashMap;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

/** Gives eligible list/quote presenters the current host's native translation state. */
final class PostTranslationBridge {
    private final Map<Object,Object> locales=new WeakHashMap<>();
    private final ThreadLocal<Object> eligibleModel=new ThreadLocal<>();
    private final RemoteSettings settings;
    private final NativeTranslationTargets targets;
    private final Class<?> scribe;
    private final Method scope,startGroup,endGroup,remembered,remember,language,listRender;
    private final Field modelField,scribeField;
    private final Field[] constructorFields;
    private boolean warned;

    private PostTranslationBridge(TargetResolver resolver,RemoteSettings settings)throws Exception {
        this.settings=settings;targets=new NativeTranslationTargets(resolver);
        scope=ComposeTargets.rememberedScope(resolver);
        Class<?>[] nativeParams=targets.nativeConstructor.getParameterTypes();scribe=nativeParams[3];
        Class<?> composer=Class.forName("androidx.compose.runtime.Composer",false,resolver.loader);
        listRender=resolver.method("translation.listPresenter",MethodMatcher.create().returnType(targets.grokRender.getReturnType())
            .paramTypes(composer).usingEqStrings("grok_translations_poll_translation_is_enabled","grok_translations_post_auto_translation_is_enabled"),
            method->method.getDeclaringClass()!=targets.grokRender.getDeclaringClass()&&listConstructor(method.getDeclaringClass(),nativeParams)!=null);
        Constructor<?> listConstructor=listConstructor(listRender.getDeclaringClass(),nativeParams);
        if(listConstructor==null)throw new NoSuchMethodException("List translation constructor");
        Class<?> list=listRender.getDeclaringClass();
        modelField=ContentTargets.onlyField(list,nativeParams[1]);
        scribeField=ContentTargets.onlyField(list,listConstructor.getParameterTypes()[2]);
        constructorFields=new Field[9];
        for(int i=0;i<9;i++)if(i!=2&&i!=7)constructorFields[i]=i==3?scribeField:ContentTargets.onlyField(list,nativeParams[i]);
        startGroup=NativeTranslationTargets.composerCall(resolver,targets.grokRender,void.class,int.class);
        endGroup=NativeTranslationTargets.composerCall(resolver,targets.grokRender,void.class,boolean.class);
        remembered=NativeTranslationTargets.composerCall(resolver,targets.grokRender,Object.class);
        remember=NativeTranslationTargets.composerCall(resolver,targets.grokRender,void.class,Object.class);
        ContentDataClass canonical=ContentTargets.data(resolver,"CanonicalPost(id=");
        Method canonicalLanguage=ContentTargets.getter(resolver,"postLanguage",canonical.type,canonical.field("language",String.class));
        language=nativeParams[1].getMethod(canonicalLanguage.getName());
        if(language.getReturnType()!=String.class)throw new NoSuchMethodException("Post language");
        Method translatable=ContentTargets.getter(resolver,"postTranslatable",canonical.type,canonical.field("isTranslatable",boolean.class));
        Class<?> contextual=ContentTargets.model(resolver,"ContextualPost(canonicalPost=");
        for(Class<?> type:new Class<?>[]{canonical.type,contextual}){
            Method method=type.getMethod(translatable.getName());
            if(method.getReturnType()!=boolean.class)throw new NoSuchMethodException("Post translation eligibility");
            Hooks.hookMethod(method,new HookCallback(){
                @Override protected void afterHookedMethod(HookParam p){if(!p.hasThrowable()&&eligibleModel.get()==p.thisObject)p.setResult(true);}
            });
        }
    }
    private static Constructor<?> listConstructor(Class<?> type,Class<?>[] nativeParams){
        Constructor<?> result=null;
        for(Constructor<?> constructor:type.getDeclaredConstructors()){
            Class<?>[] p=constructor.getParameterTypes();
            if(p.length==8&&p[0]==nativeParams[0]&&p[1]==nativeParams[1]&&p[2].isAssignableFrom(nativeParams[3])
                &&p[3]==nativeParams[5]&&p[4]==nativeParams[4]&&p[5]==nativeParams[6]&&p[6]==nativeParams[7]&&p[7]==nativeParams[8]){
                if(result!=null)return null;result=constructor;
            }
        }
        return result;
    }
    static void install(TargetResolver resolver,RemoteSettings settings)throws Exception {
        PostTranslationBridge bridge=new PostTranslationBridge(resolver,settings);
        Hooks.hookAllConstructors(bridge.listRender.getDeclaringClass(),new HookCallback(){
            @Override protected void afterHookedMethod(HookParam p){
                if(!p.hasThrowable()&&p.args.length==8)bridge.locales.put(p.thisObject,p.args[6]);
            }
        });
        Hooks.hookMethod(bridge.listRender,new HookCallback(){
            @Override protected void afterHookedMethod(HookParam p)throws Throwable {
                if(p.hasThrowable()||p.getResult()!=null||Looper.myLooper()!=Looper.getMainLooper())return;
                bridge.render(p);
            }
        });
    }
    private void render(HookCallback.HookParam p)throws Throwable {
        Object owner=p.thisObject,model=modelField.get(owner);
        if(!settings.translate||settings.feature(Feature.LOCAL_TEXT)||!settings.feature(Feature.NATIVE_TRANSLATE)||!locales.containsKey(owner)||!scribe.isInstance(scribeField.get(owner)))return;
        if(!PostTranslationPolicy.eligible((String)language.invoke(model),(String)Reflect.callMethod(model,"getText")))return;
        Object composer=p.args[0];startGroup.invoke(composer,0x58555031);Object previous=eligibleModel.get();
        try{
            Object coroutineScope=scope.invoke(null,composer);
            Object presenter=remembered.invoke(composer);
            if(!targets.grokRender.getDeclaringClass().isInstance(presenter)){
                Object[] args=new Object[9];
                for(int i=0;i<args.length;i++)args[i]=i==2?coroutineScope:i==7?locales.get(owner):constructorFields[i].get(owner);
                presenter=targets.nativeConstructor.newInstance(args);remember.invoke(composer,presenter);
            }
            eligibleModel.set(model);
            Object state=targets.grokRender.invoke(presenter,composer);
            if(settings.translate)p.setResult(state);
        }catch(Throwable error){
            if(!warned){warned=true;Log.w("XLowerSeek","List translation bridge unavailable; original retained",error);}
        }finally{
            if(previous==null)eligibleModel.remove();else eligibleModel.set(previous);endGroup.invoke(composer,false);
        }
    }
}
