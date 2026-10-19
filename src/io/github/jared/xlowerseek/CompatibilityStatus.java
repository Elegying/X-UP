package io.github.jared.xlowerseek;

import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded compatibility results, without visible text or account data. */
final class CompatibilityStatus {
    static final String AUTHORITY = "io.github.jared.xlowerseek.compatibility";
    static final Map<String,String> FEATURES = new LinkedHashMap<>();
    static {
        FEATURES.put("lower_seek", "下半区双击快进");
        FEATURES.put("seek_step", "快进步长");
        FEATURES.put("ad_block", "推广内容过滤");
        FEATURES.put("reply_sort", "最新评论优先");
        FEATURES.put("native_translate", "X 原生翻译");
        FEATURES.put("post_translation", "列表原生翻译");
        FEATURES.put("post_context", "上下文识别");
        FEATURES.put("identity_text", "用户名保护");
        FEATURES.put("local_text", "本地文字翻译");
        FEATURES.put("video", "视频下载与手势");
    }
    static Bundle snapshot(PackageInfo x) {
        Bundle result = new Bundle();
        result.putLong("x_version", x.getLongVersionCode());
        result.putLong("x_updated", x.lastUpdateTime);
        result.putInt("module_version", BuildConfig.VERSION_CODE);
        return result;
    }
    private static final long[] REPORT_DELAYS_MS = {0, 1000, 3000, 10000, 30000};
    interface RetryPause { void sleep(long millis) throws InterruptedException; }
    static boolean retryReport(java.util.concurrent.Callable<Boolean> attempt, RetryPause pause) {
        for (long delay : REPORT_DELAYS_MS) {
            try {
                if (Thread.currentThread().isInterrupted()) return false;
                if (delay > 0) pause.sleep(delay);
                if (Boolean.TRUE.equals(attempt.call())) return true;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception unavailable) {
                // Provider startup, binder death and rejected writes can recover on the next attempt.
            }
        }
        return false;
    }
    /** One report per X process; retry rounds are driven by foreground events, never a permanent timer. */
    static final class ReportState {
        private boolean running, saved;
        private long nextAllowed;
        synchronized boolean begin(long now) {
            if (running || saved || now < nextAllowed) return false;
            running = true;
            return true;
        }
        synchronized void finish(boolean acknowledged, long now) {
            saved = acknowledged;
            running = false;
            nextAllowed = now + 60_000;
        }
    }
    static Runnable reporter(Context context, Bundle snapshot) {
        Bundle report = new Bundle(snapshot);
        ReportState state = new ReportState();
        return () -> {
            if (!state.begin(android.os.SystemClock.elapsedRealtime())) return;
            Thread worker = new Thread(() -> {
                boolean saved = false;
                try {
                    saved = retryReport(() -> {
                        try (ContentProviderClient provider = context.getContentResolver().acquireUnstableContentProviderClient(Uri.parse("content://" + AUTHORITY))) {
                            if (provider == null) return false;
                            Bundle response = provider.call("report", null, report);
                            return response != null && response.getBoolean("saved", false);
                        }
                    }, Thread::sleep);
                    if (!saved) android.util.Log.w("XLowerSeek", "Compatibility status could not be shared after bounded retries; waiting for foreground retry");
                } finally {
                    state.finish(saved, android.os.SystemClock.elapsedRealtime());
                }
            }, "X-UP compatibility status");
            worker.setDaemon(true);
            try { worker.start(); }
            catch (RuntimeException failure) { state.finish(false, android.os.SystemClock.elapsedRealtime()); }
        };
    }
    static boolean save(Context context, Bundle report) {
        if (report == null || report.getInt("module_version", -1) != BuildConfig.VERSION_CODE) return false;
        try {
            PackageInfo x = context.getPackageManager().getPackageInfo("com.twitter.android", 0);
            if (report.getLong("x_version", -1) != x.getLongVersionCode() || report.getLong("x_updated", -1) != x.lastUpdateTime) return false;
            SharedPreferences.Editor edit = preferences(context).edit().clear()
                    .putLong("x_version", x.getLongVersionCode()).putLong("x_updated", x.lastUpdateTime)
                    .putInt("module_version", BuildConfig.VERSION_CODE).putLong("checked", System.currentTimeMillis());
            for (String key : FEATURES.keySet()) edit.putBoolean(key, report.getBoolean(key, false));
            return edit.commit();
        } catch (Exception failure) { return false; }
    }
    static SharedPreferences preferences(Context context) { return context.getSharedPreferences("compatibility_status", Context.MODE_PRIVATE); }
    static boolean current(SharedPreferences saved, PackageInfo x) {
        return saved.getInt("module_version", -1) == BuildConfig.VERSION_CODE
                && saved.getLong("x_version", -1) == x.getLongVersionCode()
                && saved.getLong("x_updated", -1) == x.lastUpdateTime;
    }
    private CompatibilityStatus() {}
}
