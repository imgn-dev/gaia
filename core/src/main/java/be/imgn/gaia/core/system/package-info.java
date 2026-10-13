/**
 * Sources of system properties and environment variables.
 *
 * <p>{@link be.imgn.gaia.core.system.SystemProperties} and {@link be.imgn.gaia.core.system.Environment} read
 * from the running JVM, or from fixed values in tests. Code that is given one, instead of calling
 * {@link System#getProperty(String)} or {@link System#getenv(String)}, can be tested on any platform.
 */
@NullMarked
package be.imgn.gaia.core.system;

import org.jspecify.annotations.NullMarked;
