package be.imgn.gaia.processing.testing;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.processing.testing.internal.GeneratedFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
     * @param qualifiedName the fully qualified name of the top-level type the source declares; or {@code module-info}
     *     for a module declaration; or {@code package-info} after the name of the package, if any, for a package
     *     declaration
     * @param text the source text
     * @return the compilation unit, named {@code mem:///<package path>/<simple name>.java}
     * @throws IllegalArgumentException if {@code qualifiedName} is not a qualified name, {@code module-info} or
     *     {@code package-info}
     */
    public static JavaFileObject source(String qualifiedName, String text) {
        requireNonNull(qualifiedName, "qualifiedName");
        requireNonNull(text, "text");
        Checks.checkSourceName(qualifiedName);
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
        requireNonNull(processors, "processors");
        return new TestCompiler(Checks.copyOfNonNull(Arrays.asList(processors), "processors"), options);
    }

    /**
     * Returns a compiler that passes {@code options} to javac, instead of the options this one passes. They come after
     * {@code -proc:full}, which is always passed.
     *
     * <p>To check that generated code compiles cleanly, use {@code withOptions("-Xlint:all,-processing", "-Werror")}:
     * the {@code processing} lint warns whenever a processor does not claim its annotations, which well-behaved
     * processors never do.
     *
     * <p>javac checks the options when {@link #compile(List)} runs: telling an option from the argument of another
     * takes its file manager.
     *
     * @param options the javac options
     * @return a compiler passing {@code options}
     */
    public TestCompiler withOptions(String... options) {
        requireNonNull(options, "options");
        return new TestCompiler(processors, Checks.copyOfNonNull(Arrays.asList(options), "options"));
    }

    /**
     * Compiles {@code sources}.
     *
     * @param sources the compilation units, such as the ones {@link #source(String, String)} returns
     * @return the outcome; a compilation error is reported in it, not thrown
     * @throws IllegalArgumentException if {@code sources} is empty or holds a file that is not a source file, or if
     *     javac rejects an option: unknown, without its argument, or with an invalid value
     * @throws IllegalStateException if the running Java has no system compiler, or if javac finds options that cannot
     *     be used together
     * @throws RuntimeException if an annotation processor, or a file given to javac, throws an exception, which is the
     *     cause of the one thrown
     * @throws UncheckedIOException if the temporary output directories cannot be written, read or deleted
     */
    public Compilation compile(JavaFileObject... sources) {
        requireNonNull(sources, "sources");
        return compile(Arrays.asList(sources));
    }

    /**
     * Compiles {@code sources}.
     *
     * @param sources the compilation units, such as the ones {@link #source(String, String)} returns
     * @return the outcome; a compilation error is reported in it, not thrown
     * @throws IllegalArgumentException if {@code sources} is empty or holds a file that is not a source file, or if
     *     javac rejects an option: unknown, without its argument, or with an invalid value
     * @throws IllegalStateException if the running Java has no system compiler, or if javac finds options that cannot
     *     be used together
     * @throws RuntimeException if an annotation processor, or a file given to javac, throws an exception, which is the
     *     cause of the one thrown
     * @throws UncheckedIOException if the temporary output directories cannot be written, read or deleted
     */
    public Compilation compile(List<? extends JavaFileObject> sources) {
        List<JavaFileObject> units = Checks.copyOfNonNull(sources, "sources");
        if (units.isEmpty()) {
            throw new IllegalArgumentException("No source to compile");
        }
        for (JavaFileObject unit : units) {
            if (unit.getKind() != JavaFileObject.Kind.SOURCE) {
                throw new IllegalArgumentException("Not a source file: " + unit.getName());
            }
        }
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
                    compiler.getTask(null, fileManager, diagnostics, javacOptions(units), null, units);
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
