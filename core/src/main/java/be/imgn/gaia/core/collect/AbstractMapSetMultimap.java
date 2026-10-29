package be.imgn.gaia.core.collect;

import static java.util.Objects.requireNonNull;

import java.util.AbstractSet;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The set multimaps that map each key to a set of its values.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
abstract class AbstractMapSetMultimap<K, V> extends AbstractMapMultimap<K, V, Set<V>> implements SetMultimap<K, V> {

    @Override
    Set<V> unmodifiable(Set<V> values) {
        return Collections.unmodifiableSet(values);
    }

    @Override
    Set<V> view(Object key, @Nullable K addKey) {
        return new ValueSet(key, addKey);
    }

    @Override
    public Set<Map.Entry<K, V>> entries() {
        return new EntrySet();
    }

    /** The values of a key, read from the map whenever they are used. */
    private final class ValueSet extends AbstractSet<V> {

        private final Object key;
        private final @Nullable K addKey;

        ValueSet(Object key, @Nullable K addKey) {
            this.key = key;
            this.addKey = addKey;
        }

        @Override
        public int size() {
            var values = map().get(key);
            return values == null ? 0 : values.size();
        }

        @Override
        public boolean contains(@Nullable Object o) {
            checkQuery(o);
            var values = map().get(key);
            return values != null && values.contains(o);
        }

        @Override
        public Iterator<V> iterator() {
            var values = map().get(key);
            if (values == null) {
                return Collections.emptyIterator();
            }
            var iterator = values.iterator();
            return new Iterator<>() {
                private int detachments = detachments();

                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public V next() {
                    return iterator.next();
                }

                @Override
                public void remove() {
                    checkModifiable();
                    if (detachments() != detachments) {
                        throw new ConcurrentModificationException();
                    }
                    iterator.remove();
                    removedFrom(key, values, 1);
                    detachments = detachments();
                }
            };
        }

        @Override
        public boolean add(V value) {
            checkModifiable();
            requireNonNull(value, "value");
            var key = addKey;
            if (key == null) {
                throw new UnsupportedOperationException();
            }
            return put(key, value);
        }

        @Override
        public boolean remove(@Nullable Object o) {
            return AbstractMapSetMultimap.this.remove(key, o);
        }

        @Override
        public void clear() {
            removeKey(key);
        }
    }

    /** The entries, which are distinct. */
    private final class EntrySet extends AbstractSet<Map.Entry<K, V>> {

        @Override
        public int size() {
            return AbstractMapSetMultimap.this.size();
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
            AbstractMapSetMultimap.this.clear();
        }
    }
}
