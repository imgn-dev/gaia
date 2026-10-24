package be.imgn.gaia.core.testing;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.collect.HashMultiset;
import be.imgn.gaia.core.collect.Multiset;
import java.util.Map;
import org.assertj.core.api.AbstractCollectionAssert;
import org.assertj.core.api.ObjectAssert;
import org.jspecify.annotations.Nullable;

/**
 * AssertJ assertions on a {@link Multiset}: the assertions on any collection, such as {@code contains} or
 * {@code hasSize}, which counts occurrences, and assertions on the count of each element.
 *
 * <pre>{@code
 * assertThat(words).hasCount("foo", 2).hasDistinctSize(3).containsExactlyCounts(Map.of("foo", 2, "bar", 1, "baz", 1));
 * }</pre>
 *
 * @param <E> the element type
 * @since 0.1.2
 */
public final class MultisetAssert<E>
        extends AbstractCollectionAssert<MultisetAssert<E>, Multiset<E>, E, ObjectAssert<E>> {

    private MultisetAssert(@Nullable Multiset<E> actual) {
        super(actual, MultisetAssert.class);
    }

    /**
     * Returns assertions on a multiset.
     *
     * @param <E> the element type
     * @param actual the multiset; like any AssertJ assertion, every assertion fails if it is {@code null}
     * @return the assertions
     */
    public static <E> MultisetAssert<E> assertThat(@Nullable Multiset<E> actual) {
        return new MultisetAssert<>(actual);
    }

    /**
     * Verifies that the multiset contains an element a number of times.
     *
     * @param element the element
     * @param count the expected number of occurrences, {@code 0} if the element must be absent
     * @return these assertions
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public MultisetAssert<E> hasCount(E element, int count) {
        requireNonNull(element, "element");
        checkArgument(count >= 0, "count must not be negative: %s", count);
        isNotNull();
        var actualCount = actual.count(element);
        if (actualCount != count) {
            failWithMessage(
                    "Expected the count of %s in %s to be %s, but it was %s", element, actual, count, actualCount);
        }
        return this;
    }

    /**
     * Verifies that the multiset has a number of distinct elements.
     *
     * @param size the expected number of distinct elements
     * @return these assertions
     */
    public MultisetAssert<E> hasDistinctSize(int size) {
        isNotNull();
        var actualSize = actual.elementSet().size();
        if (actualSize != size) {
            failWithMessage("Expected %s to have %s distinct elements, but it has %s", actual, size, actualSize);
        }
        return this;
    }

    /**
     * Verifies that the multiset contains exactly the given elements, each the given number of times.
     *
     * @param counts the expected elements, with their numbers of occurrences
     * @return these assertions
     * @throws IllegalArgumentException if a count is not positive
     */
    public MultisetAssert<E> containsExactlyCounts(Map<? extends E, Integer> counts) {
        requireNonNull(counts, "counts");
        counts.forEach(
                (element, count) -> checkArgument(count > 0, "count of %s must be positive: %s", element, count));
        isNotNull();
        var matches = actual.elementSet().size() == counts.size();
        for (var entry : counts.entrySet()) {
            matches &= actual.count(entry.getKey()) == entry.getValue();
        }
        if (!matches) {
            failWithMessage("Expected %s to have the counts %s, but it has %s", actual, counts, actual.entrySet());
        }
        return this;
    }

    @Override
    protected ObjectAssert<E> toAssert(E value, String description) {
        return new ObjectAssert<>(value).as(description);
    }

    @Override
    protected MultisetAssert<E> newAbstractIterableAssert(Iterable<? extends E> iterable) {
        var multiset = HashMultiset.<E>create();
        iterable.forEach(multiset::add);
        return new MultisetAssert<>(multiset);
    }
}
