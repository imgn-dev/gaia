package be.imgn.gaia.plugin.internal;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static be.imgn.gaia.core.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.plugin.Extensions;
import be.imgn.gaia.plugin.Plugin;
import be.imgn.gaia.plugin.PluginId;
import be.imgn.gaia.plugin.PluginInfo;
import be.imgn.gaia.plugin.PluginManager;
import be.imgn.gaia.plugin.PluginState;
import java.lang.System.Logger.Level;
import java.lang.module.Configuration;
import java.lang.module.FindException;
import java.lang.module.ModuleFinder;
import java.lang.module.ResolutionException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** The plugin manager: one lock guards its state, and every operation reconciles the plugins with it. */
final class DefaultPluginManager implements PluginManager {

    private static final System.Logger LOGGER = System.getLogger(DefaultPluginManager.class.getName());

    private final Settings settings;
    private final Object lock = new Object();
    private final Set<PluginId> disabled;
    private final Map<PluginId, Throwable> failures = new HashMap<>();
    // In the order the plugins started.
    private final Map<PluginId, Running> running = new LinkedHashMap<>();
    private final Map<PluginId, PluginState> states = new HashMap<>();
    private Plan plan;
    private boolean closed;

    private DefaultPluginManager(Settings settings) {
        this.settings = settings;
        this.disabled = StateFile.readDisabled(settings.stateFile());
        this.plan = Plan.of(Folders.scan(settings.directory()));
    }

    /** Creates a manager, then loads and starts the enabled plugins. */
    static PluginManager start(Settings settings) {
        var manager = new DefaultPluginManager(settings);
        synchronized (manager.lock) {
            manager.reconcile();
        }
        return manager;
    }

    @Override
    public List<PluginInfo> plugins() {
        synchronized (lock) {
            checkOpen();
            return plan.all().stream()
                    .map(folder -> new PluginInfo(folder.id(), folder.version(), folder.directory(), state(folder)))
                    .toList();
        }
    }

    @Override
    public <T> Extensions<T> extensions(Class<T> point) {
        requireNonNull(point, "point");
        return new LiveExtensions<>(settings.host().registry(), point);
    }

    @Override
    public void enable(PluginId id) {
        requireNonNull(id, "id");
        synchronized (lock) {
            checkInstalled(id);
            if (disabled.removeAll(plan.withRequirements(id))) {
                StateFile.writeDisabled(settings.stateFile(), disabled);
            }
            reconcile();
        }
    }

    @Override
    public void disable(PluginId id) {
        requireNonNull(id, "id");
        synchronized (lock) {
            checkInstalled(id);
            if (disabled.add(id)) {
                StateFile.writeDisabled(settings.stateFile(), disabled);
            }
            stop(Set.of(id));
            reconcile();
        }
    }

    @Override
    public void reload(PluginId id) {
        requireNonNull(id, "id");
        synchronized (lock) {
            checkInstalled(id);
            var stopped = stop(Set.of(id));
            stopped.add(id);
            failures.keySet().removeAll(stopped);
            plan = Plan.of(Folders.scan(settings.directory()));
            reconcile();
        }
    }

    @Override
    public void refresh() {
        synchronized (lock) {
            checkOpen();
            var next = Plan.of(Folders.scan(settings.directory()));
            stop(running.values().stream()
                    .filter(plugin -> !next.canStart(plugin.folder()))
                    .map(plugin -> plugin.folder().id())
                    .collect(Collectors.toSet()));
            plan = next;
            reconcile();
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (!closed) {
                closed = true;
                stop(Set.copyOf(running.keySet()));
            }
        }
    }

    private void checkOpen() {
        checkState(!closed, "The plugin manager is closed");
    }

    private void checkInstalled(PluginId id) {
        checkOpen();
        checkArgument(plan.contains(id), "%s holds no plugin %s", settings.directory(), id);
    }

    private PluginState state(Folder folder) {
        var current = running.get(folder.id());
        return current != null && current.folder().sameAs(folder)
                ? new PluginState.Started()
                : requireNonNull(states.get(folder.id()));
    }

    // Loads and starts, in order, every plugin that is not started and can be; and records why the others are not.
    private void reconcile() {
        var order = 0;
        for (var folder : plan.ordered()) {
            if (!running.containsKey(folder.id())) {
                states.put(folder.id(), load(folder, order));
            }
            order++;
        }
        plan.duplicates()
                .forEach((id, folders) -> states.put(
                        id,
                        new PluginState.Unresolved("Several folders hold this plugin: "
                                + folders.stream()
                                        .map(folder -> folder.directory().toString())
                                        .collect(Collectors.joining(", ")))));
    }

    private PluginState load(Folder folder, int order) {
        var blocker = blocker(folder);
        if (blocker.isPresent()) {
            return blocker.get();
        }
        ModuleLayer layer;
        try {
            layer = createLayer(folder);
        } catch (FindException | ResolutionException | LayerInstantiationException e) {
            return new PluginState.Unresolved(Objects.toString(e.getMessage(), e.toString()));
        }
        var module = layer.findModule(folder.id().name()).orElseThrow();
        return Versions.problem(module, layer)
                .<PluginState>map(PluginState.Unresolved::new)
                .orElseGet(() -> startPlugin(folder, layer, module, order));
    }

    // What keeps a plugin from loading, before its layer is created.
    private Optional<PluginState> blocker(Folder folder) {
        var id = folder.id();
        if (disabled.contains(id)) {
            return Optional.of(new PluginState.Disabled());
        }
        var failure = failures.get(id);
        if (failure != null) {
            return Optional.of(new PluginState.Failed(failure));
        }
        return cycle(folder)
                .or(() -> duplicateRequirement(folder))
                .or(() -> requirementNotStarted(folder))
                .or(() -> foreignModule(folder))
                .map(PluginState.Unresolved::new);
    }

    private Optional<String> cycle(Folder folder) {
        return plan.cyclic().contains(folder.id())
                ? Optional.of("It requires itself, through other plugins, or a plugin that does")
                : Optional.empty();
    }

    private Optional<String> duplicateRequirement(Folder folder) {
        return folder.requires().stream()
                .filter(name -> plan.duplicates().containsKey(new PluginId(name)))
                .findFirst()
                .map(name -> "It requires " + name + ", which several folders hold");
    }

    private Optional<String> requirementNotStarted(Folder folder) {
        return plan.requires().getOrDefault(folder.id(), List.of()).stream()
                .filter(required -> !running.containsKey(required))
                .findFirst()
                .map(required -> "It requires " + required + ", which is "
                        + requireNonNull(states.get(required))
                                .getClass()
                                .getSimpleName()
                                .toLowerCase(Locale.ROOT));
    }

    // A module that the host or another plugin has already: the plugin would get its own copy, with types of its own.
    private Optional<String> foreignModule(Folder folder) {
        return folder.modules().stream()
                .filter(name -> !name.equals(folder.id().name()))
                .filter(name -> settings.parent().findModule(name).isPresent() || plan.contains(new PluginId(name)))
                .sorted()
                .findFirst()
                .map(name -> "Its folder holds " + name + ", which the host or another plugin has already");
    }

    private ModuleLayer createLayer(Folder folder) {
        var parents = new ArrayList<ModuleLayer>();
        for (var required : plan.requires().getOrDefault(folder.id(), List.of())) {
            parents.add(requireNonNull(running.get(required)).layer());
        }
        parents.add(settings.parent());
        var configuration = Configuration.resolve(
                folder.finder(),
                parents.stream().map(ModuleLayer::configuration).toList(),
                ModuleFinder.of(),
                folder.modules());
        // The platform class loader, not the host's, so that the plugin never sees the host's class path.
        return ModuleLayer.defineModulesWithOneLoader(configuration, parents, ClassLoader.getPlatformClassLoader())
                .layer();
    }

    private PluginState startPlugin(Folder folder, ModuleLayer layer, Module module, int order) {
        var id = folder.id();
        var loader = layer.findLoader(id.name());
        var dataDirectory = settings.dataDirectory().resolve(id.name());
        var context = new Context(id, folder.version(), dataDirectory, loader, order, settings.host());
        try {
            var plugin = Calls.call(id + " start", loader, settings.host().timeout(), () -> {
                Files.createDirectories(dataDirectory);
                var instance = instantiate(layer, module);
                instance.start(context);
                return instance;
            });
            running.put(
                    id,
                    new Running(
                            folder,
                            layer,
                            loader,
                            plugin,
                            context,
                            plan.requires().getOrDefault(id, List.of())));
            return new PluginState.Started();
        } catch (CallFailedException e) {
            undo(id, loader, context, null);
            failures.put(id, e.failure());
            return new PluginState.Failed(e.failure());
        }
    }

    private static Plugin instantiate(ModuleLayer layer, Module module) {
        return ServiceLoader.load(layer, Plugin.class).stream()
                .filter(provider -> provider.type().getModule() == module)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(module.getName() + " provides no " + Plugin.class))
                .get();
    }

    // Stops plugins and those that require them, a plugin before the plugins it requires; returns those it stopped.
    private Set<PluginId> stop(Set<PluginId> ids) {
        var stopping = new HashSet<>(ids);
        var grew = true;
        while (grew) {
            grew = false;
            for (var plugin : running.values()) {
                var id = plugin.folder().id();
                if (!stopping.contains(id) && plugin.requires().stream().anyMatch(stopping::contains)) {
                    stopping.add(id);
                    grew = true;
                }
            }
        }
        var stopped = new HashSet<PluginId>();
        for (var id : List.copyOf(running.keySet()).reversed()) {
            if (stopping.contains(id)) {
                var plugin = requireNonNull(running.remove(id));
                undo(id, plugin.loader(), plugin.context(), plugin.plugin());
                stopped.add(id);
            }
        }
        return stopped;
    }

    // Removes the extensions, stops the plugin, shuts its executor down and closes its resources, in that order.
    private void undo(PluginId id, ClassLoader loader, Context context, @Nullable Plugin plugin) {
        var undo = context.stop();
        for (var registration : undo.registrations().reversed()) {
            registration.close();
        }
        if (plugin != null) {
            quietly(id + " stop", loader, () -> {
                plugin.stop();
                return true;
            });
        }
        var executor = undo.executor();
        if (executor != null) {
            shutDown(id, executor);
        }
        for (var resource : undo.resources().reversed()) {
            quietly(id + " close", loader, () -> {
                resource.close();
                return true;
            });
        }
    }

    private void quietly(String name, ClassLoader loader, Callable<?> code) {
        try {
            Calls.call(name, loader, settings.host().timeout(), code);
        } catch (CallFailedException e) {
            LOGGER.log(Level.WARNING, name + " failed", e.failure());
        }
    }

    private void shutDown(PluginId id, ScheduledExecutorService executor) {
        executor.shutdownNow();
        var timeout = settings.host().timeout();
        try {
            if (!executor.awaitTermination(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                LOGGER.log(Level.WARNING, "The executor of {0} did not stop within {1}", id, timeout);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * A started plugin.
     *
     * @param folder its folder
     * @param layer its layer
     * @param loader the class loader of its layer
     * @param plugin its entry point
     * @param context its context
     * @param requires the plugins it requires
     */
    private record Running(
            Folder folder,
            ModuleLayer layer,
            ClassLoader loader,
            Plugin plugin,
            Context context,
            List<PluginId> requires) {}
}
