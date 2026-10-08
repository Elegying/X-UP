package io.github.jared.xlowerseek;

public final class ModuleApplication extends android.app.Application {
    @Override public void onCreate() {
        super.onCreate();
        LegacyTranslationCleanup.run(this);
        SettingsStore.initialize(this);
        // A scoped URI grant also makes this provider visible to the receiving app.
        try {
            grantUriPermission("com.twitter.android",
                android.net.Uri.parse("content://" + LocalTranslationProvider.AUTHORITY + "/bridge"),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (IllegalArgumentException | SecurityException unavailable) {
            // X may not be installed yet; the next module launch retries the grant.
        }
    }
}
