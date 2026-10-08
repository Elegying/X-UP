package io.github.jared.xlowerseek;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Executes the production hook with X's extracted zone/active-burst contract. */
public final class ContinuationTapHookTest {
    public static class Point {public long a;public Point(long value){a=value;}}
    public static class Tap {public int a=1;public Object d=new Object(),e=new Object();}
    static int checks;
    static void check(boolean ok){checks++;if(!ok)throw new AssertionError("case "+checks);}
    static long point(float x,float y){return ((long)Float.floatToRawIntBits(x)<<32)|(Float.floatToRawIntBits(y)&0xffffffffL);}
    static class Chain implements XposedInterface.Chain {
        final Tap tap;final Point original;boolean active;int seconds=10,total,seeks,singles,calls,direction;Object received;
        Chain(Tap t,float x,float y){tap=t;original=new Point(point(x,y));}
        public Executable getExecutable(){try{return ContinuationTapHookTest.class.getDeclaredMethod("check",boolean.class);}catch(Exception e){throw new RuntimeException(e);}}
        public Object getThisObject(){return tap;}
        public List<Object> getArgs(){return Collections.singletonList(original);}
        public Object getArg(int n){return original;}
        public Object proceed(){return proceed(new Object[]{original});}
        public Object proceed(Object[] args){calls++;received=args[0];float x=Float.intBitsToFloat((int)(((Point)received).a>>32));direction=x<=240?-1:x>=960?1:0;
            if(tap.d!=null && direction!=0 && active){seeks++;total+=direction*seconds;}else singles++;
            return "native";
        }
        public Object proceedWith(Object o){return proceed();}
        public Object proceedWith(Object o,Object[] a){return proceed(a);}
    }
    public static void main(String[] args)throws Throwable {
        Tap tap=new Tap();Map<Object,int[]> sizes=new HashMap<>();sizes.put(tap.e,new int[]{1200,2000});AtomicBoolean enabled=new AtomicBoolean(true);
        HookCallback hook=ContinuationTapHook.callback(enabled::get,sizes,Point.class.getConstructor(long.class));
        for(int seconds:new int[]{5,10})for(float x:new float[]{100,600,1100}){
            Chain c=new Chain(tap,x,1500);c.active=true;c.seconds=seconds;c.total=seconds; // initial native double-tap has already sought once
            for(int n=2;n<=6;n++){
                check(Hooks.intercept(c,hook).equals("native"));check(c.total==n*seconds);check(c.seeks==n-1);check(c.calls==n-1);
                check(c.original.a==point(x,1500));check((int)((Point)c.received).a==(int)c.original.a);
            }
            c.active=false;int total=c.total;Hooks.intercept(c,hook);check(c.singles==1&&c.total==total); // native timeout
        }
        Chain normal=new Chain(tap,600,1500);Hooks.intercept(normal,hook);check(normal.singles==1&&normal.seeks==0);
        for(float x:new float[]{100,600,1100}){Chain c=new Chain(tap,x,100);c.active=true;Hooks.intercept(c,hook);check(c.received==c.original);check(c.direction==(x==100?-1:x==1100?1:0));}
        enabled.set(false);Chain off=new Chain(tap,100,1500);off.active=true;Hooks.intercept(off,hook);check(off.total==-10&&off.received==off.original);enabled.set(true);
        tap.a=0;Chain other=new Chain(tap,100,1500);Hooks.intercept(other,hook);check(other.received==other.original);tap.a=1;
        tap.d=null;Chain noCallback=new Chain(tap,100,1500);Hooks.intercept(noCallback,hook);check(noCallback.received==noCallback.original);tap.d=new Object();
        sizes.clear();Chain missing=new Chain(tap,100,1500);Hooks.intercept(missing,hook);check(missing.received==missing.original);
        System.out.println("ContinuationTapHook: "+checks+" assertions passed (native burst, 5/10, single tap, overlap, timeout, upper half, disabled, unrelated callback)");
    }
}
