package io.github.jared.xlowerseek;

import java.lang.reflect.*;
import java.util.Arrays;
import io.github.libxposed.api.XposedInterface;

/** Isolates the API 102 interceptor lifecycle from version-specific X adapters. */
final class Hooks {
    private static XposedInterface api;
    static void initialize(XposedInterface value){api=value;}
    static void log(String message){api.log(android.util.Log.INFO,"X-UP",message);}
    static void hookMethod(Executable method,HookCallback callback){
        api.hook(method).intercept(chain->intercept(chain,callback));
    }
    static Object intercept(XposedInterface.Chain chain,HookCallback callback)throws Throwable {
        Object[] args=chain.getArgs().toArray();
        HookCallback.HookParam p=new HookCallback.HookParam(chain.getExecutable(),chain.getThisObject(),args);
        try{callback.beforeHookedMethod(p);}catch(Throwable error){
            System.arraycopy(chain.getArgs().toArray(),0,args,0,args.length);p.skip=false;p.outcome(null,null);
            logFailure(error);
        }
        if(!p.skip){
            try{p.outcome(chain.proceed(args),null);}catch(Throwable original){p.outcome(null,original);}
        }
        Object result=p.getResult();Throwable failure=p.getThrowable();
        try{callback.afterHookedMethod(p);}catch(Throwable error){p.outcome(result,failure);logFailure(error);}
        if(p.hasThrowable())throw p.getThrowable();
        return p.getResult();
    }
    private static void logFailure(Throwable error){if(api!=null)api.log(android.util.Log.WARN,"X-UP","Hook callback failed; preserving original outcome",error);}
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
