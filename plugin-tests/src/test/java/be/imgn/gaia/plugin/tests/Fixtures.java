package be.imgn.gaia.plugin.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.module.ResolvedModule;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.tools.ToolProvider;

/** Compiles the modules of plugins and applications while the tests run, as exploded modules. */
final class Fixtures {

    private final Path work;

    Fixtures(Path work) {
        this.work = work;
    }

    /**
     * Returns the sources of a plugin module whose {@code Main} class runs some code when it starts, where
     * {@code context} is its context, and {@code IntSupplier} is imported.
     */
    static Map<String, String> plugin(String module, String start) {
        return plugin(module, "", "", start);
    }

    /** Returns the sources of a plugin module, with annotations on its declaration, and more directives in it. */
    static Map<String, String> plugin(String module, String annotations, String directives, String start) {
        return Map.of(
                "module-info.java",
                """
                %s
                module %s {
                    requires be.imgn.gaia.plugin;
                    %s
                    provides be.imgn.gaia.plugin.Plugin with %s.Main;
                }
                """.formatted(annotations, module, directives, module),
                module.replace('.', '/') + "/Main.java",
                """
                package %s;

                import be.imgn.gaia.plugin.Plugin;
                import be.imgn.gaia.plugin.PluginContext;
                import java.util.function.IntSupplier;

                public final class Main implements Plugin {
                    @Override
                    public void start(PluginContext context) throws Exception {
                        %s
                    }
                }
                """.formatted(module, start));
    }

    /** Returns sources with one more file. */
    static Map<String, String> with(Map<String, String> sources, String path, String content) {
        var all = new HashMap<>(sources);
        all.put(path, content);
        return all;
    }

    /** Deletes a directory and everything in it. */
    static void delete(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /**
     * Compiles a module into {@code <folder>/<module>}, against gaia-plugin and the given module path.
     *
     * @return the folder, which holds the module
     */
    Path compile(Path folder, String module, String version, Map<String, String> sources, Path... modulePath)
            throws IOException {
        Files.createDirectories(work);
        var sourceDirectory = Files.createTempDirectory(work, module);
        var arguments = new ArrayList<>(List.of(
                "-d",
                folder.resolve(module).toString(),
                "--module-version",
                version,
                "--module-path",
                modulePath(modulePath),
                "-Xlint:none"));
        for (var source : sources.entrySet()) {
            var file = sourceDirectory.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            arguments.add(file.toString());
        }
        var output = new ByteArrayOutputStream();
        var code = ToolProvider.getSystemJavaCompiler().run(null, null, output, arguments.toArray(String[]::new));
        assertThat(code)
                .as(() -> "javac exit code, with its output:\n" + output.toString(Charset.defaultCharset()))
                .isZero();
        return folder;
    }

    // gaia-plugin and every module it reads, such as gaia-core and the annotation modules gaia-core reads.
    private static String modulePath(Path... more) {
        var path = new ArrayList<Path>();
        var pending = new ArrayDeque<ResolvedModule>();
        var seen = new HashSet<ResolvedModule>();
        pending.add(ModuleLayer.boot()
                .configuration()
                .findModule("be.imgn.gaia.plugin")
                .orElseThrow());
        while (!pending.isEmpty()) {
            var module = pending.remove();
            if (seen.add(module)) {
                module.reference()
                        .location()
                        .filter(location -> location.getScheme().equals("file"))
                        .ifPresent(location -> path.add(Path.of(location)));
                pending.addAll(module.reads());
            }
        }
        path.addAll(List.of(more));
        return path.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
    }
}
