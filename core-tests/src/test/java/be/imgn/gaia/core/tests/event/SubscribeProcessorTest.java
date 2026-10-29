package be.imgn.gaia.core.tests.event;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;

import be.imgn.gaia.processing.testing.Compilation;
import be.imgn.gaia.processing.testing.ProcessorAssert;
import be.imgn.gaia.processing.testing.TestCompiler;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SubscribeProcessorTest {

    private static final String SERVICE_FILE = "META-INF/services/be.imgn.gaia.core.event.spi.SubscriberIndex";

    private static final JavaFileObject ON_QUEUE = TestCompiler.source("example.queue.OnQueue", """
            package example.queue;

            import be.imgn.gaia.core.event.SubscribingAnnotation;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;

            @SubscribingAnnotation(executor = QueueExecutor.class)
            @Target(ElementType.METHOD)
            public @interface OnQueue {}
            """);

    private static final JavaFileObject QUEUE_EXECUTOR = TestCompiler.source("example.queue.QueueExecutor", """
            package example.queue;

            import java.util.concurrent.Executor;

            public final class QueueExecutor implements Executor {
                @Override
                public void execute(Runnable task) {}
            }
            """);

    @Test
    void isRegistered() {
        ProcessorAssert.assertThat(processor().getClass()).isRegistered();
    }

    @Test
    void generatesAnIndexThatCompilesCleanly() {
        var compilation = compile(TestCompiler.source("example.Listener", """
                package example;

                import be.imgn.gaia.core.event.Subscribe;

                class Listener {
                    @Subscribe
                    void onText(String text) {}

                    @Subscribe
                    protected void onNumber(Number number) throws IllegalStateException {}

                    static class Generic<T> {
                        @Subscribe
                        public void onText(String text) {}

                        class Inner {
                            @Subscribe
                            void onText(String text) {}
                        }
                    }
                }
                """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("example.$Listener$$Subscribers")
                .isEqualTo("""
                package example;

                import be.imgn.gaia.core.event.spi.SubscriberIndex;
                import javax.annotation.processing.Generated;

                @Generated("be.imgn.gaia.processor.SubscribeProcessor")
                public final class $Listener$$Subscribers implements SubscriberIndex {
                    public $Listener$$Subscribers() {
                    }

                    @Override
                    public Class<?> listenerType() {
                        return Listener.class;
                    }

                    @Override
                    public void subscribe(Object listener, SubscriberIndex.Registrar registrar) {
                        var l = (Listener) listener;
                        registrar.subscribe(String.class, l::onText);
                        registrar.subscribe(Number.class, l::onNumber);
                    }
                }
                """);
        assertThat(compilation)
                .generatedSourceFile("example.$Listener$Generic$$Subscribers")
                .contains("var l = (Listener.Generic<?>) listener;");
        assertThat(compilation)
                .generatedSourceFile("example.$Listener$Generic$Inner$$Subscribers")
                .contains("var l = (Listener.Generic<?>.Inner) listener;");
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE)
                .isEqualTo("""
                example.$Listener$$Subscribers
                example.$Listener$Generic$$Subscribers
                example.$Listener$Generic$Inner$$Subscribers
                """);
    }

    @Test
    void doesNotWarnOfTheMissingProvidesDirectiveInAModule() {
        var compilation =
                compile(TestCompiler.source("module-info", """
                        module example {
                            requires be.imgn.gaia.core;
                        }
                        """), TestCompiler.source("example.Listener", """
                        package example;

                        import be.imgn.gaia.core.event.Subscribe;

                        class Listener {
                            @Subscribe
                            void onText(String text) {}
                        }
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("example.$Listener$$Subscribers")
                .isNotEmpty();
    }

    @Test
    void reportsTheMethodsTheGeneratedCodeCannotCall() {
        var compilation = compile(TestCompiler.source("example.Listener", """
                package example;

                import be.imgn.gaia.core.event.Subscribe;
                import java.io.IOException;
                import java.util.List;

                class Listener {
                    @Subscribe
                    private void onPrivate(String text) {}

                    @Subscribe
                    static void onStatic(String text) {}

                    @Subscribe
                    void onNothing() {}

                    @Subscribe
                    void onTwo(String text, String other) {}

                    @Subscribe
                    void onPrimitive(int number) {}

                    @Subscribe
                    void onArray(String[] texts) {}

                    @Subscribe
                    <T> void onTypeVariable(T event) {}

                    @Subscribe
                    void onParameterized(List<String> texts) {}

                    @Subscribe
                    void onHidden(Hidden hidden) {}

                    @Subscribe
                    String onReturning(String text) {
                        return text;
                    }

                    @Subscribe
                    void onChecked(String text) throws IOException {}

                    private static class Hidden {
                        @Subscribe
                        void onText(String text) {}
                    }

                    interface Contract {
                        @Subscribe
                        void onText(String text);
                    }
                }
                """));

        assertThat(compilation).failed().hadErrorCount(13).generatedNoSourceFiles();
        assertError(compilation, "@Subscribe methods must not be private", 9);
        assertError(compilation, "@Subscribe methods must not be static", 12);
        assertError(compilation, "@Subscribe methods must take exactly one parameter, the event", 15);
        assertError(compilation, "@Subscribe methods must take exactly one parameter, the event", 18);
        assertError(compilation, "The event type must not be primitive: int", 21);
        assertError(compilation, "The event type must not be an array: java.lang.String[]", 24);
        assertError(compilation, "The event type must not be a type variable: T", 27);
        assertError(compilation, "The event type must not be parameterized: java.util.List<java.lang.String>", 30);
        assertError(compilation, "The event type example.Listener.Hidden must be accessible from package example", 33);
        assertError(compilation, "@Subscribe methods must return void", 36);
        assertError(compilation, "@Subscribe methods must not declare checked exceptions: java.io.IOException", 41);
        assertError(
                compilation,
                "@Subscribe methods must be accessible from their package, but example.Listener.Hidden is private",
                45);
        assertError(compilation, "@Subscribe methods must be in a class, not an interface", 50);
    }

    @Test
    void reportsAMethodThatOverridesASubscribeMethod(@TempDir Path classes) throws IOException {
        var base = TestCompiler.source("example.Base", """
                package example;

                import be.imgn.gaia.core.event.Subscribe;

                public class Base {
                    @Subscribe
                    protected void onText(String text) {}
                }
                """);
        var compiled = compile(base);
        assertThat(compiled).succeeded();
        write(compiled, classes);
        var listener = TestCompiler.source("other.Listener", """
                package other;

                import be.imgn.gaia.core.event.Subscribe;
                import example.Base;

                class Listener extends Base {
                    @Subscribe
                    @Override
                    protected void onText(String text) {}
                }
                """);

        var together = compile(base, listener);
        // From a class file, where the annotation is retained.
        var separately = TestCompiler.javac()
                .withProcessors(processor())
                .withOptions("-classpath", classes.toString())
                .compile(listener);

        for (var compilation : new Compilation[] {together, separately}) {
            assertThat(compilation).failed().hadErrorCount(1);
            assertThat(compilation)
                    .hadErrorContaining(
                            "This method overrides the @Subscribe method of example.Base, which delivers the"
                                    + " events to it already: remove @Subscribe")
                    .inFile("other/Listener.java")
                    .onLine(9);
        }
    }

    @Test
    void reportsATypeThatHasTheNameOfTheIndex() {
        var compilation = compile(
                TestCompiler.source("example.Listener", """
                        package example;

                        import be.imgn.gaia.core.event.Subscribe;

                        class Listener {
                            @Subscribe
                            void onText(String text) {}
                        }
                        """),
                TestCompiler.source("example.$Listener$$Subscribers", """
                        package example;

                        class $Listener$$Subscribers {}
                        """));

        assertThat(compilation)
                .hadErrorContaining("Cannot generate example.$Listener$$Subscribers: a type has that name already")
                .inFile("example/Listener.java")
                .onLine(5);
    }

    @Test
    void seesNoMethodOfLocalAndAnonymousClasses() {
        var compilation = compile(TestCompiler.source("example.Listener", """
                package example;

                import be.imgn.gaia.core.event.Subscribe;

                class Listener {
                    Object anonymous = new Object() {
                        @Subscribe
                        void onText(String text) {}
                    };

                    void method() {
                        class Local {
                            @Subscribe
                            private static void onText(String text) {}
                        }
                    }
                }
                """));

        assertThat(compilation).succeeded().hadNoWarnings().generatedNoSourceFiles();
    }

    @Test
    void waitsForTheTypesAnotherProcessorGenerates() {
        var compilation = TestCompiler.javac()
                .withProcessors(new Generating(), processor())
                .withOptions("-Xlint:all,-processing", "-Werror")
                .compile(ON_QUEUE, QUEUE_EXECUTOR, TestCompiler.source("example.Listener", """
                        package example;

                        import be.imgn.gaia.core.event.Subscribe;
                        import example.queue.OnQueue;

                        class Listener {
                            @Subscribe
                            void onText(String text) {}

                            @Subscribe
                            void onGen(Gen gen) {}

                            static class Queued {
                                @Subscribe
                                void onText(String text) {}

                                @OnQueue
                                void onGen(Gen gen) {}
                            }

                            static class Throwing {
                                @Subscribe
                                void onText(String text) {}

                                @Subscribe
                                void onNumber(Number number) throws GenException {}
                            }

                            static class Annotated {
                                @Subscribe
                                void onText(String text) {}

                                @Subscribe
                                @GenMarker
                                void onNumber(Number number) {}
                            }
                        }
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("example.$Listener$$Subscribers")
                .contains("""
                                registrar.subscribe(String.class, l::onText);
                                registrar.subscribe(Gen.class, l::onGen);
                        """);
        assertThat(compilation)
                .generatedSourceFile("example.$Listener$Queued$$Subscribers")
                .contains("""
                                registrar.subscribe(String.class, l::onText);
                                registrar.subscribe(Gen.class, OnQueue.class, QueueExecutor::new, l::onGen);
                        """);
        assertThat(compilation)
                .generatedSourceFile("example.$Listener$Throwing$$Subscribers")
                .contains("""
                                registrar.subscribe(String.class, l::onText);
                                registrar.subscribe(Number.class, l::onNumber);
                        """);
        assertThat(compilation)
                .generatedSourceFile("example.$Listener$Annotated$$Subscribers")
                .contains("""
                                registrar.subscribe(String.class, l::onText);
                                registrar.subscribe(Number.class, l::onNumber);
                        """);
    }

    @Test
    void generatesTheCallsOfMethodsWithASubscribingAnnotation() {
        var compilation = compile(ON_QUEUE, QUEUE_EXECUTOR, TestCompiler.source("example.Listener", """
                package example;

                import be.imgn.gaia.core.event.Subscribe;
                import example.queue.OnQueue;

                class Listener {
                    @Subscribe
                    void onText(String text) {}

                    @OnQueue
                    void onNumber(Number number) {}
                }
                """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("example.$Listener$$Subscribers")
                .isEqualTo("""
                package example;

                import be.imgn.gaia.core.event.spi.SubscriberIndex;
                import example.queue.OnQueue;
                import example.queue.QueueExecutor;
                import javax.annotation.processing.Generated;

                @Generated("be.imgn.gaia.processor.SubscribeProcessor")
                public final class $Listener$$Subscribers implements SubscriberIndex {
                    public $Listener$$Subscribers() {
                    }

                    @Override
                    public Class<?> listenerType() {
                        return Listener.class;
                    }

                    @Override
                    public void subscribe(Object listener, SubscriberIndex.Registrar registrar) {
                        var l = (Listener) listener;
                        registrar.subscribe(String.class, l::onText);
                        registrar.subscribe(Number.class, OnQueue.class, QueueExecutor::new, l::onNumber);
                    }
                }
                """);
    }

    @Test
    void reportsTheMethodsOfASubscribingAnnotationTheGeneratedCodeCannotCall() {
        var compilation = compile(ON_QUEUE, QUEUE_EXECUTOR, TestCompiler.source("example.Listener", """
                package example;

                import be.imgn.gaia.core.event.Subscribe;
                import be.imgn.gaia.core.event.SubscribingAnnotation;
                import example.queue.OnQueue;
                import example.queue.QueueExecutor;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Target;

                @Listener.OnAnything
                class Listener {
                    @OnQueue
                    static void onStatic(String text) {}

                    @Subscribe
                    @OnQueue
                    void onTwice(String text) {}

                    @Local
                    void onLocal(String text) {}

                    @SubscribingAnnotation(executor = QueueExecutor.class)
                    @Target({ElementType.METHOD, ElementType.TYPE})
                    @interface OnAnything {}

                    @SubscribingAnnotation(executor = QueueExecutor.class)
                    private @interface Local {}
                }
                """));

        assertThat(compilation).failed().hadErrorCount(4).generatedNoSourceFiles();
        assertError(compilation, "@OnAnything can only annotate methods", 11);
        assertError(compilation, "@OnQueue methods must not be static", 13);
        assertError(
                compilation,
                "A method can have one subscribing annotation only, but this one has @Subscribe and @OnQueue",
                17);
        assertError(
                compilation,
                "@Local must be accessible from package example, where the generated code refers to it",
                20);
    }

    @Test
    void reportsTheExecutorsTheGeneratedCodeCannotCreate() {
        var executors = TestCompiler.source("example.Executors", """
                package example;

                import java.util.concurrent.Executor;

                public class Executors {
                    public abstract static class Abstract implements Executor {}

                    public class Inner implements Executor {
                        public void execute(Runnable task) {}
                    }

                    public static class WithParameter implements Executor {
                        public WithParameter(String name) {}

                        public void execute(Runnable task) {}
                    }

                    static class Hidden implements Executor {
                        public void execute(Runnable task) {}
                    }

                    public static class HiddenConstructor implements Executor {
                        private HiddenConstructor() {}

                        public void execute(Runnable task) {}
                    }
                }
                """);
        var annotations = TestCompiler.source("example.On", """
                package example;

                import be.imgn.gaia.core.event.SubscribingAnnotation;
                import java.util.concurrent.Executor;

                public class On {
                    @SubscribingAnnotation(executor = Executor.class)
                    public @interface Interface {}

                    @SubscribingAnnotation(executor = Executors.Abstract.class)
                    public @interface Abstract {}

                    @SubscribingAnnotation(executor = Executors.Inner.class)
                    public @interface Inner {}

                    @SubscribingAnnotation(executor = Executors.WithParameter.class)
                    public @interface WithParameter {}

                    @SubscribingAnnotation(executor = Executors.Hidden.class)
                    public @interface Hidden {}

                    @SubscribingAnnotation(executor = Executors.HiddenConstructor.class)
                    public @interface HiddenConstructor {}

                    @SubscribingAnnotation(executor = Executors.Hidden.class)
                    @interface InPackage {}
                }
                """);
        var listener = TestCompiler.source("example.Listener", """
                package example;

                class Listener {
                    @On.Interface
                    void onInterface(String text) {}

                    @On.Abstract
                    void onAbstract(String text) {}

                    @On.Inner
                    void onInner(String text) {}

                    @On.WithParameter
                    void onWithParameter(String text) {}

                    @On.Hidden
                    void onHidden(String text) {}

                    @On.HiddenConstructor
                    void onHiddenConstructor(String text) {}

                    @On.InPackage
                    void onInPackage(String text) {}
                }
                """);

        var compilation = compile(executors, annotations, listener);

        assertThat(compilation).failed().hadErrorCount(11).generatedNoSourceFiles();
        var publicConstructor = "must have a constructor without parameters that is public";
        var accessibleConstructor =
                "must have a constructor without parameters that is accessible from package example";
        // On the declarations of the annotations, from where they can be used: InPackage from its package only.
        assertExecutorError(compilation, "On", 7, "Interface", "java.util.concurrent.Executor", "must be a class");
        assertExecutorError(compilation, "On", 10, "Abstract", "example.Executors.Abstract", "must not be abstract");
        assertExecutorError(compilation, "On", 13, "Inner", "example.Executors.Inner", "must not be an inner class");
        assertExecutorError(
                compilation, "On", 16, "WithParameter", "example.Executors.WithParameter", publicConstructor);
        assertExecutorError(compilation, "On", 19, "Hidden", "example.Executors.Hidden", "must be public");
        assertExecutorError(
                compilation, "On", 22, "HiddenConstructor", "example.Executors.HiddenConstructor", publicConstructor);
        // On the methods, from the package of the listener.
        assertExecutorError(
                compilation, "Listener", 5, "Interface", "java.util.concurrent.Executor", "must be a class");
        assertExecutorError(
                compilation, "Listener", 8, "Abstract", "example.Executors.Abstract", "must not be abstract");
        assertExecutorError(
                compilation, "Listener", 11, "Inner", "example.Executors.Inner", "must not be an inner class");
        assertExecutorError(
                compilation, "Listener", 14, "WithParameter", "example.Executors.WithParameter", accessibleConstructor);
        assertExecutorError(
                compilation,
                "Listener",
                20,
                "HiddenConstructor",
                "example.Executors.HiddenConstructor",
                accessibleConstructor);
    }

    @Test
    void reportsAnExecutorThatTheModuleOfTheListenerCannotAccess(@TempDir Path modules) throws IOException {
        // Without -Werror: javac's lint warns that the exported annotation names an executor that is not exported.
        var queue = TestCompiler.javac()
                .withProcessors(processor())
                .compile(
                        TestCompiler.source("module-info", """
                        module example.queue {
                            requires be.imgn.gaia.core;

                            exports example.queue;
                        }
                        """),
                        TestCompiler.source("example.queue.OnQueue", """
                        package example.queue;

                        import be.imgn.gaia.core.event.SubscribingAnnotation;
                        import example.queue.internal.QueueExecutor;

                        @SubscribingAnnotation(executor = QueueExecutor.class)
                        public @interface OnQueue {}
                        """),
                        TestCompiler.source("example.queue.internal.QueueExecutor", """
                        package example.queue.internal;

                        import java.util.concurrent.Executor;

                        public final class QueueExecutor implements Executor {
                            @Override
                            public void execute(Runnable task) {}
                        }
                        """));
        assertThat(queue).succeeded();
        write(queue, modules.resolve("example.queue"));

        var compilation = TestCompiler.javac()
                .withProcessors(processor())
                .withOptions(
                        "--module-path",
                        modules.resolve("example.queue") + File.pathSeparator + System.getProperty("jdk.module.path"))
                .compile(TestCompiler.source("module-info", """
                                module example {
                                    requires be.imgn.gaia.core;
                                    requires example.queue;
                                }
                                """), TestCompiler.source("example.Listener", """
                                package example;

                                import example.queue.OnQueue;

                                class Listener {
                                    @OnQueue
                                    void onText(String text) {}
                                }
                                """));

        assertThat(compilation).failed().hadErrorCount(1);
        assertError(
                compilation,
                "The executor of @OnQueue, example.queue.internal.QueueExecutor, must be exported to module example",
                7);
    }

    @Test
    void reportsAnExecutorThatCannotBeFound(@TempDir Path classes) throws IOException {
        var queue = compile(ON_QUEUE, QUEUE_EXECUTOR);
        assertThat(queue).succeeded();
        write(queue, classes);
        Files.delete(classes.resolve("example/queue/QueueExecutor.class"));

        var compilation = TestCompiler.javac()
                .withProcessors(processor())
                .withOptions("-classpath", classes.toString())
                .compile(TestCompiler.source("example.Listener", """
                        package example;

                        import example.queue.OnQueue;

                        class Listener {
                            @OnQueue
                            void onText(String text) {}
                        }
                        """));

        assertThat(compilation).failed();
        assertError(compilation, "The executor of @OnQueue, example.queue.QueueExecutor, cannot be found", 7);
    }

    @Test
    void reportsASubscribingAnnotationThatIsNotKeptInClassFiles() {
        var compilation = compile(QUEUE_EXECUTOR, TestCompiler.source("example.OnQueue", """
                package example;

                import be.imgn.gaia.core.event.SubscribingAnnotation;
                import example.queue.QueueExecutor;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @SubscribingAnnotation(executor = QueueExecutor.class)
                @Retention(RetentionPolicy.SOURCE)
                @interface OnQueue {}
                """));

        assertThat(compilation).failed().hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("A subscribing annotation must be kept in class files, with a retention of CLASS or"
                        + " RUNTIME, so that the processor sees it on the methods of classes compiled before")
                .inFile("example/OnQueue.java")
                .onLine(9);
    }

    @Test
    void reportsAMethodThatOverridesAMethodWithASubscribingAnnotation(@TempDir Path classes) throws IOException {
        var base = compile(ON_QUEUE, QUEUE_EXECUTOR, TestCompiler.source("example.Base", """
                package example;

                import example.queue.OnQueue;

                public class Base {
                    @OnQueue
                    protected void onText(String text) {}
                }
                """));
        assertThat(base).succeeded();
        write(base, classes);

        var compilation = TestCompiler.javac()
                .withProcessors(processor())
                .withOptions("-classpath", classes.toString())
                .compile(TestCompiler.source("example.Listener", """
                        package example;

                        import be.imgn.gaia.core.event.Subscribe;

                        class Listener extends Base {
                            @Subscribe
                            @Override
                            protected void onText(String text) {}
                        }
                        """));

        assertThat(compilation).failed().hadErrorCount(1);
        assertError(
                compilation,
                "This method overrides the @OnQueue method of example.Base, which delivers the events to it already:"
                        + " remove @Subscribe",
                8);
    }

    private static Compilation compile(JavaFileObject... sources) {
        return TestCompiler.javac()
                .withProcessors(processor())
                .withOptions("-Xlint:all,-processing", "-Werror")
                .compile(sources);
    }

    private static void assertError(Compilation compilation, String message, long line) {
        assertThat(compilation)
                .hadErrorContaining(message)
                .inFile("example/Listener.java")
                .onLine(line);
    }

    private static void assertExecutorError(
            Compilation compilation, String file, long line, String annotation, String executor, String problem) {
        assertThat(compilation)
                .hadErrorContaining("The executor of @" + annotation + ", " + executor + ", " + problem)
                .inFile("example/" + file + ".java")
                .onLine(line);
    }

    /** Writes the class files of a compilation under a directory. */
    static void write(Compilation compilation, Path directory) throws IOException {
        var prefix = "/" + StandardLocation.CLASS_OUTPUT.getName() + "/";
        for (var file : compilation.generatedFiles()) {
            if (file.getName().startsWith(prefix)) {
                var target = directory.resolve(file.getName().substring(prefix.length()));
                Files.createDirectories(target.getParent());
                try (InputStream in = file.openInputStream()) {
                    Files.copy(in, target);
                }
            }
        }
    }

    /** Returns the processor, found as javac finds it. */
    static Processor processor() {
        return ServiceLoader.load(Processor.class).stream()
                .filter(provider -> provider.type().getName().equals("be.imgn.gaia.processor.SubscribeProcessor"))
                .findFirst()
                .orElseThrow()
                .get();
    }

    /** Generates, in the first round, types that listeners refer to, as another processor would. */
    private static final class Generating extends AbstractProcessor {

        private boolean generated;

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (!generated) {
                generated = true;
                write("example.Gen", "package example; public final class Gen {}");
                write("example.GenException", """
                        package example;

                        public final class GenException extends RuntimeException {
                            private static final long serialVersionUID = 1L;
                        }
                        """);
                write("example.GenMarker", "package example; public @interface GenMarker {}");
            }
            return false;
        }

        private void write(String name, String source) {
            try (var writer = processingEnv.getFiler().createSourceFile(name).openWriter()) {
                writer.write(source);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
