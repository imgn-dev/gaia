package be.imgn.gaia.core.collect;

import static java.util.Objects.requireNonNull;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A modifiable list multimap backed by a {@link HashMap} from each key to a {@link java.util.ArrayList} of its values.
 * Its keys must have consistent {@code equals} and {@code hashCode} methods.
 *
 * <ul>
 *   <li>It iterates over its keys in no particular order, and over the values of a key in the order they were added.
 *   <li>It rejects {@code null} keys and values, but querying it with {@code null}, as in {@code containsKey(null)}, is
 *       allowed.
 *   <li>Its views are live, and its iterators support {@link java.util.Iterator#remove()}. The collections of
 *       {@link #asMap()} do not support adding values: add them through {@link #get(Object)} or {@link #put}.
 *   <li>Its iterators fail fast, on a best-effort basis, when it changes while they iterate, outside of their own
 *       {@code remove()}. The iterators of the list {@link #get(Object)} returns fail fast on the changes made through
 *       that list only: a value put in the multimap meanwhile may be skipped without an exception.
 *   <li>It is serializable if all its keys and values are.
 *   <li>It is not thread-safe.
 * </ul>
 *
 * @param <K> the key type
 * @param <V> the value type
 * @since 0.1.2
 */
public final class HashListMultimap<K, V> extends AbstractMapListMultimap<K, V> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Map<K, List<V>> map = new HashMap<>();
    private transient long total;

    private HashListMultimap() {}

    /**
     * Returns a new, empty multimap.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @return a new, empty multimap
     */
    public static <K, V> HashListMultimap<K, V> create() {
        return new HashListMultimap<>();
    }

    /**
     * Returns a new multimap containing the entries of another multimap.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param multimap the multimap whose entries to add
     * @return a new multimap containing the entries of {@code multimap}
     */
    public static <K, V> HashListMultimap<K, V> create(Multimap<? extends K, ? extends V> multimap) {
        requireNonNull(multimap, "multimap");
        var copy = new HashListMultimap<K, V>();
        copy.putAll(multimap);
        return copy;
    }

    @Override
    Map<K, List<V>> map() {
        return map;
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

    private static final class SerializedForm extends SerializedMultimap {

        @Serial
        private static final long serialVersionUID = 1L;

        SerializedForm(HashListMultimap<?, ?> multimap) {
            super(multimap);
        }

        @Serial
        private Object readResolve() {
            var multimap = HashListMultimap.<Object, Object>create();
            forEachKey(multimap::putAll);
            return multimap;
        }
    }
}
