package io.github.jared.xlowerseek;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Rewrites only callbacks constructed by the native video seek path. */
final class ContinuationTapHook {
    static HookCallback callback(BooleanSupplier enabled, Map<Object,int[]> sizes,
            Map<Object,Object> states, Constructor<?> box, Field packedPoint) {
        return new HookCallback() {
            @Override protected void beforeHookedMethod(HookParam p) throws Throwable {
                if(!enabled.getAsBoolean()||!box.getDeclaringClass().isInstance(p.args[0]))return;
                Object state=states.get(p.thisObject);
                int[] size=state==null?null:sizes.get(state);
                if(size==null)return;
                long point=packedPoint.getLong(p.args[0]);
                long mapped=GesturePolicy.lowerForwardOffset(point,size[0],size[1]);
                if(mapped!=point)p.args[0]=box.newInstance(mapped);
                // Original invoke runs exactly once; X still owns active burst detection and timeout.
            }
        };
    }
}
