package io.github.jared.xlowerseek;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;

/** Module-process bridge shared by the authenticated provider, entry Activity and service. */
final class VideoDownloads {
    private static VideoDownloadState state;
    private static Runnable cancellation;
    private static String persistedId, persistedState;

    private static VideoDownloadState state(Context context) {
        if (state == null) {
            SharedPreferences prefs = context.getSharedPreferences("video_task", 0);
            state = new VideoDownloadState();
            state.restore(prefs.getString("id", null), prefs.getString("state", null), prefs.getString("message", "下载已结束"));
        }
        return state;
    }
    private static Bundle snapshot(Context context) {
        VideoDownloadState.Snapshot s = state(context).snapshot(SystemClock.elapsedRealtime());
        // Persist transitions only. Capabilities and source URLs never go to disk.
        if (!java.util.Objects.equals(persistedId, s.id) || !java.util.Objects.equals(persistedState, s.state)) {
            context.getSharedPreferences("video_task", 0).edit().putString("id", s.id)
                    .putString("state", s.state).putString("message", s.message).apply();
            persistedId = s.id; persistedState = s.state;
        }
        Bundle b = new Bundle(); b.putString("id", s.id); b.putString("state", s.state);
        b.putString("message", s.message); b.putInt("progress", s.progress); return b;
    }
    static synchronized Bundle prepare(Context context, String url) {
        VideoDownloadState.Offer offer = state(context).prepare(url, SystemClock.elapsedRealtime());
        Bundle b = snapshot(context); if (offer.capability != null) b.putString("capability", offer.capability); return b;
    }
    static synchronized Bundle status(Context context) { return snapshot(context); }
    static synchronized String consume(Context context, String id, String capability) {
        String url = state(context).consume(id, capability, SystemClock.elapsedRealtime()); snapshot(context); return url;
    }
    static synchronized boolean start(Context context, String id, String url, Runnable cancel) {
        boolean started = state(context).start(id, url, SystemClock.elapsedRealtime());
        if (started) cancellation = cancel; snapshot(context); return started;
    }
    static Bundle cancel(Context context, String id) {
        Runnable action;
        synchronized (VideoDownloads.class) {
            boolean changed = state(context).cancel(id, SystemClock.elapsedRealtime());
            action = changed ? cancellation : null;
        }
        if (action != null) action.run();
        synchronized (VideoDownloads.class) { return snapshot(context); }
    }
    static synchronized void progress(Context context, String id, String message, int progress) {
        state(context).progress(id, message, progress);
    }
    static synchronized void finish(Context context, String id, boolean success, String message) {
        VideoDownloadState.Snapshot current = state(context).snapshot(SystemClock.elapsedRealtime());
        if (!java.util.Objects.equals(id, current.id)) return;
        state(context).finish(id, success, message); cancellation = null; snapshot(context);
    }
}
