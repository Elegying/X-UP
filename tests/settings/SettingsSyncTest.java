package io.github.jared.xlowerseek;

import android.content.*;
import android.os.Handler;
import io.github.libxposed.service.*;
import java.util.concurrent.ExecutorService;

public final class SettingsSyncTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Context app = new Context();
        try {
            SettingsStore.initialize(app);
            check(SettingsStore.save(app, 5, false, false), "offline base save");
            check(SettingsStore.setEngine(app, LocalEngine.OPUS), "offline engine save");
            check(SettingsStore.setFeature(app, Feature.AD_BLOCK, false), "offline feature save");
            MemoryPreferences remote = new MemoryPreferences(); XposedService service = new XposedService(remote);
            XposedServiceHelper.bind(service); Handler.drain();
            check(SettingsStore.isShared(), "bridge connected");
            check(remote.getInt(SettingsStore.SECONDS, 0) == 5 && !remote.getBoolean(SettingsStore.LATEST, true)
                && !remote.getBoolean(SettingsStore.TRANSLATE, true), "all offline base choices synchronized");
            check(remote.getString(LocalEngine.KEY, "").equals("opus") && !remote.getBoolean(Feature.AD_BLOCK.key, true), "offline engine and feature synchronized");
            check(!app.getSharedPreferences("settings_local", 0).getBoolean("pending_sync", true), "outbox acknowledged");

            remote.unavailable = true;
            check(SettingsStore.save(app, 10, true, true), "bridge failure preserves successful local save"); Handler.drain();
            check(!SettingsStore.isShared() && SettingsStore.translate(app) && SettingsStore.seconds(app) == 10, "local choices retained after remote failure");
            check(app.getSharedPreferences("settings_local", 0).getBoolean("pending_sync", false), "failed remote save remains pending");
            remote.unavailable = false; XposedServiceHelper.bind(service); Handler.drain();
            check(SettingsStore.isShared() && remote.getInt(SettingsStore.SECONDS, 0) == 10 && remote.getBoolean(SettingsStore.TRANSLATE, false), "reconnect replays pending choices");
            check(SettingsStore.engine(app) == LocalEngine.OPUS && !SettingsStore.feature(app, Feature.AD_BLOCK), "reconnect retains independent feature choices");
            System.out.println("SettingsSync: " + checks + " assertions passed");
        } finally {
            var field = LocalTranslationService.class.getDeclaredField("worker"); field.setAccessible(true);
            ((ExecutorService) field.get(null)).shutdownNow();
        }
    }
}
