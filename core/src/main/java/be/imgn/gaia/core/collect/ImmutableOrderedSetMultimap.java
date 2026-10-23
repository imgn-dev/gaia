package be.imgn.gaia.core.collect;

import static java.util.Objects.requireNonNull;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The unmodifiable set multimap behind {@link Multimaps}, which iterates over its keys in the order they first appear,
 * and over the values of a key in the order they were first added. Its map is never changed once built: every mutator
 * throws before reaching it, and every view is wrapped into an unmodifiable one.
 */
final class ImmutableOrderedSetMultimap<K, V> extends AbstractMapSetMultimap<K, V> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Map<K, Set<V>> map;
    private final transient long total;

    private ImmutableOrderedSetMultimap(Map<K, Set<V>> map, long total) {
        this.map = map;
        this.total = total;
    }

    static <K, V> SetMultimap<K, V> copyOf(Multimap<? extends K, ? extends V> multimap) {
        requireNonNull(multimap, "multimap");
        var builder = new Builder<K, V>();
        multimap.forEach((key, value) -> builder.put(
                requireNonNull(key, "multimap contains a null key"),
                requireNonNull(value, "multimap contains a null value")));
        return builder.build();
    }

    @Override
    Map<K, Set<V>> map() {
        return map;
    }

    @Override
    long total() {
        return total;
    }

    @Override
    void addToTotal(long delta) {
        throw new UnsupportedOperationException();
    }

    @Override
    Set<V> newValues() {
        return new LinkedHashSet<>();
    }

    // Like Map.of, every mutator throws, even when the call would not change the multimap.
    @Override
    void checkModifiable() {
        throw new UnsupportedOperationException();
    }

    // Like Map.of, querying with null throws.
    @Override
    void checkQuery(@Nullable Object o) {
        requireNonNull(o);
    }

    @Override
    Set<V> view(Object key, @Nullable K addKey) {
        return Collections.unmodifiableSet(super.view(key, null));
    }

    @Override
    public Set<K> keySet() {
        return Collections.unmodifiableSet(super.keySet());
    }

    @Override
    public Collection<V> values() {
        return Collections.unmodifiableCollection(super.values());
    }

    @Override
    public Set<Map.Entry<K, V>> entries() {
        return Collections.unmodifiableSet(super.entries());
    }

    @Override
    public Map<K, Set<V>> asMap() {
        return Collections.unmodifiableMap(super.asMap());
    }

    @Serial
    private Object writeReplace() {
        return new SerializedForm(this);
    }

    @Serial
    private void readObject(ObjectInputStream in) throws InvalidObjectException {
        throw new InvalidObjectException("SerializedForm required");
    }

    /**
     * Collects entries, keeping the order their keys first appear in.
     *
     * @param <K> the key type
     * @param <V> the value type
     */
    static final class Builder<K, V> {

        private final Map<K, Set<V>> map = new LinkedHashMap<>();
        private long total;

        Builder<K, V> put(K key, V value) {
            if (map.computeIfAbsent(key, unused -> new LinkedHashSet<>()).add(value)) {
                total++;
            }
            return this;
        }

        Builder<K, V> putAll(Builder<K, V> other) {
            other.map.forEach((key, values) -> values.forEach(value -> put(key, value)));
            return this;
        }

        SetMultimap<K, V> build() {
            return new ImmutableOrderedSetMultimap<>(map, total);
        }
    }

    private static final class SerializedForm extends SerializedMultimap {

        @Serial
        private static final long serialVersionUID = 1L;

        SerializedForm(ImmutableOrderedSetMultimap<?, ?> multimap) {
            super(multimap);
        }

        @Serial
        private Object readResolve() {
            var builder = new Builder<Object, Object>();
            forEachKey((key, values) -> values.forEach(value -> builder.put(key, value)));
            return builder.build();
        }
    }
}
