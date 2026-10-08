package io.github.jared.xlowerseek;

/** X 12.31 model adapter. Captures only public post fields; never changes X's stored models. */
final class PostContextHook {
    static final PostContextIndex INDEX=new PostContextIndex();
    static void install(ClassLoader loader){
        Class<?> canonical=Reflect.findClass("com.x.models.r0",loader);
        // Cached models are read without construction when revisiting a thread.
        Hooks.findAndHookMethod(canonical,"getText",new HookCallback(){protected void afterHookedMethod(HookParam p){if(!p.hasThrowable())capture(p.thisObject);}});
        Hooks.hookAllConstructors(canonical,new HookCallback(){protected void afterHookedMethod(HookParam p){if(!p.hasThrowable())capture(p.thisObject);}});
        Hooks.hookAllConstructors(Reflect.findClass("com.x.models.o1",loader),new HookCallback(){protected void afterHookedMethod(HookParam p){
            if(p.hasThrowable())return;capture(Reflect.getObjectField(p.thisObject,"b"));
            Object quote=Reflect.getObjectField(p.thisObject,"c");if(quote!=null)try{
                String text=(String)Reflect.callMethod(quote,"getText");long id=id(Reflect.callMethod(quote,"getId"));
                INDEX.putIfAbsent(new PostContextIndex.Post(id,text,text,0,0));
            }catch(RuntimeException ignored){}
        }});
    }
    private static long id(Object value){return value==null?0:Reflect.getLongField(value,"a");}
    private static void capture(Object model){
        try{
            String text=(String)Reflect.getObjectField(model,"b");String visible=text;
            Object range=Reflect.getObjectField(model,"k");
            if(range!=null){int start=Reflect.getIntField(range,"a"),end=Reflect.getIntField(range,"b");if(start>=0&&end>=start&&end<=text.length())visible=text.substring(start,end);}
            Object parent=Reflect.getObjectField(model,"i");
            INDEX.put(new PostContextIndex.Post(id(Reflect.getObjectField(model,"a")),text,visible,parent instanceof Number?((Number)parent).longValue():0,id(Reflect.getObjectField(model,"B"))));
        }catch(RuntimeException ignored){/* Unknown model shape keeps context empty. */}
    }
}
