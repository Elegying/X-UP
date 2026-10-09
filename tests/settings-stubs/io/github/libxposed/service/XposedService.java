package io.github.libxposed.service;
import android.content.SharedPreferences;
public final class XposedService {
    private final SharedPreferences preferences;
    public XposedService(SharedPreferences preferences) { this.preferences = preferences; }
    public int getApiVersion() { return 102; }
    public SharedPreferences getRemotePreferences(String name) { return preferences; }
}
