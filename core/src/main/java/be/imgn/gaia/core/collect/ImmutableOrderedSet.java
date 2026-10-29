package be.imgn.gaia.core.collect;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Predicate;

/**
 * The unmodifiable, insertion-ordered set behind {@link Sets}. The elements are kept twice: in a list, for the order,
 * and in a {@code Set.of} set, for lookups. That set also gives {@code contains(null)} the same behavior as
 * {@code Set.of}.
 */
final class ImmutableOrderedSet<E> extends AbstractSet<E> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final ImmutableOrderedSet<Object> EMPTY = new ImmutableOrderedSet<>(List.of(), Set.of());

    private final transient List<E> elements;
    private final transient Set<E> lookup;

    private ImmutableOrderedSet(List<E> elements, Set<E> lookup) {
        this.elements = elements;
        this.lookup = lookup;
    }

    // Safe: List.of copies the array, which is never stored or exposed.
    @SafeVarargs
    @SuppressWarnings("varargs")
    static <E> Set<E> of(E... input) {
        requireNonNull(input, "elements");
        for (var i = 0; i < input.length; i++) {
            if (input[i] == null) {
                throw new NullPointerException("elements[" + i + "]");
            }
        }
        List<E> elements = List.of(input);
        Set<E> lookup = Set.copyOf(elements);
        if (lookup.size() != elements.size()) {
            throw new IllegalArgumentException("duplicate element: " + firstDuplicate(elements));
        }
        return create(elements, lookup);
    }

    static <E> Set<E> copyOf(Collection<? extends E> collection) {
        requireNonNull(collection, "collection");
        if (collection instanceof ImmutableOrderedSet<?>) {
            // Safe: the set is unmodifiable, so it can only ever be read as a Set<E>.
            @SuppressWarnings("unchecked")
            Set<E> same = (Set<E>) collection;
            return same;
        }
        var distinct = new LinkedHashSet<E>();
        for (E element : collection) {
            distinct.add(requireNonNull(element, "collection contains null"));
        }
        List<E> elements = List.copyOf(distinct);
        return create(elements, Set.copyOf(elements));
    }

    /**
     * Wraps distinct elements and a set to look them up in, which throws on {@code contains(null)} like {@code Set.of}.
     */
    @SuppressWarnings("unchecked")
    static <E> Set<E> create(List<E> elements, Set<E> lookup) {
        return elements.isEmpty() ? (Set<E>) EMPTY : new ImmutableOrderedSet<>(elements, lookup);
    }

    private static <E> E firstDuplicate(List<E> elements) {
        Set<E> seen = new HashSet<>();
        for (E element : elements) {
            if (!seen.add(element)) {
                return element;
            }
        }
        throw new AssertionError("No duplicate in " + elements);
    }

    @Override
    public Iterator<E> iterator() {
        return elements.iterator();
    }

    @Override
    public int size() {
        return elements.size();
    }

    @Override
    public boolean contains(Object o) {
        return lookup.contains(o);
    }

    @Override
    public Object[] toArray() {
        return elements.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return elements.toArray(a);
    }

    @Override
    public Spliterator<E> spliterator() {
        // Set.of reports DISTINCT, SIZED and SUBSIZED; this set is also ORDERED.
        return Spliterators.spliterator(this, Spliterator.ORDERED | Spliterator.DISTINCT);
    }

    // Like Set.of, every mutator throws, even when the call would not change the set.

    @Override
    public boolean add(E e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
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
    public boolean removeIf(Predicate<? super E> filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Serial
    private Object writeReplace() {
        return new SerializedForm(elements.toArray());
    }

    @Serial
    private void readObject(ObjectInputStream in) throws InvalidObjectException {
        throw new InvalidObjectException("SerializedForm required");
    }

    /**
     * The serialized form: the elements in order. Deserializing goes through {@link #of}, so a tampered stream cannot
     * produce a set with {@code null} or duplicate elements.
     */
    private static final class SerializedForm implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private transient Object[] elements;

        SerializedForm(Object[] elements) {
            this.elements = elements;
        }

        @Serial
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            out.writeInt(elements.length);
            for (Object element : elements) {
                out.writeObject(element);
            }
        }

        @Serial
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            int length = in.readInt();
            if (length < 0) {
                throw new InvalidObjectException("negative length: " + length);
            }
            // Growing with what the stream actually holds, rather than allocating the length it claims, keeps a
            // tampered length from exhausting the heap: the stream runs out of elements first.
            var read = new ArrayList<Object>();
            for (int i = 0; i < length; i++) {
                read.add(in.readObject());
            }
            elements = read.toArray();
        }

        @Serial
        private Object readResolve() throws InvalidObjectException {
            try {
                return of(elements);
            } catch (NullPointerException | IllegalArgumentException e) {
                // A tampered stream, reported as deserialization reports the others.
                throw new InvalidObjectException(e.getMessage(), e);
            }
        }
    }
}
