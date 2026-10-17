package be.imgn.gaia.core.system;

import static java.util.Objects.requireNonNull;

import java.util.Map;

/** Copies the fixed values of {@link Environment#of(Map)} and {@link SystemProperties#of(Map)}. */
final class NamedValues {

    private NamedValues() {}

    // Map.copyOf would reject a null too, but with no message saying which entry.
    static Map<String, String> copyOf(Map<String, String> values, String parameter) {
        requireNonNull(values, parameter);
        for (var entry : values.entrySet()) {
            requireNonNull(entry.getKey(), parameter + " contains a null name");
            requireNonNull(entry.getValue(), parameter + " contains a null value for " + entry.getKey());
        }
        return Map.copyOf(values);
    }
}
