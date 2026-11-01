import be.imgn.gaia.plugin.Plugin;

/**
 * Plugins installed at runtime: each one is loaded in its own module layer, started, stopped and reloaded in
 * dependency order, and contributes extensions to its host.
 *
 * <h2>A plugin</h2>
 *
 * <p>A plugin is a folder in the plugins directory of its host. The folder holds the plugin's module and the
 * libraries only that plugin uses, as jars. The plugin's module provides {@link be.imgn.gaia.plugin.Plugin}, its entry
 * point:
 *
 * <pre>{@code
 * @RequiresVersion(module = "com.example.editor.api", atLeast = "2.1.0")
 * @RequiresVersion(module = "com.example.git", atLeast = "1.4.0")
 * module com.example.blame {
 *     requires be.imgn.gaia.plugin;
 *     requires com.example.editor.api;
 *     requires com.example.git;
 *
 *     provides be.imgn.gaia.plugin.Plugin with com.example.blame.BlamePlugin;
 * }
 * }</pre>
 *
 * <p>The module descriptor says everything about the plugin:
 *
 * <ul>
 *   <li>its name is the plugin's id: {@code com.example.blame};
 *   <li>its version, which the build records, such as Maven from the project's version, is the plugin's version;
 *   <li>its {@code requires} name the modules it needs: the host's API module, {@code com.example.editor.api}, and
 *       other plugins, such as {@code com.example.git};
 *   <li>{@link be.imgn.gaia.plugin.RequiresVersion} gives the lowest version of each that it works with.
 * </ul>
 *
 * <h2>Loading</h2>
 *
 * <p>Each plugin gets its own {@link java.lang.ModuleLayer}, whose parents are the host's layer and the layers of the
 * plugins it requires. So a plugin sees the packages that the host's modules and the plugins it requires export, and
 * nothing else; and two plugins can each use their own version of a library. A plugin folder must not hold a module
 * that a parent layer has already, such as the host's API module: the plugin's types and the host's would then be two
 * different classes.
 *
 * <h2>Extensions</h2>
 *
 * <p>An extension point is an interface that the host's API module exports, or that a plugin exports for the plugins
 * that require it. A plugin registers its extensions when it starts, through its {@link
 * be.imgn.gaia.plugin.PluginContext}:
 *
 * <pre>{@code
 * public final class BlamePlugin implements Plugin {
 *     @Override
 *     public void start(PluginContext context) {
 *         var git = context.service(Git.class).orElseThrow();
 *         context.register(GutterContribution.class, new BlameGutter(git));
 *     }
 * }
 * }</pre>
 *
 * <p>The host gets them from its {@link be.imgn.gaia.plugin.PluginManager}, as a live view, since plugins start and
 * stop while it runs:
 *
 * <pre>{@code
 * Extensions<GutterContribution> gutters = plugins.extensions(GutterContribution.class);
 * gutters.subscribe(editor::addGutter, editor::removeGutter);
 * }</pre>
 *
 * <h2>Unloading</h2>
 *
 * <p>A plugin is unloaded when it is disabled, reloaded or removed: its layer and everything loaded from it can then
 * be garbage-collected, but only once nothing refers to them anymore. The context undoes everything done through it
 * when the plugin stops: it unregisters the plugin's extensions, shuts its executor down and closes what was handed
 * to {@link be.imgn.gaia.plugin.PluginContext#closeOnStop(AutoCloseable)}. The host drops its own references to an
 * extension when it is told that the extension is removed.
 *
 * <p>Plugins are trusted code: they run in the host's process, with all its rights.
 */
module be.imgn.gaia.plugin {
    requires transitive be.imgn.gaia.core;
    requires transitive org.jspecify;

    exports be.imgn.gaia.plugin;

    uses Plugin;
}
