package io.github.jared.xlowerseek;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

/** X can report its own loading result; this provider exposes no files or stored results. */
public final class CompatibilityProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        boolean allowed = uid == android.os.Process.myUid();
        String[] packages = getContext().getPackageManager().getPackagesForUid(uid);
        if (packages != null) for (String name : packages) if ("com.twitter.android".equals(name)) allowed = true;
        if (!allowed) throw new SecurityException("Caller not allowed");
        Bundle out = new Bundle();
        out.putBoolean("saved", "report".equals(method) && CompatibilityStatus.save(getContext(), extras));
        return out;
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String order) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
