package io.github.jared.xlowerseek;

import java.lang.reflect.*;
import java.util.*;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

/** Compose contracts are identified by semantic strings and types, never obfuscated names. */
final class ComposeTargets {
    final Class<?> annotated, range, spanStyle, paragraphStyle, textWrapper, basicText, inputs;
    final Field annotations, rangeItem, rangeTag, rangeStart, rangeEnd;
    final Constructor<?> makeAnnotated, makeRange;
    final Method mutableState;

    ComposeTargets(TargetResolver resolver) throws ReflectiveOperationException {
        annotated = resolver.type("compose.annotated", in("androidx.compose.ui.text.")
                .usingStrings("Paragraph overlap not allowed"), CharSequence.class::isAssignableFrom);
        range = resolver.type("compose.range", in("androidx.compose.ui.text.")
                .usingEqStrings("Range(item=", ", start=", ", end=", ", tag="));
        spanStyle = resolver.type("compose.span", in("androidx.compose.ui.text.").usingStrings("SpanStyle(color="));
        paragraphStyle = resolver.type("compose.paragraph", in("androidx.compose.ui.text.").usingStrings("ParagraphStyle(textAlign="));
        annotations = singleField(annotated, List.class);
        rangeItem = singleField(range, Object.class);
        rangeTag = singleField(range, String.class);
        makeAnnotated = annotated.getDeclaredConstructor(List.class, String.class);
        makeRange = range.getDeclaredConstructor(String.class, int.class, int.class, Object.class);
        makeAnnotated.setAccessible(true); makeRange.setAccessible(true);
        // Witness the constructor's range contract without relying on declaration order.
        Object marker = new Object(), sample = makeRange.newInstance("xup-probe", 17, 29, marker);
        if (rangeItem.get(sample) != marker || !"xup-probe".equals(rangeTag.get(sample)))
            throw new IllegalStateException("Annotated range contract changed");
        rangeStart = rangeOffset(range, sample, 17);
        rangeEnd = rangeOffset(range, sample, 29);
        mutableState = mutableState(resolver);
        textWrapper = resolver.type("compose.linkedText", in("com.x.ui.common.ports.text.").usingStrings("linked-text"),
                type -> Arrays.stream(type.getDeclaredMethods()).anyMatch(method -> isDisplayMethod(method, annotated, false)));
        basicText = resolver.type("compose.basicText", in("androidx.compose.foundation.text.")
                .usingStrings("androidx.compose.foundation.text.inlineContent"), type -> displayMethods(type, annotated).size() == 2);
        // BasicTextField(String, onValueChange, Modifier, ...). Guard its state-based overloads too.
        Method input = resolver.method("compose.input", MethodMatcher.create()
                .declaredClass(in("androidx.compose.foundation.text."))
                .modifiers(Modifier.STATIC).returnType(void.class), method -> {
                    Class<?>[] p = method.getParameterTypes();
                    return p.length > 3 && p[0] == String.class
                            && p[1].getName().equals("kotlin.jvm.functions.Function1")
                            && p[2].getName().equals("androidx.compose.ui.Modifier") && hasComposer(method);
                });
        inputs = input.getDeclaringClass();
    }

    static ClassMatcher in(String prefix) { return ClassMatcher.create().className(prefix, StringMatchType.StartsWith); }
    static Method mutableState(TargetResolver resolver) {
        Class<?> state = resolver.type("compose.mutableStateType", in("androidx.compose.runtime.")
                .usingStrings("MutableState(value=", "Only known types of MutableState's SnapshotMutationPolicy are supported"));
        return resolver.method("compose.mutableStateFactory", MethodMatcher.create()
                .declaredClass(in("androidx.compose.runtime.")).modifiers(Modifier.STATIC)
                .returnType(state).paramTypes(Object.class));
    }

    static Method rememberedScope(TargetResolver resolver) {
        Class<?> scope = resolver.type("compose.coroutineScope", in("kotlinx.coroutines.")
                .modifiers(Modifier.INTERFACE).addMethod(MethodMatcher.create()
                        .name("getCoroutineContext").paramCount(0).returnType("kotlin.coroutines.CoroutineContext")));
        return resolver.method("compose.scopeFactory", MethodMatcher.create()
                .declaredClass(in("androidx.compose.runtime.")).modifiers(Modifier.STATIC)
                .returnType(scope).paramTypes("androidx.compose.runtime.Composer"));
    }

    static boolean hasComposer(Method method) {
        for (Class<?> p : method.getParameterTypes()) if (p.getName().equals("androidx.compose.runtime.Composer")) return true;
        return false;
    }

    static List<Method> displayMethods(Class<?> type, Class<?> annotated) {
        List<Method> result = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (isDisplayMethod(method, annotated, true)) result.add(method);
        }
        return result;
    }

    static boolean isDisplayMethod(Method method, Class<?> annotated, boolean foundation) {
        Class<?>[] p = method.getParameterTypes();
        if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class
                || !hasComposer(method) || p.length < 2 || (p[0] != String.class && p[0] != annotated)) return false;
        return foundation ? p[1].getName().equals("androidx.compose.ui.Modifier")
                : !(p[0] == String.class && List.class.isAssignableFrom(p[1]));
    }

    private static Field singleField(Class<?> type, Class<?> fieldType) {
        Field field = CompatibilitySelection.unique(type.getName(), Arrays.asList(type.getDeclaredFields()),
                f -> !Modifier.isStatic(f.getModifiers()) && f.getType() == fieldType);
        field.setAccessible(true); return field;
    }

    private static Field rangeOffset(Class<?> type, Object sample, int expected) {
        Field field = CompatibilitySelection.unique("range offset", Arrays.asList(type.getDeclaredFields()), f -> {
            if (Modifier.isStatic(f.getModifiers()) || f.getType() != int.class) return false;
            try { f.setAccessible(true); return f.getInt(sample) == expected; }
            catch (IllegalAccessException e) { return false; }
        });
        field.setAccessible(true); return field;
    }
}
