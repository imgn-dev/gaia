package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.TestCompiler.javac;

import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.testing.Compilation;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.jspecify.annotations.Nullable;

/**
 * Runs a test body in the first round of a compilation, with a context over its processing environment, and throws
 * again, once javac is done, the assertion the body failed. Other exceptions reach the test through javac.
 */
@SupportedAnnotationTypes("*")
final class FirstRound extends AbstractProcessor {

    private final Consumer<ProcessingContext> body;
    private boolean ran;
    private @Nullable AssertionError failure;

    private FirstRound(Consumer<ProcessingContext> body) {
        this.body = body;
    }

    /**
     * Compiles sources, running a test body in the first round.
     *
     * @param sources the sources, at least one
     * @param options the javac options
     * @param body the test body
     * @return the compilation
     */
    static Compilation run(List<JavaFileObject> sources, List<String> options, Consumer<ProcessingContext> body) {
        FirstRound round = new FirstRound(body);
        Compilation compilation = javac().withProcessors(round)
                .withOptions(options.toArray(String[]::new))
                .compile(sources);
        if (!round.ran) {
            throw new AssertionError("The test body did not run: " + compilation.diagnostics());
        }
        if (round.failure != null) {
            throw round.failure;
        }
        return compilation;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (!ran) {
            ran = true;
            try {
                body.accept(ProcessingContext.of(processingEnv, FirstRound.class));
            } catch (AssertionError e) {
                failure = e;
            }
        }
        return false;
    }
}
