package io.github.jared.xlowerseek;
import java.lang.reflect.*;import java.util.*;
/** Each changed timeline needs its own identity, including an entirely filtered empty list. */
final class AdImmutableList {
    private final Constructor<?> create;private final Method append;
    AdImmutableList(Class<?> vector)throws Exception{
        create=vector.getDeclaredConstructor(Object[].class);create.setAccessible(true);
        Method candidate=null;
        for(Method method:vector.getDeclaredMethods())if(!method.isBridge()&&!Modifier.isStatic(method.getModifiers())
            &&Arrays.equals(method.getParameterTypes(),new Class<?>[]{Collection.class})&&List.class.isAssignableFrom(method.getReturnType())){
            if(candidate!=null)throw new NoSuchMethodException("Ambiguous immutable append");candidate=method;
        }
        if(candidate==null)throw new NoSuchMethodException("Immutable append");append=candidate;append.setAccessible(true);
    }
    Object copy(Collection<?> values)throws Exception{
        Object empty=create.newInstance((Object)new Object[0]);
        // Do not use X's shared empty singleton: two all-ad timelines must restore independently.
        return append.invoke(empty,values);
    }
}
