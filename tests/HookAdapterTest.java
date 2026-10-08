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
  System.out.println("HookAdapter: "+checks+" assertions passed");
 }
}
