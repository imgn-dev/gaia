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
 * <p>Each plugin gets its own {@link java.lang.ModuleLayer}, whose parents are the layer of the modules plugins may use,
 * the boot layer by default, and the layers of the plugins it requires. So a plugin sees the packages that those
 * modules and the plugins it requires export, and nothing else; and two plugins can each use their own version of a
 * library. A plugin's folder must not hold a module of that parent layer, such as the host's API module, nor another
 * plugin's module: the plugin's types and theirs would then be two different classes.
 *
 * <p>An application that keeps its own modules out of the boot layer, with {@link be.imgn.gaia.plugin.AppLauncher},
 * keeps them out of its plugins' reach: the boot layer then holds only what plugins may use, such as the application's
 * API module, and the application's layer and the plugins' layers are siblings.
 *
 * <p>The class loader of a plugin's layer has the {@linkplain ClassLoader#getPlatformClassLoader() platform class
 * loader} as parent, not the host's: a plugin loads the host's classes only from the modules it reads, and never sees
 * the host's class path.
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
 * <h2>Isolation of the host's services</h2>
 *
 * <p>A plugin cannot add providers to the host's own {@link java.util.ServiceLoader} lookups:
 *
 * <ul>
 *   <li>A lookup from the host's layer, {@code ServiceLoader.load(layer, type)}, searches that layer and its
 *       ancestors, never the plugins' layers below it.
 *   <li>A lookup from the host's class loader, {@code ServiceLoader.load(type, loader)}, searches that loader, its
 *       parents and their layers, never the plugins' loaders, which are not among them.
 *   <li>A lookup from the thread's context class loader, {@code ServiceLoader.load(type)}, which the host's libraries
 *       also use, such as the XML and scripting factories, finds the providers of whatever loader the thread has. So
 *       every call between the host and a plugin runs with the callee's context class loader, and restores the
 *       caller's afterwards, whatever the callee did with it:
 *       <ul>
 *         <li>the extensions the host gets are wrappers that run each call with the plugin's class loader;
 *         <li>the services a plugin gets are wrappers that run each call with the host's;
 *         <li>{@link be.imgn.gaia.plugin.Plugin#start(be.imgn.gaia.plugin.PluginContext)} and {@link
 *             be.imgn.gaia.plugin.Plugin#stop()} run with the plugin's;
 *         <li>the threads of the plugin's {@linkplain be.imgn.gaia.plugin.PluginContext#executor() executor} have the
 *             plugin's.
 *       </ul>
 *       So the host's code never runs with a plugin's class loader as its context class loader, and a plugin's
 *       libraries find their own providers. Wrapping is why extension points and services must be interfaces.
 * </ul>
 *
 * <p>Plugins are trusted code nonetheless: they run in the host's process, with all its rights. Nothing stops one from
 * calling an API that changes the whole JVM, such as {@code java.security.Security.insertProviderAt}. Nor from
 * attaching an agent to the host's JVM from another process, unless the host runs with {@code
 * -XX:-EnableDynamicAgentLoading}.
 */
module be.imgn.gaia.plugin {
    requires transitive be.imgn.gaia.core;
    requires transitive org.jspecify;

    exports be.imgn.gaia.plugin;

    uses Plugin;
}
