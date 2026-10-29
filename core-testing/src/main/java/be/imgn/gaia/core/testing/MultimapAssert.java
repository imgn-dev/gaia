package be.imgn.gaia.core.testing;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.collect.Multimap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;
import org.jspecify.annotations.Nullable;

/**
 * AssertJ assertions on a {@link Multimap}.
 *
 * <pre>{@code
 * assertThat(scores).hasSize(3).containsKeys("alice", "bob").hasValues("alice", 3, 5).containsEntry("bob", 4);
 * }</pre>
 *
 * @param <K> the key type
 * @param <V> the value type
 * @since 0.1.2
 */
public final class MultimapAssert<K, V> extends AbstractAssert<MultimapAssert<K, V>, Multimap<K, V>> {

    private MultimapAssert(@Nullable Multimap<K, V> actual) {
        super(actual, MultimapAssert.class);
    }

    /**
     * Returns assertions on a multimap.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param actual the multimap; like any AssertJ assertion, every assertion fails if it is {@code null}
     * @return the assertions
     */
    public static <K, V> MultimapAssert<K, V> assertThat(@Nullable Multimap<K, V> actual) {
        return new MultimapAssert<>(actual);
    }

    /**
     * Verifies that the multimap has no entries.
     *
     * @return these assertions
     */
    public MultimapAssert<K, V> isEmpty() {
        isNotNull();
        if (!actual.isEmpty()) {
            failWithMessage("Expected %s to be empty", actual);
        }
        return this;
    }

    /**
     * Verifies that the multimap has entries.
     *
     * @return these assertions
     */
    public MultimapAssert<K, V> isNotEmpty() {
        isNotNull();
        if (actual.isEmpty()) {
            failWithMessage("Expected %s not to be empty", actual);
        }
        return this;
    }

    /**
     * Verifies that the multimap has a number of entries: of key-value pairs, not of distinct keys.
     *
     * @param size the expected number of entries
     * @return these assertions
     */
    public MultimapAssert<K, V> hasSize(int size) {
        isNotNull();
        if (actual.size() != size) {
            failWithMessage("Expected %s to have %s entries, but it has %s", actual, size, actual.size());
        }
        return this;
    }

    /**
     * Verifies that the multimap contains all the given keys.
     *
     * @param keys the keys
     * @throws IllegalArgumentException if no key is given, which would check nothing
     * @return these assertions
     */
    @SafeVarargs
    public final MultimapAssert<K, V> containsKeys(K... keys) {
        requireNonNull(keys, "keys");
        checkArgument(keys.length > 0, "Give at least one key to look for");
        isNotNull();
        var missing = new ArrayList<K>();
        for (var i = 0; i < keys.length; i++) {
            if (!actual.containsKey(requireNonNull(keys[i], "keys[" + i + "]"))) {
                missing.add(keys[i]);
            }
        }
        if (!missing.isEmpty()) {
            failWithMessage("Expected %s to contain the keys %s", actual, join(missing));
        }
        return this;
    }

    /**
     * Verifies that the multimap contains none of the given keys.
     *
     * @param keys the keys
     * @throws IllegalArgumentException if no key is given, which would check nothing
     * @return these assertions
     */
    @SafeVarargs
    public final MultimapAssert<K, V> doesNotContainKeys(K... keys) {
        requireNonNull(keys, "keys");
        checkArgument(keys.length > 0, "Give at least one key to look for");
        isNotNull();
        var present = new ArrayList<K>();
        for (var i = 0; i < keys.length; i++) {
            if (actual.containsKey(requireNonNull(keys[i], "keys[" + i + "]"))) {
                present.add(keys[i]);
            }
        }
        if (!present.isEmpty()) {
            failWithMessage("Expected %s not to contain the keys %s", actual, join(present));
        }
        return this;
    }

    /**
     * Verifies that the multimap contains an entry.
     *
     * @param key the key
     * @param value the value
     * @return these assertions
     */
    public MultimapAssert<K, V> containsEntry(K key, V value) {
        requireNonNull(key, "key");
        requireNonNull(value, "value");
        isNotNull();
        if (!actual.containsEntry(key, value)) {
            failWithMessage("Expected %s to contain %s=%s", actual, key, value);
        }
        return this;
    }

    /**
     * Verifies that the multimap does not contain an entry.
     *
     * @param key the key
     * @param value the value
     * @return these assertions
     */
    public MultimapAssert<K, V> doesNotContainEntry(K key, V value) {
        requireNonNull(key, "key");
        requireNonNull(value, "value");
        isNotNull();
        if (actual.containsEntry(key, value)) {
            failWithMessage("Expected %s not to contain %s=%s", actual, key, value);
        }
        return this;
    }

    /**
     * Verifies that a key has exactly the given values: in this order if the values of the key are a {@link List}, as
     * in a {@link be.imgn.gaia.core.collect.ListMultimap}, and in any order if they are a {@link Set}, as in a
     * {@link be.imgn.gaia.core.collect.SetMultimap}. Giving no values verifies that the key is absent.
     *
     * @param key the key
     * @param values the expected values of {@code key}
     * @return these assertions
     */
    @SafeVarargs
    public final MultimapAssert<K, V> hasValues(K key, V... values) {
        requireNonNull(key, "key");
        requireNonNull(values, "values");
        var expected = new ArrayList<V>(values.length);
        for (var i = 0; i < values.length; i++) {
            expected.add(requireNonNull(values[i], "values[" + i + "]"));
        }
        isNotNull();
        var actualValues = actual.get(key);
        var matches = actualValues instanceof Set<?> set
                // A set holds each value once: a value expected twice is not there twice.
                ? set.size() == expected.size() && set.equals(new HashSet<>(expected))
                : List.copyOf(actualValues).equals(expected);
        if (!matches) {
            failWithMessage(
                    "Expected the values of %s in %s to be %s, but they were %s", key, actual, expected, actualValues);
        }
        return this;
    }

    private static String join(List<?> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }
}
