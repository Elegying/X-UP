package io.github.jared.xlowerseek;
import org.junit.Test;import static org.junit.Assert.*;
public class TranslationOutputTest {
 @Test public void malformedOrUntranslatedOutputCannotEnterSuccessCache(){
  String source="Watch ⟪X0⟫ launch ⟪X1⟫";
  for(String bad:new String[]{source,"Only English", "", "看看 ⟪X0⟫", "看看 ⟪X1⟫ ⟪X0⟫", "看看 ⟪X0⟫ ⟪X0⟫ ⟪X1⟫", "看看 ⟪X0⟫ ⟪X1⟫ ⟪Xoops⟫"})assertFalse(bad,TranslationOutput.valid(source,bad));
  assertTrue(TranslationOutput.valid(source,"看看 ⟪X0⟫ 的发射 ⟪X1⟫"));
 }
 @Test public void plainChineseAndProtectedLinksRemainValid(){
  assertTrue(TranslationOutput.valid("Hello","你好"));assertFalse(TranslationOutput.valid("Hello",null));
  assertTrue(TranslationOutput.valid("Open ⟪XX0⟫","打开 ⟪XX0⟫"));
 }

 @Test public void modelRepetitionIsRejectedButIntentionalRepetitionIsKept(){
  assertFalse(TranslationOutput.clean("Good morning","早啊 早啊 早啊 早啊 早啊"));
  assertFalse(TranslationOutput.clean("Good morning","早,早,早,早,早,早,早,早"));
  assertTrue(TranslationOutput.clean("go go go go go go go go","走走走走走走走走"));
  assertTrue(TranslationOutput.clean("Very very good","非常非常好"));
 }
 @Test public void introducedUnknownCharactersAreRejectedWithoutDeletingRealQuestions(){
  for(String bad:new String[]{"已有中文。⁇", "中文\uFFFD", "中文<unk>"})assertFalse(TranslationOutput.valid("已有中文。 Hello",bad));
  assertTrue(TranslationOutput.valid("Why? Really??","为什么？真的??"));
  assertTrue(TranslationOutput.clean("Literal ⁇ mark","字面 ⁇ 符号"));
  assertFalse(TranslationOutput.clean("Hello","? ? ?"));
  assertFalse(TranslationOutput.clean("Hello",null));
 }
}
