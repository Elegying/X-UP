package io.github.jared.xlowerseek;

import java.util.Collection;
import java.util.function.Predicate;

/** A renamed implementation is acceptable; an ambiguous implementation is never guessed. */
final class CompatibilitySelection {
    static <T> T unique(String id, Collection<T> candidates, Predicate<T> valid) {
        T match = null;
        int count = 0;
        for (T candidate : candidates) {
            if (valid.test(candidate)) { match = candidate; count++; }
        }
        if (count != 1) throw new IllegalStateException(id + ": compatible targets=" + count);
        return match;
    }
    private CompatibilitySelection() {}
}
