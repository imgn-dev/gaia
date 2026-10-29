package be.imgn.gaia.core.collect;

import static java.util.Objects.requireNonNull;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;

/**
 * The multimaps that map each key to a collection of its values, never empty. The views read that map whenever they are
 * used, so that a view of a key's values follows the key as it leaves the multimap and comes back.
 *
 * <p>Every mutator of the multimap calls {@link #checkModifiable()} first, so that an unmodifiable subclass throws even
 * when the call would change nothing. Such a subclass also wraps the views it returns into unmodifiable ones, and
 * checks queries with {@link #checkQuery(Object)}.
 *
 * @param <K> the key type
 * @param <V> the value type
 * @param <C> the type of the collection of a key's values
 */
abstract class AbstractMapMultimap<K, V, C extends Collection<V>> implements Multimap<K, V> {

    /**
     * The number of times values left the map with their key. An iterator over the values of a key compares it before
     * removing one, to fail instead of removing it from values that are no longer the key's.
     */
    private int detachments;

    /** Returns the map from each key to its values. */
    abstract Map<K, C> map();

    /** Returns the number of entries. */
    abstract long total();

    /** Records entries added, or removed if {@code delta} is negative. */
    abstract void addToTotal(long delta);

    /** Returns a new, empty collection for the values of a key. */
    abstract C newValues();

    /** Returns an unmodifiable view of values that this multimap no longer uses. */
    abstract C unmodifiable(C values);

    /**
     * Returns a live view of the values of a key.
     *
     * @param key the key whose values to read
     * @param addKey the key to add the values to, or {@code null} if the view does not support adding
     */
    abstract C view(Object key, @Nullable K addKey);

    /** Throws {@link UnsupportedOperationException} if this multimap is unmodifiable. */
    void checkModifiable() {}

    /** Throws {@link NullPointerException} if this multimap rejects querying with {@code null}. */
    void checkQuery(@Nullable Object o) {}

    @Override
    public int size() {
        return (int) Math.min(total(), Integer.MAX_VALUE);
    }

    @Override
    public boolean isEmpty() {
        return map().isEmpty();
    }

    @Override
    public boolean containsKey(@Nullable Object key) {
        checkQuery(key);
        return map().containsKey(key);
    }

    @Override
    public boolean containsValue(@Nullable Object value) {
        checkQuery(value);
        for (C values : map().values()) {
            if (values.contains(value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean containsEntry(@Nullable Object key, @Nullable Object value) {
        checkQuery(key);
        checkQuery(value);
        var values = map().get(key);
        return values != null && values.contains(value);
    }

    @Override
    public C get(K key) {
        requireNonNull(key, "key");
        return view(key, key);
    }

    @Override
    public Set<K> keySet() {
        return new KeySet();
    }

    @Override
    public Collection<V> values() {
        return new Values();
    }

    @Override
    public Collection<Map.Entry<K, V>> entries() {
        return new Entries();
    }

    @Override
    public Map<K, C> asMap() {
        return new AsMap();
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        requireNonNull(action, "action");
        for (var entry : map().entrySet()) {
            for (V value : entry.getValue()) {
                action.accept(entry.getKey(), value);
            }
        }
    }

    @Override
    public boolean put(K key, V value) {
        checkModifiable();
        requireNonNull(key, "key");
        requireNonNull(value, "value");
        var values = map().get(key);
        if (values == null) {
            values = newValues();
            values.add(value);
            map().put(key, values);
        } else if (!values.add(value)) {
            return false;
        }
        addToTotal(1);
        return true;
    }

    @Override
    public boolean putAll(K key, Iterable<? extends V> values) {
        checkModifiable();
        requireNonNull(key, "key");
        var added = checkedCopy(values);
        var changed = false;
        for (V value : added) {
            changed |= put(key, value);
        }
        return changed;
    }

    @Override
    public boolean putAll(Multimap<? extends K, ? extends V> multimap) {
        checkModifiable();
        requireNonNull(multimap, "multimap");
        // A copy, in case the multimap is this one.
        var entries = new ArrayList<Map.Entry<? extends K, ? extends V>>(multimap.entries());
        var changed = false;
        for (var entry : entries) {
            changed |= put(entry.getKey(), entry.getValue());
        }
        return changed;
    }

    @Override
    public boolean remove(@Nullable Object key, @Nullable Object value) {
        checkModifiable();
        var values = map().get(key);
        if (values == null || !values.remove(value)) {
            return false;
        }
        addToTotal(-1);
        if (values.isEmpty()) {
            detach(key);
        }
        return true;
    }

    @Override
    public C removeAll(@Nullable Object key) {
        checkModifiable();
        var values = detach(key);
        if (values == null) {
            return unmodifiable(newValues());
        }
        addToTotal(-values.size());
        return unmodifiable(values);
    }

    @Override
    public C replaceValues(K key, Iterable<? extends V> values) {
        checkModifiable();
        requireNonNull(key, "key");
        var replacement = newValues();
        replacement.addAll(checkedCopy(values));
        var previous = replacement.isEmpty() ? map().remove(key) : map().put(key, replacement);
        if (previous != null) {
            detachments++;
        }
        addToTotal(replacement.size() - (previous == null ? 0L : previous.size()));
        return unmodifiable(previous == null ? newValues() : previous);
    }

    @Override
    public void clear() {
        checkModifiable();
        addToTotal(-total());
        detachments++;
        map().clear();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o == this || (o instanceof Multimap<?, ?> other && asMap().equals(other.asMap()));
    }

    @Override
    public int hashCode() {
        return asMap().hashCode();
    }

    @Override
    public String toString() {
        return map().toString();
    }

    /** Copies values, all of them checked, so that adding them cannot fail halfway. */
    private static <V> Collection<V> checkedCopy(Iterable<? extends V> values) {
        requireNonNull(values, "values");
        var copy = new ArrayList<V>();
        for (V value : values) {
            copy.add(requireNonNull(value, "values contains null"));
        }
        return copy;
    }

    /** Returns the number of times values left the map with their key. */
    final int detachments() {
        return detachments;
    }

    /** Removes a key from the map, and returns its values, which are no longer used. */
    private @Nullable C detach(@Nullable Object key) {
        var values = map().remove(key);
        if (values != null) {
            detachments++;
        }
        return values;
    }

    /** Removes all the values of a key, and returns whether there was any. */
    final boolean removeKey(@Nullable Object key) {
        checkModifiable();
        var values = detach(key);
        if (values == null) {
            return false;
        }
        addToTotal(-values.size());
        return true;
    }

    /**
     * Records the removal of values through a view of the values of a key, which are still the values of that key in
     * the map. Removes the key once it has no values left.
     */
    final void removedFrom(Object key, C values, int removed) {
        addToTotal(-removed);
        if (values.isEmpty()) {
            detach(key);
        }
    }

    /** Returns an iterator over the entries, made as they are iterated. */
    final Iterator<Map.Entry<K, V>> entryIterator() {
        return new EntryIterator<>() {
            @Override
            Map.Entry<K, V> valueOf(K key, V value) {
                return Map.entry(key, value);
            }
        };
    }

    /** Returns whether an object is an entry of this multimap. */
    final boolean containsEntryObject(@Nullable Object o) {
        checkQuery(o);
        return o instanceof Map.Entry<?, ?> entry && containsEntry(entry.getKey(), entry.getValue());
    }

    /** Removes an object if it is an entry of this multimap. */
    final boolean removeEntryObject(@Nullable Object o) {
        checkModifiable();
        return o instanceof Map.Entry<?, ?> entry && remove(entry.getKey(), entry.getValue());
    }

    /**
     * Iterates over the entries, removing them one by one.
     *
     * @param <T> what the iterator returns for each entry
     */
    private abstract class EntryIterator<T> implements Iterator<T> {

        private final Iterator<Map.Entry<K, C>> keys = map().entrySet().iterator();
        private Map.@Nullable Entry<K, C> current;
        private Iterator<V> values = Collections.emptyIterator();
        private int expectedDetachments = detachments;

        abstract T valueOf(K key, V value);

        @Override
        public boolean hasNext() {
            // The values of a key are never empty.
            return values.hasNext() || keys.hasNext();
        }

        @Override
        public T next() {
            var entry = current;
            if (entry == null || !values.hasNext()) {
                entry = keys.next();
                current = entry;
                values = entry.getValue().iterator();
            }
            return valueOf(entry.getKey(), values.next());
        }

        @Override
        public void remove() {
            checkModifiable();
            if (detachments != expectedDetachments) {
                throw new ConcurrentModificationException();
            }
            var entry = current;
            values.remove();
            if (entry != null) {
                addToTotal(-1);
                if (entry.getValue().isEmpty()) {
                    keys.remove();
                    detachments++;
                    expectedDetachments = detachments;
                }
            }
        }
    }

    /** The distinct keys, removing which removes all their values. */
    private final class KeySet extends AbstractSet<K> {

        @Override
        public int size() {
            return map().size();
        }

        @Override
        public boolean isEmpty() {
            return map().isEmpty();
        }

        @Override
        public boolean contains(@Nullable Object o) {
            return containsKey(o);
        }

        @Override
        public Iterator<K> iterator() {
            return new Iterator<>() {
                private final Iterator<Map.Entry<K, C>> keys = map().entrySet().iterator();
                private Map.@Nullable Entry<K, C> current;

                @Override
                public boolean hasNext() {
                    return keys.hasNext();
                }

                @Override
                public K next() {
                    var entry = keys.next();
                    current = entry;
                    return entry.getKey();
                }

                @Override
                public void remove() {
                    checkModifiable();
                    var entry = current;
                    if (entry == null) {
                        throw new IllegalStateException("next() was not called since the last remove()");
                    }
                    current = null;
                    keys.remove();
                    detachments++;
                    addToTotal(-entry.getValue().size());
                }
            };
        }

        @Override
        public boolean remove(@Nullable Object o) {
            return removeKey(o);
        }

        @Override
        public void clear() {
            AbstractMapMultimap.this.clear();
        }
    }

    /** The values of all keys. */
    private final class Values extends AbstractCollection<V> {

        @Override
        public int size() {
            return AbstractMapMultimap.this.size();
        }

        @Override
        public boolean isEmpty() {
            return map().isEmpty();
        }

        @Override
        public boolean contains(@Nullable Object o) {
            return containsValue(o);
        }

        @Override
        public Iterator<V> iterator() {
            return new EntryIterator<>() {
                @Override
                V valueOf(K key, V value) {
                    return value;
                }
            };
        }

        @Override
        public void clear() {
            AbstractMapMultimap.this.clear();
        }
    }

    /** The entries, which may contain duplicates. */
    private final class Entries extends AbstractCollection<Map.Entry<K, V>> {

        @Override
        public int size() {
            return AbstractMapMultimap.this.size();
        }

        @Override
        public boolean isEmpty() {
            return map().isEmpty();
        }

        @Override
        public boolean contains(@Nullable Object o) {
            return containsEntryObject(o);
        }

        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return entryIterator();
        }

        @Override
        public boolean remove(@Nullable Object o) {
            return removeEntryObject(o);
        }

        @Override
        public void clear() {
            AbstractMapMultimap.this.clear();
        }
    }

    /** This multimap as a map, whose collections do not support adding. */
    private final class AsMap extends AbstractMap<K, C> {

        @Override
        public int size() {
            return map().size();
        }

        @Override
        public boolean isEmpty() {
            return map().isEmpty();
        }

        @Override
        public boolean containsKey(@Nullable Object key) {
            return AbstractMapMultimap.this.containsKey(key);
        }

        @Override
        public boolean containsValue(@Nullable Object value) {
            checkQuery(value);
            // The values are lists or sets, whose equality is well defined, unlike that of their Collection bound.
            Map<?, ?> map = map();
            return map.containsValue(value);
        }

        @Override
        public @Nullable C get(@Nullable Object key) {
            return containsKey(key) ? view(requireNonNull(key), null) : null;
        }

        @Override
        public @Nullable C remove(@Nullable Object key) {
            checkModifiable();
            var values = detach(key);
            if (values == null) {
                return null;
            }
            addToTotal(-values.size());
            return unmodifiable(values);
        }

        @Override
        public Set<K> keySet() {
            return AbstractMapMultimap.this.keySet();
        }

        @Override
        public Set<Map.Entry<K, C>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public int size() {
                    return map().size();
                }

                @Override
                public Iterator<Map.Entry<K, C>> iterator() {
                    var keys = AbstractMapMultimap.this.keySet().iterator();
                    return new Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            return keys.hasNext();
                        }

                        @Override
                        public Map.Entry<K, C> next() {
                            var key = keys.next();
                            return Map.entry(key, view(key, null));
                        }

                        @Override
                        public void remove() {
                            keys.remove();
                        }
                    };
                }

                @Override
                public void clear() {
                    AbstractMapMultimap.this.clear();
                }
            };
        }

        @Override
        public void clear() {
            AbstractMapMultimap.this.clear();
        }
    }
}
