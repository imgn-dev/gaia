package be.imgn.gaia.plugin.internal;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static be.imgn.gaia.core.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.base.Version;
import be.imgn.gaia.plugin.PluginContext;
import be.imgn.gaia.plugin.PluginId;
import be.imgn.gaia.plugin.Registration;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.jspecify.annotations.Nullable;

/** The context of a plugin, which keeps what was done through it, so that the manager undoes it. */
final class Context implements PluginContext {

    private final PluginId id;
    private final Version version;
    private final Path dataDirectory;
    private final ClassLoader loader;
    private final int order;
    private final Host host;
    private final Object lock = new Object();
    private final List<Registration> registrations = new ArrayList<>();
    private final List<AutoCloseable> resources = new ArrayList<>();
    private final Map<Class<?>, Object> services = new HashMap<>();
    private @Nullable ScheduledExecutorService executor;
    private boolean stopped;

    Context(PluginId id, Version version, Path dataDirectory, ClassLoader loader, int order, Host host) {
        this.id = id;
        this.version = version;
        this.dataDirectory = dataDirectory;
        this.loader = loader;
        this.order = order;
        this.host = host;
    }

    @Override
    public PluginId id() {
        return id;
    }

    @Override
    public Version version() {
        return version;
    }

    @Override
    public <T> Registration register(Class<T> point, T extension) {
        requireNonNull(point, "point");
        requireNonNull(extension, "extension");
        Wrappers.checkWrappable(point, "extension point");
        checkArgument(
                point.isInstance(extension),
                "%s is not a %s",
                extension.getClass().getName(),
                point.getName());
        var wrapper = Wrappers.wrap(point, extension, loader);
        synchronized (lock) {
            checkState(!stopped, "%s has stopped", id);
            var registration = host.registry().add(point, wrapper, order);
            registrations.add(registration);
            return registration;
        }
    }

    @Override
    public <T> Optional<T> service(Class<T> type) {
        requireNonNull(type, "type");
        var service = host.services().get(type);
        if (service == null) {
            return Optional.empty();
        }
        synchronized (lock) {
            var wrapper = services.computeIfAbsent(
                    type, t -> Wrappers.wrap(type, type.cast(service), host.contextClassLoader()));
            return Optional.of(type.cast(wrapper));
        }
    }

    @Override
    public Path dataDirectory() {
        return dataDirectory;
    }

    @Override
    public ScheduledExecutorService executor() {
        synchronized (lock) {
            checkState(!stopped, "%s has stopped", id);
            var current = executor;
            if (current == null) {
                var threads = Thread.ofPlatform().name(id + "-", 1).daemon().factory();
                current = new ScheduledThreadPoolExecutor(1, task -> {
                    var thread = threads.newThread(task);
                    thread.setContextClassLoader(loader);
                    return thread;
                });
                executor = current;
            }
            return current;
        }
    }

    @Override
    public void closeOnStop(AutoCloseable resource) {
        requireNonNull(resource, "resource");
        synchronized (lock) {
            checkState(!stopped, "%s has stopped", id);
            resources.add(resource);
        }
    }

    /** Marks the plugin stopped, and returns what was done through this context, for the manager to undo. */
    Undo stop() {
        synchronized (lock) {
            stopped = true;
            return new Undo(List.copyOf(registrations), executor, List.copyOf(resources));
        }
    }

    /**
     * What was done through a context.
     *
     * @param registrations the extensions registered, in order
     * @param executor the plugin's executor, if it asked for it
     * @param resources the resources to close, in the order they were handed
     */
    record Undo(
            List<Registration> registrations,
            @Nullable ScheduledExecutorService executor,
            List<AutoCloseable> resources) {}
}
