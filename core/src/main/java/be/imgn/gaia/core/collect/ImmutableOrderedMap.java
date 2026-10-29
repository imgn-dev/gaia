package be.imgn.gaia.core.collect;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * The unmodifiable, insertion-ordered map behind {@link Maps}. The keys and values are kept twice: in two lists, for
 * the order, and in a {@code Map.copyOf} map, for lookups. That map also gives {@code get(null)} and the other queries
 * the same behavior as {@code Map.of}.
 */
final class ImmutableOrderedMap<K, V> extends AbstractMap<K, V> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<K> keys;
    private final transient List<V> values;
    private final transient Map<K, V> lookup;

    private ImmutableOrderedMap(List<K> keys, List<V> values, Map<K, V> lookup) {
        this.keys = keys;
        this.values = values;
        this.lookup = lookup;
    }

    /**
     * Returns a map of the given keys and values, paired by position. They may contain {@code null}, reported as the
     * parameters of {@link Maps#map}: {@code k1}, {@code v1}, {@code k2}…
     */
    static <K, V> Map<K, V> of(List<K> keys, List<V> values) {
        for (var i = 0; i < keys.size(); i++) {
            requireNonNull(keys.get(i), "k" + (i + 1));
            requireNonNull(values.get(i), "v" + (i + 1));
        }
        return create(keys, values);
    }

    static <K, V> Map<K, V> copyOf(Map<? extends K, ? extends V> map) {
        requireNonNull(map, "map");
        if (map instanceof ImmutableOrderedMap<? extends K, ? extends V> ordered) {
            // Copying these unmodifiable lists and map returns them as they are.
            return new ImmutableOrderedMap<>(
                    List.copyOf(ordered.keys), List.copyOf(ordered.values), Map.copyOf(ordered.lookup));
        }
        var keys = new ArrayList<K>(map.size());
        var values = new ArrayList<V>(map.size());
        map.forEach((key, value) -> {
            keys.add(requireNonNull(key, "map contains a null key"));
            values.add(requireNonNull(value, "map contains a null value"));
        });
        return create(keys, values);
    }

    /** Creates a map of keys and values without {@code null}, paired by position. */
    static <K, V> Map<K, V> create(List<K> keys, List<V> values) {
        var lookup = HashMap.<K, V>newHashMap(keys.size());
        for (var i = 0; i < keys.size(); i++) {
            K key = keys.get(i);
            if (lookup.putIfAbsent(key, values.get(i)) != null) {
                throw new IllegalArgumentException("duplicate key: " + key);
            }
        }
        return new ImmutableOrderedMap<>(List.copyOf(keys), List.copyOf(values), Map.copyOf(lookup));
    }

    @Override
    public int size() {
        return keys.size();
    }

    @Override
    public boolean isEmpty() {
        return keys.isEmpty();
    }

    @Override
    public @Nullable V get(Object key) {
        return lookup.get(key);
    }

    @Override
    public boolean containsKey(Object key) {
        return lookup.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return lookup.containsValue(value);
    }

    @Override
    public Set<K> keySet() {
        return ImmutableOrderedSet.create(keys, lookup.keySet());
    }

    @Override
    public Collection<V> values() {
        return Collections.unmodifiableCollection(values);
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return new EntrySet();
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        requireNonNull(action, "action");
        for (var i = 0; i < keys.size(); i++) {
            action.accept(keys.get(i), values.get(i));
        }
    }

    // Like Map.of, every mutator throws, even when the call would not change the map.

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V putIfAbsent(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V replace(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        throw new UnsupportedOperationException();
    }

    @Serial
    private Object writeReplace() {
        return new SerializedForm(keys.toArray(), values.toArray());
    }

    @Serial
    private void readObject(ObjectInputStream in) throws InvalidObjectException {
        throw new InvalidObjectException("SerializedForm required");
    }

    /** The entries in order, made as they are iterated, like those of {@code Map.of}. */
    private final class EntrySet extends AbstractSet<Map.Entry<K, V>> {

        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return new Iterator<>() {
                private int next;

                @Override
                public boolean hasNext() {
                    return next < keys.size();
                }

                @Override
                public Map.Entry<K, V> next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    var entry = Map.entry(keys.get(next), values.get(next));
                    next++;
                    return entry;
                }
            };
        }

        @Override
        public int size() {
            return keys.size();
        }

        @Override
        public boolean contains(Object o) {
            requireNonNull(o);
            if (!(o instanceof Map.Entry<?, ?> entry)) {
                return false;
            }
            var key = entry.getKey();
            var value = entry.getValue();
            return key != null && value != null && value.equals(lookup.get(key));
        }

        @Override
        public Spliterator<Map.Entry<K, V>> spliterator() {
            return Spliterators.spliterator(this, Spliterator.ORDERED | Spliterator.DISTINCT);
        }

        @Override
        public boolean add(Map.Entry<K, V> e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(Collection<? extends Map.Entry<K, V>> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeIf(Predicate<? super Map.Entry<K, V>> filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * The serialized form: the keys and values in order. Deserializing goes through {@link #of}, so a tampered stream
     * cannot produce a map with {@code null} or duplicate keys.
     */
    private static final class SerializedForm implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private transient Object[] keys;
        private transient Object[] values;

        SerializedForm(Object[] keys, Object[] values) {
            this.keys = keys;
            this.values = values;
        }

        @Serial
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            out.writeInt(keys.length);
            for (var i = 0; i < keys.length; i++) {
                out.writeObject(keys[i]);
                out.writeObject(values[i]);
            }
        }

        @Serial
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            int size = in.readInt();
            if (size < 0) {
                throw new InvalidObjectException("negative size: " + size);
            }
            // Growing with what the stream actually holds, rather than allocating the size it claims, keeps a tampered
            // size from exhausting the heap: the stream runs out of entries first.
            var readKeys = new ArrayList<Object>();
            var readValues = new ArrayList<Object>();
            for (int i = 0; i < size; i++) {
                readKeys.add(in.readObject());
                readValues.add(in.readObject());
            }
            keys = readKeys.toArray();
            values = readValues.toArray();
        }

        @Serial
        private Object readResolve() throws InvalidObjectException {
            try {
                return of(Arrays.asList(keys), Arrays.asList(values));
            } catch (NullPointerException | IllegalArgumentException e) {
                // A tampered stream, reported as deserialization reports the others.
                throw new InvalidObjectException(e.getMessage(), e);
            }
        }
    }
}
