package be.imgn.gaia.processing.testing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import be.imgn.gaia.processing.testing.TestCompiler;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

class RunningCompilationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final JavaFileObject VALID = TestCompiler.source("example.Valid", """
            package example;

            class Valid {}
            """);

    @Test
    void holdsJavacInTheLastRoundUntilClosed() {
        RunningCompilation compilation = RunningCompilation.start(List.of(), VALID, TIMEOUT);

        assertThat(compilation.elements().getTypeElement("example.Valid")).isNotNull();
        assertThat(compilation.types()).isNotNull();

        compilation.close();
    }

    @Test
    void reportsTheExceptionOfAFailingProcessor() {
        assertThatIllegalStateException()
                .isThrownBy(() -> RunningCompilation.start(List.of(new Failing()), VALID, TIMEOUT))
                .withMessage("javac failed")
                .withRootCauseInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void reportsACompilationThatFailsAfterProcessing() {
        JavaFileObject typo = TestCompiler.source("example.Typo", """
                package example;

                class Typo {
                    int number = "text";
                }
                """);
        RunningCompilation compilation = RunningCompilation.start(List.of(), typo, TIMEOUT);

        assertThatIllegalStateException()
                .isThrownBy(compilation::close)
                .withMessageContaining("The compilation failed")
                .withMessageContaining("incompatible types");
    }

    @Test
    void reportsACompilationThatNeverProcesses() {
        JavaFileObject broken = TestCompiler.source("example.Broken", """
                package example;

                class Broken {
                """);

        assertThatIllegalStateException()
                .isThrownBy(() -> RunningCompilation.start(List.of(), broken, TIMEOUT))
                .withMessageContaining("javac finished without processing")
                .withMessageContaining("reached end of file");
    }

    @Test
    void givesUpWhenJavacTakesTooLong() {
        assertThatIllegalStateException()
                .isThrownBy(() -> RunningCompilation.start(List.of(new Slow()), VALID, Duration.ofMillis(100)))
                .withMessageContaining("did not reach annotation processing within");
    }

    @Test
    void cannotBeUsedOnceClosed() {
        RunningCompilation compilation = RunningCompilation.start(List.of(), VALID, TIMEOUT);

        compilation.close();
        compilation.close();

        assertThatIllegalStateException().isThrownBy(compilation::elements).withMessageContaining("closed");
        assertThatIllegalStateException().isThrownBy(compilation::types).withMessageContaining("closed");
    }

    /** Throws as soon as javac initializes it. */
    @SupportedAnnotationTypes("*")
    private static final class Failing extends AbstractProcessor {

        @Override
        public synchronized void init(ProcessingEnvironment processingEnv) {
            throw new UnsupportedOperationException("broken processor");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            return false;
        }
    }

    /** Takes longer to initialize than the test waits. */
    @SupportedAnnotationTypes("*")
    private static final class Slow extends AbstractProcessor {

        @Override
        public synchronized void init(ProcessingEnvironment processingEnv) {
            super.init(processingEnv);
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            return false;
        }
    }
}
