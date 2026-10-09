package io.github.jared.xlowerseek;

import org.junit.Test;
import static org.junit.Assert.*;

public class VideoDownloadStateTest {
    private static final String URL = "https://video.twimg.com/test/video.mp4";
    private final VideoDownloadState state = new VideoDownloadState();
    private VideoDownloadState.Offer offer() { return state.prepare(URL, 0); }
    private String run() {
        VideoDownloadState.Offer offer = offer();
        assertEquals(URL, state.consume(offer.status.id, offer.capability, 1));
        assertTrue(state.start(offer.status.id, URL, 2));
        return offer.status.id;
    }
    @Test public void onlyIssuedSingleUseCapabilityCanStartBoundSource() {
        VideoDownloadState.Offer a = offer();
        assertNull(state.consume(a.status.id, null, 1));
        assertNull(state.consume(a.status.id, "forged", 1));
        assertNull(state.consume("other", a.capability, 1));
        assertFalse(state.start(a.status.id, URL, 1));
        assertEquals(URL, state.consume(a.status.id, a.capability, 1));
        assertNull(state.consume(a.status.id, a.capability, 2));
        assertFalse(state.start(a.status.id, URL + "?changed", 2));
        assertTrue(state.start(a.status.id, URL, 2));
        assertFalse(state.start(a.status.id, URL, 3));
    }
    @Test public void duplicateRequestsReturnActiveTaskWithoutNewCapability() {
        VideoDownloadState.Offer a = offer(), b = state.prepare(URL, 1);
        assertEquals(a.status.id, b.status.id); assertNull(b.capability);
        state.consume(a.status.id, a.capability, 2); state.start(a.status.id, URL, 3);
        assertEquals(a.status.id, state.prepare("https://video.twimg.com/other.mp4", 100000).status.id);
    }
    @Test public void expiredOfferCannotStartAfterRetry() {
        VideoDownloadState.Offer a = offer();
        assertNull(state.consume(a.status.id, a.capability, 30000));
        VideoDownloadState.Offer b = state.prepare(URL, 30001);
        assertNotEquals(a.status.id, b.status.id);
        assertNull(state.consume(a.status.id, a.capability, 30002));
        assertEquals(URL, state.consume(b.status.id, b.capability, 30002));
    }
    @Test public void delayedServiceCannotStartExpiredOrCancelledReservation() {
        VideoDownloadState.Offer a = offer(); state.consume(a.status.id, a.capability, 1);
        assertFalse(state.start(a.status.id, URL, 30001));
        assertFalse(state.snapshot(30001).active());
        a = state.prepare(URL, 30002); state.consume(a.status.id, a.capability, 30003);
        assertTrue(state.cancel(a.status.id, 30004)); assertFalse(state.start(a.status.id, URL, 30005));
    }
    @Test public void runningTransferNeverBlindlyExpiresWhenProgressStops() {
        String id = run(); state.progress(id, "下载中 1%", 1);
        assertTrue(state.snapshot(600000).active());
        assertEquals(id, state.prepare(URL, 600000).status.id);
    }
    @Test public void processRestartRetiresRunningTaskAndAllowsRetry() {
        String id = run(); VideoDownloadState recovered = new VideoDownloadState();
        recovered.restore(id, VideoDownloadState.RUNNING, "下载中");
        assertFalse(recovered.snapshot(0).active());
        assertEquals("下载已中断，请重试", recovered.snapshot(0).message);
        assertNotEquals(id, recovered.prepare(URL, 1).status.id);
    }
    @Test public void processRestartPreservesCompletedResult() {
        String id = run(); state.finish(id, true, "已保存");
        VideoDownloadState recovered = new VideoDownloadState(); recovered.restore(id, state.snapshot(3).state, "已保存");
        assertEquals(VideoDownloadState.SUCCEEDED, recovered.snapshot(0).state);
    }
    @Test public void cancelWaitsForCleanupBeforeAllowingAnotherTask() {
        String id = run(); assertTrue(state.cancel(id, 3));
        assertEquals(id, state.prepare(URL, 4).status.id);
        state.progress(id, "late", 42); assertEquals("正在取消…", state.snapshot(4).message);
        state.finish(id, false, "已取消下载"); assertEquals(VideoDownloadState.CANCELLED, state.snapshot(5).state);
        assertNotEquals(id, state.prepare(URL, 6).status.id);
    }
    @Test public void oldCancellationAndCompletionCannotAffectNextTransfer() {
        String old = run(); state.finish(old, true, "已保存"); String next = run();
        assertFalse(state.cancel(old, 5)); state.finish(old, false, "old"); state.progress(old, "old", 0);
        assertEquals(next, state.snapshot(6).id); assertEquals(VideoDownloadState.RUNNING, state.snapshot(6).state);
    }
    @Test public void publishedFileWinsRaceWithCancellation() {
        String id = run(); state.cancel(id, 4); state.finish(id, true, "已保存");
        assertEquals(VideoDownloadState.SUCCEEDED, state.snapshot(5).state);
    }
    @Test public void invalidAndOversizedUrlsNeverReserveWork() {
        for (String url : new String[]{null, "http://video.twimg.com/v.mp4", "https://other.test/v.mp4", URL + "?" + "x".repeat(8192)}) {
            try { state.prepare(url, 1); fail(); } catch (IllegalArgumentException expected) {}
            assertFalse(state.snapshot(1).active());
        }
    }
}
