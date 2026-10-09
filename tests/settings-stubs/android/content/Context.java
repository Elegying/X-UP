package android.content;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

public class Context {
    public static final int MODE_PRIVATE = 0;
    private final Map<String, MemoryPreferences> prefs = new HashMap<>();
    public Context getApplicationContext() { return this; }
    public ContentResolver getContentResolver() { return null; }
    public synchronized MemoryPreferences getSharedPreferences(String name, int mode) {
        return prefs.computeIfAbsent(name, ignored -> new MemoryPreferences());
    }
    public FileInputStream openFileInput(String name) throws FileNotFoundException {
        throw new FileNotFoundException(name);
    }
}
