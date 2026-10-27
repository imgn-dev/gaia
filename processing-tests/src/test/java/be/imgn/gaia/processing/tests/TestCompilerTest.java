package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.gaia.processing.testing.Compilation;
import be.imgn.gaia.processing.testing.TestCompiler;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestCompilerTest {

    private static final JavaFileObject FOO = source("example.Foo", """
            package example;

            class Foo {}
            """);

    private static final JavaFileObject BROKEN = source("example.Broken", """
            package example;

            class Broken {
            """);

    private static final JavaFileObject TYPO = source("example.Typo", """
            package example;

            class Typo {
                int number = "text";
            }
            """);

    private static final JavaFileObject RAW = source("example.Raw", """
            package example;

            class Raw {
                java.util.List list;
            }
            """);

    @Test
    void compilesTheSourcesAndKeepsTheClassFiles() {
        Compilation compilation = javac().compile(FOO);

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "example/Foo.class"))
                .isPresent();
    }

    @Test
    void runsTheProcessorsAndKeepsWhatTheyGenerate() {
        Compilation compilation = javac().withProcessors(new InfoProcessor())
                .withOptions("-Xlint:all,-processing", "-Werror")
                .compile(FOO);

        assertThat(compilation).succeeded().hadNoWarnings().hadNoteContaining("Generated example.FooInfo");
        assertThat(compilation).generatedSourceFile("example.FooInfo").contains("NAME = \"example.Foo\"");
        assertThat(compilation.generatedSourceFiles()).hasSize(1);
        assertThat(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "example/FooInfo.class"))
                .isPresent();
    }

    @Test
    void reportsErrorsInsteadOfThrowing() {
        Compilation compilation = javac().compile(BROKEN);

        assertThat(compilation).failed().hadErrorContaining("reached end of file");
        assertThat(compilation.errors()).isNotEmpty();
    }

    @Test
    void reportsWarnings() {
        Compilation compilation = javac().withOptions("-Xlint:all").compile(RAW);

        assertThat(compilation).succeeded();
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(compilation).hadNoWarnings())
                .withMessageContaining("WARNING at /example/Raw.java:4:");
    }

    @Test
    void listsTheDiagnosticsWhenAnAssertionFails() {
        Compilation compilation = javac().compile(BROKEN);

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(compilation).succeeded())
                .withMessageContaining("ERROR at /example/Broken.java:")
                .withMessageContaining("reached end of file while parsing");
    }

    @Test
    void listsTheGeneratedSourcesWhenOneIsMissing() {
        Compilation compilation = javac().withProcessors(new InfoProcessor()).compile(FOO);

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(compilation).generatedSourceFile("example.Missing"))
                .withMessageContaining("/SOURCE_OUTPUT/example/FooInfo.java");
    }

    @Test
    void leavesTheOriginalCompilerUnchanged() {
        var compiler = javac();

        compiler.withProcessors(new InfoProcessor());
        compiler.withOptions("-Werror");

        assertThat(compiler.compile(FOO).generatedSourceFiles()).isEmpty();
    }

    @Test
    void locatesErrors() {
        Compilation compilation = javac().compile(TYPO);

        assertThat(compilation).failed().hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("incompatible types")
                .inFile("example/Typo.java")
                .onLine(4)
                .atColumn(18);
    }

    @Test
    void listsTheCandidatesWhenTheLocationIsWrong() {
        Compilation compilation = javac().compile(TYPO);

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(compilation)
                        .hadErrorContaining("incompatible types")
                        .onLine(3))
                .withMessageContaining("on line 3")
                .withMessageContaining("/example/Typo.java:4:18");
    }

    @Test
    void locatesWarningsAndNotes() {
        Compilation compilation = javac().withProcessors(new InfoProcessor())
                .withOptions("-Xlint:all,-processing")
                .compile(FOO, RAW);

        assertThat(compilation)
                .hadWarningContaining("raw type")
                .inFile("example/Raw.java")
                .onLine(4);
        assertThat(compilation)
                .hadNoteContaining("Generated example.FooInfo")
                .inFile("example/Foo.java")
                .onLine(3);
    }

    @Test
    void countsErrors() {
        assertThat(javac().compile(FOO)).hadErrorCount(0);
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(javac().compile(TYPO)).hadErrorCount(0))
                .withMessageContaining("Expected 0 error(s), but there were 1");
    }

    @Test
    void readsGeneratedResources() {
        Compilation compilation = javac().withProcessors(new InfoProcessor()).compile(FOO);

        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/infos/example.Foo")
                .isEqualTo("example.FooInfo");
    }

    @Test
    void checksThatNoSourceWasGenerated() {
        assertThat(javac().compile(FOO)).generatedNoSourceFiles();
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(
                                javac().withProcessors(new InfoProcessor()).compile(FOO))
                        .generatedNoSourceFiles())
                .withMessageContaining("/SOURCE_OUTPUT/example/FooInfo.java");
    }

    @Test
    void acceptsTypeModuleAndPackageDeclarations() {
        assertThatNoException().isThrownBy(() -> source("Foo", "class Foo {}"));
        assertThatNoException().isThrownBy(() -> source("example.Foo$Bar", ""));
        assertThatNoException().isThrownBy(() -> source("module-info", "module example {}"));
        assertThatNoException().isThrownBy(() -> source("package-info", ""));
        assertThatNoException().isThrownBy(() -> source("example.package-info", "package example;"));
    }

    @Test
    void rejectsWhatIsNotTheNameOfACompilationUnit() {
        for (String name : List.of(
                "",
                "example.",
                ".Foo",
                "example..Foo",
                "example/Foo",
                "example.class",
                "1Foo",
                "Foo Bar",
                "Foo\u0000",
                "example.module-info",
                "module-info.Foo",
                "package-info.Foo",
                ".package-info")) {
            assertThatIllegalArgumentException()
                    .as(name)
                    .isThrownBy(() -> source(name, ""))
                    .withMessageContaining(name);
        }
    }

    @Test
    void rejectsNoSource() {
        assertThatIllegalArgumentException().isThrownBy(() -> javac().compile()).withMessageContaining("No source");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> javac().compile(List.of()))
                .withMessageContaining("No source");
    }

    @Test
    void rejectsAFileThatIsNotASource() {
        JavaFileObject classFile =
                new SimpleJavaFileObject(URI.create("mem:///example/Foo.class"), JavaFileObject.Kind.CLASS) {};

        assertThatIllegalArgumentException()
                .isThrownBy(() -> javac().compile(FOO, classFile))
                .withMessageContaining("/example/Foo.class");
    }

    @Test
    void rejectsAnUnknownOption() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> javac().withOptions("--no-such-option").compile(FOO))
                .withMessageContaining("--no-such-option");
    }

    @Test
    void rejectsOptionsThatCannotBeUsedTogether() {
        assertThatIllegalStateException()
                .isThrownBy(() ->
                        javac().withOptions("--source", "21", "--target", "17").compile(FOO));
    }

    @Test
    void wrapsWhatAProcessorThrows() {
        assertThatThrownBy(() -> javac().withProcessors(new FailingProcessor()).compile(FOO))
                .isExactlyInstanceOf(RuntimeException.class)
                .cause()
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Failing on purpose");
    }

    @Test
    void rejectsANegativeErrorCount() {
        Compilation compilation = javac().compile(FOO);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> assertThat(compilation).hadErrorCount(-1))
                .withMessageContaining("-1");
    }

    @Test
    void rejectsALocationBeforeTheFirstLineOrColumn() {
        Compilation compilation = javac().compile(TYPO);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> assertThat(compilation)
                        .hadErrorContaining("incompatible types")
                        .onLine(0))
                .withMessageContaining("0");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> assertThat(compilation)
                        .hadErrorContaining("incompatible types")
                        .atColumn(0))
                .withMessageContaining("0");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> assertThat(compilation)
                        .hadErrorContaining("incompatible types")
                        .inFile(""));
    }

    @Test
    void rejectsWhatCannotNameAGeneratedFile() {
        Compilation compilation = javac().withProcessors(new InfoProcessor()).compile(FOO);

        assertThatIllegalArgumentException().isThrownBy(() -> compilation.generatedSourceFile("example/FooInfo"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "/example/Foo.class"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> compilation.generatedFile(StandardLocation.CLASS_OUTPUT, ""));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> assertThat(compilation).generatedSourceFile("example/FooInfo"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> assertThat(compilation).generatedFile(StandardLocation.CLASS_OUTPUT, "/example"));
    }

    @Test
    void givesAResourceInTheClassOutput() {
        ResourceReader reader = new ResourceReader(StandardLocation.CLASS_OUTPUT);
        Compilation compilation = javac().withProcessors(reader)
                .withResource(StandardLocation.CLASS_OUTPUT, "example/Foo.toml", "title = \"Foo\"\n")
                .compile(FOO);

        assertThat(compilation).succeeded();
        assertThat(reader.texts).containsExactly("title = \"Foo\"\n");
        assertThat(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "example/Foo.toml"))
                .isEmpty();
    }

    @Test
    void givesABinaryResource() {
        // The PNG signature, which is not UTF-8.
        byte[] content = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
        ResourceReader classOutputReader = new ResourceReader(StandardLocation.CLASS_OUTPUT, true);
        ResourceReader sourcePathReader = new ResourceReader(StandardLocation.SOURCE_PATH, true);
        var compiler = javac().withProcessors(classOutputReader, sourcePathReader)
                .withResource(StandardLocation.CLASS_OUTPUT, "example/Foo.toml", content)
                .withResource(StandardLocation.SOURCE_PATH, "example/Foo.toml", content);
        content[0] = 0;

        Compilation compilation = compiler.compile(FOO);

        assertThat(compilation).succeeded();
        assertThat(classOutputReader.texts).containsExactly("89504e470d0a1a0a");
        assertThat(sourcePathReader.texts).containsExactly("89504e470d0a1a0a");
        assertThat(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "example/Foo.toml"))
                .isEmpty();
    }

    @Test
    void givesAResourceInTheSourcePath() {
        ResourceReader reader = new ResourceReader(StandardLocation.SOURCE_PATH);
        Compilation compilation = javac().withProcessors(reader)
                .withResource(StandardLocation.SOURCE_PATH, "example/Foo.toml", "title = \"Foo\"\n")
                .compile(FOO);

        assertThat(compilation).succeeded();
        assertThat(reader.texts).containsExactly("title = \"Foo\"\n");
    }

    @Test
    void givesAResourceToTheCompilationOfAModule() {
        ResourceReader reader = new ResourceReader(StandardLocation.CLASS_OUTPUT);
        Compilation compilation = javac().withProcessors(reader)
                .withResource(StandardLocation.CLASS_OUTPUT, "example/Foo.toml", "é")
                .compile(FOO, source("module-info", "module example {}"));

        assertThat(compilation).succeeded();
        assertThat(reader.texts).containsExactly("é");
    }

    @Test
    void reportsAResourceTheCompilationRewrites() {
        Compilation compilation = javac().withProcessors(new ResourceWriter("title = \"Bar\"\n"))
                .withResource(StandardLocation.CLASS_OUTPUT, "example/Foo.toml", "title = \"Foo\"\n")
                .compile(FOO);

        assertThat(compilation)
                .succeeded()
                .generatedFile(StandardLocation.CLASS_OUTPUT, "example/Foo.toml")
                .isEqualTo("title = \"Bar\"\n");
    }

    @Test
    void replacesTheResourceAtTheSameLocationAndPath() {
        var compiler = javac().withResource(StandardLocation.CLASS_OUTPUT, "example/Foo.toml", "first");
        ResourceReader reader = new ResourceReader(StandardLocation.CLASS_OUTPUT);
        ResourceReader sourcePathReader = new ResourceReader(StandardLocation.SOURCE_PATH);

        Compilation compilation = compiler.withResource(StandardLocation.CLASS_OUTPUT, "example/Foo.toml", "second")
                .withResource(StandardLocation.SOURCE_PATH, "example/Foo.toml", "elsewhere")
                .withProcessors(reader, sourcePathReader)
                .compile(FOO);

        assertThat(compilation).succeeded();
        assertThat(reader.texts).containsExactly("second");
        assertThat(sourcePathReader.texts).containsExactly("elsewhere");
    }

    @Test
    void leavesTheOriginalCompilerWithoutTheResource() {
        var compiler = javac();
        ResourceReader reader = new ResourceReader(StandardLocation.CLASS_OUTPUT);

        compiler.withResource(StandardLocation.CLASS_OUTPUT, "example/Foo.toml", "title = \"Foo\"\n");
        compiler.withProcessors(reader).compile(FOO);

        assertThat(reader.texts).containsExactly("missing");
    }

    @Test
    void rejectsAResourceElsewhereThanInTheClassOutputOrTheSourcePath() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> javac().withResource(StandardLocation.SOURCE_OUTPUT, "example/Foo.toml", ""))
                .withMessage("A resource must be in CLASS_OUTPUT or SOURCE_PATH, not in SOURCE_OUTPUT");
    }

    @Test
    void rejectsWhatCannotNameAResource() {
        for (String path : List.of(
                "",
                "/",
                "/example/Foo.toml",
                "example/",
                "example//Foo.toml",
                "./Foo.toml",
                "example/../Foo.toml",
                "..",
                "example\\Foo.toml")) {
            assertThatIllegalArgumentException()
                    .as(path)
                    .isThrownBy(() -> javac().withResource(StandardLocation.CLASS_OUTPUT, path, ""))
                    .withMessage("Not a path to a file relative to a location, such as example/Foo.toml: \"%s\"", path);
        }
    }

    @Test
    void rejectsASourcePathOptionWithResourcesInTheSourcePath() {
        var compiler = javac().withResource(StandardLocation.SOURCE_PATH, "example/Foo.toml", "");

        for (List<String> options :
                List.of(List.of("-sourcepath", "src"), List.of("--source-path", "src"), List.of("--source-path=src"))) {
            assertThatIllegalArgumentException()
                    .as(options.toString())
                    .isThrownBy(() ->
                            compiler.withOptions(options.toArray(String[]::new)).compile(FOO))
                    .withMessage("The options give a source path, which would hide the resources given in SOURCE_PATH");
        }
    }

    @Test
    void logsWhatJavacWritesBesidesItsDiagnostics() {
        Compilation compilation = javac().withOptions("-verbose").compile(FOO);

        assertThat(compilation).succeeded();
        assertThat(RecordingLoggerFinder.records(TestCompiler.class.getName()))
                .singleElement()
                .satisfies(record -> assertThat(record).startsWith("INFO ").contains("[parsing started"));
    }

    @Test
    void rejectsAModuleInfoSourceWithResourcesInTheSourcePath() {
        var compiler = javac().withResource(StandardLocation.SOURCE_PATH, "example/Foo.toml", "");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> compiler.compile(FOO, source("module-info", "module example {}")))
                .withMessage("A module-info source cannot be compiled with resources given in SOURCE_PATH");
    }

    @Test
    void throwsWithTheOutputOfJavacWhenItFailsWithoutAnError(@TempDir Path sourcePath) {
        // javac crashes looking for a source held in memory in the source path of a module.
        var compiler = javac().withOptions("--source-path", sourcePath.toString());

        assertThatIllegalStateException()
                .isThrownBy(() -> compiler.compile(FOO, source("module-info", "module example {}")))
                .withMessageStartingWith("javac failed without reporting an error:\n")
                .withMessageContaining("java.lang.IllegalArgumentException");
    }

    @Test
    @SuppressWarnings("NullAway")
    void rejectsANullResourceArgument() {
        assertThatNullPointerException()
                .isThrownBy(() -> javac().withResource(null, "example/Foo.toml", ""))
                .withMessage("location");
        assertThatNullPointerException()
                .isThrownBy(() -> javac().withResource(StandardLocation.CLASS_OUTPUT, null, ""))
                .withMessage("relativePath");
        assertThatNullPointerException()
                .isThrownBy(
                        () -> javac().withResource(StandardLocation.CLASS_OUTPUT, "example/Foo.toml", (String) null))
                .withMessage("text");
        assertThatNullPointerException()
                .isThrownBy(() -> javac().withResource(null, "example/Foo.toml", new byte[0]))
                .withMessage("location");
        assertThatNullPointerException()
                .isThrownBy(() -> javac().withResource(StandardLocation.CLASS_OUTPUT, null, new byte[0]))
                .withMessage("relativePath");
        assertThatNullPointerException()
                .isThrownBy(
                        () -> javac().withResource(StandardLocation.CLASS_OUTPUT, "example/Foo.toml", (byte[]) null))
                .withMessage("content");
    }

    @Test
    void failsEveryAssertionOnANullCompilation() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat((Compilation) null).succeeded());
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat((Compilation) null).hadErrorContaining("error"));
    }

    /** Reads {@code example/Foo.toml} from a location in the first round, or notes that it is missing. */
    @SupportedAnnotationTypes("*")
    private static final class ResourceReader extends AbstractProcessor {

        private final List<String> texts = new ArrayList<>();
        private final JavaFileManager.Location location;
        private final boolean binary;

        ResourceReader(JavaFileManager.Location location) {
            this(location, false);
        }

        // A binary resource is read as its bytes in hexadecimal.
        ResourceReader(JavaFileManager.Location location, boolean binary) {
            this.location = location;
            this.binary = binary;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (texts.isEmpty()) {
                try {
                    FileObject resource = processingEnv.getFiler().getResource(location, "example", "Foo.toml");
                    if (binary) {
                        try (InputStream in = resource.openInputStream()) {
                            texts.add(HexFormat.of().formatHex(in.readAllBytes()));
                        }
                    } else {
                        texts.add(resource.getCharContent(false).toString());
                    }
                } catch (FileNotFoundException | NoSuchFileException e) {
                    texts.add("missing");
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return false;
        }
    }

    /** Writes {@code example/Foo.toml} to the class output in the first round. */
    @SupportedAnnotationTypes("*")
    private static final class ResourceWriter extends AbstractProcessor {

        private final String text;
        private boolean written;

        ResourceWriter(String text) {
            this.text = text;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (!written) {
                written = true;
                try (Writer writer = processingEnv
                        .getFiler()
                        .createResource(StandardLocation.CLASS_OUTPUT, "example", "Foo.toml")
                        .openWriter()) {
                    writer.write(text);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return false;
        }
    }

    @SupportedAnnotationTypes("*")
    private static final class FailingProcessor extends AbstractProcessor {

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            throw new IllegalStateException("Failing on purpose");
        }
    }
}
