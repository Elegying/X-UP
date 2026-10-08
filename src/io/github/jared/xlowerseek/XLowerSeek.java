package io.github.jared.xlowerseek;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class XLowerSeek extends io.github.libxposed.api.XposedModule {
    private static final String TAG = "XLowerSeek";
    private static final long SUPPORTED_VERSION = 312310001L;
    private boolean installed;
    private boolean failed;
    private final Map<Object, int[]> sizes = Collections.synchronizedMap(new WeakHashMap<>());

    private String processName;
    @Override public void onModuleLoaded(ModuleLoadedParam p) {
        processName=p.getProcessName();
        Hooks.initialize(this);
    }
    @Override public void onPackageReady(PackageReadyParam p) {
        if (!"com.twitter.android".equals(p.getPackageName())
                || !"com.twitter.android".equals(processName) || !p.isFirstPackage()) return;
        Hooks.findAndHookMethod(Application.class, "attach", Context.class, new HookCallback() {
            @Override protected void afterHookedMethod(HookParam p) {
                if (installed) return;
                installed = true;
                Context context = (Context) p.args[0];
                try {
                    long version = context.getPackageManager()
                        .getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
                    if (version != SUPPORTED_VERSION) {
                        report("未适配的 X 版本，保持原行为：" + version);
                        return;
                    }
                    RemoteSettings settings = new RemoteSettings(XLowerSeek.this.getRemotePreferences("settings"));
                    ClassLoader loader = context.getClassLoader();
                    try { install1231(loader,settings); }
                    catch (Throwable e) { fail(e); }
                    try { PlaybackStepHook.install(loader, settings); report("stepHook READY"); }
                    catch (Throwable e) { report("stepHook unavailable: " + e.getClass().getSimpleName()); }
                    try { ReplySortHook.install(loader, settings); report("replySortHook READY"); }
                    catch (Throwable e) { report("replySortHook unavailable: " + e.getClass().getSimpleName()); }
                    try { AutoTranslateHook.install((Application) p.thisObject, loader, settings); report("autoTranslateHook READY"); }
                    catch (Throwable e) { report("autoTranslateHook unavailable: " + e.getClass().getSimpleName()); }
                    try { PostTranslationBridge.install(loader, settings); report("postTranslationBridge READY"); }
                    catch (Throwable e) { report("postTranslationBridge unavailable: " + e.getClass().getSimpleName()); }
                    try { PostContextHook.install(loader); report("postContext READY"); }
                    catch(Throwable e){report("postContext unavailable: "+e.getClass().getSimpleName());}
                    try { GlobalTextTranslationHook.install((Application)p.thisObject, loader, settings); report("globalTextTranslation READY"); }
                    catch (Throwable e) { report("globalTextTranslation unavailable: " + e.getClass().getSimpleName()); }
                    try { VideoDownloadHook.install((Application) p.thisObject, loader,settings); report("downloadHook READY"); }
                    catch (Throwable e) { report("downloadHook unavailable: " + e.getClass().getSimpleName()); }
                    Hooks.findAndHookMethod(android.app.Activity.class, "onResume", new HookCallback() {
                        @Override protected void afterHookedMethod(HookParam param) { settings.refresh(); }
                    });
                    report("READY API=102 X=312310001 adapter=12.31.0 nativeSeekBurst=true");
                } catch (Throwable e) { fail(e); }
            }
        });
    }

    /** Version-specific adapter: future versions get separate adapters. */
    private void install1231(ClassLoader loader,RemoteSettings settings) throws Exception {
        Class<?> layout = Reflect.findClass("com.x.broadcast.controls.a2", loader);
        Class<?> gesture = Reflect.findClass("com.x.media.playback.v1", loader);
        Class<?> zoom = Reflect.findClass("me.saket.telephoto.zoomable.l0", loader);
        Class<?> continuation = Reflect.findClass("com.x.video.tab.t6", loader);
        Method layoutInvoke = layout.getDeclaredMethod("invoke", Object.class);
        Method doubleTap = gesture.getDeclaredMethod("b", zoom, long.class, continuation);
        layout.getDeclaredField("a"); layout.getDeclaredField("b"); gesture.getDeclaredField("d");
        Hooks.hookMethod(layoutInvoke, new HookCallback() {
            @Override protected void afterHookedMethod(HookParam p) {
                if (failed || p.hasThrowable()) return;
                try {
                    if (Reflect.getIntField(p.thisObject, "a") != 14) return;
                    long packed = Reflect.getLongField(p.args[0], "a");
                    Object state = Reflect.getObjectField(p.thisObject, "b");
                    sizes.put(state, new int[]{(int) (packed >> 32), (int) packed});
                } catch (Throwable e) { fail(e); }
            }
        });
        ContinuationTapHook.install(loader, ()->!failed&&settings.feature(Feature.LOWER_SEEK), sizes);
        Hooks.hookMethod(doubleTap, new HookCallback() {
            @Override protected void beforeHookedMethod(HookParam p) {
                if (failed||!settings.feature(Feature.LOWER_SEEK)) return;
                try {
                    Object state = Reflect.getObjectField(p.thisObject, "d");
                    int[] size = sizes.get(state);
                    if (size == null) { report("SKIP no current player size"); return; }
                    long point = (Long) p.args[1];
                    float x = Float.intBitsToFloat((int) (point >> 32));
                    float y = Float.intBitsToFloat((int) point);
                    boolean lower = GesturePolicy.isLower(x, y, size[0], size[1]);
                    if (lower) p.args[1] = GesturePolicy.lowerForwardOffset(point, size[0], size[1]);
                    Log.i(TAG, "doubleTap lower=" + lower);
                } catch (Throwable e) { fail(e); }
            }
        });
    }
    private void fail(Throwable e) {
        if (!failed) report("双击扩展已停用，保留原行为：" + e.getClass().getSimpleName());
        failed = true;
    }
    private static void report(String message) {
        Log.i(TAG, message);
        Hooks.log(TAG + ": " + message);
    }
}
