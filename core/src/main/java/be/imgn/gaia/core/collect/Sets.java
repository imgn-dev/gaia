package be.imgn.gaia.core.collect;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Static factories and a collector for unmodifiable sets that iterate in the order their elements are given.
 *
 * <p>The {@code set} methods behave exactly like their {@link Set#of() Set.of} counterparts, and
 * {@link #setCopy(Collection)} like {@link Set#copyOf(Collection) Set.copyOf}, and {@link #toSet()} like
 * {@link Collectors#toUnmodifiableSet()}, except for iteration order. {@code Set.of} and {@code Set.copyOf} leave the
 * order unspecified, and it changes from one run to the next. These sets iterate, stream and convert to arrays in the
 * order the elements were given. Otherwise they are the same:
 *
 * <ul>
 *   <li>They are unmodifiable: every mutator method throws {@link UnsupportedOperationException}, even when the call
 *       would not change the set.
 *   <li>They reject {@code null} elements, and querying them with {@code null}, as in {@code contains(null)}, throws
 *       {@link NullPointerException}.
 *   <li>The {@code set} methods reject duplicate elements with {@link IllegalArgumentException}.
 *       {@link #setCopy(Collection)} and {@link #toSet()} accept duplicates and keep the first occurrence of each.
 *   <li>They are serializable if all their elements are.
 *   <li>They are value-based: do not rely on their identity.
 *   <li>{@code equals} and {@code hashCode} follow the {@link Set} contract, so a set from this class equals a
 *       {@code Set.of} set with the same elements, whatever their order.
 * </ul>
 */
public final class Sets {

    private Sets() {}

    /**
     * Returns an unmodifiable set containing zero elements.
     *
     * @param <E> the element type
     * @return an empty set
     * @see Set#of()
     */
    public static <E> Set<E> set() {
        return ImmutableOrderedSet.of();
    }

    /**
     * Returns an unmodifiable set containing one element.
     *
     * @param <E> the element type
     * @param e1 the single element
     * @return a set containing the given element
     * @see Set#of(Object)
     */
    public static <E> Set<E> set(E e1) {
        return ImmutableOrderedSet.of(e1);
    }

    /**
     * Returns an unmodifiable set containing two elements, iterating in the order given.
     *
     * @param <E> the element type
     * @param e1 the first element
     * @param e2 the second element
     * @return a set containing the given elements
     * @throws IllegalArgumentException if there are any duplicate elements
     * @see Set#of(Object, Object)
     */
    public static <E> Set<E> set(E e1, E e2) {
        return ImmutableOrderedSet.of(e1, e2);
    }

    /**
     * Returns an unmodifiable set containing three elements, iterating in the order given.
     *
     * @param <E> the element type
     * @param e1 the first element
     * @param e2 the second element
     * @param e3 the third element
     * @return a set containing the given elements
     * @throws IllegalArgumentException if there are any duplicate elements
     * @see Set#of(Object, Object, Object)
     */
    public static <E> Set<E> set(E e1, E e2, E e3) {
        return ImmutableOrderedSet.of(e1, e2, e3);
    }

    /**
     * Returns an unmodifiable set containing four elements, iterating in the order given.
     *
     * @param <E> the element type
     * @param e1 the first element
     * @param e2 the second element
     * @param e3 the third element
     * @param e4 the fourth element
     * @return a set containing the given elements
     * @throws IllegalArgumentException if there are any duplicate elements
     * @see Set#of(Object, Object, Object, Object)
     */
    public static <E> Set<E> set(E e1, E e2, E e3, E e4) {
        return ImmutableOrderedSet.of(e1, e2, e3, e4);
    }

    /**
     * Returns an unmodifiable set containing five elements, iterating in the order given.
     *
     * @param <E> the element type
     * @param e1 the first element
     * @param e2 the second element
     * @param e3 the third element
     * @param e4 the fourth element
     * @param e5 the fifth element
     * @return a set containing the given elements
     * @throws IllegalArgumentException if there are any duplicate elements
     * @see Set#of(Object, Object, Object, Object, Object)
     */
    public static <E> Set<E> set(E e1, E e2, E e3, E e4, E e5) {
        return ImmutableOrderedSet.of(e1, e2, e3, e4, e5);
    }

    /**
     * Returns an unmodifiable set containing six elements, iterating in the order given.
     *
     * @param <E> the element type
     * @param e1 the first element
     * @param e2 the second element
     * @param e3 the third element
     * @param e4 the fourth element
     * @param e5 the fifth element
     * @param e6 the sixth element
     * @return a set containing the given elements
     * @throws IllegalArgumentException if there are any duplicate elements
     * @see Set#of(Object, Object, Object, Object, Object, Object)
     */
    public static <E> Set<E> set(E e1, E e2, E e3, E e4, E e5, E e6) {
        return ImmutableOrderedSet.of(e1, e2, e3, e4, e5, e6);
    }

    /**
     * Returns an unmodifiable set containing seven elements, iterating in the order given.
     *
     * @param <E> the element type
     * @param e1 the first element
     * @param e2 the second element
     * @param e3 the third element
     * @param e4 the fourth element
     * @param e5 the fifth element
     * @param e6 the sixth element
     * @param e7 the seventh element
     * @return a set containing the given elements
     * @throws IllegalArgumentException if there are any duplicate elements
     * @see Set#of(Object, Object, Object, Object, Object, Object, Object)
     */
    public static <E> Set<E> set(E e1, E e2, E e3, E e4, E e5, E e6, E e7) {
        return ImmutableOrderedSet.of(e1, e2, e3, e4, e5, e6, e7);
    }

    /**
     * Returns an unmodifiable set containing eight elements, iterating in the order given.
     *
     * @param <E> the element type
     * @param e1 the first element
     * @param e2 the second element
     * @param e3 the third element
     * @param e4 the fourth element
     * @param e5 the fifth element
     * @param e6 the sixth element
     * @param e7 the seventh element
     * @param e8 the eighth element
     * @return a set containing the given elements
     * @throws IllegalArgumentException if there are any duplicate elements
     * @see Set#of(Object, Object, Object, Object, Object, Object, Object, Object)
     */
    // Mirrors Set.of, which goes up to ten elements.
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static <E> Set<E> set(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8) {
        return ImmutableOrderedSet.of(e1, e2, e3, e4, e5, e6, e7, e8);
    }

    /**
     * Returns an unmodifiable set containing nine elements, iterating in the order given.
     *
     * @param <E> the element type
     * @param e1 the first element
     * @param e2 the second element
     * @param e3 the third element
     * @param e4 the fourth element
     * @param e5 the fifth element
     * @param e6 the sixth element
     * @param e7 the seventh element
     * @param e8 the eighth element
     * @param e9 the ninth element
     * @return a set containing the given elements
     * @throws IllegalArgumentException if there are any duplicate elements
     * @see Set#of(Object, Object, Object, Object, Object, Object, Object, Object, Object)
     */
    // Mirrors Set.of, which goes up to ten elements.
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static <E> Set<E> set(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9) {
        return ImmutableOrderedSet.of(e1, e2, e3, e4, e5, e6, e7, e8, e9);
    }

    /**
     * Returns an unmodifiable set containing ten elements, iterating in the order given.
     *
     * @param <E> the element type
     * @param e1 the first element
     * @param e2 the second element
     * @param e3 the third element
     * @param e4 the fourth element
     * @param e5 the fifth element
     * @param e6 the sixth element
     * @param e7 the seventh element
     * @param e8 the eighth element
     * @param e9 the ninth element
     * @param e10 the tenth element
     * @return a set containing the given elements
     * @throws IllegalArgumentException if there are any duplicate elements
     * @see Set#of(Object, Object, Object, Object, Object, Object, Object, Object, Object, Object)
     */
    // Mirrors Set.of, which goes up to ten elements.
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static <E> Set<E> set(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9, E e10) {
        return ImmutableOrderedSet.of(e1, e2, e3, e4, e5, e6, e7, e8, e9, e10);
    }
    /**
     * Returns an unmodifiable set containing an arbitrary number of elements, iterating in the order given.
     *
     * @param <E> the element type
     * @param elements the elements
     * @return a set containing the given elements
     * @throws IllegalArgumentException if there are any duplicate elements
     * @see Set#of(Object[])
     */
    // Safe: the array is only read, by a copying factory, and never stored or exposed.
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> Set<E> set(E... elements) {
        return ImmutableOrderedSet.of(elements);
    }

    /**
     * Returns an unmodifiable set containing the elements of {@code collection}, iterating in the collection's
     * iteration order. Duplicates are allowed: the first occurrence of each element is kept. If {@code collection} is
     * already a set returned by this class, it is returned as is.
     *
     * @param <E> the element type
     * @param collection the collection whose elements to copy
     * @return a set containing the elements of {@code collection}
     * @see Set#copyOf(Collection)
     */
    public static <E> Set<E> setCopy(Collection<? extends E> collection) {
        return ImmutableOrderedSet.copyOf(collection);
    }

    /**
     * Returns a collector into an unmodifiable set that iterates in encounter order: the ordered counterpart of
     * {@link Collectors#toUnmodifiableSet()}. Duplicates are allowed: the first occurrence of each element is kept. The
     * resulting set behaves like the other sets of this class.
     *
     * @param <E> the element type
     * @return a collector into an ordered, unmodifiable set
     */
    public static <E> Collector<E, ?, Set<E>> toSet() {
        return Collector.<E, LinkedHashSet<E>, Set<E>>of(
                LinkedHashSet::new,
                (set, element) -> set.add(requireNonNull(element, "element")),
                (left, right) -> {
                    left.addAll(right);
                    return left;
                },
                ImmutableOrderedSet::copyOf);
    }
}
