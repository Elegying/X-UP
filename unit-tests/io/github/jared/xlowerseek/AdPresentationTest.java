package io.github.jared.xlowerseek;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class AdPresentationTest {
 @Test public void toggleRestoresOriginalEvenFromCapturedFilteredList()throws Exception{
  Object original=Arrays.asList("post","ad");Object filtered=Collections.singletonList("post");int[] calls={0};
  AdPresentation p=new AdPresentation(s->{assertSame(original,s);calls[0]++;return filtered;});
  assertSame(original,p.render(original,false));assertEquals(0,calls[0]);assertSame(filtered,p.render(original,true));
  assertSame(filtered,p.render(filtered,true));assertEquals(1,calls[0]);assertSame(original,p.render(filtered,false));assertSame(filtered,p.render(original,true));assertEquals(1,calls[0]);
 }
 @Test public void differentTimelineDoesNotReuseOldResult()throws Exception{
  Object first=new Object(),second=new Object();int[] calls={0};AdPresentation p=new AdPresentation(s->{calls[0]++;return new Object();});
  Object a=p.render(first,true),b=p.render(second,true);assertNotSame(a,b);assertSame(first,p.render(a,false));assertSame(second,p.render(b,false));assertEquals(2,calls[0]);
 }
 @Test public void failedTransformDoesNotPoisonCache()throws Exception{
  Object source=new Object();int[] calls={0};AdPresentation p=new AdPresentation(s->{if(calls[0]++==0)throw new Exception("shape mismatch");return s;});
  try{p.render(source,true);fail();}catch(Exception expected){}
  assertSame(source,p.render(source,false));assertSame(source,p.render(source,true));assertEquals(2,calls[0]);
 }
 @Test public void cacheEvictionDoesNotLoseRestoration()throws Exception{
  AdPresentation p=new AdPresentation(s->new Object());Object source=new Object(),filtered=p.render(source,true);
  for(int i=0;i<20;i++)p.render(new Object(),true);assertSame(source,p.render(filtered,false));
 }
}
