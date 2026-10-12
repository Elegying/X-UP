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
    static void publish(Context context, Bundle snapshot) {
        Thread worker = new Thread(() -> {
            try (ContentProviderClient provider = context.getContentResolver().acquireUnstableContentProviderClient(Uri.parse("content://" + AUTHORITY))) {
                if (provider != null) provider.call("report", null, snapshot);
            } catch (Exception failure) { android.util.Log.w("XLowerSeek", "Compatibility status could not be shared"); }
        }, "X-UP compatibility status");
        worker.setDaemon(true); worker.start();
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
