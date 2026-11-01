package be.imgn.gaia.plugin;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableSet;

import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Launches an application in a module layer of its own, so that its plugins cannot see it.
 *
 * <p>Every module layer descends from the boot layer, so plugins can read every module of the boot layer. An
 * application that keeps its own modules out of the boot layer keeps them out of its plugins' reach: they cannot
 * require them, and its {@link java.util.ServiceLoader} lookups never find their providers, nor its plugins' lookups
 * the application's. The boot layer then holds only what plugins may use: the JDK, gaia, and the application's API
 * module, whose packages hold its extension points and the types of its services.
 *
 * <pre>{@code
 * java --module-path launcher:api --module com.example.launcher/com.example.launcher.Main
 *
 * public static void main(String[] args) throws Exception {
 *     AppLauncher.launch(Path.of("app"), "com.example.editor", "com.example.editor.Main", args);
 * }
 * }</pre>
 *
 * <p>The application's {@code PluginManager} then keeps the boot layer as the parent of its plugins' layers, which is
 * the default.
 *
 * @since 0.1.3
 */
public final class AppLauncher {

    private AppLauncher() {}

    /**
     * Loads the application's modules into a layer of their own, a child of the boot layer, and runs its main method on
     * the current thread, with the class loader of that layer as the thread's context class loader.
     *
     * <p>The directory may hold modules that the boot layer has already, such as gaia's: the application gets those of
     * the boot layer, so that it shares their types with its plugins.
     *
     * @param directory the directory of the application's modules, as modular jars or exploded modules
     * @param mainModule the name of the module that holds the main class
     * @param mainClass the binary name of the main class, a public class with a {@code public static void
     *     main(String[])} method
     * @param args the arguments of the main method
     * @return the application's layer, once the main method returns
     * @throws IllegalArgumentException if the directory holds no such main module, or that module no such main class or
     *     main method
     * @throws java.lang.module.FindException if a module of the directory cannot be read
     * @throws java.lang.module.ResolutionException if the application's modules cannot be resolved
     * @throws Exception what the main method throws
     */
    public static ModuleLayer launch(Path directory, String mainModule, String mainClass, String... args)
            throws Exception {
        requireNonNull(directory, "directory");
        requireNonNull(mainModule, "mainModule");
        requireNonNull(mainClass, "mainClass");
        requireNonNull(args, "args");
        var boot = ModuleLayer.boot();
        var finder = new WithoutBootModules(ModuleFinder.of(directory), boot);
        var names = finder.findAll().stream()
                .map(reference -> reference.descriptor().name())
                .collect(toUnmodifiableSet());
        checkArgument(
                names.contains(mainModule),
                "%s holds no module %s that the boot layer does not have already",
                directory,
                mainModule);
        var configuration = boot.configuration().resolve(finder, ModuleFinder.of(), names);
        var controller = ModuleLayer.defineModulesWithOneLoader(
                configuration, List.of(boot), ClassLoader.getSystemClassLoader());
        var layer = controller.layer();
        var module = layer.findModule(mainModule).orElseThrow();
        var main = Class.forName(module, mainClass);
        checkArgument(main != null, "%s has no class %s", mainModule, mainClass);
        // So that this module may call the main method, whether or not the application exports its package.
        controller.addExports(module, main.getPackageName(), AppLauncher.class.getModule());
        run(mainMethod(main), layer.findLoader(mainModule), args);
        return layer;
    }

    private static Method mainMethod(Class<?> main) {
        Method method;
        try {
            method = main.getMethod("main", String[].class);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(main.getName() + " has no public main(String[]) method", e);
        }
        checkArgument(
                Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(main.getModifiers()),
                "%s is not a public class with a public static main(String[]) method",
                main.getName());
        return method;
    }

    private static void run(Method main, ClassLoader loader, String[] args) throws Exception {
        var thread = Thread.currentThread();
        var previous = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            main.invoke(null, (Object) args.clone());
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static Exception unwrap(InvocationTargetException e) {
        var cause = e.getCause();
        if (cause instanceof Error error) {
            throw error;
        }
        return cause instanceof Exception exception ? exception : e;
    }

    /** The modules of a finder that a layer does not have already. */
    private static final class WithoutBootModules implements ModuleFinder {

        private final ModuleFinder finder;
        private final ModuleLayer layer;

        WithoutBootModules(ModuleFinder finder, ModuleLayer layer) {
            this.finder = finder;
            this.layer = layer;
        }

        @Override
        public Optional<ModuleReference> find(String name) {
            return layer.findModule(name).isPresent() ? Optional.empty() : finder.find(name);
        }

        @Override
        public Set<ModuleReference> findAll() {
            return finder.findAll().stream()
                    .filter(reference ->
                            layer.findModule(reference.descriptor().name()).isEmpty())
                    .collect(toUnmodifiableSet());
        }
    }
}
