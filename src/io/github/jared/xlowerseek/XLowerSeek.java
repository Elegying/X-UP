package io.github.jared.xlowerseek;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.util.Log;

/** Load independently verified capabilities, rather than rejecting an entire X version. */
public final class XLowerSeek extends io.github.libxposed.api.XposedModule {
    private static final String TAG = "XLowerSeek";
    private boolean installed;
    private String processName;
    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
        Hooks.initialize(this);
    }
    @Override public void onPackageReady(PackageReadyParam param) {
        if (!"com.twitter.android".equals(param.getPackageName())
                || !"com.twitter.android".equals(processName) || !param.isFirstPackage()) return;
        Hooks.findAndHookMethod(Application.class, "attach", Context.class, new HookCallback() {
            @Override protected void afterHookedMethod(HookParam call) {
                if (installed || call.hasThrowable()) return;
                installed = true;
                Context context = (Context) call.args[0];
                Application app = (Application) call.thisObject;
                try {
                    PackageInfo x = context.getPackageManager().getPackageInfo("com.twitter.android", 0);
                    Bundle result = CompatibilityStatus.snapshot(x);
                    RemoteSettings settings = new RemoteSettings(getRemotePreferences("settings"));
                    try (TargetResolver resolver = new TargetResolver(context, x.getLongVersionCode())) {
                        install(result, "lower_seek", () -> LowerSeekHook.install(resolver, settings));
                        install(result, "seek_step", () -> PlaybackStepHook.install(resolver, settings));
                        install(result, "ad_block", () -> AdBlockHook.install(resolver, settings));
                        install(result, "reply_sort", () -> ReplySortHook.install(resolver, settings));
                        install(result, "native_translate", () -> AutoTranslateHook.install(app, resolver, settings));
                        install(result, "post_translation", () -> PostTranslationBridge.install(resolver, settings));
                        install(result, "post_context", () -> PostContextHook.install(resolver));
                        install(result, "identity_text", () -> IdentityTextHook.install(resolver));
                        // Identity protection must be available before translating display text.
                        if (result.getBoolean("identity_text"))
                            install(result, "local_text", () -> GlobalTextTranslationHook.install(app, resolver, settings));
                        else report("compat local_text unavailable: identity protection unavailable");
                        install(result, "video", () -> VideoDownloadHook.install(app, resolver, settings));
                    }
                    Runnable publishCompatibility = CompatibilityStatus.reporter(context, result);
                    Hooks.findAndHookMethod(android.app.Activity.class, "onResume", new HookCallback() {
                        @Override protected void afterHookedMethod(HookParam p) { settings.refresh(); publishCompatibility.run(); }
                    });
                    int loaded = 0;
                    for (String key : CompatibilityStatus.FEATURES.keySet()) if (result.getBoolean(key)) loaded++;
                    report("COMPAT X=" + x.getLongVersionCode() + " loaded=" + loaded + "/" + CompatibilityStatus.FEATURES.size());
                    publishCompatibility.run();
                } catch (Throwable error) { report("compat initialization unavailable: " + error.getClass().getSimpleName()); }
            }
        });
    }
    private static void install(Bundle result, String key, Hooks.Installation installation) {
        try {
            int count = Hooks.installAtomically(installation);
            result.putBoolean(key, true);
            report("compat " + key + " LOADED hooks=" + count);
        } catch (Throwable error) {
            result.putBoolean(key, false);
            report("compat " + key + " unavailable: " + error.getClass().getSimpleName() + " " + error.getMessage());
        }
    }
    private static void report(String message) { Log.i(TAG, message); Hooks.log(TAG + ": " + message); }
}
