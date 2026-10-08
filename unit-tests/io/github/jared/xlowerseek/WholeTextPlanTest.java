package io.github.jared.xlowerseek;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class WholeTextPlanTest {
    @Test public void wholeParagraphIsOneRequest(){String text="Yeah, that aged well.\nThe launch succeeded.";WholeTextPlan p=new WholeTextPlan(text,Collections.emptyList());assertEquals(text,p.encoded);assertEquals("这话可真讽刺。\n发射成功了。",p.decode("这话可真讽刺。\n发射成功了。").text);}
    @Test public void identifiersSurviveAndSpanMoves(){
        String text="Hello @name, see https://x.com/test";WholeTextPlan p=new WholeTextPlan(text,Arrays.asList(new TextTranslationPlan.Range(6,11,true)));
        String answer=p.encoded.replace("Hello ","你好 ").replace(", see ","，看 ");TextTranslationPlan.Result r=p.decode(answer);
        assertEquals("你好 @name，看 https://x.com/test",r.text);assertEquals(3,r.offset(6));assertEquals(8,r.offset(11));
    }
    @Test public void clickableSentenceStillHasMeaningfulText(){WholeTextPlan p=new WholeTextPlan("A complete clickable sentence",Arrays.asList(new TextTranslationPlan.Range(0,29,true)));assertEquals("A complete clickable sentence",p.encoded);assertEquals("完整句子",p.decode("完整句子").text);}
    @Test public void missingOrDuplicateMarkerKeepsOriginal(){WholeTextPlan p=new WholeTextPlan("Hello @name",Collections.emptyList());assertEquals(p.source,p.decode("你好").text);assertEquals(p.source,p.decode(p.encoded+p.encoded).text);}
    @Test public void stylesDoNotSplitRequests(){WholeTextPlan p=new WholeTextPlan("Very nice indeed",Arrays.asList(new TextTranslationPlan.Range(5,9,false)));assertTrue(p.encoded.contains("Very"));assertTrue(p.encoded.contains("nice"));assertTrue(p.encoded.contains("indeed"));TextTranslationPlan.Result r=p.decode(p.encoded.replace("Very ","真是 ").replace("nice","不错").replace(" indeed"," 啊"));assertEquals("真是 不错 啊",r.text);assertEquals(3,r.offset(5));assertEquals(5,r.offset(9));}
    @Test public void displayedBareUrlsAreProtected(){
        WholeTextPlan plan=new WholeTextPlan("Visit spacex.com/content/starsh… today",java.util.Collections.emptyList());
        assertFalse(plan.encoded.contains("spacex.com"));
        assertEquals("访问 spacex.com/content/starsh… 今天",plan.decode(plan.encoded.replace("Visit","访问").replace("today","今天")).text);
    }

}
