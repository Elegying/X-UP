package android.content; public abstract class ContentResolver{public ContentProviderClient acquireUnstableContentProviderClient(android.net.Uri uri){return new ContentProviderClient(this,uri);}
 public abstract android.os.Bundle call(android.net.Uri u,String method,String arg,android.os.Bundle extras);}
