package be.imgn.gaia.core.system;

import java.util.Map;
import java.util.Objects;

/** Copies the fixed values of {@link Environment#of(Map)} and {@link SystemProperties#of(Map)}. */
final class NamedValues {

    private NamedValues() {}

    // Map.copyOf would reject a null too, but with no message saying which entry.
    static Map<String, String> copyOf(Map<String, String> values, String parameter) {
        Objects.requireNonNull(values, parameter);
        for (var entry : values.entrySet()) {
            Objects.requireNonNull(entry.getKey(), parameter + " contains a null name");
            Objects.requireNonNull(entry.getValue(), parameter + " contains a null value for " + entry.getKey());
        }
        return Map.copyOf(values);
    }
}
