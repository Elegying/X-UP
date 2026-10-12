package io.github.jared.xlowerseek;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompatibilitySelectionTest {
    @Test public void renamedUniqueImplementationIsAccepted() {
        assertEquals("new-obfuscated-name", CompatibilitySelection.unique("display",
                Arrays.asList("unrelated", "new-obfuscated-name"), value -> value.endsWith("name")));
    }
    @Test public void ambiguousStructureIsRejected() {
        assertThrows(IllegalStateException.class, () -> CompatibilitySelection.unique("display",
                Arrays.asList("first-name", "second-name"), value -> value.endsWith("name")));
    }
    @Test public void missingStructureIsRejected() {
        assertThrows(IllegalStateException.class, () -> CompatibilitySelection.unique("display", Collections.emptyList(), value -> true));
    }
    @Test public void invalidCandidateDoesNotHideValidCandidate() {
        assertEquals(Integer.valueOf(42), CompatibilitySelection.unique("model", Arrays.asList(0,42), n -> n > 0));
    }
}
