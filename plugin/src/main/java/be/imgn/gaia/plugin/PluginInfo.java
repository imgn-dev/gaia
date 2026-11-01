package be.imgn.gaia.plugin;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.base.Version;
import java.nio.file.Path;

/**
 * A plugin installed in the plugins directory, and its state.
 *
 * @param id the id of the plugin: the name of its module
 * @param version the version of the plugin: the version of its module
 * @param directory the plugin's folder in the plugins directory
 * @param state the state of the plugin
 * @since 0.1.3
 */
public record PluginInfo(String id, Version version, Path directory, PluginState state) {

    /**
     * Creates the description of a plugin.
     *
     * @param id the id of the plugin: the name of its module
     * @param version the version of the plugin: the version of its module
     * @param directory the plugin's folder in the plugins directory
     * @param state the state of the plugin
     */
    public PluginInfo {
        requireNonNull(id, "id");
        requireNonNull(version, "version");
        requireNonNull(directory, "directory");
        requireNonNull(state, "state");
    }
}
