package be.imgn.gaia.core.collect;

import static java.util.Objects.requireNonNull;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Collectors that {@link Collectors} does not provide. */
public final class MoreCollectors {

    private MoreCollectors() {}

    /**
     * Returns a collector of the one element of a stream, which must contain exactly one.
     *
     * <pre>{@code
     * User admin = users.stream().filter(User::isAdmin).collect(MoreCollectors.onlyElement());
     * }</pre>
     *
     * @param <T> the element type
     * @return a collector of the only element
     * @throws NoSuchElementException when collecting, if the stream is empty
     * @throws IllegalArgumentException when collecting, if the stream has more than one element
     */
    public static <T> Collector<T, ?, T> onlyElement() {
        return Collectors.collectingAndThen(toOptional(), MoreCollectors::present);
    }

    /**
     * Returns a collector of the one element of a stream, if any: the stream must contain zero or one element.
     *
     * <pre>{@code
     * Optional<User> admin = users.stream().filter(User::isAdmin).collect(MoreCollectors.toOptional());
     * }</pre>
     *
     * @param <T> the element type
     * @return a collector of the only element, or of an empty {@link Optional} for an empty stream
     * @throws IllegalArgumentException when collecting, if the stream has more than one element
     */
    public static <T> Collector<T, ?, Optional<T>> toOptional() {
        return Collector.<T, OnlyElement<T>, Optional<T>>of(
                OnlyElement::new,
                OnlyElement::add,
                OnlyElement::merge,
                OnlyElement::optional,
                Collector.Characteristics.UNORDERED);
    }

    private static <T> T present(Optional<T> element) {
        return element.orElseThrow(
                () -> new NoSuchElementException("Expected exactly one element, but the stream is empty"));
    }

    /** The state of {@link #toOptional()}, which {@link #onlyElement()} builds on: the element seen so far, if any. */
    private static final class OnlyElement<T> {

        private @Nullable T element;

        void add(T newElement) {
            requireNonNull(newElement, "element");
            if (element != null) {
                throw tooMany(element, newElement);
            }
            element = newElement;
        }

        OnlyElement<T> merge(OnlyElement<T> other) {
            if (other.element != null) {
                add(other.element);
            }
            return this;
        }

        Optional<T> optional() {
            return Optional.ofNullable(element);
        }

        private static IllegalArgumentException tooMany(Object first, Object second) {
            return new IllegalArgumentException(
                    "Expected a single element, but found at least two: " + first + ", " + second);
        }
    }
}
