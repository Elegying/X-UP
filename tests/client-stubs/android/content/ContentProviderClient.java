package android.content;
public final class ContentProviderClient implements AutoCloseable {
 public static final java.util.concurrent.atomic.AtomicInteger active=new java.util.concurrent.atomic.AtomicInteger();
 private final ContentResolver resolver;private final android.net.Uri uri;private boolean closed;
 public ContentProviderClient(ContentResolver resolver,android.net.Uri uri){this.resolver=resolver;this.uri=uri;active.incrementAndGet();}
 public android.os.Bundle call(String method,String arg,android.os.Bundle extras){if(closed)throw new IllegalStateException();return resolver.call(uri,method,arg,extras);}
 public void close(){if(closed)throw new AssertionError("lease closed twice");closed=true;active.decrementAndGet();}
}