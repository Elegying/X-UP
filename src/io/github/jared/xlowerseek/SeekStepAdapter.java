package io.github.jared.xlowerseek;
import java.lang.reflect.*;
import java.util.function.IntSupplier;
/** Shared executor adapter, isolated from Android settings for regression tests. */
final class SeekStepAdapter {
    static HookCallback callback(IntSupplier seconds,Object forward,Constructor<?> makeState,Constructor<?> makeStep,Method encode,Method decode,Object unit){
        return new HookCallback(){
            @Override protected void beforeHookedMethod(HookParam p){
                if(p.args[6]!=forward||p.args[7]==null)return;
                try{
                    if(seconds.getAsInt()!=5)return;
                    Object oldStep=p.args[7];long raw=Reflect.getLongField(oldStep,"a");
                    long nativeSeconds=((Number)decode.invoke(null,raw,unit)).longValue();
                    // X uses 10-second units, including multi-unit repeat gestures.
                    if(nativeSeconds<=0||nativeSeconds>Integer.MAX_VALUE||nativeSeconds%10!=0)return;
                    int adjusted=(int)(nativeSeconds/2);
                    Object oldState=Reflect.getObjectField(oldStep,"b");
                    int cumulative=Math.toIntExact((long)Reflect.getIntField(oldState,"b")-nativeSeconds+adjusted);
                    long duration=((Number)encode.invoke(null,adjusted,unit)).longValue();
                    p.args[7]=makeStep.newInstance(duration,makeState.newInstance(forward,cumulative));
                }catch(Throwable e){Hooks.log("XLowerSeek: seek step override unavailable; original retained");}
            }
        };
    }
}
