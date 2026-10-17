package be.imgn.gaia.processing.source;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Checks of the collections the public API is given. */
final class Checks {

    private Checks() {}

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
