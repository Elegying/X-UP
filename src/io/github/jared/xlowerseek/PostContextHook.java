package io.github.jared.xlowerseek;

import java.lang.reflect.Field;

/** Captures already loaded public post context through validated semantic model properties. */
final class PostContextHook {
    static final PostContextIndex INDEX=new PostContextIndex();
    private final Field text,range,parent,quoted,id,rangeStart,rangeEnd,idValue,canonical,quote;
    private PostContextHook(TargetResolver resolver)throws Exception {
        ContentDataClass post=ContentTargets.data(resolver,"CanonicalPost(id=");
        ContentDataClass context=ContentTargets.data(resolver,"ContextualPost(canonicalPost=");
        text=post.field("text",String.class);range=post.field("displayTextRange");
        parent=post.field("repliedPostId",Long.class);quoted=post.field("quotedStatusId");id=post.field("id");
        if(quoted.getType()!=id.getType())throw new NoSuchFieldException("Post identifier shape");
        idValue=ContentTargets.onlyField(id.getType(),long.class);
        Object sample=range.getType().getDeclaredConstructor(int.class,int.class).newInstance(17,29);
        rangeStart=ContentModelProbe.property(sample,"startIdx",int.class);
        rangeEnd=ContentModelProbe.property(sample,"endIdx",int.class);
        if(rangeStart.equals(rangeEnd))throw new NoSuchFieldException("Ambiguous text range");
        canonical=context.field("canonicalPost",post.type);quote=context.field("quotedPost");
    }
    static void install(TargetResolver resolver)throws Exception {
        PostContextHook hook=new PostContextHook(resolver);
        Class<?> canonical=hook.text.getDeclaringClass();
        // Cached models are read without construction when revisiting a thread.
        Hooks.findAndHookMethod(canonical,"getText",new HookCallback(){protected void afterHookedMethod(HookParam p){if(!p.hasThrowable())hook.capture(p.thisObject);}});
        Hooks.hookAllConstructors(canonical,new HookCallback(){protected void afterHookedMethod(HookParam p){if(!p.hasThrowable())hook.capture(p.thisObject);}});
        Hooks.hookAllConstructors(hook.canonical.getDeclaringClass(),new HookCallback(){protected void afterHookedMethod(HookParam p)throws Throwable {
            if(p.hasThrowable())return;hook.capture(hook.canonical.get(p.thisObject));
            Object quote=hook.quote.get(p.thisObject);if(quote!=null)try{
                String text=(String)Reflect.callMethod(quote,"getText");long id=hook.id(Reflect.callMethod(quote,"getId"));
                INDEX.putIfAbsent(new PostContextIndex.Post(id,text,text,0,0));
            }catch(ReflectiveOperationException|RuntimeException ignored){}
        }});
    }
    private long id(Object value)throws IllegalAccessException{return value==null?0:idValue.getLong(value);}
    private void capture(Object model){
        try{
            String body=(String)text.get(model);if(body==null)return;String visible=body;
            Object interval=range.get(model);
            if(interval!=null){int start=rangeStart.getInt(interval),end=rangeEnd.getInt(interval);if(start>=0&&end>=start&&end<=body.length())visible=body.substring(start,end);}
            Object reply=parent.get(model);
            INDEX.put(new PostContextIndex.Post(id(id.get(model)),body,visible,reply instanceof Number?((Number)reply).longValue():0,id(quoted.get(model))));
        }catch(ReflectiveOperationException|RuntimeException ignored){/* Unknown model shape keeps context empty. */}
    }
}
