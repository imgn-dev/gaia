package be.imgn.gaia.maven;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.lang.model.SourceVersion;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * Reads the provider-configuration files in the {@code META-INF/services} directory of compiled classes, in the format
 * {@link java.util.ServiceLoader} reads: one provider per line, with {@code #} comments and blank lines ignored.
 */
final class ProviderFiles {

    static final String DIRECTORY = "META-INF/services";

    private ProviderFiles() {}

    /**
     * Returns the providers of services, by service name. A service with no file is left out; a file that lists no
     * provider gives an empty list, so that a directive written from an earlier version of the file can be removed.
     *
     * @param classes the directory of compiled classes
     * @param services the names of the services to read, or an empty list to read every file in the directory
     * @return the providers of each service in the order they are listed, without duplicates, sorted by service
     * @throws MojoExecutionException if a name is not a binary class name, or if a provider is not a class in
     *     {@code classes}
     */
    static SortedMap<String, List<String>> read(Path classes, List<String> services)
            throws IOException, MojoExecutionException {
        var directory = classes.resolve(DIRECTORY);
        var providers = new TreeMap<String, List<String>>();
        for (var service : services.isEmpty() ? list(directory) : services) {
            checkName(service, "Not a service name: " + service);
            var file = directory.resolve(service);
            if (Files.isRegularFile(file)) {
                providers.put(service, providers(classes, file));
            }
        }
        return providers;
    }

    private static List<String> list(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .map(file -> file.getFileName().toString())
                    .toList();
        }
    }

    private static List<String> providers(Path classes, Path file) throws IOException, MojoExecutionException {
        var providers = new LinkedHashSet<String>();
        for (var line : Files.readAllLines(file, UTF_8)) {
            var comment = line.indexOf('#');
            var provider = (comment < 0 ? line : line.substring(0, comment)).strip();
            if (!provider.isEmpty()) {
                checkName(provider, "Not a provider name in " + file + ": " + provider);
                if (!Files.isRegularFile(classes.resolve(provider.replace('.', '/') + ".class"))) {
                    throw new MojoExecutionException(
                            "The provider " + provider + " listed in " + file + " is not a class in " + classes);
                }
                providers.add(provider);
            }
        }
        return List.copyOf(providers);
    }

    private static void checkName(String name, String message) throws MojoExecutionException {
        if (!SourceVersion.isName(name)) {
            throw new MojoExecutionException(message);
        }
    }
}
