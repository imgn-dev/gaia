package be.imgn.gaia.plugin;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The versions of a module that a plugin works with, on the plugin's module declaration. The module is one the plugin
 * {@code requires}: the host's API module, or another plugin.
 *
 * <pre>{@code
 * @RequiresVersion(module = "com.example.editor.api", atLeast = "2.1.0")
 * module com.example.blame {
 *     requires com.example.editor.api;
 *     ...
 * }
 * }</pre>
 *
 * <p>The plugin works with that version, and with every later one of the same major version: {@code 2.1.0} accepts
 * {@code 2.1.0} and {@code 2.7.3}, but neither {@code 2.0.9} nor {@code 3.0.0}. Otherwise, the plugin is
 * {@linkplain PluginState.Unresolved unresolved}. A required module without this annotation is accepted in any version.
 *
 * @since 0.1.3
 */
@Documented
@Target(ElementType.MODULE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RequiresVersion.List.class)
public @interface RequiresVersion {

    /**
     * Returns the name of the module.
     *
     * @return the name of the module
     */
    String module();

    /**
     * Returns the lowest version of the module that the plugin works with, as a semantic version such as {@code 2.1.0}.
     *
     * @return the lowest version of the module
     */
    String atLeast();

    /**
     * The versions of several modules that a plugin works with.
     *
     * @since 0.1.3
     */
    @Documented
    @Target(ElementType.MODULE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {

        /**
         * Returns the versions of each module.
         *
         * @return the versions of each module
         */
        RequiresVersion[] value();
    }
}
