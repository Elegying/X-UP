package io.github.jared.xlowerseek;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class AdTimelineFilterTest {
 interface Item{}
 public static class Post implements Item{public Object e;Post(boolean ad){e=ad?new Object():null;}}
 public static class User implements Item{public Object h;User(boolean ad){h=ad?new Object():null;}}
 public static class ImageAd implements Item{}
 public static class TrendData{public Object j;TrendData(boolean ad){j=ad?new Object():null;}}
 public static class Trend implements Item{public TrendData a;Trend(boolean ad){a=new TrendData(ad);}}
 public static class Module implements Item{
  public List a;public String b,c,d;public long e;public String f,g;
  public Module(List a,String b,String c,String d,long e,String f,String g){this.a=a;this.b=b;this.c=c;this.d=d;this.e=e;this.f=f;this.g=g;}
 }
 public static class Wrapped{public Item a;public boolean b;public Wrapped(Item a,boolean b){this.a=a;this.b=b;}}
 public static class Unknown implements Item{public String toString(){throw new AssertionError("Do not inspect text to guess ads");}}
 private AdTimelineFilter filter()throws Exception{return new AdTimelineFilter(Post.class,User.class,ImageAd.class,Trend.class,Module.class,Wrapped.class);}
 private Module module(List list){return new Module(list,"header","footer","display",876L,"entry-id","analytics");}
 @Test public void onlyExplicitAdsAreRemoved()throws Exception{
  Post post=new Post(false);User user=new User(false);Trend trend=new Trend(false);Unknown cursor=new Unknown();
  List input=Arrays.asList(post,new Post(true),user,new User(true),new ImageAd(),trend,new Trend(true),cursor,null);
  List result=filter().filter(input);assertEquals(Arrays.asList(post,user,trend,cursor,null),result);assertEquals(9,input.size());
 }
 @Test public void unchangedListRetainsIdentity()throws Exception{List input=Arrays.asList(new Post(false),new Unknown());assertSame(input,filter().filter(input));}
 @Test public void copyMixedModuleRetainsMetadataAndNormalChildren()throws Exception{
  Wrapped normal=new Wrapped(new Post(false),true),ad=new Wrapped(new Post(true),false);Module original=module(Arrays.asList(normal,ad));
  List input=Collections.singletonList(original);Module result=(Module)filter().filter(input).get(0);
  assertNotSame(original,result);assertEquals(Collections.singletonList(normal),result.a);assertEquals(2,original.a.size());
  assertEquals(original.b,result.b);assertEquals(original.c,result.c);assertEquals(original.d,result.d);assertEquals(original.e,result.e);assertEquals(original.f,result.f);assertEquals(original.g,result.g);
 }
 @Test public void removeAdOnlyModuleButKeepOriginallyEmptyModule()throws Exception{
  Module empty=module(Collections.emptyList());List input=Arrays.asList(module(Collections.singletonList(new Wrapped(new ImageAd(),true))),empty);
  assertEquals(Collections.singletonList(empty),filter().filter(input));assertEquals(2,input.size());
 }
 @Test public void nestedWrapperCopiesAreIsolated()throws Exception{
  Module nested=module(Arrays.asList(new Wrapped(new User(true),false),new Wrapped(new User(false),true)));Wrapped wrapper=new Wrapped(nested,true);Module root=module(Collections.singletonList(wrapper));
  Module result=(Module)filter().filter(Collections.singletonList(root)).get(0);Wrapped copied=(Wrapped)result.a.get(0);
  assertNotSame(wrapper,copied);assertTrue(copied.b);assertEquals(1,((Module)copied.a).a.size());assertEquals(2,nested.a.size());
 }
 @Test public void boundedRecursionKeepsUnknownDeepContent()throws Exception{
  ArrayList list=new ArrayList();Module cycle=module(list);list.add(new Wrapped(cycle,false));List input=Collections.singletonList(cycle);assertSame(input,filter().filter(input));
 }
 @Test public void outputIsReadOnlyAndRepeatedFilteringIsStable()throws Exception{
  AdTimelineFilter f=filter();List result=f.filter(Arrays.asList(new Post(false),new Post(true)));assertSame(result,f.filter(result));
  try{result.clear();fail();}catch(UnsupportedOperationException expected){}
 }
 @Test public void unsupportedShapeFailsBeforeInstalling()throws Exception{
  try{new AdTimelineFilter(Unknown.class,User.class,ImageAd.class,Trend.class,Module.class,Wrapped.class);fail();}catch(NoSuchFieldException expected){}
 }
}
