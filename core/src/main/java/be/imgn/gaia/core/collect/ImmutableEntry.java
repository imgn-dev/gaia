package be.imgn.gaia.core.collect;

import org.jspecify.annotations.Nullable;

/** An entry that no longer changes, such as the entry of an element removed from its multiset. */
final class ImmutableEntry<E> implements Multiset.Entry<E> {

    private final E element;
    private final int count;

    ImmutableEntry(E element, int count) {
        this.element = element;
        this.count = count;
    }

    @Override
    public E element() {
        return element;
    }

    @Override
    public int count() {
        return count;
    }

    @Override
    public int setCount(int count) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof Multiset.Entry<?> other && element.equals(other.element()) && count == other.count();
    }

    @Override
    public int hashCode() {
        return element.hashCode() ^ count;
    }

    @Override
    public String toString() {
        return element + "=" + count;
    }
}
