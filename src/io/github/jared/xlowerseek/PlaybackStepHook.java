package io.github.jared.xlowerseek;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

/** Adjust X's native seek executor, including taps inside its current seek burst. */
final class PlaybackStepHook {
    static void install(TargetResolver resolver, RemoteSettings settings) throws Exception {
        Class<?> skipping=resolver.type("video.skip-state",ClassMatcher.create().usingStrings("Skipping(direction=",", skippedSeconds="));
        Class<?> step=resolver.type("video.skip-step",ClassMatcher.create().usingStrings("DoubleTapSkipStep(amount="));
        Class<?> direction=VideoMembers.field(skipping,f->f.getType().isEnum()).getType();
        Object forward=Enum.valueOf(direction.asSubclass(Enum.class),"FORWARD");
        Constructor<?> stateConstructor=VideoMembers.constructor(skipping,direction,int.class);
        Constructor<?> stepConstructor=VideoMembers.constructor(step,long.class,skipping);
        Method execute=resolver.method("video.skip-executor",MethodMatcher.create().paramCount(8).returnType(boolean.class),
            m->Modifier.isStatic(m.getModifiers())&&m.getParameterTypes()[6]==direction&&m.getParameterTypes()[7]==step);
        Class<?> unit=Reflect.findClass("kotlin.time.DurationUnit",resolver.loader);
        Class<?> durationKt=Reflect.findClass("kotlin.time.DurationKt",resolver.loader);
        Class<?> duration=Reflect.findClass("kotlin.time.Duration",resolver.loader);
        Method encode=VideoMembers.method(durationKt,long.class,int.class,unit);
        Method decode=resolver.method("video.duration-seconds",MethodMatcher.create().declaredClass(duration)
            .paramTypes(long.class,unit).returnType(long.class).usingNumbers(Long.MAX_VALUE,Long.MIN_VALUE));
        Object secondsUnit=Enum.valueOf(unit.asSubclass(Enum.class),"SECONDS");
        long probe=((Number)encode.invoke(null,10,secondsUnit)).longValue();
        if(((Number)decode.invoke(null,probe,secondsUnit)).longValue()!=10)throw new IllegalStateException("Duration contract changed");
        Hooks.hookMethod(execute,SeekStepAdapter.callback(()->{
            settings.refresh();return settings.feature(Feature.SEEK_STEP)?settings.seconds:10;
        },forward,stateConstructor,stepConstructor,encode,decode,secondsUnit));
    }
}
