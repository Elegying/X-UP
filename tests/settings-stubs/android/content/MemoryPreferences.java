package android.content;

import java.util.HashMap;
import java.util.Map;

/** Models typed preferences and an unavailable remote bridge, without disk/Binder I/O. */
public final class MemoryPreferences implements SharedPreferences {
    private final Map<String, Object> values = new HashMap<>();
    private final java.util.List<OnSharedPreferenceChangeListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { listeners.add(listener); }
    public boolean unavailable;
    public synchronized boolean contains(String key) { return values.containsKey(key); }
    public synchronized String getString(String key, String fallback) { return (String) values.getOrDefault(key, fallback); }
    public synchronized int getInt(String key, int fallback) { return (Integer) values.getOrDefault(key, fallback); }
    public synchronized long getLong(String key, long fallback) { return (Long) values.getOrDefault(key, fallback); }
    public synchronized boolean getBoolean(String key, boolean fallback) { return (Boolean) values.getOrDefault(key, fallback); }
    public Editor edit() {
        return new Editor() {
            private final Map<String, Object> changes = new HashMap<>();
            public Editor putString(String key, String value) { changes.put(key, value); return this; }
            public Editor putInt(String key, int value) { changes.put(key, value); return this; }
            public Editor putLong(String key, long value) { changes.put(key, value); return this; }
            public Editor putBoolean(String key, boolean value) { changes.put(key, value); return this; }
            public Editor remove(String key) { changes.put(key, null); return this; }
            public boolean commit() {
                if (unavailable) throw new IllegalStateException("Remote service unavailable");
                synchronized (MemoryPreferences.this) {
                    changes.forEach((key, value) -> { if (value == null) values.remove(key); else values.put(key, value); });
                }
                for (String key : changes.keySet()) for (OnSharedPreferenceChangeListener listener : listeners) listener.onSharedPreferenceChanged(MemoryPreferences.this, key);
                return true;
            }
        };
    }
}
