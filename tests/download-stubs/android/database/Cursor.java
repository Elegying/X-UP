package android.database; public interface Cursor extends AutoCloseable {boolean moveToFirst();int getInt(int i);void close();}
