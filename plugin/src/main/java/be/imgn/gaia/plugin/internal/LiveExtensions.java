package be.imgn.gaia.plugin.internal;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.plugin.Extensions;
import be.imgn.gaia.plugin.Registration;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** The extensions of a point, read from the registry each time. */
final class LiveExtensions<T> implements Extensions<T> {

    private final ExtensionRegistry registry;
    private final Class<T> point;

    LiveExtensions(ExtensionRegistry registry, Class<T> point) {
        this.registry = registry;
        this.point = point;
    }

    @Override
    public Iterator<T> iterator() {
        return current().iterator();
    }

    @Override
    public Stream<T> stream() {
        return current().stream();
    }

    @Override
    public Registration subscribe(Consumer<? super T> added, Consumer<? super T> removed) {
        requireNonNull(added, "added");
        requireNonNull(removed, "removed");
        return registry.subscribe(
                point,
                extension -> added.accept(point.cast(extension)),
                extension -> removed.accept(point.cast(extension)));
    }

    private List<T> current() {
        return registry.extensions(point).stream().map(point::cast).toList();
    }
}
