package io.github.jared.xlowerseek;

import android.content.*;
import android.os.Handler;
import io.github.libxposed.service.*;
import java.util.concurrent.ExecutorService;

/** Exercises fresh defaults and both local/remote upgrade settings through production readers. */
public final class SettingsEngineDefaultsTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        String mode = args[0]; Context app = new Context();
        MemoryPreferences local = app.getSharedPreferences("settings_local", 0), remote = new MemoryPreferences();
        String saved = mode.contains("tencent") ? "tencent" : mode.contains("opus") ? "opus" : "mlkit";
        boolean remoteSaved = mode.startsWith("remote-"), localSaved = mode.startsWith("local-");
        if (localSaved || remoteSaved) {
            local.edit().putInt("schema", 1).putInt(TranslationMode.KEY, TranslationMode.LOCAL).commit();
            (remoteSaved ? remote : local).edit().putInt("schema", 1).putInt(TranslationMode.KEY, TranslationMode.LOCAL)
                .putString(LocalEngine.KEY, saved).commit();
        } else if (mode.equals("invalid")) local.edit().putString(LocalEngine.KEY, "retired-engine").commit();
        try {
            SettingsStore.initialize(app);
            check(SettingsStore.engine(app) == (localSaved ? LocalEngine.parse(saved) : LocalEngine.MLKIT), "local default/choice before connection");
            RemoteSettings before = new RemoteSettings(remote);
            check(before.engine.equals(remoteSaved ? saved : "mlkit"), "X reader uses matching default/choice");
            XposedServiceHelper.bind(new XposedService(remote)); Handler.drain();
            String expected = localSaved || remoteSaved ? saved : "mlkit";
            check(SettingsStore.isShared(), "settings bridge connected");
            check(SettingsStore.engine(app).id.equals(expected), "connection preserves valid selection");
            check(new RemoteSettings(remote).engine.equals(expected), "module and X agree after synchronization");
            check(SettingsStore.feature(app, Feature.LOCAL_TEXT) && !SettingsStore.feature(app, Feature.NATIVE_TRANSLATE), "local/native mode remains exclusive");
            System.out.println("SettingsEngineDefaults (" + mode + "): " + checks + " assertions passed");
        } finally {
            var field = LocalTranslationService.class.getDeclaredField("worker"); field.setAccessible(true);
            ((ExecutorService) field.get(null)).shutdownNow();
        }
    }
}
