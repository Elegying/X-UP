package io.github.jared.xlowerseek;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompatibilityStatusTest {
    @Test public void acknowledgedReportStopsImmediately() {
        AtomicInteger calls = new AtomicInteger();
        assertTrue(CompatibilityStatus.retryReport(() -> { calls.incrementAndGet(); return true; }, delay -> fail("Unexpected retry")));
        assertEquals(1, calls.get());
    }
    @Test public void unavailableProviderAndRejectedWriteRecover() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        assertTrue(CompatibilityStatus.retryReport(() -> {
            int call = calls.incrementAndGet();
            if (call == 1) throw new IllegalStateException("Provider unavailable");
            return call == 3;
        }, delays::add));
        assertEquals(3, calls.get());
        assertEquals(Arrays.asList(1000L, 3000L), delays);
    }
    @Test public void persistentFailureIsBounded() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        assertFalse(CompatibilityStatus.retryReport(() -> { calls.incrementAndGet(); return null; }, delays::add));
        assertEquals(5, calls.get());
        assertEquals(Arrays.asList(1000L, 3000L, 10000L, 30000L), delays);
    }
    @Test public void interruptionCancelsPendingRetry() {
        AtomicInteger calls = new AtomicInteger();
        try {
            assertFalse(CompatibilityStatus.retryReport(() -> { calls.incrementAndGet(); return false; }, delay -> { throw new InterruptedException(); }));
            assertEquals(1, calls.get());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
    @Test public void foregroundEventsCannotOverlapAnActiveRound() {
        CompatibilityStatus.ReportState state = new CompatibilityStatus.ReportState();
        assertTrue(state.begin(0));
        assertFalse(state.begin(1));
        assertFalse(state.begin(120_000));
    }
    @Test public void failedRoundWaitsSixtySecondsAfterCompletion() {
        CompatibilityStatus.ReportState state = new CompatibilityStatus.ReportState();
        assertTrue(state.begin(0));
        state.finish(false, 44_000);
        assertFalse(state.begin(44_001));
        assertFalse(state.begin(103_999));
        assertTrue(state.begin(104_000));
        state.finish(false, 148_000);
        assertFalse(state.begin(207_999));
        assertTrue(state.begin(208_000));
    }
    @Test public void acknowledgedReportNeverPublishesAgain() {
        CompatibilityStatus.ReportState state = new CompatibilityStatus.ReportState();
        assertTrue(state.begin(0));
        state.finish(true, 1000);
        assertFalse(state.begin(1001));
        assertFalse(state.begin(Long.MAX_VALUE));
    }
}
