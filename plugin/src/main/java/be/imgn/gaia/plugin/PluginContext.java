package be.imgn.gaia.plugin;

import be.imgn.gaia.core.base.Version;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

/**
 * What a started plugin gets from its host: its extension points, the host's services, a data directory and an
 * executor.
 *
 * <p>The context undoes everything done through it when the plugin stops: it unregisters the plugin's extensions, shuts
 * its executor down, and closes what was handed to {@link #closeOnStop(AutoCloseable)}. A plugin that does everything
 * through its context can be unloaded without leaving anything behind.
 *
 * @since 0.1.3
 */
public interface PluginContext {

    /**
     * Returns the id of the plugin.
     *
     * @return the id of the plugin
     */
    PluginId id();

    /**
     * Returns the version of the plugin: the version of its module.
     *
     * @return the version of the plugin
     */
    Version version();

    /**
     * Registers an extension, until the plugin stops or the registration is closed.
     *
     * <p>The host and the other plugins get it through {@link PluginManager#extensions(Class)}, typed by its extension
     * point, which is an interface the host's API module or a plugin exports. They get a wrapper, which runs each call
     * with this plugin's class loader as the thread's context class loader.
     *
     * @param point the extension point
     * @param extension the extension
     * @param <T> the type of the extension point
     * @return the registration, which unregisters the extension when it is closed
     * @throws IllegalArgumentException if the extension point is not an interface
     * @throws IllegalStateException if the plugin has stopped
     */
    <T> Registration register(Class<T> point, T extension);

    /**
     * Returns a service the host offers to its plugins, such as its editor or its settings. The plugin gets a wrapper,
     * which runs each call with the host's context class loader.
     *
     * @param type the type of the service
     * @param <T> the type of the service
     * @return the service, or an empty optional if the host offers none of that type
     */
    <T> Optional<T> service(Class<T> type);

    /**
     * Returns the directory where the plugin keeps its data, which only it uses, and which stays when it is reloaded or
     * updated. The directory exists when the plugin starts.
     *
     * @return the directory of the plugin's data
     */
    Path dataDirectory();

    /**
     * Returns the plugin's executor, for what it runs in the background. Its threads have the plugin's class loader as
     * their context class loader. It is shut down when the plugin stops: its threads are interrupted, and the manager
     * waits for them up to its timeout.
     *
     * @return the plugin's executor
     */
    ScheduledExecutorService executor();

    /**
     * Has a resource closed when the plugin stops, such as a subscription to an event bus of the host. Resources are
     * closed in the reverse order they were handed here.
     *
     * @param resource the resource to close
     * @throws IllegalStateException if the plugin has stopped
     */
    void closeOnStop(AutoCloseable resource);
}
