package be.imgn.gaia.processing.testing.internal;

import be.imgn.gaia.processing.testing.Compilation;
import be.imgn.gaia.processing.testing.TestCompiler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;
import org.jspecify.annotations.Nullable;

/**
 * A compilation held open in its last processing round, so that its {@link Elements} and {@link Types} can be used from
 * another thread. Closing it lets javac finish, and checks that it succeeded.
 */
public final class RunningCompilation implements AutoCloseable {

    private static final Duration TIMEOUT = Duration.ofMinutes(1);

    private static final JavaFileObject SOURCE = TestCompiler.source("gaia.CompilationExtension", """
            package gaia;

            final class CompilationExtension {}
            """);

    private static final Executor DAEMON = task -> {
        Thread thread = new Thread(task, "gaia-compilation-extension");
        thread.setDaemon(true);
        thread.start();
    };

    private final CountDownLatch processing = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);
    private final Duration timeout;
    private final CompletableFuture<Compilation> outcome;
    private volatile @Nullable ProcessingEnvironment environment;
    private volatile boolean closed;

    private RunningCompilation(List<Processor> processors, JavaFileObject source, Duration timeout) {
        this.timeout = timeout;
        List<Processor> all = new ArrayList<>(processors);
        all.add(new Holder());
        TestCompiler compiler = TestCompiler.javac().withProcessors(all.toArray(Processor[]::new));
        this.outcome = CompletableFuture.supplyAsync(() -> compile(compiler, source), DAEMON);
    }

    /**
     * Starts a compilation and waits until javac reaches its last processing round.
     *
     * @return the running compilation
     * @throws IllegalStateException if javac fails, finishes without processing, or does not reach the last round
     *     within a minute
     */
    public static RunningCompilation start() {
        return start(List.of(), SOURCE, TIMEOUT);
    }

    static RunningCompilation start(List<Processor> processors, JavaFileObject source, Duration timeout) {
        RunningCompilation compilation = new RunningCompilation(processors, source, timeout);
        compilation.awaitProcessing();
        return compilation;
    }

    private static String describe(Compilation compilation) {
        return compilation.diagnostics().stream()
                .map(diagnostic -> "  " + diagnostic.getKind() + ": " + diagnostic.getMessage(Locale.ROOT))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    /**
     * Returns the elements of the compilation, valid until it is closed.
     *
     * @return the elements
     * @throws IllegalStateException if the compilation is closed
     */
    public Elements elements() {
        return running().getElementUtils();
    }

    /**
     * Returns the types of the compilation, valid until it is closed.
     *
     * @return the types
     * @throws IllegalStateException if the compilation is closed
     */
    public Types types() {
        return running().getTypeUtils();
    }

    /**
     * Lets javac finish, waits for it, and checks that the compilation succeeded. Closing again does nothing.
     *
     * @throws IllegalStateException if javac fails, reports an error, or does not finish within a minute
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        environment = null;
        released.countDown();
        Compilation compilation = awaitOutcome();
        if (!compilation.success()) {
            throw new IllegalStateException("The compilation failed:" + System.lineSeparator() + describe(compilation));
        }
    }

    private Compilation compile(TestCompiler compiler, JavaFileObject source) {
        try {
            return compiler.compile(source);
        } finally {
            processing.countDown();
        }
    }

    private void awaitProcessing() {
        boolean counted;
        try {
            counted = processing.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for javac", e);
        }
        if (!counted) {
            closed = true;
            released.countDown();
            throw new IllegalStateException("javac did not reach annotation processing within " + timeout);
        }
        if (environment == null) {
            closed = true;
            throw new IllegalStateException(
                    "javac finished without processing:" + System.lineSeparator() + describe(awaitOutcome()));
        }
    }

    private Compilation awaitOutcome() {
        try {
            return outcome.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw new IllegalStateException("javac failed", e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("javac did not finish within " + timeout, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for javac", e);
        }
    }

    private ProcessingEnvironment running() {
        ProcessingEnvironment running = environment;
        if (running == null) {
            throw new IllegalStateException(
                    "The compilation is closed: its Elements and Types are only valid while the test runs");
        }
        return running;
    }

    /** Publishes the processing environment in the last round, and holds javac there until released. */
    @SupportedAnnotationTypes("*")
    private final class Holder extends AbstractProcessor {

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (roundEnv.processingOver()) {
                environment = processingEnv;
                processing.countDown();
                try {
                    released.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return false;
        }
    }
}
