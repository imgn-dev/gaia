package be.imgn.gaia.core.base;

import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Checks that a method is called correctly, and throws the matching exception if not.
 *
 * <pre>{@code
 * Preconditions.checkArgument(count >= 0, "count must not be negative: %d", count);
 * Preconditions.checkState(!closed, "Already closed");
 * }</pre>
 *
 * <p>A message is only built if the check fails. A message format follows the syntax of {@link String#format(Locale,
 * String, Object...)} and is formatted in {@link Locale#ROOT}, so that messages do not depend on the default locale.
 * Use {@link java.util.Objects#requireNonNull(Object, String)} to check that a value is not null.
 *
 * @since 0.1.1
 */
public final class Preconditions {

    private Preconditions() {}

    /**
     * Checks an expression about the arguments of the calling method.
     *
     * @param expression the expression to check
     * @throws IllegalArgumentException if {@code expression} is false
     */
    public static void checkArgument(boolean expression) {
        if (!expression) {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Checks an expression about the arguments of the calling method.
     *
     * @param expression the expression to check
     * @param message the message of the exception, converted with {@link String#valueOf(Object)}
     * @throws IllegalArgumentException if {@code expression} is false
     */
    public static void checkArgument(boolean expression, @Nullable Object message) {
        if (!expression) {
            throw new IllegalArgumentException(String.valueOf(message));
        }
    }

    /**
     * Checks an expression about the arguments of the calling method.
     *
     * @param expression the expression to check
     * @param messageFormat the format of the message of the exception
     * @param params the values the format refers to
     * @throws IllegalArgumentException if {@code expression} is false
     * @throws java.util.IllegalFormatException if {@code expression} is false and {@code messageFormat} is not a valid
     *     format for {@code params}
     */
    @FormatMethod
    public static void checkArgument(
            boolean expression, @FormatString String messageFormat, @Nullable Object... params) {
        requireNonNull(messageFormat, "messageFormat");
        requireNonNull(params, "params");
        if (!expression) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, messageFormat, params));
        }
    }

    /**
     * Checks an expression about the state of the object the calling method is called on.
     *
     * @param expression the expression to check
     * @throws IllegalStateException if {@code expression} is false
     */
    public static void checkState(boolean expression) {
        if (!expression) {
            throw new IllegalStateException();
        }
    }

    /**
     * Checks an expression about the state of the object the calling method is called on.
     *
     * @param expression the expression to check
     * @param message the message of the exception, converted with {@link String#valueOf(Object)}
     * @throws IllegalStateException if {@code expression} is false
     */
    public static void checkState(boolean expression, @Nullable Object message) {
        if (!expression) {
            throw new IllegalStateException(String.valueOf(message));
        }
    }

    /**
     * Checks an expression about the state of the object the calling method is called on.
     *
     * @param expression the expression to check
     * @param messageFormat the format of the message of the exception
     * @param params the values the format refers to
     * @throws IllegalStateException if {@code expression} is false
     * @throws java.util.IllegalFormatException if {@code expression} is false and {@code messageFormat} is not a valid
     *     format for {@code params}
     */
    @FormatMethod
    public static void checkState(boolean expression, @FormatString String messageFormat, @Nullable Object... params) {
        requireNonNull(messageFormat, "messageFormat");
        requireNonNull(params, "params");
        if (!expression) {
            throw new IllegalStateException(String.format(Locale.ROOT, messageFormat, params));
        }
    }
}
