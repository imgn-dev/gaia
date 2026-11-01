package be.imgn.gaia.plugin;

/**
 * The entry point of a plugin, which its module provides as a service:
 *
 * <pre>{@code
 * provides be.imgn.gaia.plugin.Plugin with com.example.blame.BlamePlugin;
 * }</pre>
 *
 * <p>The plugin's module provides exactly one. The {@link PluginManager} creates it when it loads the plugin, starts it
 * after the plugins it requires, and stops it before them.
 *
 * @since 0.1.3
 */
public interface Plugin {

    /**
     * Starts this plugin: registers its extensions, and starts what it runs in the background.
     *
     * <p>Everything this plugin registers or starts goes through the context, which undoes it when the plugin stops. If
     * this method throws or does not return within the manager's timeout, the plugin is {@linkplain PluginState.Failed
     * failed}: what it registered is undone, and the plugins that require it do not start.
     *
     * @param context the context of this plugin, valid until it stops
     * @throws Exception if this plugin cannot start
     */
    void start(PluginContext context) throws Exception;

    /**
     * Stops this plugin, before its context undoes what was done through it. The plugins that require this one are
     * already stopped.
     *
     * <p>Does nothing by default: a plugin that did everything through its context has nothing more to stop.
     *
     * @throws Exception if this plugin could not stop cleanly; it is unloaded all the same
     */
    default void stop() throws Exception {}
}
