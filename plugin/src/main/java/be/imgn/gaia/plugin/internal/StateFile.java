package be.imgn.gaia.plugin.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import be.imgn.gaia.core.io.AtomicFiles;
import be.imgn.gaia.plugin.PluginId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * The file that keeps which plugins are disabled: a properties file, with one {@code <id>=disabled} line per disabled
 * plugin. A plugin that it does not list is enabled.
 */
final class StateFile {

    private static final System.Logger LOGGER = System.getLogger(StateFile.class.getName());
    private static final String DISABLED = "disabled";

    private StateFile() {}

    /**
     * Reads the disabled plugins. A missing file lists none.
     *
     * @throws UncheckedIOException if the file cannot be read
     */
    static Set<PluginId> readDisabled(Path file) {
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(file, UTF_8)) {
            properties.load(reader);
        } catch (NoSuchFileException e) {
            return new HashSet<>();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var disabled = new HashSet<PluginId>();
        for (var name : properties.stringPropertyNames()) {
            if (DISABLED.equals(properties.getProperty(name))) {
                id(file, name).ifPresent(disabled::add);
            }
        }
        return disabled;
    }

    /**
     * Writes the disabled plugins, atomically.
     *
     * @throws UncheckedIOException if the file cannot be written
     */
    static void writeDisabled(Path file, Set<PluginId> disabled) {
        var text = new StringBuilder("# The plugins that are disabled.\n");
        disabled.stream()
                .map(PluginId::name)
                .sorted()
                .forEach(name -> text.append(name).append('=').append(DISABLED).append('\n'));
        try {
            var parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            AtomicFiles.writeString(file, text, UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<PluginId> id(Path file, String name) {
        try {
            return Optional.of(new PluginId(name));
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Ignoring {0} in {1}: it is not a plugin id", name, file);
            return Optional.empty();
        }
    }
}
