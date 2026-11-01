package be.imgn.gaia.plugin.internal;

import static java.util.stream.Collectors.toUnmodifiableSet;

import be.imgn.gaia.core.base.Version;
import be.imgn.gaia.plugin.Plugin;
import be.imgn.gaia.plugin.PluginId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.lang.module.FindException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Reads the plugins directory: one folder per plugin. */
final class Folders {

    private static final System.Logger LOGGER = System.getLogger(Folders.class.getName());
    private static final String PLUGIN = Plugin.class.getName();

    private Folders() {}

    /**
     * Reads the folders of the plugins directory, in the order of their names. A folder that does not hold a plugin is
     * left out, with a warning.
     *
     * @throws UncheckedIOException if the directory cannot be listed
     */
    static List<Folder> scan(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<Path> paths;
        try (var stream = Files.list(directory)) {
            paths = stream.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var folders = new ArrayList<Folder>();
        for (var path : paths) {
            read(path).ifPresent(folders::add);
        }
        return List.copyOf(folders);
    }

    private static Optional<Folder> read(Path directory) {
        var finder = ModuleFinder.of(directory);
        Set<ModuleReference> references;
        try {
            references = finder.findAll();
        } catch (FindException e) {
            return leaveOut(directory, Objects.toString(e.getMessage(), e.toString()));
        }
        var descriptors = references.stream().map(ModuleReference::descriptor).toList();
        var plugins = descriptors.stream().filter(Folders::providesPlugin).toList();
        var providers = plugins.stream()
                .flatMap(descriptor -> descriptor.provides().stream())
                .filter(provides -> provides.service().equals(PLUGIN))
                .mapToInt(provides -> provides.providers().size())
                .sum();
        if (providers != 1) {
            return leaveOut(
                    directory, "its modules provide " + providers + " implementations of " + PLUGIN + ", not 1");
        }
        var plugin = plugins.getFirst();
        var version = plugin.rawVersion().flatMap(Version::tryParse);
        if (version.isEmpty()) {
            return leaveOut(directory, "the version of " + plugin.name() + " is missing or not a semantic version");
        }
        var modules = descriptors.stream().map(ModuleDescriptor::name).collect(toUnmodifiableSet());
        var requires = descriptors.stream()
                .flatMap(descriptor -> descriptor.requires().stream())
                .map(ModuleDescriptor.Requires::name)
                .collect(toUnmodifiableSet());
        return Optional.of(
                new Folder(new PluginId(plugin.name()), version.get(), directory, finder, modules, requires));
    }

    private static boolean providesPlugin(ModuleDescriptor descriptor) {
        return descriptor.provides().stream()
                .anyMatch(provides -> provides.service().equals(PLUGIN));
    }

    private static Optional<Folder> leaveOut(Path directory, String reason) {
        LOGGER.log(Level.WARNING, "Leaving out {0}, which is not a plugin: {1}", directory, reason);
        return Optional.empty();
    }
}
