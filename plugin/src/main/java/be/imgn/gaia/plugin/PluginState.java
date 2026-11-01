package be.imgn.gaia.plugin;

import static java.util.Objects.requireNonNull;

/**
 * The state of a plugin.
 *
 * @since 0.1.3
 */
public sealed interface PluginState {

    /** The plugin is enabled and started: its extensions are registered. */
    record Started() implements PluginState {}

    /** The plugin is disabled: it is not loaded. */
    record Disabled() implements PluginState {}

    /**
     * The plugin is enabled, but cannot be loaded: a module it requires is missing, has too low a version, or is a
     * plugin that is not started; another folder of the plugins directory holds the same plugin; or its module layer
     * could not be created.
     *
     * @param reason why the plugin cannot be loaded, for the user
     */
    record Unresolved(String reason) implements PluginState {

        /**
         * Creates the state of a plugin that cannot be loaded.
         *
         * @param reason why the plugin cannot be loaded, for the user
         */
        public Unresolved {
            requireNonNull(reason, "reason");
        }
    }

    /**
     * The plugin is enabled and was loaded, but failed to start: {@link Plugin#start(PluginContext)} threw, or did not
     * return in time. The plugin stays failed until it is reloaded.
     *
     * @param cause what {@code start} threw, or a {@link java.util.concurrent.TimeoutException} if it did not return in
     *     time
     */
    record Failed(Throwable cause) implements PluginState {

        /**
         * Creates the state of a plugin that failed to start.
         *
         * @param cause what {@code start} threw, or a {@link java.util.concurrent.TimeoutException} if it did not
         *     return in time
         */
        public Failed {
            requireNonNull(cause, "cause");
        }
    }
}
