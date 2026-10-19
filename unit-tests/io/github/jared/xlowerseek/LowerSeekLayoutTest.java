package io.github.jared.xlowerseek;
import org.junit.Test;
import static org.junit.Assert.*;
public class LowerSeekLayoutTest {
 static class State {}
 static class Old { Old(State s,int tag){} }
 static class Current { Current(State s,byte tag){} }
 static class Wrong { Wrong(Object s,byte tag){} }
 static class Unsupported { Unsupported(State s,long tag){} }
 static class Ambiguous { Ambiguous(State s,int tag){} Ambiguous(State s,byte tag){} }
 @Test public void acceptsObservedDiscriminators(){assertTrue(LowerSeekHook.layoutConstructor(Old.class,State.class));assertTrue(LowerSeekHook.layoutConstructor(Current.class,State.class));}
 @Test public void rejectsUnrelatedOrAmbiguousLayouts(){assertFalse(LowerSeekHook.layoutConstructor(Wrong.class,State.class));assertFalse(LowerSeekHook.layoutConstructor(Unsupported.class,State.class));assertFalse(LowerSeekHook.layoutConstructor(Ambiguous.class,State.class));}
}
