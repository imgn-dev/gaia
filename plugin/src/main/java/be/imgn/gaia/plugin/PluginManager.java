package be.imgn.gaia.plugin;

import be.imgn.gaia.plugin.internal.ManagerBuilder;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * The plugins of a host: finds them in the plugins directory, then loads, starts, stops and reloads them, a plugin
 * always after the plugins it requires.
 *
 * <pre>{@code
 * PluginManager plugins = PluginManager.newBuilder()
 *         .directory(directories.data().resolve("plugins"))
 *         .stateFile(directories.config().resolve("plugins.properties"))
 *         .dataDirectory(directories.data().resolve("plugin-data"))
 *         .service(Editor.class, editor)
 *         .start();
 * }</pre>
 *
 * <p>Which plugins are disabled is saved in the state file; a plugin not listed there is enabled.
 *
 * <p>A folder of the plugins directory that is not a plugin is left out, with a warning in the log: one whose modules
 * provide no {@link Plugin}, or more than one, or whose plugin module has no version, or one that is not a semantic
 * version.
 *
 * <p>Two folders that hold the same plugin, such as an old and a new version side by side, are both
 * {@linkplain PluginState.Unresolved unresolved}, until one of them is removed: the manager never guesses which one is
 * meant.
 *
 * <p>Every method of the manager is thread-safe. Its operations run one at a time: one that waits for a plugin to start
 * or stop holds the others back.
 *
 * @since 0.1.3
 */
public interface PluginManager extends AutoCloseable {

    /**
     * Returns a new builder of a plugin manager.
     *
     * @return a new builder
     */
    static Builder newBuilder() {
        return new ManagerBuilder();
    }

    /**
     * Returns every plugin of the plugins directory, a plugin after the plugins it requires.
     *
     * @return every plugin
     * @throws IllegalStateException if the manager is closed
     */
    List<PluginInfo> plugins();

    /**
     * Returns the extensions that started plugins register for an extension point.
     *
     * @param point the extension point
     * @param <T> the type of the extension point
     * @return the extensions of that point, as a live view
     */
    <T> Extensions<T> extensions(Class<T> point);

    /**
     * Enables a plugin and the plugins it requires, saves that, and starts those that can start.
     *
     * @param id the id of the plugin
     * @throws IllegalArgumentException if the plugins directory has no plugin with that id
     * @throws IllegalStateException if the manager is closed
     * @throws java.io.UncheckedIOException if the state file cannot be written
     */
    void enable(PluginId id);

    /**
     * Disables a plugin and saves that. Stops and unloads it, after the plugins that require it, which stay enabled but
     * {@linkplain PluginState.Unresolved unresolved} until it is enabled again.
     *
     * @param id the id of the plugin
     * @throws IllegalArgumentException if the plugins directory has no plugin with that id
     * @throws IllegalStateException if the manager is closed
     * @throws java.io.UncheckedIOException if the state file cannot be written
     */
    void disable(PluginId id);

    /**
     * Loads a plugin again from its folder, such as after it was updated there. Stops and unloads it and the plugins
     * that require it, reads the plugins directory again, then loads and starts them again. A plugin that
     * {@linkplain PluginState.Failed failed} to start gets another try.
     *
     * @param id the id of the plugin
     * @throws IllegalArgumentException if the plugins directory has no plugin with that id
     * @throws IllegalStateException if the manager is closed
     * @throws java.io.UncheckedIOException if the plugins directory cannot be read
     */
    void reload(PluginId id);

    /**
     * Reads the plugins directory again: loads and starts the plugins added to it since, and stops and unloads those
     * removed from it, with the plugins that require them.
     *
     * @throws IllegalStateException if the manager is closed
     * @throws java.io.UncheckedIOException if the plugins directory cannot be read
     */
    void refresh();

    /** Stops and unloads every plugin, a plugin before the plugins it requires. Closing it again does nothing. */
    @Override
    void close();

    /**
     * A builder of a plugin manager.
     *
     * @since 0.1.3
     */
    interface Builder {

        /**
         * Sets the plugins directory, which has a folder for each plugin. Required.
         *
         * @param directory the plugins directory
         * @return this builder
         */
        Builder directory(Path directory);

        /**
         * Sets the file where the manager saves which plugins are disabled. Required.
         *
         * @param file the state file
         * @return this builder
         */
        Builder stateFile(Path file);

        /**
         * Sets the directory where each plugin gets its {@linkplain PluginContext#dataDirectory() data directory},
         * named after its id. Required.
         *
         * @param directory the directory of the plugins' data
         * @return this builder
         */
        Builder dataDirectory(Path directory);

        /**
         * Sets the layer of the modules plugins may use, which is the parent of every plugin's layer. By default, it is
         * the {@linkplain ModuleLayer#boot() boot layer}: with {@link AppLauncher}, the host's own modules are not in
         * it.
         *
         * @param layer the layer of the modules plugins may use
         * @return this builder
         */
        Builder parent(ModuleLayer layer);

        /**
         * Offers a service to the plugins, which they get through {@link PluginContext#service(Class)}. They get a
         * wrapper, which runs each call with the context class loader of the thread that calls {@link #start()}.
         *
         * @param type the type of the service, a public interface of a package exported to {@code be.imgn.gaia.plugin},
         *     such as one of the host's API module
         * @param service the service
         * @param <T> the type of the service
         * @return this builder
         * @throws IllegalArgumentException if the type is not such an interface, or the service is not of that type
         */
        <T> Builder service(Class<T> type, T service);

        /**
         * Sets how long a plugin may take to start or stop, or its executor to shut down. By default, 10 seconds.
         *
         * @param timeout how long a plugin may take
         * @return this builder
         * @throws IllegalArgumentException if the timeout is not positive
         */
        Builder timeout(Duration timeout);

        /**
         * Creates the manager, then loads and starts the enabled plugins.
         *
         * @return the manager
         * @throws IllegalStateException if the plugins directory, the state file or the data directory is not set
         * @throws java.io.UncheckedIOException if the state file or the plugins directory cannot be read
         */
        PluginManager start();
    }
}
