package io.github.jared.xlowerseek;
public final class SwipePolicyTest {
 public static void main(String[] args){
  check(30000,60000,500,1000,60000);check(30000,60000,-500,1000,0);
  check(30000,60000,100,1000,36000);check(30000,60000,-100,1000,24000);
  check(180000,600000,500,1000,240000);check(180000,600000,-500,1000,120000);
  check(0,15000,1000,1000,15000);check(0,15000,-1000,1000,0);
  check(1000,15000,Float.NaN,1000,1000);check(1000,15000,10,0,1000);
  check(1000,-1,10,1000,1000);check(1000,15000,Float.POSITIVE_INFINITY,1000,1000);
  System.out.println("SwipePolicy: 12 assertions passed");
 }
 static void check(long s,long d,float x,float w,long want){long got=SwipePolicy.position(s,d,x,w);if(got!=want)throw new AssertionError(got+" != "+want);}
}
