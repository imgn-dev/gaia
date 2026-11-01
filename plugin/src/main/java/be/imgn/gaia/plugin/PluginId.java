package be.imgn.gaia.plugin;

import static java.util.Objects.requireNonNull;

import java.lang.module.ModuleDescriptor;

/**
 * The id of a plugin: the name of its module, such as {@code com.example.blame}.
 *
 * <p>An id is always a legal module name: names separated by dots, each a Java identifier that is not a keyword. Text
 * such as a command-line option becomes an id only once it is checked:
 *
 * <pre>{@code
 * var id = new PluginId(option); // throws if the option is not a module name
 * plugins.disable(id);
 * }</pre>
 *
 * <p>An id stays the same across reloads and updates of its plugin.
 *
 * @param name the name of the plugin's module
 * @since 0.1.3
 */
public record PluginId(String name) {

    /**
     * Creates the id of a plugin.
     *
     * @param name the name of the plugin's module
     * @throws IllegalArgumentException if the name is not a legal module name
     */
    public PluginId {
        requireNonNull(name, "name");
        // The JDK's own check of module names, the one that module descriptors pass.
        var unused = ModuleDescriptor.newModule(name);
    }

    /**
     * Returns the name of the plugin's module.
     *
     * @return the name of the plugin's module
     */
    @Override
    public String toString() {
        return name;
    }
}
