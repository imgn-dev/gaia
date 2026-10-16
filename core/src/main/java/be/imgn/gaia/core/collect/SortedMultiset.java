package be.imgn.gaia.core.collect;

import java.util.Comparator;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.SequencedCollection;
import org.jspecify.annotations.Nullable;

/**
 * A multiset that keeps its elements sorted, by their natural ordering or by a comparator: the multiset counterpart of
 * {@link java.util.NavigableSet}. It iterates over its elements in ascending order, each as many times as it occurs.
 * Its entry set, element set and the views it returns follow the same order.
 *
 * <p>As a {@link SequencedCollection}, its first and last elements are the lowest and the highest: {@link #getFirst()}
 * returns the lowest element, and {@link #removeFirst()} removes one occurrence of it. {@link #addFirst(Object)} and
 * {@link #addLast(Object)} throw {@link UnsupportedOperationException}, since the order decides where an element goes.
 *
 * <p>The views of a range of a sorted multiset, and its {@linkplain #reversed() reversed} view, are live: changes to
 * the view show in the multiset, and the other way around. A range view rejects elements outside its range with
 * {@link IllegalArgumentException}.
 *
 * @param <E> the element type
 * @since 0.1.1
 */
public interface SortedMultiset<E> extends Multiset<E>, SequencedCollection<E> {

    /**
     * Returns the comparator that orders the elements of this multiset.
     *
     * @return the comparator, or {@code null} if the elements are in their natural ordering
     */
    @Nullable
    Comparator<? super E> comparator();

    /**
     * Returns a view of the distinct elements of this multiset, in the same order. Removing an element from the view
     * removes all its occurrences from this multiset. The view does not support adding elements.
     *
     * @return the distinct elements of this multiset
     */
    @Override
    NavigableSet<E> elementSet();

    /**
     * Returns the entry of the lowest element.
     *
     * @return the entry of the lowest element, or an empty {@code Optional} if this multiset is empty
     */
    Optional<Entry<E>> firstEntry();

    /**
     * Returns the entry of the highest element.
     *
     * @return the entry of the highest element, or an empty {@code Optional} if this multiset is empty
     */
    Optional<Entry<E>> lastEntry();

    /**
     * Removes all occurrences of the lowest element, and returns its entry as it was.
     *
     * @return the entry of the element removed, which no longer changes, or an empty {@code Optional} if this multiset
     *     is empty
     * @throws UnsupportedOperationException if this multiset is unmodifiable
     */
    Optional<Entry<E>> pollFirstEntry();

    /**
     * Removes all occurrences of the highest element, and returns its entry as it was.
     *
     * @return the entry of the element removed, which no longer changes, or an empty {@code Optional} if this multiset
     *     is empty
     * @throws UnsupportedOperationException if this multiset is unmodifiable
     */
    Optional<Entry<E>> pollLastEntry();

    /**
     * Returns a view of the elements lower than an element, or equal to it if {@code inclusive} is {@code true}.
     *
     * @param toElement the upper bound of the view
     * @param inclusive whether the view includes {@code toElement}
     * @return a view of the elements up to {@code toElement}
     * @throws IllegalArgumentException if this multiset is itself a range view and {@code toElement} is outside its
     *     range
     */
    SortedMultiset<E> headMultiset(E toElement, boolean inclusive);

    /**
     * Returns a view of the elements higher than an element, or equal to it if {@code inclusive} is {@code true}.
     *
     * @param fromElement the lower bound of the view
     * @param inclusive whether the view includes {@code fromElement}
     * @return a view of the elements from {@code fromElement}
     * @throws IllegalArgumentException if this multiset is itself a range view and {@code fromElement} is outside its
     *     range
     */
    SortedMultiset<E> tailMultiset(E fromElement, boolean inclusive);

    /**
     * Returns a view of the elements between two elements.
     *
     * @param fromElement the lower bound of the view
     * @param fromInclusive whether the view includes {@code fromElement}
     * @param toElement the upper bound of the view
     * @param toInclusive whether the view includes {@code toElement}
     * @return a view of the elements from {@code fromElement} to {@code toElement}
     * @throws IllegalArgumentException if {@code fromElement} is higher than {@code toElement}, or if this multiset is
     *     itself a range view and either bound is outside its range
     */
    SortedMultiset<E> subMultiset(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive);

    /**
     * Returns a view of this multiset in descending order. Its {@linkplain #comparator() comparator} is the reverse of
     * the comparator of this multiset.
     *
     * @return a view of this multiset in descending order
     */
    @Override
    SortedMultiset<E> reversed();
}
