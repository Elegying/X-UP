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

    private static Constructor<?> nativeConstructor(Class<?> type,Class<?> coroutineScope){
        Constructor<?> result=null;
        for(Constructor<?> constructor:type.getDeclaredConstructors()){
            Class<?>[] p=constructor.getParameterTypes();
            if(p.length==9&&p[8]==Context.class&&p[2]==coroutineScope){
                if(result!=null)return null;result=constructor;
            }
        }
        return result;
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
