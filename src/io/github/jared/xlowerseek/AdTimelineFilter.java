package io.github.jared.xlowerseek;

import java.lang.reflect.*;
import java.util.*;

/** Pinned X 12.31 adapter. Explicit metadata only; copy changed branches, retain unknown entries. */
final class AdTimelineFilter {
    private final Class<?> post,user,imageAd,trend,module,moduleItem;
    private final Field postAd,userAd,trendValue,trendAd,children,wrapped;
    private final Field[] moduleFields;
    private final Field dispensable;
    private final Constructor<?> copyModule,copyItem;
    AdTimelineFilter(ClassLoader loader)throws Exception {
        this(Class.forName("com.x.models.timelines.items.j1",false,loader),Class.forName("com.x.models.timelines.items.b2",false,loader),
            Class.forName("com.x.models.timelines.items.p1",false,loader),Class.forName("com.x.models.timelines.items.y1",false,loader),
            Class.forName("com.x.models.timelines.items.a1",false,loader),Class.forName("com.x.models.timelines.items.d1",false,loader));
    }
    AdTimelineFilter(Class<?> post,Class<?> user,Class<?> imageAd,Class<?> trend,Class<?> module,Class<?> moduleItem)throws Exception {
        this.post=post;this.user=user;this.imageAd=imageAd;this.trend=trend;this.module=module;this.moduleItem=moduleItem;
        postAd=field(post,"e");userAd=field(user,"h");trendValue=field(trend,"a");trendAd=field(trendValue.getType(),"j");
        children=field(module,"a");wrapped=field(moduleItem,"a");dispensable=field(moduleItem,"b");
        moduleFields=new Field[]{field(module,"b"),field(module,"c"),field(module,"d"),field(module,"e"),field(module,"f"),field(module,"g")};
        Class<?>[] params=new Class<?>[7];params[0]=List.class;for(int i=0;i<6;i++)params[i+1]=moduleFields[i].getType();
        copyModule=module.getDeclaredConstructor(params);copyModule.setAccessible(true);
        copyItem=moduleItem.getDeclaredConstructor(wrapped.getType(),boolean.class);copyItem.setAccessible(true);
    }
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    List<?> filter(List<?> input)throws Exception{return filter(input,0);}
    private List<?> filter(List<?> input,int depth)throws Exception {
        ArrayList<Object> changed=null;
        for(int i=0;i<input.size();i++){
            Object original=input.get(i),replacement=entry(original,depth);
            if(changed==null&&original!=replacement){changed=new ArrayList<>(input.size());changed.addAll(input.subList(0,i));}
            if(changed!=null&&replacement!=REMOVED)changed.add(replacement);
        }
        return changed==null?input:Collections.unmodifiableList(changed);
    }
    private static final Object REMOVED=new Object();
    private Object entry(Object value,int depth)throws Exception {
        if(value==null||depth>12)return value;
        if(post.isInstance(value))return postAd.get(value)!=null?REMOVED:value;
        if(user.isInstance(value))return userAd.get(value)!=null?REMOVED:value;
        if(imageAd.isInstance(value))return REMOVED;
        if(trend.isInstance(value)){Object data=trendValue.get(value);return data!=null&&trendAd.get(data)!=null?REMOVED:value;}
        if(moduleItem.isInstance(value)){
            Object before=wrapped.get(value),after=entry(before,depth+1);
            return after==REMOVED?REMOVED:after==before?value:copyItem.newInstance(after,dispensable.getBoolean(value));
        }
        if(module.isInstance(value)){
            List<?> before=(List<?>)children.get(value),after=filter(before,depth+1);
            if(after==before)return value;
            if(after.isEmpty()&&!before.isEmpty())return REMOVED;
            Object[] args=new Object[7];args[0]=after;for(int i=0;i<6;i++)args[i+1]=moduleFields[i].get(value);
            return copyModule.newInstance(args);
        }
        return value;
    }
}
