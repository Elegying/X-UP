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
    private String feature="shared";
    private boolean cached, fallback;
    void beginFeature(String name){feature=name;cached=false;fallback=false;}
    boolean usedCache(){return cached;}
    boolean usedFallback(){return fallback;}
    void invalidateFeature(){
        SharedPreferences.Editor edit=cache.edit();
        for(String key:cache.getAll().keySet())if(key.startsWith(feature+"."))edit.remove(key);
        edit.apply();cached=false;fallback=false;
    }

    TargetResolver(Context context, long version, long request) {
        loader = context.getClassLoader();
        cache = context.getSharedPreferences("xup_compatibility_targets", Context.MODE_PRIVATE);
        ApplicationInfo app = context.getApplicationInfo();
        StringBuilder identity = new StringBuilder("5:").append(request).append(':').append(BuildConfig.VERSION_CODE).append(':').append(version);
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
        String key = feature + ".class." + id, saved = cache.getString(key, null);
        if (saved != null) try {
            Class<?> type = Class.forName(saved, false, loader);
            if (valid.test(type)) { cached=true;return type; }
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

    /** Try alternate contracts only when absent, never when a contract is ambiguous. */
    Class<?> alternatives(String id,Predicate<Class<?>> valid,ClassMatcher... variants) {
        String choice=feature+".strategy."+id;
        int saved=cache.getInt(choice,-1);
        if(saved>=0&&saved<variants.length) {
            try {
                Class<?> result=type(id+"."+saved,variants[saved],valid);
                fallback|=saved>0;return result;
            } catch(CompatibilityFailure absent) { if(absent.matches!=0)throw absent; }
            cache.edit().remove(choice).apply();
        }
        CompatibilityFailure last=null;
        for(int i=0;i<variants.length;i++) {
            if(i==saved)continue;
            try {
                Class<?> result=type(id+"."+i,variants[i],valid);
                cache.edit().putInt(choice,i).apply();fallback|=i>0;return result;
            } catch(CompatibilityFailure absent) {if(absent.matches!=0)throw absent;last=absent;}
        }
        throw last==null?new CompatibilityFailure(id,0):last;
    }

    Method method(String id, MethodMatcher matcher) { return method(id, matcher, method -> true); }
    Method method(String id, MethodMatcher matcher, Predicate<Method> valid) {
        String key = feature + ".method." + id, saved = cache.getString(key, null);
        if (saved != null) try {
            Method method = new DexMethod(saved).getMethodInstance(loader);
            if (valid.test(method)) { cached=true;method.setAccessible(true); return method; }
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
