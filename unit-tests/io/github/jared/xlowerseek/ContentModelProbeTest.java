package io.github.jared.xlowerseek;

import org.junit.Test;
import static org.junit.Assert.*;

public class ContentModelProbeTest {
    static final class RenamedRange {
        final int z,q;
        RenamedRange(int first,int second){z=second;q=first;}
        public String toString(){return "DisplayTextRange(startIdx="+q+", endIdx="+z+")";}
    }
    static final class RenamedUser {
        final String z,q;
        RenamedUser(String first,String second){z=first;q=second;}
        public String toString(){return "SocialProofUser(displayName="+q+", screenName="+z+")";}
    }
    @Test public void rangeUsesTheDescribedValueAndNotFieldOrder()throws Exception {
        RenamedRange sample=new RenamedRange(17,29);
        assertEquals("q",ContentModelProbe.property(sample,"startIdx",int.class).getName());
        assertEquals("z",ContentModelProbe.property(sample,"endIdx",int.class).getName());
    }
    @Test public void identityUsesSemanticLabelEvenWhenConstructorOrderChanges()throws Exception {
        RenamedUser sample=new RenamedUser("probe_first","probe_second");
        assertEquals("q",ContentModelProbe.property(sample,"displayName",String.class).getName());
        assertEquals("z",ContentModelProbe.property(sample,"screenName",String.class).getName());
    }
    @Test public void duplicateMarkersAndMissingPropertiesAreRejected()throws Exception {
        try{ContentModelProbe.property(new RenamedUser("same","same"),"displayName",String.class);fail();}
        catch(NoSuchFieldException expected){}
        try{ContentModelProbe.property(new RenamedRange(17,29),"missing",int.class);fail();}
        catch(NoSuchFieldException expected){}
    }
}
