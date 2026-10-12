package io.github.jared.xlowerseek;

import androidx.compose.runtime.Composer;
import androidx.compose.ui.Modifier;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ComposeTargetsTest {
    static class CoroutineWithSameString {
        public void invoke(String text, Composer composer) {}
    }
    static class RenamedWrapper {
        public static void render(String text, boolean links, Composer composer, int changed) {}
        public static void template(String text, List<?> offsets, Composer composer, int changed) {}
    }
    static class AddedOptionalParameter {
        public static void render(String text, boolean links, Object optional, Composer composer, int changed) {}
    }
    static class Foundation {
        public static void display(String text, Modifier modifier, Composer composer, int changed) {}
        public static void input(String text, Object onChange, Modifier modifier, Composer composer, int changed) {}
    }
    private static boolean wrapper(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).anyMatch(m -> ComposeTargets.isDisplayMethod(m, CharSequence.class, false));
    }
    @Test public void sameStringInCoroutineDoesNotMakeDisplayTargetAmbiguous() {
        assertEquals(RenamedWrapper.class, CompatibilitySelection.unique("linked-text",
                Arrays.asList(CoroutineWithSameString.class, RenamedWrapper.class), ComposeTargetsTest::wrapper));
    }
    @Test public void optionalParameterCanBeAddedWithoutChangingDisplayContract() {
        assertTrue(wrapper(AddedOptionalParameter.class));
    }
    @Test public void templateOffsetsAreNotTranslatedBeforeTheirWrapper() throws Exception {
        Method template=RenamedWrapper.class.getDeclaredMethod("template",String.class,List.class,Composer.class,int.class);
        assertFalse(ComposeTargets.isDisplayMethod(template,CharSequence.class,false));
    }
    @Test public void foundationInputIsExcludedByRoleOrder() {
        List<Method> selected=ComposeTargets.displayMethods(Foundation.class,CharSequence.class);
        assertEquals(1,selected.size());assertEquals("display",selected.get(0).getName());
    }
}
