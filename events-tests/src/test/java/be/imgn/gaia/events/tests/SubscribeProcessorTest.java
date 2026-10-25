package be.imgn.gaia.events.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;

import be.imgn.gaia.processing.testing.Compilation;
import be.imgn.gaia.processing.testing.ProcessorAssert;
import be.imgn.gaia.processing.testing.TestCompiler;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;
import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SubscribeProcessorTest {

    private static final String SERVICE_FILE = "META-INF/services/be.imgn.gaia.events.spi.SubscriberIndex";

    @Test
    void isRegistered() {
        ProcessorAssert.assertThat(processor().getClass()).isRegistered();
    }

    @Test
    void generatesAnIndexThatCompilesCleanly() {
        var compilation = compile(TestCompiler.source("example.Listener", """
                package example;

                import be.imgn.gaia.events.Subscribe;

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
                .generatedSourceFile("example.$$Listener$Subscribers")
                .isEqualTo("""
                package example;

                import be.imgn.gaia.events.spi.SubscriberIndex;
                import javax.annotation.processing.Generated;

                @Generated("be.imgn.gaia.events.processor.SubscribeProcessor")
                public final class $$Listener$Subscribers implements SubscriberIndex {
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
                .generatedSourceFile("example.$$Listener$Generic$Subscribers")
                .contains("var l = (Listener.Generic<?>) listener;");
        assertThat(compilation)
                .generatedSourceFile("example.$$Listener$Generic$Inner$Subscribers")
                .contains("var l = (Listener.Generic<?>.Inner) listener;");
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE)
                .isEqualTo("""
                example.$$Listener$Generic$Inner$Subscribers
                example.$$Listener$Generic$Subscribers
                example.$$Listener$Subscribers
                """);
    }

    @Test
    void doesNotWarnOfTheMissingProvidesDirectiveInAModule() {
        var compilation =
                compile(TestCompiler.source("module-info", """
                        module example {
                            requires be.imgn.gaia.events;
                        }
                        """), TestCompiler.source("example.Listener", """
                        package example;

                        import be.imgn.gaia.events.Subscribe;

                        class Listener {
                            @Subscribe
                            void onText(String text) {}
                        }
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("example.$$Listener$Subscribers")
                .isNotEmpty();
    }

    @Test
    void reportsTheMethodsTheGeneratedCodeCannotCall() {
        var compilation = compile(TestCompiler.source("example.Listener", """
                package example;

                import be.imgn.gaia.events.Subscribe;
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

                import be.imgn.gaia.events.Subscribe;

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

                import be.imgn.gaia.events.Subscribe;
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

                        import be.imgn.gaia.events.Subscribe;

                        class Listener {
                            @Subscribe
                            void onText(String text) {}
                        }
                        """),
                TestCompiler.source("example.$$Listener$Subscribers", """
                        package example;

                        class $$Listener$Subscribers {}
                        """));

        assertThat(compilation)
                .hadErrorContaining("Cannot generate example.$$Listener$Subscribers: a type has that name already")
                .inFile("example/Listener.java")
                .onLine(5);
    }

    @Test
    void seesNoMethodOfLocalAndAnonymousClasses() {
        var compilation = compile(TestCompiler.source("example.Listener", """
                package example;

                import be.imgn.gaia.events.Subscribe;

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
                .filter(provider ->
                        provider.type().getName().equals("be.imgn.gaia.events.processor.SubscribeProcessor"))
                .findFirst()
                .orElseThrow()
                .get();
    }
}
