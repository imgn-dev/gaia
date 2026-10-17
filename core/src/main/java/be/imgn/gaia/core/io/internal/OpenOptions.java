package be.imgn.gaia.core.io.internal;

import static java.util.Objects.requireNonNull;

import java.nio.file.OpenOption;

/** Checks the options a file is opened with, so that a null option fails when it is given rather than when used. */
public final class OpenOptions {

    private OpenOptions() {}

    /**
     * Copies options, checking that neither the array nor an option is null.
     *
     * @param options the options to copy
     * @return a copy of {@code options}
     */
    public static OpenOption[] copyOf(OpenOption[] options) {
        var copy = requireNonNull(options, "options").clone();
        for (var i = 0; i < copy.length; i++) {
            requireNonNull(copy[i], "options contains null");
        }
        return copy;
    }
}
