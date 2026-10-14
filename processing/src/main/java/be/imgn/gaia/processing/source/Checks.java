package be.imgn.gaia.processing.source;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Checks of the collections the public API is given. */
final class Checks {

    private Checks() {}

    // A copy is checked, so that a builder can check everything it is given before it changes.
    static <T> List<T> elements(Iterable<? extends T> values, String name) {
        Objects.requireNonNull(values, name);
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
        return elements(Arrays.asList(Objects.requireNonNull(values, name)), name);
    }
}
