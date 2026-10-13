package be.imgn.gaia.processing.testing;

import be.imgn.gaia.processing.testing.internal.GeneratedFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.annotation.processing.Processor;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * Compiles in-memory sources with the system Java compiler, in process, running the given annotation processors.
 *
 * <pre>{@code
 * Compilation compilation = TestCompiler.javac()
 *         .withProcessors(new MyProcessor())
 *         .withOptions("-Xlint:all,-processing", "-Werror")
 *         .compile(TestCompiler.source("example.Foo", """
 *                 package example;
 *                 class Foo {}
 *                 """));
 * }</pre>
 *
 * <p>The sources are compiled against the class path and the module path of the running JVM, unless the options give a
 * module path of their own. Sources outside a module read every module of that path; sources that include a
 * {@code module-info.java} read what it requires. Generated sources, class files and resources are written to temporary
 * directories, read back into the {@link Compilation}, and the directories are deleted.
 *
 * <p>A {@code TestCompiler} is immutable: {@link #withProcessors(Processor...)} and {@link #withOptions(String...)}
 * return a new one.
 */
public final class TestCompiler {

    private final List<Processor> processors;
    private final List<String> options;

    private TestCompiler(List<Processor> processors, List<String> options) {
        this.processors = processors;
        this.options = options;
    }

    /**
     * Returns a compiler with no annotation processor and no option.
     *
     * @return a compiler
     */
    public static TestCompiler javac() {
        return new TestCompiler(List.of(), List.of());
    }

    /**
     * Returns an in-memory compilation unit.
     *
     * @param qualifiedName the fully qualified name of the top-level type the source declares
     * @param text the source text
     * @return the compilation unit, named {@code mem:///<package path>/<simple name>.java}
     */
    public static JavaFileObject source(String qualifiedName, String text) {
        URI uri = URI.create("mem:///" + qualifiedName.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension);
        return new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return text;
            }
        };
    }

    /**
     * Returns a compiler that runs {@code processors}, instead of the processors this one runs. Processors are
     * stateful: give each compilation new instances.
     *
     * @param processors the annotation processors
     * @return a compiler running {@code processors}
     */
    public TestCompiler withProcessors(Processor... processors) {
        return new TestCompiler(List.of(processors), options);
    }

    /**
     * Returns a compiler that passes {@code options} to javac, instead of the options this one passes. They come after
     * {@code -proc:full}, which is always passed.
     *
     * <p>To check that generated code compiles cleanly, use {@code withOptions("-Xlint:all,-processing", "-Werror")}:
     * the {@code processing} lint warns whenever a processor does not claim its annotations, which well-behaved
     * processors never do.
     *
     * @param options the javac options
     * @return a compiler passing {@code options}
     */
    public TestCompiler withOptions(String... options) {
        return new TestCompiler(processors, List.of(options));
    }

    /**
     * Compiles {@code sources}.
     *
     * @param sources the compilation units, such as the ones {@link #source(String, String)} returns
     * @return the outcome; a compilation error is reported in it, not thrown
     * @throws IllegalStateException if the running Java has no system compiler
     * @throws UncheckedIOException if the temporary output directories cannot be written, read or deleted
     */
    public Compilation compile(JavaFileObject... sources) {
        return compile(List.of(sources));
    }

    /**
     * Compiles {@code sources}.
     *
     * @param sources the compilation units, such as the ones {@link #source(String, String)} returns
     * @return the outcome; a compilation error is reported in it, not thrown
     * @throws IllegalStateException if the running Java has no system compiler
     * @throws UncheckedIOException if the temporary output directories cannot be written, read or deleted
     */
    public Compilation compile(List<? extends JavaFileObject> sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler: run the tests on a JDK, not a JRE");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Path classOutput = createTempDirectory("class-output");
        Path sourceOutput = createTempDirectory("source-output");
        try (StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classOutput));
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(sourceOutput));
            JavaCompiler.CompilationTask task =
                    compiler.getTask(null, fileManager, diagnostics, javacOptions(sources), null, sources);
            task.setProcessors(processors);
            boolean success = task.call();
            List<JavaFileObject> generated = new ArrayList<>();
            generated.addAll(readBack(StandardLocation.CLASS_OUTPUT, classOutput));
            generated.addAll(readBack(StandardLocation.SOURCE_OUTPUT, sourceOutput));
            return new Compilation(success, diagnostics.getDiagnostics(), generated);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteRecursively(classOutput);
            deleteRecursively(sourceOutput);
        }
    }

    private List<String> javacOptions(List<? extends JavaFileObject> sources) {
        List<String> all = new ArrayList<>();
        all.add("-proc:full");
        // javac only gets java.class.path by default, which lacks the modules of a JVM started on the module path,
        // as test runners do for modular projects. A compiled module reads what its descriptor requires; anything
        // else reads every module of the module path.
        String modulePath = System.getProperty("jdk.module.path");
        if (modulePath != null && !options.contains("--module-path") && !options.contains("-p")) {
            all.addAll(List.of("--module-path", modulePath));
            boolean compilesModule = sources.stream()
                    .anyMatch(source -> source.isNameCompatible("module-info", JavaFileObject.Kind.SOURCE));
            if (!compilesModule) {
                all.addAll(List.of("--add-modules", "ALL-MODULE-PATH"));
            }
        }
        all.addAll(options);
        return all;
    }

    private static List<JavaFileObject> readBack(StandardLocation location, Path root) throws IOException {
        List<JavaFileObject> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                String relative = root.relativize(path)
                        .toString()
                        .replace(path.getFileSystem().getSeparator(), "/");
                files.add(new GeneratedFile(location.getName() + "/" + relative, Files.readAllBytes(path)));
            }
        }
        return files;
    }

    private static Path createTempDirectory(String prefix) {
        try {
            return Files.createTempDirectory("gaia-processing-testing-" + prefix);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteRecursively(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
