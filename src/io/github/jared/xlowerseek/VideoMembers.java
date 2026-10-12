package io.github.jared.xlowerseek;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.function.Predicate;

/** Structural member lookup. A changed or ambiguous contract must never guess a target. */
final class VideoMembers {
    private VideoMembers() {}

    static Field field(Class<?> owner, Class<?> type) {
        return field(owner, f -> f.getType() == type);
    }

    static Field field(Class<?> owner, Predicate<Field> matches) {
        Field result = null;
        for (Field f : owner.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            if (!matches.test(f)) continue;
            if (result != null) throw new IllegalArgumentException("Ambiguous video field in " + owner.getName());
            result = f;
        }
        if (result == null) throw new IllegalArgumentException("Missing video field in " + owner.getName());
        result.setAccessible(true);
        return result;
    }

    static Method method(Class<?> owner, Predicate<Method> matches) {
        Method result = null;
        for (Method m : owner.getDeclaredMethods()) {
            if (m.isBridge() || !matches.test(m)) continue;
            if (result != null) throw new IllegalArgumentException("Ambiguous video method in " + owner.getName());
            result = m;
        }
        if (result == null) throw new IllegalArgumentException("Missing video method in " + owner.getName());
        result.setAccessible(true);
        return result;
    }

    static Method method(Class<?> owner, Class<?> result, Class<?>... parameters) {
        return method(owner, m -> m.getReturnType() == result && Arrays.equals(m.getParameterTypes(), parameters));
    }

    static Constructor<?> constructor(Class<?> owner, Class<?>... parameters) throws NoSuchMethodException {
        Constructor<?> constructor = owner.getDeclaredConstructor(parameters);
        constructor.setAccessible(true);
        return constructor;
    }

    static Object read(Field field, Object receiver) {
        try { return field.get(receiver); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    static Object invoke(Method method, Object receiver, Object... args) {
        try { return method.invoke(receiver, args); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
}
