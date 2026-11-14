package io.github.jared.xlowerseek;
import org.junit.Test;
import static org.junit.Assert.*;
public class ContinuationCaptureTest {
    static class State {} interface Action {} interface Fallback {}
    final State state=new State(); final Action action=new Action(){}; final Fallback fallback=new Fallback(){};
    Object find(Object... args){return ContinuationCapture.state(args,State.class,Action.class,Fallback.class);}
    @Test public void acceptsTypedAndErasedReorderedCaptures(){
        assertSame(state,find(1f,action,fallback,state));
        assertSame(state,find(action,1f,fallback,state,(byte)1));
    }
    @Test public void rejectsOtherMergedBranchesAndAmbiguousCaptures(){
        assertNull(find(1f,new Object(),state));
        assertNull(find(action,fallback,state,new State()));
        assertNull(find(action,action,fallback,state));
        assertNull(find(action,fallback,null));
    }
}
