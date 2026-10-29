package be.imgn.gaia.core.collect;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Gatherer;
import java.util.stream.Gatherers;
import org.jspecify.annotations.Nullable;

/**
 * Gatherers that {@link Gatherers} does not provide.
 *
 * <p>Gatherers exist since Java 24, so this class is only present when gaia runs on Java 24 or later: it lives in the
 * {@code META-INF/versions/24} part of the multi-release jar.
 *
 * <p>Like streams, the gatherers accept {@code null} elements, and the key functions may return {@code null}. Keys and
 * elements are compared with {@link Objects#equals(Object, Object)}.
 *
 * <p>In a parallel stream, {@link #instanceOf(Class)}, {@link #takeWhileInclusive(Predicate)}, {@link #takeLast(int)},
 * {@link #max(int, Comparator)} and {@link #min(int, Comparator)} run in parallel. The others depend on the elements
 * before each one, so their stage runs sequentially, in encounter order.
 */
public final class MoreGatherers {

    private MoreGatherers() {}

    /**
     * Returns a gatherer that keeps the elements that are instances of {@code type}, cast to it: a
     * {@code filter(type::isInstance)} and a {@code map(type::cast)} in one step, with the right stream type.
     *
     * <pre>{@code
     * Stream<Shape> shapes = ...;
     * Stream<Circle> circles = shapes.gather(MoreGatherers.instanceOf(Circle.class));
     * }</pre>
     *
     * <p>Like the {@code instanceof} operator, it drops {@code null} elements.
     *
     * @param <R> the type to keep
     * @param type the class of the type to keep
     * @return a gatherer of the instances of {@code type}
     */
    public static <R> Gatherer<Object, ?, R> instanceOf(Class<R> type) {
        requireNonNull(type, "type");
        return Gatherer.of(Gatherer.Integrator.ofGreedy(
                (state, element, downstream) -> !type.isInstance(element) || downstream.push(type.cast(element))));
    }

    /**
     * Returns a gatherer that keeps the first element of each key, in encounter order.
     *
     * <pre>{@code
     * Stream.of("apple", "avocado", "banana", "blueberry", "cherry")
     *         .gather(MoreGatherers.distinctBy(s -> s.charAt(0)))  // apple, banana, cherry
     * }</pre>
     *
     * <p>It keeps every key it has seen until the stream ends.
     *
     * @param <T> the type of the elements
     * @param <K> the type of the keys
     * @param key the function that gives the key of an element
     * @return a gatherer of the elements whose key was not seen before
     * @since 0.1.2
     */
    public static <T extends @Nullable Object, K extends @Nullable Object> Gatherer<T, ?, T> distinctBy(
            Function<? super T, ? extends K> key) {
        requireNonNull(key, "key");
        return Gatherer.<T, Set<K>, T>ofSequential(
                HashSet::new,
                Gatherer.Integrator.ofGreedy(
                        (seen, element, downstream) -> !seen.add(key.apply(element)) || downstream.push(element)));
    }

    /**
     * Returns a gatherer that keeps the elements while a predicate holds, and the first element that fails it, then
     * stops. Unlike {@link java.util.stream.Stream#takeWhile(Predicate)}, the element that ends the run is kept, such
     * as the terminator of a block.
     *
     * <pre>{@code
     * Stream.of(1, 2, 3, 4, 5).gather(MoreGatherers.takeWhileInclusive(i -> i < 3))  // 1, 2, 3
     * }</pre>
     *
     * <p>It stops pulling elements after the first that fails the predicate, so it ends an infinite stream.
     *
     * @param <T> the type of the elements
     * @param predicate the predicate the elements before the last one satisfy
     * @return a gatherer of the elements up to and including the first that fails {@code predicate}
     * @since 0.1.2
     */
    public static <T extends @Nullable Object> Gatherer<T, ?, T> takeWhileInclusive(Predicate<? super T> predicate) {
        requireNonNull(predicate, "predicate");
        // Stateless, so parallelizable: the stream discards what later partitions push once an earlier one stops.
        return Gatherer.<T, T>of((_, element, downstream) -> {
            var more = predicate.test(element);
            return downstream.push(element) && more;
        });
    }

    /**
     * Returns a gatherer of the runs of consecutive elements with equal keys, each as an unmodifiable list. Unlike
     * grouping, an element whose key appeared before, but not right before, starts a new run.
     *
     * <pre>{@code
     * Stream.of("a1", "a2", "b1", "a3")
     *         .gather(MoreGatherers.groupRuns(s -> s.charAt(0)))  // [a1, a2], [b1], [a3]
     * }</pre>
     *
     * @param <T> the type of the elements
     * @param <K> the type of the keys
     * @param key the function that gives the key of an element
     * @return a gatherer of the runs of elements with equal keys
     * @since 0.1.2
     */
    public static <T extends @Nullable Object, K extends @Nullable Object> Gatherer<T, ?, List<T>> groupRuns(
            Function<? super T, ? extends K> key) {
        requireNonNull(key, "key");
        return Gatherer.<T, Run<T, K>, List<T>>ofSequential(
                Run::new,
                Gatherer.Integrator.ofGreedy((run, element, downstream) -> {
                    K elementKey = key.apply(element);
                    var more = run.elements.isEmpty()
                            || Objects.equals(run.key, elementKey)
                            || downstream.push(run.take());
                    run.key = elementKey;
                    run.elements.add(element);
                    return more;
                }),
                (run, downstream) -> {
                    if (!run.elements.isEmpty() && !downstream.isRejecting()) {
                        downstream.push(run.take());
                    }
                });
    }

    /**
     * Returns a gatherer that keeps the last {@code n} elements, or all of them if there are fewer. It holds at most
     * {@code n} elements, and pushes them when the stream ends.
     *
     * <pre>{@code
     * Stream.of(1, 2, 3, 4, 5).gather(MoreGatherers.takeLast(2))  // 4, 5
     * }</pre>
     *
     * <p>With {@code n} of 0, it stops pulling elements at once, so it ends an infinite stream.
     *
     * @param <T> the type of the elements
     * @param n the number of elements to keep
     * @return a gatherer of the last {@code n} elements
     * @throws IllegalArgumentException if {@code n} is negative
     * @since 0.1.2
     */
    public static <T extends @Nullable Object> Gatherer<T, ?, T> takeLast(int n) {
        checkArgument(n >= 0, "n must not be negative: %s", n);
        if (n == 0) {
            return nothing();
        }
        return Gatherer.<T, Ring<T>, T>of(
                () -> new Ring<>(n),
                Gatherer.Integrator.ofGreedy((ring, element, downstream) -> {
                    ring.offer(element);
                    return true;
                }),
                Ring::followedBy,
                Ring::pushAll);
    }

    /**
     * Returns a gatherer that drops the last {@code n} elements, or all of them if there are fewer. It holds at most
     * {@code n} elements, each pushed once {@code n} more have followed it.
     *
     * <pre>{@code
     * Stream.of(1, 2, 3, 4, 5).gather(MoreGatherers.dropLast(2))  // 1, 2, 3
     * }</pre>
     *
     * @param <T> the type of the elements
     * @param n the number of elements to drop
     * @return a gatherer of the elements but the last {@code n}
     * @throws IllegalArgumentException if {@code n} is negative
     * @since 0.1.2
     */
    public static <T extends @Nullable Object> Gatherer<T, ?, T> dropLast(int n) {
        checkArgument(n >= 0, "n must not be negative: %s", n);
        if (n == 0) {
            return Gatherer.of(Gatherer.Integrator.ofGreedy((_, element, downstream) -> downstream.push(element)));
        }
        return Gatherer.<T, Ring<T>, T>ofSequential(
                () -> new Ring<>(n), Gatherer.Integrator.ofGreedy((ring, element, downstream) -> {
                    if (!ring.isFull()) {
                        ring.offer(element);
                        return true;
                    }
                    return downstream.push(ring.replaceOldest(element));
                }));
    }

    /**
     * Returns a gatherer that drops each element equal to the one right before it.
     *
     * <pre>{@code
     * Stream.of(1, 1, 2, 2, 2, 1).gather(MoreGatherers.distinctAdjacent())  // 1, 2, 1
     * }</pre>
     *
     * @param <T> the type of the elements
     * @return a gatherer of the elements that differ from the one before
     * @since 0.1.2
     */
    public static <T extends @Nullable Object> Gatherer<T, ?, T> distinctAdjacent() {
        return distinctAdjacentBy(element -> element);
    }

    /**
     * Returns a gatherer that drops each element whose key equals the key of the element right before it.
     *
     * <pre>{@code
     * Stream.of("apple", "avocado", "banana", "apricot")
     *         .gather(MoreGatherers.distinctAdjacentBy(s -> s.charAt(0)))  // apple, banana, apricot
     * }</pre>
     *
     * @param <T> the type of the elements
     * @param <K> the type of the keys
     * @param key the function that gives the key of an element
     * @return a gatherer of the elements whose key differs from the one before
     * @since 0.1.2
     */
    public static <T extends @Nullable Object, K extends @Nullable Object> Gatherer<T, ?, T> distinctAdjacentBy(
            Function<? super T, ? extends K> key) {
        requireNonNull(key, "key");
        return Gatherer.<T, PreviousKey<K>, T>ofSequential(
                PreviousKey::new, Gatherer.Integrator.ofGreedy((previous, element, downstream) -> {
                    K elementKey = key.apply(element);
                    if (previous.present && Objects.equals(previous.key, elementKey)) {
                        return true;
                    }
                    previous.present = true;
                    previous.key = elementKey;
                    return downstream.push(element);
                }));
    }

    /**
     * Returns a gatherer that maps each element with its index in the stream, counted from 0.
     *
     * <pre>{@code
     * Stream.of("a", "b").gather(MoreGatherers.mapWithIndex((i, s) -> i + ": " + s))  // 0: a, 1: b
     * }</pre>
     *
     * @param <T> the type of the elements
     * @param <R> the type of the results
     * @param mapper the function that maps an element and its index
     * @return a gatherer of the mapped elements
     * @since 0.1.2
     */
    public static <T extends @Nullable Object, R extends @Nullable Object> Gatherer<T, ?, R> mapWithIndex(
            IndexedFunction<? super T, ? extends R> mapper) {
        requireNonNull(mapper, "mapper");
        return Gatherer.<T, Counter, R>ofSequential(
                Counter::new,
                Gatherer.Integrator.ofGreedy(
                        (counter, element, downstream) -> downstream.push(mapper.apply(counter.next(), element))));
    }

    /**
     * Returns a gatherer that keeps the elements that satisfy a predicate of the element and its index in the stream,
     * counted from 0.
     *
     * <pre>{@code
     * Stream.of("a", "b", "c").gather(MoreGatherers.filterWithIndex((i, s) -> i != 1))  // a, c
     * }</pre>
     *
     * @param <T> the type of the elements
     * @param predicate the predicate of an element and its index
     * @return a gatherer of the elements that satisfy {@code predicate}
     * @since 0.1.2
     */
    public static <T extends @Nullable Object> Gatherer<T, ?, T> filterWithIndex(
            IndexedPredicate<? super T> predicate) {
        requireNonNull(predicate, "predicate");
        return Gatherer.<T, Counter, T>ofSequential(
                Counter::new,
                Gatherer.Integrator.ofGreedy((counter, element, downstream) ->
                        !predicate.test(counter.next(), element) || downstream.push(element)));
    }

    /**
     * Returns a gatherer that keeps every {@code n}th element, starting with the first: the elements at index 0,
     * {@code n}, {@code 2n}, and so on.
     *
     * <pre>{@code
     * Stream.of(1, 2, 3, 4, 5).gather(MoreGatherers.everyNth(2))  // 1, 3, 5
     * }</pre>
     *
     * @param <T> the type of the elements
     * @param n the distance between two kept elements
     * @return a gatherer of every {@code n}th element
     * @throws IllegalArgumentException if {@code n} is not positive
     * @since 0.1.2
     */
    public static <T extends @Nullable Object> Gatherer<T, ?, T> everyNth(int n) {
        checkArgument(n > 0, "n must be positive: %s", n);
        return filterWithIndex((index, element) -> index % n == 0);
    }

    /**
     * Returns a gatherer of the {@code n} greatest elements, the greatest first, or of all of them, sorted, if there
     * are fewer. Equal elements keep their encounter order, and the earliest of them are kept when not all fit. It
     * holds at most {@code n} elements, and pushes them when the stream ends.
     *
     * <pre>{@code
     * Stream.of(3, 1, 4, 1, 5, 9, 2).gather(MoreGatherers.max(3, Comparator.naturalOrder()))  // 9, 5, 4
     * }</pre>
     *
     * <p>An element that is not greater than the least of {@code n} kept ones costs one comparison, and one that is
     * kept a number of comparisons logarithmic in {@code n}. With {@code n} of 0, it stops pulling elements at once, so
     * it ends an infinite stream.
     *
     * @param <T> the type of the elements
     * @param n the number of elements to keep
     * @param comparator the order of the elements
     * @return a gatherer of the {@code n} greatest elements
     * @throws IllegalArgumentException if {@code n} is negative
     * @since 0.1.2
     */
    public static <T extends @Nullable Object> Gatherer<T, ?, T> max(int n, Comparator<? super T> comparator) {
        requireNonNull(comparator, "comparator");
        checkArgument(n >= 0, "n must not be negative: %s", n);
        if (n == 0) {
            return nothing();
        }
        return Gatherer.<T, Greatest<T>, T>of(
                () -> new Greatest<>(n, comparator),
                Gatherer.Integrator.ofGreedy((greatest, element, downstream) -> {
                    greatest.offer(element);
                    return true;
                }),
                Greatest::followedBy,
                Greatest::pushAll);
    }

    /**
     * Returns a gatherer of the {@code n} least elements, the least first, or of all of them, sorted, if there are
     * fewer. Equal elements keep their encounter order, and the earliest of them are kept when not all fit. It holds at
     * most {@code n} elements, and pushes them when the stream ends.
     *
     * <pre>{@code
     * Stream.of(3, 1, 4, 1, 5, 9, 2).gather(MoreGatherers.min(3, Comparator.naturalOrder()))  // 1, 1, 2
     * }</pre>
     *
     * <p>An element that is not less than the greatest of {@code n} kept ones costs one comparison, and one that is
     * kept a number of comparisons logarithmic in {@code n}. With {@code n} of 0, it stops pulling elements at once, so
     * it ends an infinite stream.
     *
     * @param <T> the type of the elements
     * @param n the number of elements to keep
     * @param comparator the order of the elements
     * @return a gatherer of the {@code n} least elements
     * @throws IllegalArgumentException if {@code n} is negative
     * @since 0.1.2
     */
    public static <T extends @Nullable Object> Gatherer<T, ?, T> min(int n, Comparator<? super T> comparator) {
        requireNonNull(comparator, "comparator");
        return MoreGatherers.<T>max(n, Collections.reverseOrder(comparator));
    }

    /** A gatherer that wants no element: it stops the stream at once. */
    private static <T extends @Nullable Object> Gatherer<T, ?, T> nothing() {
        return Gatherer.<T, T>of((_, _, _) -> false);
    }

    /**
     * A function of an element and its index in a stream.
     *
     * @param <T> the type of the elements
     * @param <R> the type of the results
     * @since 0.1.2
     */
    @FunctionalInterface
    public interface IndexedFunction<T extends @Nullable Object, R extends @Nullable Object> {

        /**
         * Applies this function.
         *
         * @param index the index of the element in the stream, counted from 0
         * @param element the element
         * @return the result
         */
        R apply(long index, T element);
    }

    /**
     * A predicate of an element and its index in a stream.
     *
     * @param <T> the type of the elements
     * @since 0.1.2
     */
    @FunctionalInterface
    public interface IndexedPredicate<T extends @Nullable Object> {

        /**
         * Evaluates this predicate.
         *
         * @param index the index of the element in the stream, counted from 0
         * @param element the element
         * @return whether the element and its index satisfy the predicate
         */
        boolean test(long index, T element);
    }

    private static final class Counter {

        private long next;

        long next() {
            return next++;
        }
    }

    private static final class PreviousKey<K extends @Nullable Object> {

        private boolean present;
        private @Nullable K key;
    }

    /** The current run of elements with equal keys. */
    private static final class Run<T extends @Nullable Object, K extends @Nullable Object> {

        private List<T> elements = new ArrayList<>();
        private @Nullable K key;

        List<T> take() {
            var taken = Collections.unmodifiableList(elements);
            elements = new ArrayList<>();
            return taken;
        }
    }

    /** The last elements of a stream, at most a capacity of them, the oldest replaced first. */
    private static final class Ring<T extends @Nullable Object> {

        private final int capacity;
        private final List<T> elements = new ArrayList<>();
        private int oldest;

        Ring(int capacity) {
            this.capacity = capacity;
        }

        boolean isFull() {
            return elements.size() == capacity;
        }

        void offer(T element) {
            if (isFull()) {
                replaceOldest(element);
            } else {
                elements.add(element);
            }
        }

        T replaceOldest(T element) {
            var replaced = elements.set(oldest, element);
            oldest = (oldest + 1) % capacity;
            return replaced;
        }

        /** Combines this ring, of a partition of the stream, with the ring of the partition that follows it. */
        Ring<T> followedBy(Ring<T> next) {
            if (next.isFull()) {
                return next;
            }
            var combined = new Ring<T>(capacity);
            forEach(combined::offer);
            next.forEach(combined::offer);
            return combined;
        }

        void pushAll(Gatherer.Downstream<? super T> downstream) {
            for (var i = 0; i < elements.size() && !downstream.isRejecting(); i++) {
                downstream.push(elements.get((oldest + i) % elements.size()));
            }
        }

        private void forEach(Consumer<T> action) {
            for (var i = 0; i < elements.size(); i++) {
                action.accept(elements.get((oldest + i) % elements.size()));
            }
        }
    }

    /** The greatest elements of a stream, at most a capacity of them, the greatest first, equal ones in order. */
    private static final class Greatest<T extends @Nullable Object> {

        private final int capacity;
        private final Comparator<? super T> comparator;
        // The least kept element at the head, so that a new element is compared with it alone, and replaces it in
        // logarithmic time.
        private final PriorityQueue<Ranked<T>> elements;
        private long offered;

        Greatest(int capacity, Comparator<? super T> comparator) {
            this.capacity = capacity;
            this.comparator = comparator;
            this.elements = new PriorityQueue<>(this::compare);
        }

        void offer(T element) {
            offer(new Ranked<>(element, offered++));
        }

        private void offer(Ranked<T> ranked) {
            if (elements.size() < capacity) {
                elements.add(ranked);
            } else if (compare(elements.element(), ranked) < 0) {
                elements.remove();
                elements.add(ranked);
            }
        }

        /** Orders the least element first and, among equal ones, the latest in the stream first. */
        private int compare(Ranked<T> a, Ranked<T> b) {
            var comparison = comparator.compare(a.element(), b.element());
            return comparison != 0 ? comparison : Long.compare(b.index(), a.index());
        }

        /** Combines these elements, of a partition of the stream, with those of the partition that follows it. */
        Greatest<T> followedBy(Greatest<T> next) {
            // The next partition's elements come after all of these in the stream.
            for (var ranked : next.elements) {
                offer(new Ranked<>(ranked.element(), offered + ranked.index()));
            }
            offered += next.offered;
            return this;
        }

        void pushAll(Gatherer.Downstream<? super T> downstream) {
            var sorted = new ArrayList<>(elements);
            sorted.sort((a, b) -> compare(b, a));
            for (var i = 0; i < sorted.size() && !downstream.isRejecting(); i++) {
                downstream.push(sorted.get(i).element());
            }
        }
    }

    /** An element and its index in the stream. */
    private record Ranked<T extends @Nullable Object>(T element, long index) {}
}
