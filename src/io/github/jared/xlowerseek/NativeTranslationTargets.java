package io.github.jared.xlowerseek;

import android.content.Context;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

/** Separate native translator discovery from event dispatch and UI lifetime. */
final class NativeTranslationTargets {
    final Method standardRender,grokRender;
    final Class<?> available,idle;
    final Field standardSink,grokSink,showTranslation,translationState,localeLazy,localeValue,localeInitializer;
    final Object standardEvent,grokEvent;
    final Constructor<?> nativeConstructor;

    NativeTranslationTargets(TargetResolver resolver)throws Exception {
        Class<?> composer=Class.forName("androidx.compose.runtime.Composer",false,resolver.loader);
        Class<?> function=Class.forName("kotlin.jvm.functions.Function1",false,resolver.loader);
        ContentDataClass standard=ContentTargets.data(resolver,"CanTranslate(eventSink=");
        available=standard.type;standardSink=standard.field("eventSink",function);
        standardRender=resolver.method("translation.standardPresenter",MethodMatcher.create()
            .usingEqStrings("translatePostResponse must not be null").paramTypes(composer,int.class),
            method->!Modifier.isStatic(method.getModifiers())&&method.getReturnType().isAssignableFrom(available));
        standardEvent=event(resolver,"standard",standardRender.getDeclaringClass().getPackage().getName());
        ContentDataClass grok=ContentTargets.data(resolver,"GrokTranslatePostState(shouldShowTranslation=");
        grokSink=grok.field("eventSink",function);showTranslation=grok.field("shouldShowTranslation",boolean.class);
        translationState=grok.field("translationState");
        idle=resolver.type("translation.idle",ClassMatcher.create().usingEqStrings("Idle"),
            type->translationState.getType().isAssignableFrom(type));
        Class<?> coroutineScope=ComposeTargets.rememberedScope(resolver).getReturnType();
        grokRender=resolver.method("translation.grokPresenter",MethodMatcher.create().returnType(grok.type).paramTypes(composer)
            .usingEqStrings("grok_translations_poll_translation_is_enabled","grok_translations_post_auto_translation_is_enabled"),
            method->!Modifier.isStatic(method.getModifiers())&&nativeConstructor(method.getDeclaringClass(),coroutineScope)!=null);
        nativeConstructor=nativeConstructor(grokRender.getDeclaringClass(),coroutineScope);
        if(nativeConstructor==null)throw new NoSuchMethodException("Native translation constructor");
        nativeConstructor.setAccessible(true);
        grokEvent=event(resolver,"grok",grokRender.getDeclaringClass().getPackage().getName());
        Class<?> lazy=Class.forName("kotlin.Lazy",false,resolver.loader);Field candidate=null;
        for(Field field:grokRender.getDeclaringClass().getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&lazy.isAssignableFrom(field.getType())){
            if(candidate!=null)throw new NoSuchFieldException("Ambiguous translator locale");candidate=field;
        }
        if(candidate==null)throw new NoSuchFieldException("Translator locale");localeLazy=candidate;localeLazy.setAccessible(true);
        localeValue=lazyValue(localeLazy.getType());
        localeInitializer=ContentTargets.onlyField(localeLazy.getType(),Class.forName("kotlin.jvm.functions.Function0",false,resolver.loader));
    }

    static Field lazyValue(Class<?> type)throws NoSuchFieldException {
        Field result=null;
        // SynchronizedLazyImpl also owns a final Object monitor. Only its cached value is writable.
        for(Field field:type.getDeclaredFields()){
            int modifiers=field.getModifiers();
            if(field.getType()!=Object.class||Modifier.isStatic(modifiers)||Modifier.isFinal(modifiers))continue;
            if(result!=null)throw new NoSuchFieldException("Ambiguous lazy value in "+type.getName());result=field;
        }
        if(result==null)throw new NoSuchFieldException("Missing lazy value in "+type.getName());
        result.setAccessible(true);return result;
    }

    static Constructor<?> nativeConstructor(Class<?> type,Class<?> coroutineScope){
        Constructor<?> result=null;
        for(Constructor<?> constructor:type.getDeclaredConstructors()){
            Class<?>[] p=constructor.getParameterTypes();
            if((p.length==9||p.length==10)&&p[p.length-1]==Context.class&&acceptsScope(p[2],coroutineScope)){
                if(result!=null)return null;result=constructor;
            }
        }
        return result;
    }
    static boolean acceptsScope(Class<?> target,Class<?> scope){
        if(target==scope)return true;
        if(!scope.isAssignableFrom(target))return false;
        try{
            Class<?> context=scope.getMethod("getCoroutineContext").getReturnType();
            target.getDeclaredConstructor(context);
            return true;
        }catch(NoSuchMethodException ignored){return false;}
    }
    static Object adaptScope(Class<?> target,Class<?> scope,Object value)throws ReflectiveOperationException {
        if(target.isInstance(value))return value;
        if(!acceptsScope(target,scope))throw new NoSuchMethodException("Unsupported native translation scope");
        Method context=scope.getMethod("getCoroutineContext");
        Constructor<?> constructor=target.getDeclaredConstructor(context.getReturnType());
        constructor.setAccessible(true);
        // Keep the remembered scope's context (including its Job), so Compose still owns cancellation.
        return constructor.newInstance(context.invoke(value));
    }
    // APK 12.35 adds a scribing dependency before the locale provider and reorders list dependencies.
    static boolean listParametersMatch(Class<?>[] p,Class<?>[] nativeParams){
        int[] mapping=nativeParams.length==9?new int[]{0,1,3,5,4,6,7,8}
            :nativeParams.length==10?new int[]{0,1,3,4,6,5,7,8,9}:null;
        if(mapping==null||p.length!=mapping.length)return false;
        for(int i=0;i<p.length;i++){
            if(i==2){if(!p[i].isAssignableFrom(nativeParams[mapping[i]]))return false;}
            else if(p[i]!=nativeParams[mapping[i]])return false;
        }
        return true;
    }
    private static Object event(TargetResolver resolver,String id,String packageName)throws Exception {
        Class<?> type=resolver.type("translation.request."+id,ClassMatcher.create().usingEqStrings("RequestTranslation"),
            candidate->candidate.getPackage()!=null&&candidate.getPackage().getName().equals(packageName));
        Field singleton=null;
        for(Field field:type.getDeclaredFields())if(Modifier.isStatic(field.getModifiers())&&field.getType()==type){
            if(singleton!=null)throw new NoSuchFieldException("Ambiguous translation event");singleton=field;
        }
        if(singleton==null)throw new NoSuchFieldException("Translation event");singleton.setAccessible(true);return singleton.get(null);
    }

    static Method composerCall(TargetResolver resolver,Method renderer,Class<?> returns,Class<?>... parameters)throws Exception {
        Class<?> composer=Class.forName("androidx.compose.runtime.Composer",false,resolver.loader);List<Method> candidates=new ArrayList<>();
        for(MethodData data:resolver.dex().getMethodData(renderer).getInvokes()){
            if(!data.isMethod())continue;Method method=data.getMethodInstance(resolver.loader);
            if(composer.isAssignableFrom(method.getDeclaringClass())&&!Modifier.isStatic(method.getModifiers())
                &&method.getReturnType()==returns&&java.util.Arrays.equals(method.getParameterTypes(),parameters)&&!candidates.contains(method))candidates.add(method);
        }
        if(candidates.size()!=1)throw new NoSuchMethodException("Ambiguous Compose operation");
        Method method=candidates.get(0);method.setAccessible(true);return method;
    }
}
