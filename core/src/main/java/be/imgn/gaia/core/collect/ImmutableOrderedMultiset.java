package be.imgn.gaia.core.collect;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import org.jspecify.annotations.Nullable;

/**
 * The unmodifiable multiset behind {@link Multisets}, which iterates in the order its elements first appear. Its map is
 * never changed once built: every mutator throws before reaching it.
 */
final class ImmutableOrderedMultiset<E> extends AbstractMapMultiset<E> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Map<E, Count> counts;
    private final transient long total;

    private ImmutableOrderedMultiset(Map<E, Count> counts, long total) {
        this.counts = counts;
        this.total = total;
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
        throw new UnsupportedOperationException();
    }

    // Like Set.of, every mutator throws, even when the call would not change the multiset.
    @Override
    void checkModifiable() {
        throw new UnsupportedOperationException();
    }

    // Like Set.of, querying with null throws.

    @Override
    public boolean contains(@Nullable Object o) {
        return super.contains(requireNonNull(o));
    }

    @Override
    public int count(@Nullable Object element) {
        return super.count(requireNonNull(element));
    }

    @Override
    public Spliterator<E> spliterator() {
        // The total, not size(), which is capped at Integer.MAX_VALUE.
        return Spliterators.spliterator(iterator(), total, Spliterator.ORDERED | Spliterator.IMMUTABLE);
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
     * Collects elements in the order they first appear, with their counts.
     *
     * @param <E> the element type
     */
    static final class Builder<E> {

        private final Map<E, Count> counts = new LinkedHashMap<>();
        private long total;

        Builder<E> add(E element, int occurrences) {
            var count = counts.get(element);
            if (count == null) {
                counts.put(element, new Count(occurrences));
            } else {
                var before = count.get();
                checkArgument(
                        occurrences <= Integer.MAX_VALUE - before,
                        "Too many occurrences of %s: %s + %s",
                        element,
                        before,
                        occurrences);
                count.set(before + occurrences);
            }
            total += occurrences;
            return this;
        }

        Builder<E> addAll(Builder<E> other) {
            other.counts.forEach((element, count) -> add(element, count.get()));
            return this;
        }

        Multiset<E> build() {
            return new ImmutableOrderedMultiset<>(counts, total);
        }
    }

    private static final class SerializedForm extends SerializedMultiset {

        @Serial
        private static final long serialVersionUID = 1L;

        SerializedForm(ImmutableOrderedMultiset<?> multiset) {
            super(multiset);
        }

        @Serial
        private Object readResolve() throws InvalidObjectException {
            var builder = new Builder<Object>();
            forEachEntry(builder::add);
            return builder.build();
        }
    }
}
