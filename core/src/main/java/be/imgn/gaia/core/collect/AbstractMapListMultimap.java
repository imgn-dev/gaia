package be.imgn.gaia.core.collect;

import static java.util.Objects.checkIndex;
import static java.util.Objects.requireNonNull;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.RandomAccess;
import org.jspecify.annotations.Nullable;

/**
 * The list multimaps that map each key to an {@link ArrayList} of its values.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
abstract class AbstractMapListMultimap<K, V> extends AbstractMapMultimap<K, V, List<V>> implements ListMultimap<K, V> {

    @Override
    List<V> newValues() {
        return new ArrayList<>();
    }

    @Override
    List<V> unmodifiable(List<V> values) {
        return Collections.unmodifiableList(values);
    }

    @Override
    List<V> view(Object key, @Nullable K addKey) {
        return new ValueList(key, addKey);
    }

    /** The values of a key, read from the map whenever they are used. */
    private final class ValueList extends AbstractList<V> implements RandomAccess {

        private final Object key;
        private final @Nullable K addKey;

        ValueList(Object key, @Nullable K addKey) {
            this.key = key;
            this.addKey = addKey;
        }

        private @Nullable List<V> values() {
            return map().get(key);
        }

        /** Returns the values, after checking that they have an element at {@code index}. */
        private List<V> valuesAt(int index) {
            var values = values();
            checkIndex(index, values == null ? 0 : values.size());
            return values == null ? List.of() : values;
        }

        @Override
        public V get(int index) {
            return valuesAt(index).get(index);
        }

        @Override
        public int size() {
            var values = values();
            return values == null ? 0 : values.size();
        }

        @Override
        public boolean contains(@Nullable Object o) {
            checkQuery(o);
            var values = values();
            return values != null && values.contains(o);
        }

        @Override
        public int indexOf(@Nullable Object o) {
            checkQuery(o);
            var values = values();
            return values == null ? -1 : values.indexOf(o);
        }

        @Override
        public int lastIndexOf(@Nullable Object o) {
            checkQuery(o);
            var values = values();
            return values == null ? -1 : values.lastIndexOf(o);
        }

        @Override
        public V set(int index, V element) {
            checkModifiable();
            requireNonNull(element, "element");
            return valuesAt(index).set(index, element);
        }

        @Override
        public void add(int index, V element) {
            checkModifiable();
            requireNonNull(element, "element");
            var key = addKey;
            if (key == null) {
                throw new UnsupportedOperationException();
            }
            var values = values();
            checkIndex(index, (values == null ? 0 : values.size()) + 1);
            if (values == null) {
                values = newValues();
                map().put(key, values);
            }
            values.add(index, element);
            addToTotal(1);
            modCount++;
        }

        @Override
        public V remove(int index) {
            checkModifiable();
            var values = valuesAt(index);
            V removed = values.remove(index);
            removedFrom(key, values);
            modCount++;
            return removed;
        }

        @Override
        public void clear() {
            if (removeKey(key)) {
                modCount++;
            }
        }
    }
}
