package io.github.jared.xlowerseek;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Extend X's native continuation tap. X still owns the active burst, timeout and seek. */
final class ContinuationTapHook {
    static void install(ClassLoader loader, BooleanSupplier enabled, Map<Object,int[]> sizes) throws Exception {
        Class<?> tap = Reflect.findClass("com.x.americanfootball.game.ui.d2", loader);
        Class<?> point = Reflect.findClass("androidx.compose.ui.geometry.b", loader);
        tap.getDeclaredField("a"); tap.getDeclaredField("d"); tap.getDeclaredField("e");
        point.getDeclaredField("a");
        Constructor<?> box = point.getDeclaredConstructor(long.class);
        box.setAccessible(true);
        Hooks.hookMethod(tap.getDeclaredMethod("invoke", Object.class), callback(enabled,sizes,box));
    }

    static HookCallback callback(BooleanSupplier enabled, Map<Object,int[]> sizes, Constructor<?> box) {
        return new HookCallback() {
            private boolean unavailable;
            @Override protected void beforeHookedMethod(HookParam p) {
                if(unavailable || !enabled.getAsBoolean()) return;
                try {
                    // This synthetic class also implements unrelated UI callbacks.
                    if(Reflect.getIntField(p.thisObject,"a")!=1 || Reflect.getObjectField(p.thisObject,"d")==null) return;
                    int[] size=sizes.get(Reflect.getObjectField(p.thisObject,"e"));
                    if(size==null || !box.getDeclaringClass().isInstance(p.args[0])) return;
                    long point=Reflect.getLongField(p.args[0],"a");
                    long mapped=GesturePolicy.lowerForwardOffset(point,size[0],size[1]);
                    if(mapped!=point) p.args[0]=box.newInstance(mapped);
                    // Never invoke the callback or seek ourselves: original invoke runs exactly once.
                    // Its Boolean continuation callback returns false outside a native seek burst,
                    // preserving the ordinary single-tap action even in the lower half.
                } catch(Throwable e) {
                    unavailable=true;
                    Hooks.log("XLowerSeek: continuation extension unavailable; original retained");
                }
            }
        };
    }
}
