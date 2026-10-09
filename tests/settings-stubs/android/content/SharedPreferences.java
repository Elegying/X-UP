package android.content;
public interface SharedPreferences {
    boolean contains(String key);
    String getString(String key, String fallback);
    int getInt(String key, int fallback);
    long getLong(String key, long fallback);
    boolean getBoolean(String key, boolean fallback);
    Editor edit();
    interface OnSharedPreferenceChangeListener { void onSharedPreferenceChanged(SharedPreferences prefs, String key); }
    void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener);
    interface Editor {
        Editor putString(String key, String value);
        Editor putInt(String key, int value);
        Editor putLong(String key, long value);
        Editor putBoolean(String key, boolean value);
        Editor remove(String key);
        boolean commit();
    }
}
