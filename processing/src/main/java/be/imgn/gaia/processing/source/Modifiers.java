package be.imgn.gaia.processing.source;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import javax.lang.model.element.Modifier;

/** Checks and copies of modifier sets. */
final class Modifiers {

    static final Set<Modifier> ACCESS = EnumSet.of(Modifier.PUBLIC, Modifier.PROTECTED, Modifier.PRIVATE);

    private Modifiers() {}

    static Set<Modifier> copy(Set<Modifier> modifiers) {
        return modifiers.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(modifiers));
    }

    // The modifiers given to one call, none of them null or repeated.
    static Set<Modifier> added(Iterable<Modifier> more, String name, String what) {
        Set<Modifier> added = EnumSet.noneOf(Modifier.class);
        for (Modifier modifier : Checks.elements(more, name)) {
            if (!added.add(modifier)) {
                throw new IllegalArgumentException(what + " cannot have " + modifier + " twice");
            }
        }
        return added;
    }

    static Set<Modifier> added(Modifier[] more, String name, String what) {
        return added(Checks.elements(more, name), name, what);
    }

    static Set<Modifier> union(Set<Modifier> modifiers, Set<Modifier> added) {
        Set<Modifier> union = EnumSet.noneOf(Modifier.class);
        union.addAll(modifiers);
        union.addAll(added);
        return union;
    }

    static void requireOnly(Set<Modifier> modifiers, Set<Modifier> allowed, String what) {
        for (Modifier modifier : modifiers) {
            if (!allowed.contains(modifier)) {
                throw new IllegalArgumentException(what + " cannot be " + modifier + ": " + modifiers);
            }
        }
    }

    static void requireNoneWith(Set<Modifier> modifiers, Modifier modifier, Set<Modifier> conflicting, String what) {
        if (modifiers.contains(modifier)) {
            for (Modifier other : conflicting) {
                if (modifiers.contains(other)) {
                    throw new IllegalArgumentException(what + " cannot be both " + modifier + " and " + other);
                }
            }
        }
    }

    static void requireAtMostOne(Set<Modifier> modifiers, Set<Modifier> exclusive, String what) {
        Modifier first = null;
        for (Modifier modifier : modifiers) {
            if (exclusive.contains(modifier)) {
                if (first != null) {
                    throw new IllegalArgumentException(what + " cannot be both " + first + " and " + modifier);
                }
                first = modifier;
            }
        }
    }
}
