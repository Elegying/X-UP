package io.github.jared.xlowerseek;
import java.lang.ref.*;
import java.util.*;
/** Weak object identity keys; values must never retain their key. */
final class WeakIdentityMap<K,V> {
    private final ReferenceQueue<K> queue=new ReferenceQueue<>();
    private final Map<Key<K>,V> entries=new HashMap<>();
    private static final class Key<K> extends WeakReference<K> {
        final int hash;
        Key(K key,ReferenceQueue<K> q){super(key,q);hash=System.identityHashCode(key);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return this==other||(other instanceof Key&&get()!=null&&get()==((Key<?>)other).get());}
    }
    private void clean(){Reference<? extends K> key;while((key=queue.poll())!=null)entries.remove(key);}
    V get(K key){clean();return entries.get(new Key<>(key,null));}
    void put(K key,V value){clean();entries.put(new Key<>(key,queue),value);}
}
