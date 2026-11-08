/**
 * The foundation the other packages build on: preconditions, exceptions, charsets, versions, text, time measurement,
 * and system properties and environment variables.
 *
 * <p>{@link be.imgn.gaia.core.base.SystemProperties} and {@link be.imgn.gaia.core.base.Environment} read from the
 * running JVM, or from fixed values in tests. Code that is given one, instead of calling
 * {@link System#getProperty(String)} or {@link System#getenv(String)}, can be tested on any platform.
 */
@NullMarked
package be.imgn.gaia.core.base;

import org.jspecify.annotations.NullMarked;
