package io.github.jared.xlowerseek;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.LazyThreadSafetyMode;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeTranslationTargetsTest {
    @Test public void synchronizedLazyUsesForcedLocaleWithoutRunningInitializer()throws Exception {
        assertForcedLocale(LazyThreadSafetyMode.SYNCHRONIZED);
    }
    @Test public void otherLazyModesUseForcedLocaleWithoutRunningInitializer()throws Exception {
        assertForcedLocale(LazyThreadSafetyMode.PUBLICATION);
        assertForcedLocale(LazyThreadSafetyMode.NONE);
    }
    private static void assertForcedLocale(LazyThreadSafetyMode mode)throws Exception {
        AtomicInteger calls=new AtomicInteger();
        Lazy<Locale> lazy=LazyKt.lazy(mode,()->{calls.incrementAndGet();return Locale.ENGLISH;});
        NativeTranslationTargets.lazyValue(lazy.getClass()).set(lazy,Locale.SIMPLIFIED_CHINESE);
        assertSame(Locale.SIMPLIFIED_CHINESE,lazy.getValue());
        assertTrue(lazy.isInitialized());
        assertEquals(0,calls.get());
    }
    static final class RenamedLazy {
        static Object constant=new Object();
        final Object monitor=new Object();
        volatile Object renamedValue;
    }
    @Test public void staticObjectAndFinalLockRemainUntouched()throws Exception {
        RenamedLazy sample=new RenamedLazy();Object monitor=sample.monitor,constant=RenamedLazy.constant;
        NativeTranslationTargets.lazyValue(RenamedLazy.class).set(sample,Locale.SIMPLIFIED_CHINESE);
        assertSame(Locale.SIMPLIFIED_CHINESE,sample.renamedValue);
        assertSame(monitor,sample.monitor);
        assertSame(constant,RenamedLazy.constant);
    }
    static final class AmbiguousLazy {Object first,second;}
    @Test public void ambiguousWritableValuesAreRejected()throws Exception {
        try{NativeTranslationTargets.lazyValue(AmbiguousLazy.class);fail();}
        catch(NoSuchFieldException expected){}
    }
    static final class ImmutableLazy {final Object value=new Object();}
    @Test public void finalValuesAreNotMutated()throws Exception {
        try{NativeTranslationTargets.lazyValue(ImmutableLazy.class);fail();}
        catch(NoSuchFieldException expected){}
    }
    static class Scope {} static class Navigation {} static class Model {}
    static class Scribe {} static class PostScribe extends Scribe {}
    static class Grok {} static class Translation {} static class Switches {}
    static class Analytics {} static class LocaleProvider {}
    static class LegacyPresenter {
        LegacyPresenter(Navigation n,Model m,Scope s,PostScribe p,Grok g,Translation t,Switches f,LocaleProvider l,android.content.Context c){}
    }
    static class CurrentPresenter {
        CurrentPresenter(Navigation n,Model m,Scope s,PostScribe p,Grok g,Translation t,Switches f,Analytics a,LocaleProvider l,android.content.Context c){}
    }
    @Test public void legacyConstructorAndListContractRemainSupported(){
        java.lang.reflect.Constructor<?> constructor=NativeTranslationTargets.nativeConstructor(LegacyPresenter.class,Scope.class);
        assertNotNull(constructor);
        assertTrue(NativeTranslationTargets.listParametersMatch(new Class<?>[]{Navigation.class,Model.class,Scribe.class,Translation.class,Grok.class,Switches.class,LocaleProvider.class,android.content.Context.class},constructor.getParameterTypes()));
    }
    @Test public void currentConstructorPreservesAnalyticsAndLocalePositions(){
        java.lang.reflect.Constructor<?> constructor=NativeTranslationTargets.nativeConstructor(CurrentPresenter.class,Scope.class);
        assertNotNull(constructor);
        Class<?>[] list={Navigation.class,Model.class,Scribe.class,Grok.class,Switches.class,Translation.class,Analytics.class,LocaleProvider.class,android.content.Context.class};
        assertTrue(NativeTranslationTargets.listParametersMatch(list,constructor.getParameterTypes()));
        list[6]=LocaleProvider.class;
        assertFalse(NativeTranslationTargets.listParametersMatch(list,constructor.getParameterTypes()));
        assertNull(NativeTranslationTargets.nativeConstructor(CurrentPresenter.class,Object.class));
    }
    public interface ScopeContract { Object getCoroutineContext(); }
    public static class ConcreteScope implements ScopeContract {
        final Object context;
        public ConcreteScope(Object context){this.context=context;}
        public Object getCoroutineContext(){return context;}
    }
    public static class UnsupportedScope implements ScopeContract {
        public Object getCoroutineContext(){return null;}
    }
    @Test public void narrowedScopePreservesComposeContextIdentity()throws Exception {
        Object context=new Object();ScopeContract remembered=()->context;
        assertTrue(NativeTranslationTargets.acceptsScope(ConcreteScope.class,ScopeContract.class));
        Object adapted=NativeTranslationTargets.adaptScope(ConcreteScope.class,ScopeContract.class,remembered);
        assertSame(context,((ConcreteScope)adapted).getCoroutineContext());
        assertSame(adapted,NativeTranslationTargets.adaptScope(ConcreteScope.class,ScopeContract.class,adapted));
        assertFalse(NativeTranslationTargets.acceptsScope(UnsupportedScope.class,ScopeContract.class));
        assertFalse(NativeTranslationTargets.acceptsScope(Object.class,ScopeContract.class));
    }
}
