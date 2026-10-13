package be.imgn.gaia.processing.source;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import javax.lang.model.element.Modifier;

/** Checks and copies of modifier sets. */
final class Modifiers {

    private Modifiers() {}

    static Set<Modifier> copy(Set<Modifier> modifiers) {
        return modifiers.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(modifiers));
    }

    static void requireOnly(Set<Modifier> modifiers, Set<Modifier> allowed, String what) {
        for (Modifier modifier : modifiers) {
            if (!allowed.contains(modifier)) {
                throw new IllegalArgumentException(what + " cannot be " + modifier + ": " + modifiers);
            }
        }
    }
}
