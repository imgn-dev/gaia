package be.imgn.gaia.core.collect;

import static java.util.Objects.requireNonNull;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A modifiable multiset backed by a {@link HashMap}, the multiset counterpart of {@link java.util.HashSet}. Its
 * elements must have consistent {@code equals} and {@code hashCode} methods.
 *
 * <ul>
 *   <li>It iterates in no particular order, with all occurrences of an element in a row.
 *   <li>It rejects {@code null} elements, but querying it with {@code null}, as in {@code count(null)}, is allowed.
 *   <li>Its views and entries are live, and its iterators support {@link java.util.Iterator#remove()}.
 *   <li>Its iterators fail fast when an element is added or removed while they iterate, outside of their own
 *       {@code remove()}. A change of the count of an element already there does not disturb them.
 *   <li>It is serializable if all its elements are.
 *   <li>It is not thread-safe.
 * </ul>
 *
 * @param <E> the element type
 * @since 0.1.1
 */
public final class HashMultiset<E> extends AbstractMapMultiset<E> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Map<E, Count> counts = new HashMap<>();
    private transient long total;

    private HashMultiset() {}

    /**
     * Returns a new, empty multiset.
     *
     * @param <E> the element type
     * @return a new, empty multiset
     */
    public static <E> HashMultiset<E> create() {
        return new HashMultiset<>();
    }

    /**
     * Returns a new multiset containing the elements of a collection, each as many times as it occurs there.
     *
     * @param <E> the element type
     * @param elements the elements to add
     * @return a new multiset containing {@code elements}
     */
    public static <E> HashMultiset<E> create(Collection<? extends E> elements) {
        requireNonNull(elements, "elements");
        var multiset = new HashMultiset<E>();
        multiset.addAll(elements);
        return multiset;
    }

    @Override
    Map<E, Count> counts() {
        return counts;
    }

    @Override
    long total() {
        return total;
    }

    @Override
    void addToTotal(long delta) {
        total += delta;
    }

    @Serial
    private Object writeReplace() {
        return new SerializedForm(this);
    }

    @Serial
    private void readObject(ObjectInputStream in) throws InvalidObjectException {
        throw new InvalidObjectException("SerializedForm required");
    }

    private static final class SerializedForm extends SerializedMultiset {

        @Serial
        private static final long serialVersionUID = 1L;

        SerializedForm(HashMultiset<?> multiset) {
            super(multiset);
        }

        @Serial
        private Object readResolve() throws InvalidObjectException {
            var multiset = HashMultiset.<Object>create();
            forEachEntry(multiset::add);
            return multiset;
        }
    }
}
