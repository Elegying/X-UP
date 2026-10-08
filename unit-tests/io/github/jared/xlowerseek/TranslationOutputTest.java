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
}
