package io.github.jared.xlowerseek;
import java.lang.ref.WeakReference;
import java.util.*;

/** A recycled surface belongs only to its latest player; never retain either strongly. */
final class SurfaceRegistry<P,V> {
    private final Map<P,WeakReference<V>> owners=new WeakHashMap<>();
    synchronized void set(P player,V view){
        owners.entrySet().removeIf(e->e.getKey()==player||e.getValue().get()==null||view!=null&&e.getValue().get()==view);
        if(view!=null)owners.put(player,new WeakReference<>(view));
    }
    synchronized void remove(P player){owners.remove(player);}
    synchronized Map<P,WeakReference<V>> snapshot(){return new HashMap<>(owners);}
}
