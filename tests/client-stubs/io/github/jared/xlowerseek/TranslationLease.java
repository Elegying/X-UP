package io.github.jared.xlowerseek;
final class TranslationLease {
 static final java.util.concurrent.atomic.AtomicInteger active=new java.util.concurrent.atomic.AtomicInteger();
 static Runnable acquire(android.content.Context context){active.incrementAndGet();return ()->active.decrementAndGet();}
}