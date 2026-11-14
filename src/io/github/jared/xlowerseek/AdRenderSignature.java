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
        int list=listIndex(p);
        if(!Modifier.isStatic(modifiers)||result!=void.class||list<0)return -1;
        // The Lazy-prefixed 12.42 boundary has one changed mask after R8 optimization.
        int minMasks=list==1?1:2;
        for(int masks=minMasks;masks<=3;masks++) {
            int composer=p.length-masks-1;
            if(composer<=list+3||!p[composer].getName().equals("androidx.compose.runtime.Composer"))continue;
            boolean valid=true;
            for(int i=composer+1;i<p.length;i++)if(p[i]!=int.class)valid=false;
            if(valid)return composer;
        }
        return -1;
    }

    static int listIndex(Class<?>[] p) {
        if(p.length<=8)return -1;
        int index=p[0].getName().equals("kotlin.Lazy")?1:0;
        return List.class.isAssignableFrom(p[index])&&p[index+1].isEnum()
            &&p[index+2]==boolean.class&&p[index+3]==boolean.class?index:-1;
    }
    static int changedFlags(int flags,int listIndex) {
        int shift=3*listIndex;
        return (flags&~(14<<shift))|(5<<shift);
    }
}
