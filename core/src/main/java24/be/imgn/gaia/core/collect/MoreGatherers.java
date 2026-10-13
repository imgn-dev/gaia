package be.imgn.gaia.core.collect;

import static java.util.Objects.requireNonNull;

import java.util.stream.Gatherer;
import java.util.stream.Gatherers;

/**
 * Gatherers that {@link Gatherers} does not provide.
 *
 * <p>Gatherers exist since Java 24, so this class is only present when gaia runs on Java 24 or later: it lives in the
 * {@code META-INF/versions/24} part of the multi-release jar.
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
}
