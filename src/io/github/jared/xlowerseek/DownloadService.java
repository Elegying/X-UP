package io.github.jared.xlowerseek;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class DownloadService extends Service {
    private static final String CHANNEL = "video_download";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Transfer active;
    private boolean destroyed;
    private int lastStartId;

    private static final class Transfer {
        final String id, url;
        volatile boolean cancelled;
        volatile HttpURLConnection connection;
        Transfer(String id, String url) { this.id = id; this.url = url; }
        synchronized void cancel() {
            if (cancelled) return;
            cancelled = true;
            HttpURLConnection c = connection;
            // disconnect() can wait for network I/O; do not block an Activity or provider caller.
            if (c != null) new Thread(c::disconnect, "X-UP-video-cancel").start();
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL, "视频保存", NotificationManager.IMPORTANCE_LOW));
    }
    private Notification notice(String id, String text, int progress, boolean running) {
        Notification.Builder b = new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("X 视频保存").setContentText(text).setOnlyAlertOnce(true).setOngoing(running);
        PendingIntent details = PendingIntent.getActivity(this, 2, new Intent(this, DownloadSettingsActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        b.setContentIntent(details);
        if (running) {
            b.setProgress(100, Math.max(0, progress), progress < 0);
            Intent intent = new Intent(this, DownloadService.class).setAction("cancel").putExtra("id", id)
                    .setData(Uri.parse("xup-download:" + id));
            PendingIntent cancel = PendingIntent.getService(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.addAction(new Notification.Action.Builder(null, "取消", cancel).build());
        }
        return b.build();
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        lastStartId = startId;
        if (intent == null) { if (active == null) stopSelf(startId); return START_NOT_STICKY; }
        String id = intent.getStringExtra("id");
        if ("cancel".equals(intent.getAction())) {
            VideoDownloads.cancel(this, id);
            if (active == null) stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (active != null) return START_NOT_STICKY;
        String url = intent.getStringExtra("url");
        Transfer job = new Transfer(id, url);
        if (!VideoDownloads.start(this, id, url, job::cancel)) { stopSelf(startId); return START_NOT_STICKY; }
        active = job;
        try { startForeground(42, notice(id, "正在连接…", -1, true)); }
        catch (RuntimeException unavailable) {
            active = null;
            VideoDownloads.finish(this, id, false, "系统暂不允许后台下载，请重试");
            stopSelf(startId); return START_NOT_STICKY;
        }
        worker.execute(() -> download(job));
        return START_NOT_STICKY;
    }
    private void download(Transfer job) {
        Uri pending = null;
        String result, url = job.url;
        boolean success = false;
        try {
            // Only the unfinished row from this module is eligible for cleanup.
            String old = getSharedPreferences("download", 0).getString("pending", null);
            if (old != null) { cleanupPending(Uri.parse(old)); getSharedPreferences("download", 0).edit().remove("pending").commit(); }
            HttpURLConnection c = null;
            boolean connected = false;
            for (int redirects = 0; redirects < 5; redirects++) {
                if (job.cancelled) throw new IOException("已取消下载");
                if (!DownloadPolicy.validUrl(url)) throw new IOException("来源地址不受支持");
                c = (HttpURLConnection) new URL(url).openConnection(); job.connection = c;
                c.setInstanceFollowRedirects(false); c.setConnectTimeout(20000); c.setReadTimeout(25000);
                c.setRequestProperty("User-Agent", "X-UP/" + BuildConfig.VERSION_NAME);
                c.setRequestProperty("Accept-Encoding", "identity");
                if (job.cancelled) throw new IOException("已取消下载");
                int status = c.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = c.getHeaderField("Location"); c.disconnect();
                    if (location == null) throw new IOException("下载地址已失效");
                    url = new URL(new URL(url), location).toString(); continue;
                }
                if (status != 200) throw new IOException("下载失败（HTTP " + status + "）");
                connected = true; break;
            }
            if (c == null || !connected) throw new IOException("重定向次数过多");
            if (job.cancelled) throw new IOException("已取消下载");
            long total = c.getContentLengthLong();
            if (total > 2L * 1024 * 1024 * 1024) throw new IOException("视频超过 2 GB，暂不支持保存");
            String name = "X_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + "_" + Integer.toHexString(url.hashCode()) + ".mp4";
            ContentValues values = new ContentValues(); values.put(MediaStore.Video.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/X");
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
            pending = getContentResolver().insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
            if (pending == null) throw new IOException("无法创建相册文件");
            if (!getSharedPreferences("download", 0).edit().putString("pending", pending.toString()).commit()) throw new IOException("无法记录下载状态，请重试");
            final long[] last = {0, 0};
            try (InputStream in = c.getInputStream(); OutputStream out = getContentResolver().openOutputStream(pending, "w")) {
                if (out == null) throw new IOException("无法写入相册");
                Mp4Transfer.copy(in, out, total, 2L * 1024 * 1024 * 1024, () -> job.cancelled, (count, length) -> {
                    long now = SystemClock.elapsedRealtime();
                    if (now - last[0] >= 150) {
                        last[0] = now;
                        int percent = DownloadProgress.percent(count, length);
                        String message = percent < 0 ? String.format(Locale.US, "下载中 · %.1f MB", count / 1048576.0) : "下载中 " + percent + "%";
                        VideoDownloads.progress(this, job.id, message, percent);
                        if (now - last[1] >= 700) { last[1] = now; notifySafely(42, notice(job.id, message, percent, true)); }
                    }
                });
            }
            if (job.cancelled) throw new IOException("已取消下载");
            values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0);
            if (getContentResolver().update(pending, values, null, null) != 1) throw new IOException("相册保存未完成");
            getSharedPreferences("download", 0).edit().remove("pending").commit();
            pending = null; success = true; result = "已保存到相册 · Movies/X";
        } catch (Exception e) {
            result = job.cancelled ? "已取消下载" : "保存失败：" + (e instanceof IOException ? e.getMessage() : "请检查网络或可用空间");
        } finally {
            if (job.connection != null) { job.connection.disconnect(); job.connection = null; }
            if (pending != null) try { cleanupPending(pending); getSharedPreferences("download", 0).edit().remove("pending").commit(); } catch (Exception ignored) {}
        }
        final String message = result;
        final boolean saved = success;
        new Handler(Looper.getMainLooper()).post(() -> {
            active = null;
            VideoDownloads.finish(this, job.id, saved, message);
            if (!destroyed) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                notifySafely(43, notice(job.id, message, saved ? 100 : -1, false));
                stopSelf(lastStartId);
            }
        });
    }
    private void cleanupPending(Uri uri) throws IOException {
        try (android.database.Cursor c = getContentResolver().query(uri, new String[]{MediaStore.Video.Media.IS_PENDING}, null, null, null)) {
            if (c == null) throw new IOException("暂时无法清理上次下载，请重试");
            if (c.moveToFirst() && c.getInt(0) == 1 && getContentResolver().delete(uri, null, null) != 1) throw new IOException("暂时无法清理上次下载，请重试");
        }
    }
    private void notifySafely(int id, Notification n) { try { getSystemService(NotificationManager.class).notify(id, n); } catch (RuntimeException ignored) {} }
    @Override public void onDestroy() {
        destroyed = true;
        if (active != null) VideoDownloads.cancel(this, active.id);
        // Let even a queued transfer enter finally and retire its task after cleanup.
        worker.shutdown(); super.onDestroy();
    }
    @Override public void onTimeout(int startId, int fgsType) {
        if (active != null) VideoDownloads.cancel(this, active.id);
        stopSelf();
    }
}
