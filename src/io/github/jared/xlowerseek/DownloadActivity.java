package io.github.jared.xlowerseek;

import android.app.Activity;
import android.content.Intent;

public final class DownloadActivity extends Activity {
    private boolean started;
    @Override protected void onResume() {
        super.onResume();
        if (started) return;
        started = true;
        String id;
        String url;
        try {
            id = getIntent().getStringExtra("id");
            url = VideoDownloads.consume(this, id, getIntent().getStringExtra("capability"));
        } catch (RuntimeException invalid) { finish(); return; }
        if (url == null) { finish(); return; }
        try {
            startForegroundService(new Intent(this, DownloadService.class).putExtra("id", id).putExtra("url", url));
        } catch (RuntimeException unavailable) {
            VideoDownloads.finish(this, id, false, "无法启动下载，请重试");
        }
        finish();
    }
}
