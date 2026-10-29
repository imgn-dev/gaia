package be.imgn.gaia.processing.source;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.lang.model.SourceVersion;

/** Checks of the collections and names the public API is given. */
final class Checks {

    // The identifiers that are not keywords but cannot name a type (JLS 3.9): "class record {}" does not compile.
    private static final Set<String> RESTRICTED = Set.of("permits", "record", "sealed", "var", "yield");

    private Checks() {}

    // Whether a name can be the simple name of a type, or of a type variable.
    static boolean isTypeName(String name) {
        return SourceVersion.isIdentifier(name) && !SourceVersion.isKeyword(name) && !RESTRICTED.contains(name);
    }

    // A copy is checked, so that a builder can check everything it is given before it changes.
    static <T> List<T> elements(Iterable<? extends T> values, String name) {
        requireNonNull(values, name);
        List<T> copy = new ArrayList<>();
        for (T value : values) {
            if (value == null) {
                throw new NullPointerException(name + "[" + copy.size() + "]");
            }
            copy.add(value);
        }
        return List.copyOf(copy);
    }

    static <T> List<T> elements(T[] values, String name) {
        return elements(Arrays.asList(requireNonNull(values, name)), name);
    }
}
