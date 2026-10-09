package io.github.jared.xlowerseek;

import android.content.*;
import android.os.*;
import io.github.libxposed.service.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Uses production settings and scheduler together; only storage, bridge and engine are doubles. */
public final class SettingsTranslationTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static boolean until(BooleanSupplier done) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!done.getAsBoolean() && System.nanoTime() < deadline) {
            Handler.drain(); Thread.sleep(2);
        }
        return done.getAsBoolean();
    }
    private static void request(Context app, String id, String text, AtomicInteger result) {
        LocalTranslationService.request(app, 7, id, SettingsStore.engine(app).id, 1, text, "",
            new ResultReceiver(new Handler(Looper.getMainLooper())) {
                protected void onReceiveResult(int code, Bundle data) { result.set(code); }
            });
    }
    private static void stopWorker() throws Exception {
        var field = LocalTranslationService.class.getDeclaredField("worker"); field.setAccessible(true);
        ExecutorService worker = (ExecutorService) field.get(null);
        worker.shutdownNow(); worker.awaitTermination(2, TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws Exception {
        Context app = new Context();
        try {
            SettingsStore.initialize(app);
            boolean connected = args.length > 0 && args[0].equals("connected");
            MemoryPreferences remote = new MemoryPreferences();
            if (connected) { XposedServiceHelper.bind(new XposedService(remote)); Handler.drain(); }
            check(SettingsStore.isShared() == connected, "bridge state initialized");
            LocalTranslationService.foreground(app, 7, "visible-page", true);
            check(until(() -> LocalModels.loads.get() == 1), "engine warmed");
            LocalModels.blocking = true;
            AtomicInteger active = new AtomicInteger(-1), queued = new AtomicInteger(-1);
            request(app, "active", "Current work", active);
            check(LocalModels.started.await(2, TimeUnit.SECONDS), "active inference started");
            request(app, "queued", "Queued work", queued);

            check(SettingsStore.save(app, 5, false, false), "total switch saved");
            check(!SettingsStore.translate(app), "total switch disabled locally");
            if (connected) check(!remote.getBoolean(SettingsStore.TRANSLATE, true), "total switch synced");
            boolean stopped = until(() -> LocalModels.cancels.get() == 1 && LocalModels.closes.get() == 1);
            System.out.println("Total switch " + (connected ? "connected" : "offline") +
                ": cancelled=" + LocalModels.cancels.get() + " closed=" + LocalModels.closes.get() +
                " activeResult=" + active.get() + " queuedResult=" + queued.get());
            check(stopped, "saving total switch must cancel active work and release the engine");
            check(until(() -> active.get() == 2 && queued.get() == 2), "active and queued requests invalidated");
            check(LocalModels.calls.get() == 1, "queued inference never started");

            LocalModels.blocking = false;
            check(SettingsStore.save(app, 5, false, true), "total switch re-enabled"); Handler.drain();
            LocalTranslationService.foreground(app, 7, "visible-page", true);
            AtomicInteger recovered = new AtomicInteger(-1);
            request(app, "recovered", "Work after enabling", recovered);
            check(until(() -> recovered.get() == 0), "translation resumes after enabling");
            System.out.println("SettingsTranslation (" + (connected ? "connected" : "offline") + "): " + checks + " assertions passed");
        } finally { LocalModels.blocking = false; stopWorker(); }
    }
}
