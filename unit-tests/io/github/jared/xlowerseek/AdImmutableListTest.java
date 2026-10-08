package io.github.jared.xlowerseek;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class AdImmutableListTest {
 public static class Vector extends AbstractList<Object>{
  private final Object[] data;public Vector(Object[] data){this.data=data;}
  public Vector b(Collection<?> values){return values.isEmpty()?this:new Vector(values.toArray());}
  public Object get(int i){return data[i];}public int size(){return data.length;}
 }
 @Test public void emptyResultsHaveDistinctIdentity()throws Exception{AdImmutableList f=new AdImmutableList(Vector.class);Object a=f.copy(Collections.emptyList()),b=f.copy(Collections.emptyList());assertNotSame(a,b);assertEquals(a,b);}
 @Test public void allAdTimelinesRestoreIndependently()throws Exception{
  AdImmutableList f=new AdImmutableList(Vector.class);AdPresentation p=new AdPresentation(source->f.copy(Collections.emptyList()));
  Object first=Arrays.asList("ad-A"),second=Arrays.asList("ad-B");Object a=p.render(first,true),b=p.render(second,true);
  assertNotSame(a,b);assertSame(first,p.render(a,false));assertSame(second,p.render(b,false));
 }
 @Test public void largerListsKeepOrderAndSource()throws Exception{
  AdImmutableList f=new AdImmutableList(Vector.class);List<Integer> source=new ArrayList<>();for(int i=0;i<100;i++)source.add(i);
  Object result=f.copy(source);assertEquals(source,result);assertEquals(100,source.size());assertTrue(result instanceof Vector);
 }
}
