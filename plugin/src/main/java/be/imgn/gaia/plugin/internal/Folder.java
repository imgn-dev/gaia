package be.imgn.gaia.plugin.internal;

import be.imgn.gaia.core.base.Version;
import be.imgn.gaia.plugin.PluginId;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.Set;

/**
 * A folder of the plugins directory that holds a plugin.
 *
 * @param id the id of the plugin
 * @param version the version of the plugin
 * @param directory the folder
 * @param finder the finder of the folder's modules
 * @param modules the names of the folder's modules
 * @param requires the names of the modules that the folder's modules require
 */
record Folder(
        PluginId id, Version version, Path directory, ModuleFinder finder, Set<String> modules, Set<String> requires) {

    /** Returns whether this folder holds the same plugin as another, at the same place. */
    boolean sameAs(Folder other) {
        return id.equals(other.id) && directory.equals(other.directory);
    }
}
