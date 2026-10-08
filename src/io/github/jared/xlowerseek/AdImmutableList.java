package io.github.jared.xlowerseek;
import java.lang.reflect.*;import java.util.*;
/** Each changed timeline needs its own identity, including an entirely filtered empty list. */
final class AdImmutableList {
    private final Constructor<?> create;private final Method append;
    AdImmutableList(Class<?> vector)throws Exception{create=vector.getDeclaredConstructor(Object[].class);create.setAccessible(true);append=vector.getDeclaredMethod("b",Collection.class);append.setAccessible(true);}
    Object copy(Collection<?> values)throws Exception{
        Object empty=create.newInstance((Object)new Object[0]);
        // Do not use X's shared empty singleton: two all-ad timelines must restore independently.
        return append.invoke(empty,values);
    }
}
