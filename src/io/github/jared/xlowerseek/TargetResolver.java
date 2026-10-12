package io.github.jared.xlowerseek;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.function.Predicate;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.wrap.DexMethod;

/** APK-bound discovery cache. Semantic queries must produce exactly one structurally valid target. */
final class TargetResolver implements AutoCloseable {
    final ClassLoader loader;
    private final SharedPreferences cache;
    private DexKitBridge bridge;
    private boolean closed;

    TargetResolver(Context context, long version) {
        loader = context.getClassLoader();
        cache = context.getSharedPreferences("xup_compatibility_targets", Context.MODE_PRIVATE);
        ApplicationInfo app = context.getApplicationInfo();
        StringBuilder identity = new StringBuilder("3:").append(BuildConfig.VERSION_CODE).append(':').append(version);
        appendPath(identity, app.sourceDir);
        if (app.splitSourceDirs != null) for (String split : app.splitSourceDirs) appendPath(identity, split);
        String key = identity.toString();
        if (!key.equals(cache.getString("identity", ""))) cache.edit().clear().putString("identity", key).apply();
    }

    private static void appendPath(StringBuilder identity, String path) {
        File file = new File(path);
        identity.append('|').append(path).append(':').append(file.length()).append(':').append(file.lastModified());
    }

    DexKitBridge dex() {
        if (closed) throw new IllegalStateException("Compatibility discovery has finished");
        if (bridge == null) {
            System.loadLibrary("dexkit");
            // Inspect the target class loader, including installed feature splits.
            bridge = DexKitBridge.create(loader, false);
            bridge.setThreadNum(2);
        }
        return bridge;
    }

    Class<?> type(String id, ClassMatcher matcher) { return type(id, matcher, type -> true); }
    Class<?> type(String id, ClassMatcher matcher, Predicate<Class<?>> valid) {
        String key = "class." + id, saved = cache.getString(key, null);
        if (saved != null) try {
            Class<?> type = Class.forName(saved, false, loader);
            if (valid.test(type)) return type;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {}
        cache.edit().remove(key).apply();
        ArrayList<Class<?>> candidates = new ArrayList<>();
        for (ClassData data : dex().findClass(FindClass.create().matcher(matcher))) {
            try { candidates.add(data.getInstance(loader)); }
            catch (ClassNotFoundException | LinkageError ignored) {}
        }
        Class<?> target = CompatibilitySelection.unique(id, candidates, valid);
        cache.edit().putString(key, target.getName()).apply();
        return target;
    }

    Method method(String id, MethodMatcher matcher) { return method(id, matcher, method -> true); }
    Method method(String id, MethodMatcher matcher, Predicate<Method> valid) {
        String key = "method." + id, saved = cache.getString(key, null);
        if (saved != null) try {
            Method method = new DexMethod(saved).getMethodInstance(loader);
            if (valid.test(method)) { method.setAccessible(true); return method; }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {}
        cache.edit().remove(key).apply();
        ArrayList<Method> candidates = new ArrayList<>();
        for (MethodData data : dex().findMethod(FindMethod.create().matcher(matcher))) {
            if (!data.isMethod()) continue;
            try { candidates.add(data.getMethodInstance(loader)); }
            catch (NoSuchMethodException | LinkageError ignored) {}
        }
        Method target = CompatibilitySelection.unique(id, candidates, valid);
        target.setAccessible(true);
        cache.edit().putString(key, new DexMethod(target).serialize()).apply();
        return target;
    }

    @Override public void close() {
        closed = true;
        if (bridge != null) { bridge.close(); bridge = null; }
    }
}
