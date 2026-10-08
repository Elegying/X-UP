package io.github.jared.xlowerseek;

import android.util.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

final class PlaybackStepHook {
    static void install(ClassLoader loader, RemoteSettings settings) throws Exception {
        Class<?> policy = Reflect.findClass("com.x.video.tab.k0", loader);
        Class<?> state = Reflect.findClass("com.x.video.tab.n0", loader);
        Class<?> direction = Reflect.findClass("com.x.ui.common.media.m1", loader);
        Class<?> skipping = Reflect.findClass("com.x.video.tab.m0", loader);
        Class<?> step = Reflect.findClass("com.x.video.tab.o0", loader);
        Class<?> durationUnit = Reflect.findClass("kotlin.time.DurationUnit", loader);
        Method encode = Reflect.findClass("kotlin.time.DurationKt", loader)
            .getDeclaredMethod("h", int.class, durationUnit);
        Object secondsUnit = Reflect.getStaticObjectField(durationUnit, "SECONDS");
        long five = (Long) encode.invoke(null, 5, secondsUnit);
        Constructor<?> makeState = skipping.getDeclaredConstructor(direction, int.class);
        Constructor<?> makeStep = step.getDeclaredConstructor(long.class, skipping);
        Object forward = Reflect.getStaticObjectField(direction, "FORWARD");
        Hooks.hookMethod(policy.getDeclaredMethod("a", state, direction), new HookCallback() {
            @Override protected void afterHookedMethod(HookParam p) {
                if (p.hasThrowable() || p.args[1] != forward) return;
                try {
                    settings.refresh();
                    int seconds = settings.feature(Feature.SEEK_STEP)?settings.seconds:10;
                    if (seconds == 5) {
                        Object oldState = Reflect.getObjectField(p.getResult(), "b");
                        int cumulative = Reflect.getIntField(oldState, "b") - 10 + 5;
                        p.setResult(makeStep.newInstance(five, makeState.newInstance(forward, cumulative)));
                    }
                    Log.i("XLowerSeek", "forwardStep seconds=" + seconds);
                } catch (Throwable e) { Log.w("XLowerSeek", "Step override unavailable; original retained"); }
            }
        });
    }
}
