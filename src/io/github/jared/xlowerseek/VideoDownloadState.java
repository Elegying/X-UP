package io.github.jared.xlowerseek;

import java.util.Objects;
import java.util.UUID;

/** One authoritative task. A timeout may retire an unstarted offer, never a running transfer. */
final class VideoDownloadState {
    static final long START_TIMEOUT_MS = 30_000;
    static final String IDLE = "idle", WAITING = "waiting", STARTING = "starting",
            RUNNING = "running", CANCELLING = "cancelling", SUCCEEDED = "succeeded",
            FAILED = "failed", CANCELLED = "cancelled";

    static final class Snapshot {
        final String id, state, message;
        final int progress;
        Snapshot(String id, String state, String message, int progress) {
            this.id = id; this.state = state; this.message = message; this.progress = progress;
        }
        boolean active() { return VideoDownloadState.active(state); }
    }
    static final class Offer {
        final Snapshot status;
        final String capability;
        Offer(Snapshot status, String capability) { this.status = status; this.capability = capability; }
    }

    private String id, url, capability;
    private String state = IDLE, message = "暂无下载任务";
    private int progress = -1;
    private long deadline;

    static boolean active(String state) {
        return WAITING.equals(state) || STARTING.equals(state) || RUNNING.equals(state) || CANCELLING.equals(state);
    }

    synchronized void restore(String savedId, String savedState, String savedMessage) {
        if (savedId == null) return;
        id = savedId;
        if (SUCCEEDED.equals(savedState) || FAILED.equals(savedState) || CANCELLED.equals(savedState)) {
            state = savedState; message = savedMessage;
        } else {
            state = FAILED; message = "下载已中断，请重试";
        }
    }

    synchronized Offer prepare(String source, long now) {
        if (source == null || source.length() > 8192 || !DownloadPolicy.validUrl(source))
            throw new IllegalArgumentException("视频来源无效");
        expire(now);
        if (active(state)) return new Offer(snapshot(now), null);
        id = UUID.randomUUID().toString(); capability = UUID.randomUUID().toString(); url = source;
        state = WAITING; message = "准备下载…"; progress = -1; deadline = now + START_TIMEOUT_MS;
        return new Offer(snapshot(now), capability);
    }

    synchronized String consume(String task, String ticket, long now) {
        expire(now);
        if (!WAITING.equals(state) || !Objects.equals(id, task) || capability == null || !capability.equals(ticket)) return null;
        capability = null; state = STARTING; deadline = now + START_TIMEOUT_MS;
        return url;
    }

    synchronized boolean start(String task, String source, long now) {
        expire(now);
        if (!STARTING.equals(state) || !Objects.equals(id, task) || !Objects.equals(url, source)) return false;
        state = RUNNING; message = "正在连接…"; return true;
    }

    synchronized boolean cancel(String task, long now) {
        expire(now);
        if (!Objects.equals(id, task) || !active(state)) return false;
        if (WAITING.equals(state) || STARTING.equals(state)) {
            state = CANCELLED; message = "已取消下载"; clearSource();
        } else { state = CANCELLING; message = "正在取消…"; }
        return true;
    }

    synchronized void progress(String task, String text, int value) {
        if (Objects.equals(id, task) && RUNNING.equals(state)) { message = text; progress = value; }
    }

    synchronized void finish(String task, boolean success, String text) {
        if (!Objects.equals(id, task) || !active(state)) return;
        state = success ? SUCCEEDED : CANCELLING.equals(state) ? CANCELLED : FAILED;
        message = text; if (success) progress = 100; clearSource();
    }

    synchronized Snapshot snapshot(long now) { expire(now); return new Snapshot(id, state, message, progress); }
    private void expire(long now) {
        if ((WAITING.equals(state) || STARTING.equals(state)) && now >= deadline) {
            state = FAILED; message = "下载未能启动，请重试"; clearSource();
        }
    }
    private void clearSource() { url = null; capability = null; }
}
