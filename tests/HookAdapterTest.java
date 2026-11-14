package io.github.jared.xlowerseek;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.*;
import java.util.*;
public final class HookAdapterTest {
 static int checks;
 static void check(boolean ok){checks++;if(!ok)throw new AssertionError("case "+checks);}
 static class Chain implements XposedInterface.Chain {
  Object[] args={3};int calls;Throwable failure;
  public Executable getExecutable(){try{return HookAdapterTest.class.getDeclaredMethod("check",boolean.class);}catch(Exception e){throw new RuntimeException(e);}}
  public Object getThisObject(){return this;}
  public List<Object> getArgs(){return Collections.unmodifiableList(Arrays.asList(args));}
  public Object getArg(int n){return args[n];}
  public Object proceed()throws Throwable{return proceed(args);}
  public Object proceed(Object[] a)throws Throwable{calls++;if(failure!=null)throw failure;return (int)a[0]*2;}
  public Object proceedWith(Object o)throws Throwable{return proceed();}
  public Object proceedWith(Object o,Object[] a)throws Throwable{return proceed(a);}
 }
 public static void main(String[] args)throws Throwable {
  Chain c=new Chain();check(Hooks.intercept(c,new HookCallback(){}) .equals(6));check(c.calls==1);
  c=new Chain();check(Hooks.intercept(c,new HookCallback(){protected void beforeHookedMethod(HookParam p){p.args[0]=5;}}).equals(10));check(c.calls==1);
  c=new Chain();check(Hooks.intercept(c,new HookCallback(){protected void beforeHookedMethod(HookParam p){p.setResult(true);}}).equals(true));check(c.calls==0);
  c=new Chain();check(Hooks.intercept(c,new HookCallback(){protected void afterHookedMethod(HookParam p){p.setResult((int)p.getResult()+1);}}).equals(7));check(c.calls==1);
  c=new Chain();check(Hooks.intercept(c,new HookCallback(){protected void beforeHookedMethod(HookParam p){p.args[0]=99;p.setResult(42);throw new IllegalStateException();}}).equals(6));check(c.calls==1);
  c=new Chain();check(Hooks.intercept(c,new HookCallback(){protected void afterHookedMethod(HookParam p){p.setResult(99);throw new IllegalStateException();}}).equals(6));check(c.calls==1);
  c=new Chain();Throwable original=new IllegalArgumentException("original");c.failure=original;
  try{Hooks.intercept(c,new HookCallback(){});throw new AssertionError();}catch(IllegalArgumentException e){check(e==original);}check(c.calls==1);
  c=new Chain();c.failure=original;check(Hooks.intercept(c,new HookCallback(){protected void afterHookedMethod(HookParam p){if(p.hasThrowable())p.setResult(8);}}).equals(8));check(c.calls==1);
  java.util.concurrent.atomic.AtomicInteger faults=new java.util.concurrent.atomic.AtomicInteger(), callbacks=new java.util.concurrent.atomic.AtomicInteger(), reports=new java.util.concurrent.atomic.AtomicInteger();
  Hooks.observeFailures((feature,error)->{check(feature.equals("probe"));reports.incrementAndGet();});
  HookCallback broken=new HookCallback(){protected void beforeHookedMethod(HookParam p){callbacks.incrementAndGet();p.args[0]=99;throw new IllegalStateException("broken");}};
  for(int i=0;i<5;i++){c=new Chain();check(Hooks.intercept(c,broken,"probe",faults).equals(6));check(c.calls==1);}
  check(callbacks.get()==3);check(reports.get()==3);check(faults.get()==3);
  c=new Chain();c.failure=original;
  try{Hooks.intercept(c,broken,"probe",faults);throw new AssertionError();}catch(IllegalArgumentException e){check(e==original);}
  check(faults.get()==3);Hooks.observeFailures(null);
  System.out.println("HookAdapter: "+checks+" assertions passed");
 }
}
