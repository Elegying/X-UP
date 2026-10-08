package io.github.jared.xlowerseek;

import java.lang.reflect.Executable;

/** Module-owned callback state; the only framework backend is libxposed API 102. */
abstract class HookCallback {
    static final class HookParam {
        final Executable method;
        final Object thisObject;
        final Object[] args;
        private Object result;
        private Throwable throwable;
        boolean skip;
        HookParam(Executable method,Object receiver,Object[] args){this.method=method;this.thisObject=receiver;this.args=args;}
        Object getResult(){return result;}
        boolean hasThrowable(){return throwable!=null;}
        Throwable getThrowable(){return throwable;}
        void setResult(Object value){result=value;throwable=null;skip=true;}
        void outcome(Object value,Throwable error){result=value;throwable=error;}
    }
    protected void beforeHookedMethod(HookParam p)throws Throwable {}
    protected void afterHookedMethod(HookParam p)throws Throwable {}
}
