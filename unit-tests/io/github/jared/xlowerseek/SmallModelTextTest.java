package io.github.jared.xlowerseek;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class SmallModelTextTest {
 @Test public void preservesAnchorsLinesAndWhitespace()throws Exception{List<String> seen=new ArrayList<>();String out=SmallModelText.translate(" Hello ⟪X0⟫ world.\n  Thank you! ",(s,ja)->{seen.add(s);return "中文";},new TranslationCancellation());assertEquals(" 中文 ⟪X0⟫ 中文\n  中文 ",out);assertEquals(Arrays.asList("Hello","world.","Thank you!"),seen);}
 @Test public void japaneseRouteRemainsAcrossMarkers()throws Exception{List<Boolean> seen=new ArrayList<>();SmallModelText.translate("今日は⟪X0⟫nice!",(s,ja)->{seen.add(ja);return "中文";},new TranslationCancellation());assertEquals(Arrays.asList(true,true),seen);}
 @Test public void cancellationStopsLaterSentences()throws Exception{TranslationCancellation c=new TranslationCancellation();int[] calls={0};try{SmallModelText.translate("First. Second.",(s,ja)->{calls[0]++;c.cancel();return "中文";},c);fail();}catch(InterruptedException expected){}assertEquals(1,calls[0]);}
 @Test public void chineseAndEmptyRemainUntouched()throws Exception{assertEquals("中文\n  ",SmallModelText.translate("中文\n  ",(s,ja)->{throw new AssertionError();},new TranslationCancellation()));}
 @Test public void invalidEngineRetainsTencentDefault(){assertEquals(LocalEngine.TENCENT,LocalEngine.parse(null));assertEquals(LocalEngine.TENCENT,LocalEngine.parse("unknown"));assertEquals(LocalEngine.OPUS,LocalEngine.parse("opus"));}
}
