package io.github.jared.xlowerseek;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.*;

public final class DownloadSettingsActivity extends Activity {
    private TextView status, taskStatus;
    private Button cancel;
    private String taskId;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        public void run() { refresh(); main.postDelayed(this, 1000); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        SettingsUi ui = new SettingsUi(this, "视频下载", "在全屏视频页点击下载图标", true);
        LinearLayout task = ui.section("当前任务");
        taskStatus = ui.status(task, "暂无下载任务");
        cancel = ui.button(task, "取消下载", false);
        cancel.setOnClickListener(v -> { VideoDownloads.cancel(this, taskId); refresh(); });
        LinearLayout card = ui.section("保存");
        ui.info(card, "保存位置", "相册 · Movies/X"); ui.divider(card);
        ui.info(card, "画质", "自动选择可用的最高画质 MP4。"); ui.divider(card);
        ui.info(card, "下载进度", "点击下载中的视频图标可查看或取消，也可在本页取消下载。");
        LinearLayout notice = ui.section("通知"); status = ui.status(notice, "");
        ui.button(notice, "管理下载通知", true).setOnClickListener(v -> {
            if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
            else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        });
        ui.navigation(ui.section("帮助"), "下载限制", "", () -> ui.details("下载限制", "仅保存可直接获取的 MP4，暂不支持直播或只有分片的来源。单个视频上限 2 GB，一次下载一个视频。失败或取消会清理未完成文件。"));
    }
    @Override protected void onResume() { super.onResume(); main.removeCallbacks(tick); main.post(tick); }
    @Override protected void onPause() { main.removeCallbacks(tick); super.onPause(); }
    private void refresh() {
        Bundle task = VideoDownloads.status(this);
        taskId = task.getString("id");
        taskStatus.setText(task.getString("message", "暂无下载任务"));
        cancel.setVisibility(VideoDownloadState.active(task.getString("state")) ? android.view.View.VISIBLE : android.view.View.GONE);
        cancel.setEnabled(!VideoDownloadState.CANCELLING.equals(task.getString("state")));
        status.setText(getSystemService(android.app.NotificationManager.class).areNotificationsEnabled()
                ? "通知已开启" : "通知已关闭 · 仍可在本页或视频图标上查看和取消下载");
    }
    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) { super.onRequestPermissionsResult(r, p, g); refresh(); }
}
