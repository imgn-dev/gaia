package be.imgn.gaia.core.collect;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.Set;
import java.util.function.ObjIntConsumer;
import org.jspecify.annotations.Nullable;

/**
 * A collection that counts how many times it contains each element: its occurrences. Equal elements are not kept apart;
 * a multiset only knows each distinct element and its count.
 *
 * <pre>{@code
 * Multiset<String> words = HashMultiset.create();
 * words.add("foo");
 * words.add("bar", 2);
 * words.count("bar"); // 2
 * words.size();       // 3
 * }</pre>
 *
 * <p>The methods of {@link Collection} work on occurrences:
 *
 * <ul>
 *   <li>{@link #size()} is the number of occurrences, up to {@link Integer#MAX_VALUE}.
 *   <li>The iterator returns each element as many times as it occurs, all occurrences of an element in a row.
 *   <li>{@link #add(Object)} adds one occurrence, and always returns {@code true}.
 *   <li>{@link #remove(Object)} removes one occurrence.
 *   <li>{@link #removeAll(Collection)} and {@link #retainAll(Collection)} remove or keep every occurrence of each
 *       element.
 * </ul>
 *
 * <p>A count is at most {@link Integer#MAX_VALUE}; an operation that would go beyond throws
 * {@link IllegalArgumentException}. No multiset of this library contains {@code null}.
 *
 * @param <E> the element type
 * @since 0.1.1
 */
public interface Multiset<E> extends Collection<E> {

    /**
     * Returns the number of occurrences of an element.
     *
     * @param element the element to count
     * @return the number of occurrences of {@code element}, or {@code 0} if it is absent
     */
    int count(@Nullable Object element);

    /**
     * Adds occurrences of an element.
     *
     * @param element the element to add
     * @param occurrences the number of occurrences to add, possibly {@code 0}
     * @return the number of occurrences of {@code element} before the call
     * @throws IllegalArgumentException if {@code occurrences} is negative, or if the count of {@code element} would
     *     exceed {@link Integer#MAX_VALUE}
     * @throws UnsupportedOperationException if this multiset is unmodifiable
     */
    int add(E element, int occurrences);

    /**
     * Removes occurrences of an element, as many as there are if there are fewer.
     *
     * @param element the element to remove
     * @param occurrences the number of occurrences to remove, possibly {@code 0}
     * @return the number of occurrences of {@code element} before the call
     * @throws IllegalArgumentException if {@code occurrences} is negative
     * @throws UnsupportedOperationException if this multiset is unmodifiable
     */
    int remove(@Nullable Object element, int occurrences);

    /**
     * Sets the number of occurrences of an element, adding or removing occurrences as needed.
     *
     * @param element the element
     * @param count the new number of occurrences of {@code element}; {@code 0} removes it
     * @return the number of occurrences of {@code element} before the call
     * @throws IllegalArgumentException if {@code count} is negative
     * @throws UnsupportedOperationException if this multiset is unmodifiable
     */
    int setCount(E element, int count);

    /**
     * Returns a view of the distinct elements of this multiset. Removing an element from the view removes all its
     * occurrences from this multiset. The view does not support adding elements.
     *
     * @return the distinct elements of this multiset
     */
    Set<E> elementSet();

    /**
     * Returns a view of the distinct elements of this multiset with their counts, one entry per element, in the order
     * of {@link #elementSet()}. Removing an entry from the view removes all occurrences of its element from this
     * multiset. The view does not support adding entries.
     *
     * @return the entries of this multiset
     */
    Set<Entry<E>> entrySet();

    /**
     * Calls an action with each distinct element of this multiset and its count, in the order of {@link #elementSet()}.
     *
     * @param action the action to call
     */
    default void forEachEntry(ObjIntConsumer<? super E> action) {
        requireNonNull(action, "action");
        for (Entry<E> entry : entrySet()) {
            action.accept(entry.element(), entry.count());
        }
    }

    /**
     * Compares this multiset with an object. They are equal if the object is also a {@code Multiset} and both contain
     * the same elements with the same counts, whatever their order or implementation.
     *
     * @param o the object to compare with
     * @return {@code true} if {@code o} is a multiset with the same elements and counts
     */
    @Override
    boolean equals(@Nullable Object o);

    /**
     * Returns the hash code of this multiset: the hash code of its {@link #entrySet()}, the sum of the hash codes of
     * its entries. Equal multisets have equal hash codes, whatever their implementation.
     *
     * @return the hash code of this multiset
     */
    @Override
    int hashCode();

    /**
     * An element of a multiset with its count. The entries of a modifiable multiset are live: their count follows the
     * multiset, and is {@code 0} once their element is removed from it.
     *
     * @param <E> the element type
     * @since 0.1.1
     */
    interface Entry<E> {

        /**
         * Returns the element of this entry.
         *
         * @return the element
         */
        E element();

        /**
         * Returns the number of occurrences of the element in the multiset.
         *
         * @return the count of the element
         */
        int count();

        /**
         * Sets the number of occurrences of the element in the multiset (optional operation). The count must stay
         * positive, so that the entry set does not change while it is iterated: remove the entry to remove its element.
         *
         * @param count the new count of the element
         * @return the count of the element before the call
         * @throws IllegalArgumentException if {@code count} is not positive
         * @throws IllegalStateException if the element is no longer in the multiset
         * @throws UnsupportedOperationException if the multiset is unmodifiable
         */
        int setCount(int count);

        /**
         * Compares this entry with an object. They are equal if the object is also an {@code Entry} with an equal
         * element and the same count.
         *
         * @param o the object to compare with
         * @return {@code true} if {@code o} is an entry with an equal element and the same count
         */
        @Override
        boolean equals(@Nullable Object o);

        /**
         * Returns the hash code of this entry: {@code element().hashCode() ^ count()}.
         *
         * @return the hash code of this entry
         */
        @Override
        int hashCode();
    }
}
