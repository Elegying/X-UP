package io.github.jared.xlowerseek;
import org.junit.Test;import static org.junit.Assert.*;
public class IdentityTextIndexTest {
 @Test public void namesAreExactAndBodiesRemainEligible(){IdentityTextIndex i=new IdentityTextIndex();i.remember("SpaceX");i.remember("Night Rain 🌧️");assertTrue(i.contains("SpaceX"));assertTrue(i.contains(" Night Rain 🌧️ "));assertFalse(i.contains("SpaceX launched a rocket."));assertFalse(i.contains("My biography"));assertFalse(i.contains(null));}
 @Test public void memoryIsBounded(){IdentityTextIndex i=new IdentityTextIndex();for(int n=0;n<2100;n++)i.remember("user"+n);assertFalse(i.contains("user0"));assertTrue(i.contains("user2099"));}
}
