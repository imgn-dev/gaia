package be.imgn.gaia.core.collect;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.stream.Collector;

/**
 * Static factories and a collector for unmodifiable multisets that iterate in the order their elements first appear,
 * with all occurrences of an element in a row: {@code multiset("foo", "bar", "foo")} iterates over {@code foo, foo,
 * bar}.
 *
 * <p>They behave like the sets of {@link Sets}:
 *
 * <ul>
 *   <li>They are unmodifiable: every mutator method throws {@link UnsupportedOperationException}, even when the call
 *       would not change the multiset. That includes the methods of their views, their iterators and their entries.
 *   <li>They reject {@code null} elements, and querying them with {@code null}, as in {@code count(null)}, throws
 *       {@link NullPointerException}.
 *   <li>They are serializable if all their elements are.
 *   <li>They are value-based: do not rely on their identity.
 *   <li>{@code equals} and {@code hashCode} follow the {@link Multiset} contract, so a multiset from this class equals
 *       any multiset with the same elements and counts, whatever their order.
 * </ul>
 *
 * @since 0.1.1
 */
public final class Multisets {

    private Multisets() {}

    /**
     * Returns an unmodifiable multiset containing the given elements, each as many times as it is given.
     *
     * @param <E> the element type
     * @param elements the elements
     * @return a multiset containing {@code elements}
     * @throws IllegalArgumentException if an element is given more than {@link Integer#MAX_VALUE} times
     */
    @SafeVarargs
    public static <E> Multiset<E> multiset(E... elements) {
        requireNonNull(elements, "elements");
        var builder = new ImmutableOrderedMultiset.Builder<E>();
        for (var i = 0; i < elements.length; i++) {
            builder.add(requireNonNull(elements[i], "elements[" + i + "]"), 1);
        }
        return builder.build();
    }

    /**
     * Returns an unmodifiable multiset containing the elements of a collection, each as many times as it occurs there,
     * in the order they first appear in its iteration.
     *
     * @param <E> the element type
     * @param elements the elements to copy
     * @return a multiset containing {@code elements}
     * @throws IllegalArgumentException if an element occurs more than {@link Integer#MAX_VALUE} times
     */
    public static <E> Multiset<E> multisetCopy(Collection<? extends E> elements) {
        requireNonNull(elements, "elements");
        var builder = new ImmutableOrderedMultiset.Builder<E>();
        if (elements instanceof Multiset<? extends E> multiset) {
            multiset.forEachEntry(builder::add);
        } else {
            for (E element : elements) {
                builder.add(requireNonNull(element, "elements contains null"), 1);
            }
        }
        return builder.build();
    }

    /**
     * Returns a collector into an unmodifiable multiset that iterates in the order the elements are first encountered.
     * The resulting multiset behaves like the other multisets of this class.
     *
     * @param <E> the element type
     * @return a collector into an ordered, unmodifiable multiset
     */
    public static <E> Collector<E, ?, Multiset<E>> toMultiset() {
        return Collector.<E, ImmutableOrderedMultiset.Builder<E>, Multiset<E>>of(
                ImmutableOrderedMultiset.Builder::new,
                (builder, element) -> builder.add(requireNonNull(element, "element"), 1),
                ImmutableOrderedMultiset.Builder::addAll,
                ImmutableOrderedMultiset.Builder::build);
    }
}
