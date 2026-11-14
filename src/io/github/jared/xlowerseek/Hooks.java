package io.github.jared.xlowerseek;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import io.github.libxposed.api.XposedInterface;

/** Isolates the API 102 interceptor lifecycle from version-specific X adapters. */
final class Hooks {
    private static XposedInterface api;
    private static final ThreadLocal<List<XposedInterface.HookHandle>> pending = new ThreadLocal<>();
    private static final ThreadLocal<String> owner=new ThreadLocal<>();
    private static volatile java.util.function.BiConsumer<String,Throwable> failures;
    static void observeFailures(java.util.function.BiConsumer<String,Throwable> observer){failures=observer;}
    static int installAtomically(String feature,Installation installation)throws Throwable {
        String previous=owner.get();owner.set(feature);
        try{return installAtomically(installation);}finally{if(previous==null)owner.remove();else owner.set(previous);}
    }
    interface Installation { void run() throws Throwable; }
    /** Roll back a partially installed feature while preserving the other features. */
    static int installAtomically(Installation installation) throws Throwable {
        if (pending.get() != null) throw new IllegalStateException("Nested hook installation");
        List<XposedInterface.HookHandle> handles = new ArrayList<>();
        pending.set(handles);
        try {
            installation.run();
            if (handles.isEmpty()) throw new IllegalStateException("No compatible hooks installed");
            return handles.size();
        } catch (Throwable failure) {
            for (int i = handles.size() - 1; i >= 0; i--) {
                try { handles.get(i).unhook(); }
                catch (Throwable rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        } finally { pending.remove(); }
    }
    static void initialize(XposedInterface value){api=value;}
    static void log(String message){api.log(android.util.Log.INFO,"X-UP",message);}
    static void hookMethod(Executable method,HookCallback callback){
        String feature=owner.get();
        java.util.concurrent.atomic.AtomicInteger faults=new java.util.concurrent.atomic.AtomicInteger();
        XposedInterface.HookHandle handle = api.hook(method).intercept(chain->intercept(chain,callback,feature,faults));
        List<XposedInterface.HookHandle> handles = pending.get();
        if (handles != null) handles.add(handle);
    }
    static Object intercept(XposedInterface.Chain chain,HookCallback callback)throws Throwable {return intercept(chain,callback,null,new java.util.concurrent.atomic.AtomicInteger());}
    static Object intercept(XposedInterface.Chain chain,HookCallback callback,String feature,java.util.concurrent.atomic.AtomicInteger faults)throws Throwable {
        if(faults.get()>=3)return chain.proceed();
        Object[] args=chain.getArgs().toArray();
        HookCallback.HookParam p=new HookCallback.HookParam(chain.getExecutable(),chain.getThisObject(),args);
        try{callback.beforeHookedMethod(p);}catch(Throwable error){
            System.arraycopy(chain.getArgs().toArray(),0,args,0,args.length);p.skip=false;p.outcome(null,null);
            faults.incrementAndGet();logFailure(feature,error);
        }
        if(!p.skip){
            try{p.outcome(chain.proceed(args),null);}catch(Throwable original){p.outcome(null,original);}
        }
        Object result=p.getResult();Throwable failure=p.getThrowable();
        try{callback.afterHookedMethod(p);}catch(Throwable error){p.outcome(result,failure);faults.incrementAndGet();logFailure(feature,error);}
        if(p.hasThrowable())throw p.getThrowable();
        return p.getResult();
    }
    private static void logFailure(String feature,Throwable error){
        if(api!=null)api.log(android.util.Log.WARN,"X-UP","Hook callback failed; preserving original outcome",error);
        java.util.function.BiConsumer<String,Throwable> observer=failures;
        if(feature!=null&&observer!=null)try{observer.accept(feature,error);}catch(Throwable ignored){}
    }
    static void hookAllConstructors(Class<?> type,HookCallback callback){for(Constructor<?> c:type.getDeclaredConstructors())hookMethod(c,callback);}
    static void hookAllMethods(Class<?> type,String name,HookCallback callback){
        boolean found=false;
        for(Method m:type.getDeclaredMethods())if(m.getName().equals(name)){hookMethod(m,callback);found=true;}
        if(!found)throw new IllegalArgumentException("Method missing: "+type.getName()+"."+name);
    }
    static void findAndHookMethod(String name,ClassLoader loader,String method,Object... signature){findAndHookMethod(Reflect.findClass(name,loader),method,signature);}
    static void findAndHookMethod(Class<?> type,String method,Object... signature){
        Class<?>[] types=Arrays.copyOf(signature,signature.length-1,Class[].class);
        hookMethod(Reflect.findMethodExact(type,method,types),(HookCallback)signature[signature.length-1]);
    }
    static Object invokeOriginalMethod(Member method,Object receiver,Object[] args)throws Throwable {
        try{return api.getInvoker((Method)method).setType(new XposedInterface.Invoker.Type.Origin()).invoke(receiver,args);}
        catch(InvocationTargetException e){throw e.getCause();}
    }
}
