package android.content;import android.net.Uri;import android.os.Bundle;import android.database.Cursor;import java.io.*;public abstract class ContentResolver {
 public ContentProviderClient acquireUnstableContentProviderClient(Uri uri){return new ContentProviderClient(this,uri);}public abstract Bundle call(Uri u,String method,String arg,Bundle b);
 public abstract Uri insert(Uri u,ContentValues values);public abstract Cursor query(Uri u,String[] p,String s,String[] a,String o);public abstract int update(Uri u,ContentValues values,String s,String[] a);public abstract int delete(Uri u,String s,String[] a);public abstract OutputStream openOutputStream(Uri u,String mode);
}
