package be.imgn.gaia.core.collect;

import static java.util.Objects.requireNonNull;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * The multisets that map each distinct element to its count. Counts are mutable, so that changing one leaves the map as
 * it is and does not disturb the iterators over it.
 *
 * <p>Every mutator calls {@link #checkModifiable()} first, so that an unmodifiable subclass throws even when the call
 * would change nothing.
 */
abstract class AbstractMapMultiset<E> extends AbstractCollection<E> implements Multiset<E> {

    /** Returns the map from each distinct element to its count. */
    abstract Map<E, Count> counts();

    /** Returns the number of occurrences of all elements. */
    abstract long total();

    /** Records occurrences added, or removed if {@code delta} is negative. */
    abstract void addToTotal(long delta);

    /** Throws {@link UnsupportedOperationException} if this multiset is unmodifiable. */
    void checkModifiable() {}

    @Override
    public int size() {
        return (int) Math.min(total(), Integer.MAX_VALUE);
    }

    @Override
    public boolean isEmpty() {
        return counts().isEmpty();
    }

    @Override
    public boolean contains(@Nullable Object o) {
        return counts().containsKey(o);
    }

    @Override
    public int count(@Nullable Object element) {
        var count = counts().get(element);
        return count == null ? 0 : count.get();
    }

    @Override
    public Iterator<E> iterator() {
        return new OccurrenceIterator();
    }

    // The default spliterator would report size(), capped at Integer.MAX_VALUE, as the exact number of occurrences.
    @Override
    public Spliterator<E> spliterator() {
        return new OccurrenceSpliterator();
    }

    @Override
    public boolean add(E element) {
        add(element, 1);
        return true;
    }

    @Override
    public int add(E element, int occurrences) {
        checkModifiable();
        requireNonNull(element, "element");
        checkNotNegative(occurrences, "occurrences");
        var count = counts().get(element);
        int before = count == null ? 0 : count.get();
        if (occurrences == 0) {
            return before;
        }
        if (occurrences > Integer.MAX_VALUE - before) {
            throw new IllegalArgumentException(
                    "Too many occurrences of " + element + ": " + before + " + " + occurrences);
        }
        if (count == null) {
            counts().put(element, new Count(occurrences));
        } else {
            count.set(count.get() + occurrences);
        }
        addToTotal(occurrences);
        return before;
    }

    @Override
    public boolean remove(@Nullable Object o) {
        return remove(o, 1) > 0;
    }

    @Override
    public int remove(@Nullable Object element, int occurrences) {
        checkModifiable();
        checkNotNegative(occurrences, "occurrences");
        var count = counts().get(element);
        if (count == null) {
            return 0;
        }
        int before = count.get();
        if (occurrences >= before) {
            counts().remove(element);
            addToTotal(-before);
        } else {
            count.set(before - occurrences);
            addToTotal(-occurrences);
        }
        return before;
    }

    @Override
    public int setCount(E element, int count) {
        checkModifiable();
        requireNonNull(element, "element");
        checkNotNegative(count, "count");
        var current = counts().get(element);
        int before = current == null ? 0 : current.get();
        if (count == 0) {
            if (current != null) {
                counts().remove(element);
            }
        } else if (current == null) {
            counts().put(element, new Count(count));
        } else {
            current.set(count);
        }
        addToTotal((long) count - before);
        return before;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        checkModifiable();
        requireNonNull(c, "c");
        if (c instanceof Multiset<? extends E> multiset) {
            multiset.forEachEntry((element, count) -> add(element, count));
            return !multiset.isEmpty();
        }
        return super.addAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        checkModifiable();
        requireNonNull(c, "c");
        return elementSet().removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        checkModifiable();
        requireNonNull(c, "c");
        return elementSet().retainAll(c);
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        checkModifiable();
        requireNonNull(filter, "filter");
        var removed = false;
        for (var entries = counts().entrySet().iterator(); entries.hasNext(); ) {
            var entry = entries.next();
            if (filter.test(entry.getKey())) {
                addToTotal(-entry.getValue().get());
                entries.remove();
                removed = true;
            }
        }
        return removed;
    }

    @Override
    public void clear() {
        checkModifiable();
        var removed = total();
        counts().clear();
        addToTotal(-removed);
    }

    @Override
    public Set<E> elementSet() {
        return new ElementSet();
    }

    @Override
    public Set<Entry<E>> entrySet() {
        return new EntrySet();
    }

    @Override
    public void forEachEntry(ObjIntConsumer<? super E> action) {
        requireNonNull(action, "action");
        for (var entry : counts().entrySet()) {
            action.accept(entry.getKey(), entry.getValue().get());
        }
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Multiset<?> other) || other.elementSet().size() != counts().size()) {
            return false;
        }
        try {
            for (var entry : counts().entrySet()) {
                if (other.count(entry.getKey()) != entry.getValue().get()) {
                    return false;
                }
            }
            return true;
        } catch (ClassCastException e) {
            // A sorted multiset cannot compare elements of another type: it does not contain them.
            return false;
        }
    }

    @Override
    public int hashCode() {
        var hash = 0;
        for (var entry : counts().entrySet()) {
            hash += entry.getKey().hashCode() ^ entry.getValue().get();
        }
        return hash;
    }

    /**
     * Returns each element once, followed by {@code ×} and its count if it occurs more than once, as in {@code [foo ×
     * 3, bar]}: the occurrences may be too many to write one by one.
     */
    @Override
    public String toString() {
        var string = new StringBuilder("[");
        for (var entry : counts().entrySet()) {
            if (string.length() > 1) {
                string.append(", ");
            }
            string.append(entry.getKey());
            var count = entry.getValue().get();
            if (count > 1) {
                string.append(" × ").append(count);
            }
        }
        return string.append(']').toString();
    }

    // Unlike checkArgument, boxes nothing and allocates no array on the calls that pass, which are the common ones.
    private static void checkNotNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative: " + value);
        }
    }

    /** Removes every occurrence of an element, and returns whether there was any. */
    final boolean removeAllOccurrences(@Nullable Object element) {
        checkModifiable();
        var count = counts().remove(element);
        if (count == null) {
            return false;
        }
        addToTotal(-count.get());
        return true;
    }

    // An entry's count must stay positive: removing its element through it would change the entry set while it is
    // iterated, and fail the iteration.
    final int setEntryCount(E element, int count) {
        checkModifiable();
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive: " + count);
        }
        var current = counts().get(element);
        if (current == null) {
            throw new IllegalStateException(element + " is no longer in the multiset");
        }
        int before = current.get();
        current.set(count);
        addToTotal((long) count - before);
        return before;
    }

    /** Returns each element as many times as it occurs. */
    private final class OccurrenceIterator implements Iterator<E> {

        private final Iterator<Map.Entry<E, Count>> entries =
                counts().entrySet().iterator();
        private Map.@Nullable Entry<E, Count> current;
        // Counted against the live count rather than a copy of it, so that a change of the count while the element
        // is iterated is seen.
        private int returned;
        private boolean removable;

        @Override
        public boolean hasNext() {
            var entry = current;
            return (entry != null && returned < entry.getValue().get()) || entries.hasNext();
        }

        @Override
        public E next() {
            var entry = current;
            if (entry == null || returned >= entry.getValue().get()) {
                if (!entries.hasNext()) {
                    throw new NoSuchElementException();
                }
                entry = entries.next();
                current = entry;
                returned = 0;
            }
            returned++;
            removable = true;
            return entry.getKey();
        }

        @Override
        public void remove() {
            checkModifiable();
            var entry = current;
            if (!removable || entry == null) {
                throw new IllegalStateException("next() was not called since the last remove()");
            }
            removable = false;
            var count = entry.getValue();
            if (count.get() == 1) {
                entries.remove();
                current = null;
            } else {
                count.set(count.get() - 1);
                returned--;
            }
            addToTotal(-1);
        }
    }

    /** Iterates over the occurrences, and reads their number when it is first used, as the default spliterator does. */
    private final class OccurrenceSpliterator implements Spliterator<E> {

        private @Nullable Spliterator<E> bound;

        private Spliterator<E> bound() {
            var spliterator = bound;
            if (spliterator == null) {
                spliterator = Spliterators.spliterator(iterator(), total(), 0);
                bound = spliterator;
            }
            return spliterator;
        }

        @Override
        public boolean tryAdvance(Consumer<? super E> action) {
            return bound().tryAdvance(action);
        }

        @Override
        public void forEachRemaining(Consumer<? super E> action) {
            bound().forEachRemaining(action);
        }

        @Override
        public @Nullable Spliterator<E> trySplit() {
            return bound().trySplit();
        }

        @Override
        public long estimateSize() {
            return bound().estimateSize();
        }

        @Override
        public int characteristics() {
            return Spliterator.SIZED | Spliterator.SUBSIZED;
        }
    }

    /**
     * Iterates over the map's entries, removing an element with all its occurrences.
     *
     * @param <T> what the iterator returns for each entry
     */
    private abstract class EntryIterator<T> implements Iterator<T> {

        private final Iterator<Map.Entry<E, Count>> entries =
                counts().entrySet().iterator();
        private Map.@Nullable Entry<E, Count> current;

        abstract T valueOf(Map.Entry<E, Count> entry);

        @Override
        public boolean hasNext() {
            return entries.hasNext();
        }

        @Override
        public T next() {
            var entry = entries.next();
            current = entry;
            return valueOf(entry);
        }

        @Override
        public void remove() {
            checkModifiable();
            var entry = current;
            if (entry == null) {
                throw new IllegalStateException("next() was not called since the last remove()");
            }
            current = null;
            addToTotal(-entry.getValue().get());
            entries.remove();
        }
    }

    /**
     * A view of this multiset, which never adds to it.
     *
     * @param <T> the type of the view's elements
     */
    abstract class View<T> extends AbstractSet<T> {

        @Override
        public int size() {
            return counts().size();
        }

        @Override
        public boolean isEmpty() {
            return counts().isEmpty();
        }

        @Override
        public boolean addAll(Collection<? extends T> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            checkModifiable();
            return super.removeAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            checkModifiable();
            return super.retainAll(c);
        }

        @Override
        public boolean removeIf(Predicate<? super T> filter) {
            checkModifiable();
            return super.removeIf(filter);
        }

        @Override
        public void clear() {
            AbstractMapMultiset.this.clear();
        }
    }

    /** The distinct elements of this multiset. */
    class ElementSet extends View<E> {

        @Override
        public Iterator<E> iterator() {
            return new EntryIterator<>() {
                @Override
                E valueOf(Map.Entry<E, Count> entry) {
                    return entry.getKey();
                }
            };
        }

        @Override
        public boolean contains(@Nullable Object o) {
            return AbstractMapMultiset.this.contains(o);
        }

        @Override
        public boolean remove(@Nullable Object o) {
            return removeAllOccurrences(o);
        }
    }

    /** The distinct elements of this multiset, with their counts. */
    private final class EntrySet extends View<Entry<E>> {

        @Override
        public Iterator<Entry<E>> iterator() {
            return new EntryIterator<>() {
                @Override
                Entry<E> valueOf(Map.Entry<E, Count> entry) {
                    return new LiveEntry(entry.getKey());
                }
            };
        }

        @Override
        public boolean contains(@Nullable Object o) {
            return o instanceof Entry<?> entry && entry.count() > 0 && count(entry.element()) == entry.count();
        }

        @Override
        public boolean remove(@Nullable Object o) {
            checkModifiable();
            return o instanceof Entry<?> entry && contains(entry) && removeAllOccurrences(entry.element());
        }
    }

    /** An entry that reads and writes its count in this multiset. */
    final class LiveEntry implements Entry<E> {

        private final E element;

        LiveEntry(E element) {
            this.element = element;
        }

        @Override
        public E element() {
            return element;
        }

        @Override
        public int count() {
            return AbstractMapMultiset.this.count(element);
        }

        @Override
        public int setCount(int count) {
            return setEntryCount(element, count);
        }

        @Override
        public boolean equals(@Nullable Object o) {
            return o instanceof Entry<?> other && element.equals(other.element()) && count() == other.count();
        }

        @Override
        public int hashCode() {
            return element.hashCode() ^ count();
        }

        @Override
        public String toString() {
            return element + "=" + count();
        }
    }

    /** The number of occurrences of an element, always positive while the element is in the map. */
    static final class Count {

        private int value;

        Count(int value) {
            this.value = value;
        }

        int get() {
            return value;
        }

        void set(int value) {
            this.value = value;
        }
    }
}
