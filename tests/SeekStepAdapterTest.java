package io.github.jared.xlowerseek;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.*;import java.util.*;import java.util.concurrent.atomic.AtomicInteger;
public final class SeekStepAdapterTest {
 enum Direction{FORWARD,BACKWARD}
 public static class State{public Direction a;public int b;public State(Direction d,int total){a=d;b=total;}}
 public static class Step{public long a;public State b;public Step(long amount,State state){a=amount;b=state;}}
 public static long encode(int seconds,String unit){return seconds*2000000000L;}
 public static long decode(long raw,String unit){return raw/2000000000L;}
 static int checks;static void check(boolean ok){checks++;if(!ok)throw new AssertionError("seek step "+checks);}
 static class Chain implements XposedInterface.Chain {
  Object[] args=new Object[8];int calls;Step result;
  Chain(Direction d,int increment,int nativeTotal){args[6]=d;args[7]=new Step(encode(increment,"s"),new State(d,nativeTotal));}
  public Executable getExecutable(){try{return SeekStepAdapterTest.class.getDeclaredMethod("check",boolean.class);}catch(Exception e){throw new RuntimeException(e);}}
  public Object getThisObject(){return null;}public List<Object> getArgs(){return Arrays.asList(args);}public Object getArg(int i){return args[i];}
  public Object proceed(){return proceed(args);}public Object proceed(Object[] a){calls++;result=(Step)a[7];return true;}
  public Object proceedWith(Object o){return proceed();}public Object proceedWith(Object o,Object[] a){return proceed(a);}
 }
 public static void main(String[] args)throws Throwable{
  AtomicInteger seconds=new AtomicInteger(5);
  HookCallback hook=SeekStepAdapter.callback(seconds::get,Direction.FORWARD,State.class.getConstructor(Direction.class,int.class),Step.class.getConstructor(long.class,State.class),SeekStepAdapterTest.class.getMethod("encode",int.class,String.class),SeekStepAdapterTest.class.getMethod("decode",long.class,String.class),"s");
  int total=0;
  for(int n=1;n<=6;n++){Chain c=new Chain(Direction.FORWARD,10,total+10);Hooks.intercept(c,hook);check(c.calls==1);check(decode(c.result.a,"s")==5);total=c.result.b.b;check(total==n*5);}
  Chain repeated=new Chain(Direction.FORWARD,20,total+20);Hooks.intercept(repeated,hook);check(decode(repeated.result.a,"s")==10);check(repeated.result.b.b==total+10);
  seconds.set(10);Chain live=new Chain(Direction.FORWARD,10,total+10);Hooks.intercept(live,hook);check(live.result==live.args[7]);check(live.result.b.b==total+10);
  seconds.set(5);Chain back=new Chain(Direction.BACKWARD,10,20);Hooks.intercept(back,hook);check(back.result==back.args[7]);
  Chain idle=new Chain(Direction.FORWARD,10,10);idle.args[7]=null;Hooks.intercept(idle,hook);check(idle.result==null&&idle.calls==1);
  Chain unknown=new Chain(Direction.FORWARD,7,7);Hooks.intercept(unknown,hook);check(unknown.result==unknown.args[7]);
  System.out.println("SeekStepAdapter: "+checks+" assertions passed (initial, continuation, repeated units, live settings, backward, idle)");
 }
}
