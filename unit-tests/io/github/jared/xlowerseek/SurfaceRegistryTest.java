package io.github.jared.xlowerseek;
import org.junit.Test;import static org.junit.Assert.*;
public class SurfaceRegistryTest {
 @Test public void reusedSurfaceBelongsOnlyToNewestPlayer(){
  SurfaceRegistry<Object,Object> r=new SurfaceRegistry<>();Object first=new Object(),second=new Object(),view=new Object();
  r.set(first,view);r.set(second,view);assertFalse(r.snapshot().containsKey(first));assertSame(view,r.snapshot().get(second).get());assertEquals(1,r.snapshot().size());
  r.remove(first);assertEquals(1,r.snapshot().size());r.set(second,null);assertTrue(r.snapshot().isEmpty());
 }
 @Test public void replacingOneSurfaceDoesNotDropOtherPlayers(){
  SurfaceRegistry<Object,Object> r=new SurfaceRegistry<>();Object first=new Object(),second=new Object(),one=new Object(),two=new Object(),replacement=new Object();
  r.set(first,one);r.set(second,two);r.set(first,replacement);assertEquals(2,r.snapshot().size());assertSame(two,r.snapshot().get(second).get());assertSame(replacement,r.snapshot().get(first).get());
 }
}
