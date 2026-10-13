package be.imgn.gaia.core.base;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Creates exceptions from the exception that caused them.
 *
 * <p>The methods return the exception rather than throw it, so that the caller throws it: the compiler then knows that
 * the code after it is not reached.
 *
 * <pre>{@code
 * int port;
 * try {
 *     port = Integer.parseInt(text);
 * } catch (NumberFormatException e) {
 *     throw Throwables.illegalArgument(e, "Not a port: %s", text);
 * }
 * }</pre>
 *
 * <p>A message format follows the syntax of {@link String#format(Locale, String, Object...)} and is formatted in
 * {@link Locale#ROOT}, so that messages do not depend on the default locale.
 *
 * @since 0.1.1
 */
public final class Throwables {

    private Throwables() {}

    /**
     * Creates an exception that reports an illegal argument, caused by another exception.
     *
     * @param cause the exception that caused it
     * @param message the message of the exception, converted with {@link String#valueOf(Object)}
     * @return a new exception, for the caller to throw
     */
    public static IllegalArgumentException illegalArgument(Throwable cause, @Nullable Object message) {
        return new IllegalArgumentException(String.valueOf(message), cause);
    }

    /**
     * Creates an exception that reports an illegal argument, caused by another exception.
     *
     * @param cause the exception that caused it
     * @param messageFormat the format of the message of the exception
     * @param params the values the format refers to
     * @return a new exception, for the caller to throw
     * @throws java.util.IllegalFormatException if {@code messageFormat} is not a valid format for {@code params}
     */
    @FormatMethod
    public static IllegalArgumentException illegalArgument(
            Throwable cause, @FormatString String messageFormat, @Nullable Object... params) {
        return new IllegalArgumentException(String.format(Locale.ROOT, messageFormat, params), cause);
    }

    /**
     * Creates an exception that reports an illegal state, caused by another exception.
     *
     * @param cause the exception that caused it
     * @param message the message of the exception, converted with {@link String#valueOf(Object)}
     * @return a new exception, for the caller to throw
     */
    public static IllegalStateException illegalState(Throwable cause, @Nullable Object message) {
        return new IllegalStateException(String.valueOf(message), cause);
    }

    /**
     * Creates an exception that reports an illegal state, caused by another exception.
     *
     * @param cause the exception that caused it
     * @param messageFormat the format of the message of the exception
     * @param params the values the format refers to
     * @return a new exception, for the caller to throw
     * @throws java.util.IllegalFormatException if {@code messageFormat} is not a valid format for {@code params}
     */
    @FormatMethod
    public static IllegalStateException illegalState(
            Throwable cause, @FormatString String messageFormat, @Nullable Object... params) {
        return new IllegalStateException(String.format(Locale.ROOT, messageFormat, params), cause);
    }
}
