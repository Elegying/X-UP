package io.github.jared.xlowerseek;

import android.content.ContentProviderClient;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Foreground-only reconciliation; no transfer is retried without consulting the service. */
final class VideoDownloadClient {
    interface Transport { Bundle call(String method, String argument) throws Exception; }
    interface Listener {
        void changed(boolean busy, int progress, String message);
        void launch(String id, String capability);
        void completed(String message);
    }
    private final Transport transport;
    private final Executor ipc;
    private final Handler main;
    private final Listener listener;
    private boolean visible, querying, submitting, busy, cancelSubmission, uncertain;
    private String id, submittedId;
    private long submission;
    private final Runnable poll = this::refresh;

    VideoDownloadClient(Context context, Listener listener) {
        this((method, argument) -> {
            try (ContentProviderClient provider = context.getContentResolver().acquireUnstableContentProviderClient(
                    Uri.parse("content://io.github.jared.xlowerseek.translation"))) {
                if (provider == null) throw new IllegalStateException("Provider unavailable");
                return provider.call(method, argument, null);
            }
        }, Executors.newSingleThreadExecutor(), new Handler(Looper.getMainLooper()), listener);
    }
    VideoDownloadClient(Transport transport, Executor ipc, Handler main, Listener listener) {
        this.transport = transport; this.ipc = ipc; this.main = main; this.listener = listener;
    }
    void visible(boolean value) {
        visible = value; main.removeCallbacks(poll);
        if (visible) refresh();
    }
    void refresh() {
        if (!visible || querying || submitting) return;
        querying = true;
        request("video.status", null, result -> {
            querying = false;
            accept(result);
            schedule();
        });
    }
    void start(String url) {
        if (!visible || submitting) return;
        submitting = true; cancelSubmission = false; submittedId = null; submission++;
        listener.changed(true, -1, "正在确认下载任务…");
        request("video.prepare", url, result -> {
            submitting = false;
            if (result == null) {
                unavailable();
                if (visible) listener.completed("暂时无法连接下载服务，请先打开插件设置页，再重试");
                schedule(); return;
            }
            submittedId = result.getString("id");
            accept(result);
            String capability = result.getString("capability");
            if (cancelSubmission) cancelTask(submittedId);
            else if (capability != null) {
                if (!visible) cancelTask(submittedId);
                else try { listener.launch(result.getString("id"), capability); }
                catch (RuntimeException failure) {
                    cancelTask(submittedId); listener.completed("请先打开插件设置页，再重试下载");
                }
            }
            schedule();
        });
    }
    Runnable cancellation() {
        String task = id;
        boolean pending = submitting;
        long request = submission;
        return () -> {
            if (!pending) cancelTask(task);
            else if (submission == request) {
                if (submitting) cancelSubmission = true;
                else cancelTask(submittedId);
            }
        };
    }
    void cancel() { cancellation().run(); }
    private void cancelTask(String task) {
        if (task == null) { refresh(); return; }
        request("video.cancel", task, result -> {
            accept(result);
            if (result == null && visible) listener.completed("取消状态尚未确认，请稍后查看或重试");
            schedule();
        });
    }
    private interface Reply { void receive(Bundle result); }
    private void request(String method, String argument, Reply reply) {
        ipc.execute(() -> {
            Bundle result;
            try { result = transport.call(method, argument); } catch (Exception unavailable) { result = null; }
            Bundle delivered = result;
            main.post(() -> reply.receive(delivered));
        });
    }
    private void accept(Bundle result) {
        if (result == null || result.getString("state") == null) { unavailable(); return; }
        uncertain = false;
        boolean running = VideoDownloadState.active(result.getString("state"));
        String task = result.getString("id");
        String message = result.getString("message", running ? "下载中…" : "暂无下载任务");
        boolean completed = busy && !running && Objects.equals(id, task);
        id = task; busy = running;
        listener.changed(busy || submitting, result.getInt("progress", -1), message);
        if (completed && visible) listener.completed(message);
    }
    private void unavailable() {
        // Keep the last authoritative state. The button remains usable for query/cancel.
        uncertain = true;
        listener.changed(busy, -1, "暂时无法确认下载状态，请重试");
    }
    private void schedule() {
        main.removeCallbacks(poll);
        if (visible && (busy || uncertain)) main.postDelayed(poll, busy ? 2000 : 5000);
    }
}
