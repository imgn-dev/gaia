package be.imgn.gaia.core.collect;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * A modifiable sorted multiset backed by a {@link TreeMap}, the multiset counterpart of {@link java.util.TreeSet}. Its
 * ordering must be consistent with {@code equals}: two elements are the same element when they compare equal.
 *
 * <ul>
 *   <li>It iterates in ascending order, with all occurrences of an element in a row.
 *   <li>It rejects {@code null} elements. Querying it with {@code null}, as in {@code count(null)}, throws
 *       {@link NullPointerException} unless its comparator accepts {@code null}.
 *   <li>Its views and entries are live, and its iterators support {@link Iterator#remove()}.
 *   <li>Its iterators fail fast when an element is added or removed while they iterate, outside of their own
 *       {@code remove()}. A change of the count of an element already there does not disturb them.
 *   <li>Finding, adding and removing an element take a time logarithmic in the number of distinct elements.
 *       {@link #size()} takes a constant time on the multiset and its reversed view, and a time linear in the number of
 *       distinct elements on a range view.
 *   <li>It is serializable if all its elements and its comparator are. A view is serialized as a copy.
 *   <li>It is not thread-safe.
 * </ul>
 *
 * @param <E> the element type
 * @since 0.1.1
 */
public final class TreeMultiset<E> extends AbstractMapMultiset<E> implements SortedMultiset<E>, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient NavigableMap<E, Count> counts;

    // The multiset this one is a view of, or null if this one is not a view. Only that multiset keeps the total.
    private final transient @Nullable TreeMultiset<E> root;

    // Whether this multiset holds every element of its root, so that its total is the root's.
    private final transient boolean whole;

    private transient long total;

    private TreeMultiset(NavigableMap<E, Count> counts, @Nullable TreeMultiset<E> root, boolean whole) {
        this.counts = counts;
        this.root = root;
        this.whole = whole;
    }

    /**
     * Returns a new, empty multiset sorted by the natural ordering of its elements.
     *
     * @param <E> the element type
     * @return a new, empty multiset
     */
    public static <E extends Comparable<? super E>> TreeMultiset<E> create() {
        return new TreeMultiset<>(new TreeMap<>(), null, true);
    }

    /**
     * Returns a new multiset sorted by the natural ordering of its elements, containing the elements of a collection,
     * each as many times as it occurs there.
     *
     * @param <E> the element type
     * @param elements the elements to add
     * @return a new multiset containing {@code elements}
     */
    public static <E extends Comparable<? super E>> TreeMultiset<E> create(Collection<? extends E> elements) {
        requireNonNull(elements, "elements");
        TreeMultiset<E> multiset = create();
        multiset.addAll(elements);
        return multiset;
    }

    /**
     * Returns a new, empty multiset sorted by a comparator.
     *
     * @param <E> the element type
     * @param comparator the comparator that orders the elements
     * @return a new, empty multiset
     */
    public static <E> TreeMultiset<E> create(Comparator<? super E> comparator) {
        requireNonNull(comparator, "comparator");
        return new TreeMultiset<>(new TreeMap<>(comparator), null, true);
    }

    @Override
    Map<E, Count> counts() {
        return counts;
    }

    @Override
    long total() {
        var multiset = root;
        if (multiset == null) {
            return total;
        }
        if (whole) {
            return multiset.total;
        }
        long sum = 0;
        for (var count : counts.values()) {
            sum += count.get();
        }
        return sum;
    }

    @Override
    void addToTotal(long delta) {
        var multiset = root;
        if (multiset == null) {
            total += delta;
        } else {
            multiset.total += delta;
        }
    }

    @Override
    public @Nullable Comparator<? super E> comparator() {
        return counts.comparator();
    }

    @Override
    public NavigableSet<E> elementSet() {
        return new NavigableElementSet();
    }

    @Override
    public Optional<Entry<E>> firstEntry() {
        return liveEntry(counts.firstEntry());
    }

    @Override
    public Optional<Entry<E>> lastEntry() {
        return liveEntry(counts.lastEntry());
    }

    @Override
    public Optional<Entry<E>> pollFirstEntry() {
        return removedEntry(counts.pollFirstEntry());
    }

    @Override
    public Optional<Entry<E>> pollLastEntry() {
        return removedEntry(counts.pollLastEntry());
    }

    @Override
    public SortedMultiset<E> headMultiset(E toElement, boolean inclusive) {
        requireNonNull(toElement, "toElement");
        return view(counts.headMap(toElement, inclusive), false);
    }

    @Override
    public SortedMultiset<E> tailMultiset(E fromElement, boolean inclusive) {
        requireNonNull(fromElement, "fromElement");
        return view(counts.tailMap(fromElement, inclusive), false);
    }

    @Override
    public SortedMultiset<E> subMultiset(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
        requireNonNull(fromElement, "fromElement");
        requireNonNull(toElement, "toElement");
        return view(counts.subMap(fromElement, fromInclusive, toElement, toInclusive), false);
    }

    @Override
    public SortedMultiset<E> reversed() {
        return view(counts.descendingMap(), whole);
    }

    private TreeMultiset<E> view(NavigableMap<E, Count> range, boolean all) {
        var multiset = root;
        return new TreeMultiset<>(range, multiset == null ? this : multiset, all);
    }

    private Optional<Entry<E>> liveEntry(Map.@Nullable Entry<E, Count> entry) {
        return entry == null ? Optional.empty() : Optional.of(new LiveEntry(entry.getKey()));
    }

    private Optional<Entry<E>> removedEntry(Map.@Nullable Entry<E, Count> entry) {
        if (entry == null) {
            return Optional.empty();
        }
        var count = entry.getValue().get();
        addToTotal(-count);
        return Optional.of(new ImmutableEntry<>(entry.getKey(), count));
    }

    @Serial
    private Object writeReplace() {
        var copy = new TreeMap<E, Integer>(comparator());
        for (var entry : counts.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().get());
        }
        return new SerializedForm(copy);
    }

    @Serial
    private void readObject(ObjectInputStream in) throws InvalidObjectException {
        throw new InvalidObjectException("SerializedForm required");
    }

    /** The distinct elements of this multiset, in order. Its ranges are the element sets of the matching views. */
    private final class NavigableElementSet extends ElementSet implements NavigableSet<E> {

        @Override
        public @Nullable Comparator<? super E> comparator() {
            return TreeMultiset.this.comparator();
        }

        @Override
        public E first() {
            return counts.firstKey();
        }

        @Override
        public E last() {
            return counts.lastKey();
        }

        @Override
        public @Nullable E lower(E e) {
            return counts.lowerKey(e);
        }

        @Override
        public @Nullable E floor(E e) {
            return counts.floorKey(e);
        }

        @Override
        public @Nullable E ceiling(E e) {
            return counts.ceilingKey(e);
        }

        @Override
        public @Nullable E higher(E e) {
            return counts.higherKey(e);
        }

        @Override
        public @Nullable E pollFirst() {
            return pollFirstEntry().map(Entry::element).orElse(null);
        }

        @Override
        public @Nullable E pollLast() {
            return pollLastEntry().map(Entry::element).orElse(null);
        }

        @Override
        public NavigableSet<E> descendingSet() {
            return TreeMultiset.this.reversed().elementSet();
        }

        @Override
        public Iterator<E> descendingIterator() {
            return descendingSet().iterator();
        }

        @Override
        public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
            return subMultiset(fromElement, fromInclusive, toElement, toInclusive)
                    .elementSet();
        }

        @Override
        public NavigableSet<E> subSet(E fromElement, E toElement) {
            return subSet(fromElement, true, toElement, false);
        }

        @Override
        public NavigableSet<E> headSet(E toElement, boolean inclusive) {
            return headMultiset(toElement, inclusive).elementSet();
        }

        @Override
        public NavigableSet<E> headSet(E toElement) {
            return headSet(toElement, false);
        }

        @Override
        public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
            return tailMultiset(fromElement, inclusive).elementSet();
        }

        @Override
        public NavigableSet<E> tailSet(E fromElement) {
            return tailSet(fromElement, true);
        }
    }

    /**
     * The serialized form: the elements with their counts, in a {@link TreeMap} that also holds the comparator.
     * Deserializing adds them to a new multiset, so that a tampered stream cannot produce one with a {@code null}
     * element or a count that is not positive.
     */
    private static final class SerializedForm implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private transient SortedMap<?, ?> counts;

        SerializedForm(SortedMap<?, ?> counts) {
            this.counts = counts;
        }

        @Serial
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            out.writeObject(counts);
        }

        @Serial
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            if (!(in.readObject() instanceof SortedMap<?, ?> read)) {
                throw new InvalidObjectException("the counts are not a SortedMap");
            }
            counts = read;
        }

        @Serial
        private Object readResolve() throws InvalidObjectException {
            return copyOf(counts);
        }

        private static <T> TreeMultiset<T> copyOf(SortedMap<T, ?> counts) throws InvalidObjectException {
            var multiset = new TreeMultiset<T>(new TreeMap<>(counts.comparator()), null, true);
            for (var entry : counts.entrySet()) {
                if (entry.getKey() == null) {
                    throw new InvalidObjectException("null element");
                }
                if (!(entry.getValue() instanceof Integer count) || count <= 0) {
                    throw new InvalidObjectException(
                            "count of " + entry.getKey() + " is not a positive integer: " + entry.getValue());
                }
                multiset.setCount(entry.getKey(), count);
            }
            return multiset;
        }
    }
}
