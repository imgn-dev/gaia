package be.imgn.gaia.core.system;

import be.imgn.gaia.core.text.Splitter;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A source of environment variables: the running JVM's, or fixed ones for tests.
 *
 * <p>Code that reads environment variables through an {@code Environment} it is given, rather than through
 * {@link System#getenv(String)}, can be tested with {@link #of(Map)}.
 *
 * <p>Paths are returned as strings: which {@link java.nio.file.FileSystem} they belong to is for the caller to decide.
 */
@FunctionalInterface
public interface Environment {

    /**
     * Returns the environment of the running JVM. On Windows, variable names are case-insensitive.
     *
     * @return the JVM's environment
     */
    static Environment system() {
        return name -> Optional.ofNullable(System.getenv(name));
    }

    /**
     * Returns a fixed environment. Its variable names are case-sensitive, whatever the platform.
     *
     * @param variables the variables, by name; copied
     * @return an environment holding exactly {@code variables}
     * @throws NullPointerException if a name or value is {@code null}
     */
    static Environment of(Map<String, String> variables) {
        Map<String, String> copy = Map.copyOf(variables);
        return name -> Optional.ofNullable(copy.get(name));
    }

    /**
     * Returns the value of a variable.
     *
     * @param name the name of the variable, such as {@code "PATH"}
     * @return the value, or empty if the variable is not set
     */
    Optional<String> get(String name);

    /**
     * Returns the user's home directory: the {@code HOME} variable, set on Unix-like systems.
     *
     * @return the home directory, or empty if {@code HOME} is not set
     */
    default Optional<String> home() {
        return get("HOME");
    }

    /**
     * Returns the directories searched for executables: the {@code PATH} variable, split on the running JVM's path
     * separator ({@link File#pathSeparator}). Empty entries are skipped, and the double quotes Windows allows around an
     * entry are removed.
     *
     * @return the directories, in search order; empty if {@code PATH} is not set
     */
    default List<String> path() {
        return get("PATH").stream()
                .flatMap(Splitter.on(File.pathSeparator)::splitToStream)
                .map(entry -> entry.length() > 1 && entry.startsWith("\"") && entry.endsWith("\"")
                        ? entry.substring(1, entry.length() - 1)
                        : entry)
                .filter(entry -> !entry.isEmpty())
                .toList();
    }
}
