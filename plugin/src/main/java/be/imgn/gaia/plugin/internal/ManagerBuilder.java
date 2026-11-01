package be.imgn.gaia.plugin.internal;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.plugin.PluginManager;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** The builder that {@link PluginManager#newBuilder()} returns. */
public final class ManagerBuilder implements PluginManager.Builder {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final Map<Class<?>, Object> services = new LinkedHashMap<>();
    private @Nullable Path directory;
    private @Nullable Path stateFile;
    private @Nullable Path dataDirectory;
    private ModuleLayer parent = ModuleLayer.boot();
    private Duration timeout = DEFAULT_TIMEOUT;

    /** Creates a builder with the defaults. */
    public ManagerBuilder() {}

    @Override
    public PluginManager.Builder directory(Path directory) {
        this.directory = requireNonNull(directory, "directory");
        return this;
    }

    @Override
    public PluginManager.Builder stateFile(Path file) {
        this.stateFile = requireNonNull(file, "file");
        return this;
    }

    @Override
    public PluginManager.Builder dataDirectory(Path directory) {
        this.dataDirectory = requireNonNull(directory, "directory");
        return this;
    }

    @Override
    public PluginManager.Builder parent(ModuleLayer layer) {
        this.parent = requireNonNull(layer, "layer");
        return this;
    }

    @Override
    public <T> PluginManager.Builder service(Class<T> type, T service) {
        requireNonNull(type, "type");
        requireNonNull(service, "service");
        Wrappers.checkWrappable(type, "service type");
        checkArgument(
                type.isInstance(service), "%s is not a %s", service.getClass().getName(), type.getName());
        services.put(type, service);
        return this;
    }

    @Override
    public PluginManager.Builder timeout(Duration timeout) {
        requireNonNull(timeout, "timeout");
        checkArgument(timeout.isPositive(), "The timeout is not positive: %s", timeout);
        this.timeout = timeout;
        return this;
    }

    @Override
    public PluginManager start() {
        var host = new Host(
                Map.copyOf(services), Thread.currentThread().getContextClassLoader(), new ExtensionRegistry(), timeout);
        var settings = new Settings(
                required(directory, "The plugins directory"),
                required(stateFile, "The state file"),
                required(dataDirectory, "The data directory"),
                parent,
                host);
        return DefaultPluginManager.start(settings);
    }

    private static Path required(@Nullable Path path, String what) {
        if (path == null) {
            throw new IllegalStateException(what + " is not set");
        }
        return path;
    }
}
