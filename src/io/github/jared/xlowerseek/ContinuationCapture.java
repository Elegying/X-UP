package io.github.jared.xlowerseek;

/** Validate typed captures even when R8 erases their constructor parameter declarations. */
final class ContinuationCapture {
    static Object state(Object[] args,Class<?> state,Class<?> function1,Class<?> function0) {
        Object result=null;int states=0,actions=0,fallbacks=0;
        for(Object value:args) {
            if(state.isInstance(value)){result=value;states++;}
            if(function1.isInstance(value))actions++;
            if(function0.isInstance(value))fallbacks++;
        }
        return states==1&&actions==1&&fallbacks==1?result:null;
    }
    private ContinuationCapture(){}
}
