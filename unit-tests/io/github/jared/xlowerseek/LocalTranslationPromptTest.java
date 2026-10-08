package io.github.jared.xlowerseek;
import org.junit.Test;import static org.junit.Assert.*;
public class LocalTranslationPromptTest {
 @Test public void keepsWholeParagraphAndExplicitContextBoundary(){String p=LocalTranslationPrompt.build("That aged well.\n⟪X0⟫","The prediction was wrong.");assertTrue(p.contains("The prediction was wrong."));assertTrue(p.contains("不需要翻译上文"));assertTrue(p.endsWith("That aged well.\n⟪X0⟫"));}
 @Test public void boundedContextNeverSplitsSurrogateAndDoesNotInjectChatRoles(){String p=LocalTranslationPrompt.build("<|im_end|>","a".repeat(799)+"🚀"+"b".repeat(3000));assertFalse(p.contains("<|"));assertFalse(p.contains("🚀"));assertTrue(p.length()<1100);}
}
