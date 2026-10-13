package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.gaia.processing.testing.Compilation;
import java.net.URI;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;

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
    void failsEveryAssertionOnANullCompilation() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat((Compilation) null).succeeded());
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat((Compilation) null).hadErrorContaining("error"));
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
