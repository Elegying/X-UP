package io.github.jared.xlowerseek;

import java.lang.ref.WeakReference;
import java.util.*;

/** Reversible presentation copies: never remove entries from X's source timeline. Main thread only. */
final class AdPresentation {
    interface Transform {Object apply(Object source)throws Exception;}
    private final WeakIdentityMap<Object,Object> originals=new WeakIdentityMap<>();
    private final ArrayDeque<Cached> cache=new ArrayDeque<>();
    private final Transform transform;
    private static final class Cached {
        final WeakReference<Object> source,result;
        Cached(Object s,Object r){source=new WeakReference<>(s);result=new WeakReference<>(r);}
    }
    AdPresentation(Transform transform){this.transform=transform;}
    Object render(Object incoming,boolean enabled)throws Exception {
        Object source=originals.get(incoming);if(source==null)source=incoming;
        if(!enabled)return source;
        for(Iterator<Cached> it=cache.iterator();it.hasNext();){Cached c=it.next();Object s=c.source.get(),r=c.result.get();if(s==null||r==null){it.remove();continue;}if(s==source)return r;}
        Object result=transform.apply(source);
        if(result!=source)originals.put(result,source);
        if(cache.size()>=8)cache.removeFirst();cache.addLast(new Cached(source,result));
        return result;
    }
}
