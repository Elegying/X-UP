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
                    long request=getRemotePreferences("settings").getLong("compatibility_rescan",0);
                    Bundle result = CompatibilityStatus.snapshot(x);
                    result.putLong("scan_request",request);
                    RemoteSettings settings = new RemoteSettings(getRemotePreferences("settings"));
                    try (TargetResolver resolver = new TargetResolver(context, x.getLongVersionCode(), request)) {
                        LowerSeekHook.Binding[] lower={null};
                        install(resolver, result, "lower_seek", () -> lower[0]=LowerSeekHook.installBase(resolver, settings));
                        if(result.getBoolean("lower_seek"))
                            install(resolver,result,"continuation_seek",()->lower[0].installContinuation(resolver,settings));
                        else result.putString("detail.continuation_seek","依赖的基础双击未加载");
                        install(resolver, result, "seek_step", () -> PlaybackStepHook.install(resolver, settings));
                        install(resolver, result, "ad_block", () -> AdBlockHook.install(resolver, settings));
                        install(resolver, result, "reply_sort", () -> ReplySortHook.install(resolver, settings));
                        install(resolver, result, "native_translate", () -> AutoTranslateHook.install(app, resolver, settings));
                        install(resolver, result, "post_translation", () -> PostTranslationBridge.install(resolver, settings));
                        install(resolver, result, "post_context", () -> PostContextHook.install(resolver));
                        install(resolver, result, "identity_text", () -> IdentityTextHook.install(resolver));
                        // Identity protection must be available before translating display text.
                        if (result.getBoolean("identity_text"))
                            install(resolver, result, "local_text", () -> GlobalTextTranslationHook.install(app, resolver, settings));
                        else result.putString("detail.local_text","依赖的用户名保护未加载");
                        install(resolver, result, "video", () -> VideoDownloadHook.install(app, resolver, settings));
                    }
                    CompatibilityRuntime runtime=new CompatibilityRuntime(context,result);
                    Hooks.observeFailures(runtime::failed);
                    Runnable publishCompatibility = CompatibilityStatus.reporter(context, result);
                    Hooks.findAndHookMethod(android.app.Activity.class, "onResume", new HookCallback() {
                        @Override protected void afterHookedMethod(HookParam p) { settings.refresh(); publishCompatibility.run(); }
                    });
                    int loaded = 0;
                    for (String key : CompatibilityStatus.FEATURES.keySet()) if (result.getBoolean(key)) loaded++;
                    report("COMPAT X=" + x.getLongVersionCode() + " loaded=" + loaded + "/" + CompatibilityStatus.FEATURES.size());
                    publishCompatibility.run();
                    runtime.notifyPartial();
                } catch (Throwable error) { report("compat initialization unavailable: " + error.getClass().getSimpleName()); }
            }
        });
    }
    private static void install(TargetResolver resolver,Bundle result,String key,Hooks.Installation installation) {
        resolver.beginFeature(key);
        boolean rescanned=false;
        for(int attempt=0;attempt<2;attempt++) {
            try {
                int count=Hooks.installAtomically(key,installation);
                result.putBoolean(key,true);
                result.putInt("hooks."+key,count);
                String mode=rescanned?"已自动清理缓存并恢复":resolver.usedCache()?"缓存校验通过":"结构扫描通过";
                if(resolver.usedFallback())mode+=" · 备用匹配路径";
                result.putString("detail."+key,mode);
                report("compat "+key+" LOADED hooks="+count+" rescan="+rescanned);
                return;
            } catch(Throwable error) {
                if(attempt==0&&CompatibilityFailure.retry(resolver.usedCache(),error)) {
                    resolver.invalidateFeature();rescanned=true;continue;
                }
                result.putBoolean(key,false);
                result.putString("detail."+key,CompatibilityFailure.describe(error)+(rescanned?"；重新扫描后仍不可用":""));
                report("compat "+key+" unavailable: "+error.getClass().getSimpleName());
                return;
            }
        }
    }
    private static void report(String message) { Log.i(TAG, message); Hooks.log(TAG + ": " + message); }
}
