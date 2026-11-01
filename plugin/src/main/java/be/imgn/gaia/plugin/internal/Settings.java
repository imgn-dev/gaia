package be.imgn.gaia.plugin.internal;

import java.nio.file.Path;

/**
 * How a plugin manager was built.
 *
 * @param directory the plugins directory
 * @param stateFile the file that keeps which plugins are disabled
 * @param dataDirectory the directory of the plugins' data
 * @param parent the layer of the host's modules
 * @param host what the host gives every plugin
 */
record Settings(Path directory, Path stateFile, Path dataDirectory, ModuleLayer parent, Host host) {}
