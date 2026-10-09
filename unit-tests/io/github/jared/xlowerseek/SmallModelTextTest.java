package io.github.jared.xlowerseek;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class SmallModelTextTest {
 @Test public void preservesAnchorsLinesAndWhitespace()throws Exception{List<String> seen=new ArrayList<>();String out=SmallModelText.translate(" Hello ⟪X0⟫ world.\n  Thank you! ",(s,ja)->{seen.add(s);return "中文";},new TranslationCancellation());assertEquals(" 中文 ⟪X0⟫ 中文\n  中文 ",out);assertEquals(Arrays.asList("Hello","world.","Thank you!"),seen);}
 @Test public void eachSentenceAndAnchorUsesItsOwnLanguage()throws Exception{List<Boolean> seen=new ArrayList<>();SmallModelText.translate("今日は⟪X0⟫nice!",(s,ja)->{seen.add(ja);return "中文";},new TranslationCancellation());assertEquals(Arrays.asList(true,false),seen);}
 @Test public void cancellationStopsLaterSentences()throws Exception{TranslationCancellation c=new TranslationCancellation();int[] calls={0};try{SmallModelText.translate("First. Second.",(s,ja)->{calls[0]++;c.cancel();return "中文";},c);fail();}catch(InterruptedException expected){}assertEquals(1,calls[0]);}
 @Test public void chineseAndEmptyRemainUntouched()throws Exception{assertEquals("中文\n  ",SmallModelText.translate("中文\n  ",(s,ja)->{throw new AssertionError();},new TranslationCancellation()));}
 @Test public void missingOrInvalidEngineUsesGoogleDefault(){assertEquals(LocalEngine.MLKIT,LocalEngine.parse(null));assertEquals(LocalEngine.MLKIT,LocalEngine.parse("unknown"));assertEquals(LocalEngine.MLKIT,LocalEngine.parse(""));}
 @Test public void savedEngineSelectionsArePreserved(){for(LocalEngine engine:LocalEngine.values())assertEquals(engine,LocalEngine.parse(engine.id));}

 @Test public void chineseWithLatinLetterNeverReachesEnglishModel()throws Exception{
  String source="这是中文，看着跟傻B一样😂\n电脑 GPU 和 AI 都正常。";
  assertEquals(source,SmallModelText.translate(source,(s,j)->{throw new AssertionError(s);},new TranslationCancellation()));
 }
 @Test public void unsupportedScriptsRemainExact()throws Exception{
  for(String source:new String[]{"안녕하세요 반갑습니다","مرحبا بالعالم","Привет, как дела?","English и русский", "The rare character is 𠮷."})
   assertEquals(source,SmallModelText.translate(source,(s,j)->{throw new AssertionError(s);},new TranslationCancellation()));
 }
 @Test public void emojiSequencesAndKeycapsBypassInference()throws Exception{
  String emoji="👩🏽‍💻🇯🇵😂🔥1️⃣";
  assertEquals("你好 "+emoji+"!",SmallModelText.translate("Hello "+emoji+"!",(s,j)->{assertEquals("Hello",s);return "你好";},new TranslationCancellation()));
 }
 @Test public void englishSentenceDoesNotTakeJapanesePivot()throws Exception{
  List<Boolean> routes=new ArrayList<>();
  assertEquals("你好。 今天下雨。",SmallModelText.translate("Hello world. 今日は雨です。",(s,j)->{routes.add(j);return j?"今天下雨。":"你好。";},new TranslationCancellation()));
  assertEquals(Arrays.asList(false,true),routes);
 }
 @Test public void malformedSegmentCannotHideBehindExistingChinese()throws Exception{
  for(String bad:new String[]{"⁇ , ⁇", "? ? ?", "坏\uFFFD译文", "未知<unk>"}){
   try{SmallModelText.translate("已有中文。 Hello.",(s,j)->bad,new TranslationCancellation());fail(bad);}catch(IllegalStateException expected){}
  }
 }
 @Test public void compactCountersDoNotEnterModels()throws Exception{
  for(String source:new String[]{"76.4M","7.1K","104K","1,416","1.2B"})assertEquals(source,SmallModelText.translate(source,(s,j)->{throw new AssertionError(s);},new TranslationCancellation()));
 }
}
