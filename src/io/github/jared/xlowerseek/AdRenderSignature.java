package io.github.jared.xlowerseek;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/** Known URT list boundaries; Compose may omit the default-argument mask. */
final class AdRenderSignature {
    static int composerIndex(Method method) {
        return composerIndex(method.getModifiers(),method.getReturnType(),method.getParameterTypes());
    }

    static int composerIndex(int modifiers,Class<?> result,Class<?>[] p) {
        if(!Modifier.isStatic(modifiers)||result!=void.class||p.length<=8
            ||!List.class.isAssignableFrom(p[0])||!p[1].isEnum()
            ||p[2]!=boolean.class||p[3]!=boolean.class)return -1;
        // X 12.35's list boundary has two changed masks and no default mask;
        // older releases include a third int. Do not accept arbitrary tails.
        for(int masks=2;masks<=3;masks++) {
            int composer=p.length-masks-1;
            if(!p[composer].getName().equals("androidx.compose.runtime.Composer"))continue;
            boolean valid=true;
            for(int i=composer+1;i<p.length;i++)if(p[i]!=int.class)valid=false;
            if(valid)return composer;
        }
        return -1;
    }
}
