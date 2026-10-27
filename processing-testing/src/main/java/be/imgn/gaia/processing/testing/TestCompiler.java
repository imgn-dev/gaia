package be.imgn.gaia.processing.testing;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.processing.testing.internal.GeneratedFile;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
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
 * <p>A {@code TestCompiler} is immutable: {@link #withProcessors(Processor...)}, {@link #withOptions(String...)} and
 * {@link #withResource(JavaFileManager.Location, String, String)} return a new one.
 */
public final class TestCompiler {

    private static final System.Logger LOGGER = System.getLogger(TestCompiler.class.getName());

    private final List<Processor> processors;
    private final List<String> options;
    private final List<Resource> resources;

    private TestCompiler(List<Processor> processors, List<String> options, List<Resource> resources) {
        this.processors = processors;
        this.options = options;
        this.resources = resources;
    }

    /**
     * Returns a compiler with no annotation processor and no option.
     *
     * @return a compiler
     */
    public static TestCompiler javac() {
        return new TestCompiler(List.of(), List.of(), List.of());
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
        return new TestCompiler(Checks.copyOfNonNull(Arrays.asList(processors), "processors"), options, resources);
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
        return new TestCompiler(processors, Checks.copyOfNonNull(Arrays.asList(options), "options"), resources);
    }

    /**
     * Returns a compiler that also gives the compilation a resource, such as a file an annotation processor reads with
     * {@link javax.annotation.processing.Filer#getResource}, besides the resources this one gives. It replaces the
     * resource this one gives at the same location and path, if any.
     *
     * <pre>{@code
     * Compilation compilation = TestCompiler.javac()
     *         .withProcessors(new ViewProcessor())
     *         .withResource(StandardLocation.CLASS_OUTPUT, "example/MainView.toml", """
     *                 title = "Main"
     *                 """)
     *         .compile(TestCompiler.source("example.MainView", "..."));
     * }</pre>
     *
     * <p>A resource in {@link StandardLocation#CLASS_OUTPUT} is there before javac starts, as are the resources a build
     * tool copies to the class output before compiling. It is not among the {@linkplain Compilation#generatedFiles()
     * generated files}, unless the compilation writes other content to it. A resource in
     * {@link StandardLocation#SOURCE_PATH} is in a source path of its own, so the options must not give one, and the
     * sources must not have a {@code module-info}: javac would then look for them in that source path.
     *
     * @param location {@link StandardLocation#CLASS_OUTPUT} or {@link StandardLocation#SOURCE_PATH}
     * @param relativePath the path of the resource relative to the location, separated by {@code /}, such as
     *     {@code example/MainView.toml}
     * @param text the content of the resource, written in UTF-8
     * @return a compiler giving that resource too
     * @throws IllegalArgumentException if {@code location} is another location, or if {@code relativePath} is empty,
     *     starts or ends with {@code /}, contains {@code \}, or has an empty, {@code .} or {@code ..} segment
     */
    public TestCompiler withResource(JavaFileManager.Location location, String relativePath, String text) {
        requireNonNull(location, "location");
        requireNonNull(relativePath, "relativePath");
        requireNonNull(text, "text");
        if (location != StandardLocation.CLASS_OUTPUT && location != StandardLocation.SOURCE_PATH) {
            throw new IllegalArgumentException(
                    "A resource must be in CLASS_OUTPUT or SOURCE_PATH, not in " + location.getName());
        }
        Checks.checkResourcePath(relativePath);
        StandardLocation standard = location == StandardLocation.CLASS_OUTPUT
                ? StandardLocation.CLASS_OUTPUT
                : StandardLocation.SOURCE_PATH;
        List<Resource> all = new ArrayList<>();
        resources.stream()
                .filter(resource -> resource.location() != standard
                        || !resource.relativePath().equals(relativePath))
                .forEach(all::add);
        all.add(new Resource(standard, relativePath, text));
        return new TestCompiler(processors, options, List.copyOf(all));
    }

    /**
     * Compiles {@code sources}.
     *
     * <p>What javac writes besides its diagnostics, such as the output of {@code -verbose}, is logged at
     * {@link System.Logger.Level#INFO} with {@link System.Logger}.
     *
     * @param sources the compilation units, such as the ones {@link #source(String, String)} returns
     * @return the outcome; a compilation error is reported in it, not thrown
     * @throws IllegalArgumentException if {@code sources} is empty or holds a file that is not a source file, if javac
     *     rejects an option: unknown, without its argument, or with an invalid value, or if the options give a source
     *     path while a {@linkplain #withResource(JavaFileManager.Location, String, String) resource} is in
     *     {@link StandardLocation#SOURCE_PATH}, or if the sources have a {@code module-info} while a resource is in
     *     {@link StandardLocation#SOURCE_PATH}
     * @throws IllegalStateException if the running Java has no system compiler, if javac finds options that cannot be
     *     used together, or if javac fails without reporting an error, as when it crashes, with the output of javac as
     *     its message
     * @throws RuntimeException if an annotation processor, or a file given to javac, throws an exception, which is the
     *     cause of the one thrown
     * @throws UncheckedIOException if the temporary directories cannot be written, read or deleted
     */
    public Compilation compile(JavaFileObject... sources) {
        requireNonNull(sources, "sources");
        return compile(Arrays.asList(sources));
    }

    /**
     * Compiles {@code sources}.
     *
     * <p>What javac writes besides its diagnostics, such as the output of {@code -verbose}, is logged at
     * {@link System.Logger.Level#INFO} with {@link System.Logger}.
     *
     * @param sources the compilation units, such as the ones {@link #source(String, String)} returns
     * @return the outcome; a compilation error is reported in it, not thrown
     * @throws IllegalArgumentException if {@code sources} is empty or holds a file that is not a source file, if javac
     *     rejects an option: unknown, without its argument, or with an invalid value, or if the options give a source
     *     path while a {@linkplain #withResource(JavaFileManager.Location, String, String) resource} is in
     *     {@link StandardLocation#SOURCE_PATH}, or if the sources have a {@code module-info} while a resource is in
     *     {@link StandardLocation#SOURCE_PATH}
     * @throws IllegalStateException if the running Java has no system compiler, if javac finds options that cannot be
     *     used together, or if javac fails without reporting an error, as when it crashes, with the output of javac as
     *     its message
     * @throws RuntimeException if an annotation processor, or a file given to javac, throws an exception, which is the
     *     cause of the one thrown
     * @throws UncheckedIOException if the temporary directories cannot be written, read or deleted
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
        Map<String, byte[]> classOutputResources = resourcesIn(StandardLocation.CLASS_OUTPUT);
        Map<String, byte[]> sourcePathResources = resourcesIn(StandardLocation.SOURCE_PATH);
        if (!sourcePathResources.isEmpty()) {
            checkSourcePath(units);
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler: run the tests on a JDK, not a JRE");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Path classOutput = createTempDirectory("class-output");
        Path sourceOutput = createTempDirectory("source-output");
        Path sourcePath = createTempDirectory("source-path");
        try (StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            write(classOutput, classOutputResources);
            write(sourcePath, sourcePathResources);
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classOutput));
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(sourceOutput));
            if (!sourcePathResources.isEmpty()) {
                fileManager.setLocationFromPaths(StandardLocation.SOURCE_PATH, List.of(sourcePath));
            }
            StringWriter output = new StringWriter();
            JavaCompiler.CompilationTask task =
                    compiler.getTask(output, fileManager, diagnostics, javacOptions(units), null, units);
            task.setProcessors(processors);
            boolean success = call(task, diagnostics, output);
            List<JavaFileObject> generated = new ArrayList<>();
            generated.addAll(readBack(StandardLocation.CLASS_OUTPUT, classOutput, classOutputResources));
            generated.addAll(readBack(StandardLocation.SOURCE_OUTPUT, sourceOutput, Map.of()));
            return new Compilation(success, diagnostics.getDiagnostics(), generated);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteRecursively(classOutput);
            deleteRecursively(sourceOutput);
            deleteRecursively(sourcePath);
        }
    }

    // The source path that holds the resources given in SOURCE_PATH must be the one javac uses.
    private void checkSourcePath(List<JavaFileObject> units) {
        if (givesSourcePath()) {
            throw new IllegalArgumentException(
                    "The options give a source path, which would hide the resources given in SOURCE_PATH");
        }
        // javac looks for the sources of a module in its source path, where it cannot find sources held in memory.
        if (units.stream().anyMatch(unit -> unit.isNameCompatible("module-info", JavaFileObject.Kind.SOURCE))) {
            throw new IllegalArgumentException(
                    "A module-info source cannot be compiled with resources given in SOURCE_PATH");
        }
    }

    // javac reports a crash only in its output, which would otherwise leave a failure without a cause.
    private static boolean call(
            JavaCompiler.CompilationTask task, DiagnosticCollector<JavaFileObject> diagnostics, StringWriter output) {
        boolean success = task.call();
        if (!success
                && diagnostics.getDiagnostics().stream()
                        .noneMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)) {
            throw new IllegalStateException("javac failed without reporting an error:\n" + output);
        }
        if (!output.getBuffer().isEmpty()) {
            LOGGER.log(System.Logger.Level.INFO, output.toString());
        }
        return success;
    }

    private Map<String, byte[]> resourcesIn(StandardLocation location) {
        Map<String, byte[]> contents = new LinkedHashMap<>();
        resources.stream()
                .filter(resource -> resource.location() == location)
                .forEach(resource ->
                        contents.put(resource.relativePath(), resource.text().getBytes(StandardCharsets.UTF_8)));
        return contents;
    }

    private boolean givesSourcePath() {
        return options.stream()
                .anyMatch(option -> option.equals("-sourcepath")
                        || option.equals("--source-path")
                        || option.startsWith("--source-path="));
    }

    private static void write(Path root, Map<String, byte[]> files) throws IOException {
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            Path path = root.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.write(path, file.getValue());
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

    // The resources given in the location are left out, unless the compilation changed them.
    private static List<JavaFileObject> readBack(StandardLocation location, Path root, Map<String, byte[]> given)
            throws IOException {
        List<JavaFileObject> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                String relative = root.relativize(path)
                        .toString()
                        .replace(path.getFileSystem().getSeparator(), "/");
                byte[] content = Files.readAllBytes(path);
                byte[] original = given.get(relative);
                if (original == null || !Arrays.equals(original, content)) {
                    files.add(new GeneratedFile(location.getName() + "/" + relative, content));
                }
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

    /** A resource given to the compilation. */
    private record Resource(StandardLocation location, String relativePath, String text) {}
}
