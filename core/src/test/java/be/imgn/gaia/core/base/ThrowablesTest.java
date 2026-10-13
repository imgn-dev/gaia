package be.imgn.gaia.core.base;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ThrowablesTest {

    // Not a String, and not formatted: its % stays as it is.
    private static final Object MESSAGE = new Object() {
        @Override
        public String toString() {
            return "Must be 100%";
        }
    };

    @Test
    void createsAnIllegalArgumentException() {
        var cause = new NumberFormatException("For input string: \"x\"");

        var exception = Throwables.illegalArgument(cause, "Not a port: %s", "x");

        assertThat(exception)
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Not a port: x")
                .hasCause(cause);
    }

    @Test
    void createsAnIllegalArgumentExceptionWithAMessage() {
        var cause = new NumberFormatException();

        assertThat(Throwables.illegalArgument(cause, MESSAGE))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Must be 100%")
                .hasCause(cause);
        assertThat(Throwables.illegalArgument(cause, (Object) null)).hasMessage("null");
    }

    @Test
    void createsAnIllegalStateException() {
        var cause = new IOException("Disk full");

        var exception = Throwables.illegalState(cause, "Cannot save %d of %s", 2, null);

        assertThat(exception)
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot save 2 of null")
                .hasCause(cause);
    }

    @Test
    void createsAnIllegalStateExceptionWithAMessage() {
        var cause = new IOException();

        assertThat(Throwables.illegalState(cause, MESSAGE))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Must be 100%")
                .hasCause(cause);
    }

    @Test
    void formatsInTheRootLocale() {
        var locale = Locale.getDefault(Locale.Category.FORMAT);
        Locale.setDefault(Locale.Category.FORMAT, Locale.FRANCE);
        try {
            assertThat(Throwables.illegalArgument(new IOException(), "%.1f", 1.5))
                    .hasMessage("1.5");
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, locale);
        }
    }
}
