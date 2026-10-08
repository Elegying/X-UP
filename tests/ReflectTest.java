package io.github.jared.xlowerseek;
public final class ReflectTest {
 static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("case "+checks);}
 static class Parent {private long position=7;public String invoke(Object o){return "object";} }
 static class Child extends Parent {private static String NAME="static";private float speed;Child(float speed,float pitch){this.speed=speed;}private long seekTo(long n){return n;}public String invoke(String s){return "string";}private void fail(){throw new IllegalArgumentException("original");}}
 public static void main(String[] args){
  Object o=Reflect.newInstance(Child.class,2f,1f);
  check(Reflect.getFloatField(o,"speed")==2f);check(Reflect.getLongField(o,"position")==7);
  check(Reflect.callMethod(o,"seekTo",123L).equals(123L));check(Reflect.callMethod(o,"invoke","text").equals("string"));
  check(Reflect.callMethod(o,"invoke",new Object()).equals("object"));check(Reflect.getStaticObjectField(Child.class,"NAME").equals("static"));
  check(Reflect.findMethodExactIfExists(Child.class,"seekTo",long.class)!=null);check(Reflect.findMethodExactIfExists(Child.class,"missing")==null);
  try{Reflect.callMethod(o,"fail");throw new AssertionError();}catch(IllegalArgumentException e){check(e.getMessage().equals("original"));}
  System.out.println("Reflect: "+checks+" assertions passed");
 }
}
