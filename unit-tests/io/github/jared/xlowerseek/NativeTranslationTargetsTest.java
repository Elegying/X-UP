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
}
