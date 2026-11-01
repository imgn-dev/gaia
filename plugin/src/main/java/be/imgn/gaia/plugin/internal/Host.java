package be.imgn.gaia.plugin.internal;

import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What the host gives every plugin.
 *
 * @param services the services the host offers, by type
 * @param contextClassLoader the context class loader the host's services run with
 * @param registry where the plugins' extensions go
 * @param timeout how long a plugin may take to start or stop
 */
record Host(
        Map<Class<?>, Object> services,
        @Nullable ClassLoader contextClassLoader,
        ExtensionRegistry registry,
        Duration timeout) {}
