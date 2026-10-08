package io.github.jared.xlowerseek;
import org.junit.Test;import static org.junit.Assert.*;
public class PostContextIndexTest {
    @Test public void usesOnlyDirectRelations(){PostContextIndex i=new PostContextIndex();i.put(new PostContextIndex.Post(1,"Prediction","Prediction",0,0));i.put(new PostContextIndex.Post(2,"Quoted","Quoted",0,0));i.put(new PostContextIndex.Post(3,"Unrelated","Unrelated",0,0));i.put(new PostContextIndex.Post(4,"Reply","Reply",1,2));String c=i.forText("Reply").text;assertTrue(c.contains("Prediction"));assertTrue(c.contains("Quoted"));assertFalse(c.contains("Unrelated"));assertFalse(i.forText("Private message").post);}
    @Test public void ambiguousReplyDoesNotGetWrongContext(){PostContextIndex i=new PostContextIndex();i.put(new PostContextIndex.Post(1,"Yes","Yes",9,0));i.put(new PostContextIndex.Post(2,"Yes","Yes",8,0));assertTrue(i.forText("Yes").post);assertEquals("",i.forText("Yes").text);}
    @Test public void boundedContextAndEviction(){PostContextIndex i=new PostContextIndex();i.put(new PostContextIndex.Post(1,"a".repeat(6000),"a".repeat(6000),0,0));i.put(new PostContextIndex.Post(2,"Reply","Reply",1,1));assertTrue(i.forText("Reply").text.length()<1100);for(int n=3;n<270;n++)i.put(new PostContextIndex.Post(n,"item"+n,"item"+n,0,0));assertFalse(i.forText("Reply").post);}
    @Test public void editedPostDropsOldAlias(){PostContextIndex i=new PostContextIndex();i.put(new PostContextIndex.Post(1,"old","old",0,0));i.put(new PostContextIndex.Post(1,"new","new",0,0));assertFalse(i.forText("old").post);assertTrue(i.forText("new").post);}
    @Test public void visibleBindingSurvivesIndexEvictionWhileApiIsPending(){
        PostContextIndex i=new PostContextIndex();i.put(new PostContextIndex.Post(1,"Parent text","Parent text",0,0));i.put(new PostContextIndex.Post(2,"Visible reply","Visible reply",1,0));
        PostContextIndex.Binding binding=new PostContextIndex.Binding();String expected=binding.resolve(i.forText("Visible reply")).text;
        for(int n=3;n<300;n++)i.put(new PostContextIndex.Post(n,"item"+n,"item"+n,0,0));
        assertFalse(i.forText("Visible reply").post);PostContextIndex.Context bound=binding.resolve(i.forText("Visible reply"));assertTrue(bound.post);assertEquals(expected,bound.text);
        assertFalse(new PostContextIndex.Binding().resolve(i.forText("Visible reply")).post);
    }
    @Test public void neverSplitsSurrogatePair(){assertEquals("a",PostContextIndex.clip("a🚀b",2));}
    @Test public void lateMetadataNotifiesOnceAndCanResolveVisibleRow(){
        PostContextIndex index=new PostContextIndex();int[] changed={0};index.onChange(()->changed[0]++);
        assertFalse(index.forText("Visible comment").post);
        PostContextIndex.Post post=new PostContextIndex.Post(9,"Visible comment","Visible comment",10,0);
        index.put(post);index.put(post);assertEquals(1,changed[0]);assertTrue(index.forText("Visible comment").post);
        index.put(new PostContextIndex.Post(10,"Late parent","Late parent",0,0));assertEquals(2,changed[0]);assertTrue(index.forText("Visible comment").text.contains("Late parent"));
    }

    @Test public void displayUrlsStillIdentifyFullScreenPost(){
        PostContextIndex index=new PostContextIndex();index.put(new PostContextIndex.Post(8,"Watch the episode → https://t.co/abc","Watch the episode → https://t.co/abc",0,0));
        assertTrue(index.forText("Watch the episode → spacex.com/content/starsh…").post);
    }

}
