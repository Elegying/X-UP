package io.github.jared.xlowerseek;
public final class WeakIdentityMapTest {
 public static void main(String[] args){
  WeakIdentityMap<String,String> map=new WeakIdentityMap<>();
  String a=new String("same"),b=new String("same");map.put(a,"first");map.put(b,"second");
  if(!"first".equals(map.get(a)))throw new AssertionError("identity a");
  if(!"second".equals(map.get(b)))throw new AssertionError("identity b");
  if(map.get(new String("same"))!=null)throw new AssertionError("equal text must not alias");
  map.put(a,"updated");if(!"updated".equals(map.get(a)))throw new AssertionError("update");
  System.out.println("WeakIdentityMap: 4 assertions passed");
 }
}
