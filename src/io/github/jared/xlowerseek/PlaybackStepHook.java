package io.github.jared.xlowerseek;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.IntSupplier;

/** Adjust the shared seek executor: continuation taps bypass X's initial step factory. */
final class PlaybackStepHook {
    static void install(ClassLoader loader, RemoteSettings settings) throws Exception {
        Class<?> direction=Reflect.findClass("com.x.ui.common.media.m1",loader);
        Class<?> skipping=Reflect.findClass("com.x.video.tab.m0",loader);
        Class<?> step=Reflect.findClass("com.x.video.tab.o0",loader);
        Class<?> unit=Reflect.findClass("kotlin.time.DurationUnit",loader);
        Method encode=Reflect.findClass("kotlin.time.DurationKt",loader).getDeclaredMethod("h",int.class,unit);
        Method decode=Reflect.findClass("kotlin.time.Duration",loader).getDeclaredMethod("x",long.class,unit);
        Object secondsUnit=Reflect.getStaticObjectField(unit,"SECONDS");
        Object forward=Reflect.getStaticObjectField(direction,"FORWARD");
        Class<?> mutable=Reflect.findClass("androidx.compose.runtime.l1",loader);
        Method execute=Reflect.findClass("com.x.video.tab.x6",loader).getDeclaredMethod("i",
            Reflect.findClass("kotlinx.coroutines.channels.n",loader),Reflect.findClass("androidx.compose.ui.hapticfeedback.a",loader),
            mutable,mutable,mutable,Reflect.findClass("androidx.compose.runtime.r1",loader),direction,step);
        Hooks.hookMethod(execute,SeekStepAdapter.callback(()->{settings.refresh();return settings.feature(Feature.SEEK_STEP)?settings.seconds:10;},
            forward,skipping.getDeclaredConstructor(direction,int.class),step.getDeclaredConstructor(long.class,skipping),encode,decode,secondsUnit));
    }
}
