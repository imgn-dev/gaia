package be.imgn.gaia.core.tests.event;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.core.event.EventBus;
import be.imgn.gaia.processing.testing.TestCompiler;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests of {@link EventBus#register(Object)} with a listener on the class path of a class loader of its own, whose
 * index is listed in {@code META-INF/services}.
 */
class ClassLoaderTest {

    @Test
    void findsTheIndexOfANestedClassThroughItsClassLoader(@TempDir Path classes) throws Exception {
        var compilation = TestCompiler.javac()
                .withProcessors(SubscribeProcessorTest.processor())
                .compile(TestCompiler.source("example.Outer", """
                        package example;

                        import be.imgn.gaia.core.event.Subscribe;
                        import java.util.ArrayList;
                        import java.util.List;
                        import java.util.function.Supplier;

                        public class Outer {
                            public static class Listener implements Supplier<List<String>> {
                                private final List<String> received = new ArrayList<>();

                                @Subscribe
                                void onText(String text) {
                                    received.add(text);
                                }

                                @Override
                                public List<String> get() {
                                    return received;
                                }
                            }
                        }
                        """));
        assertThat(compilation)
                .succeeded()
                .generatedSourceFile("example.$Outer$Listener$$Subscribers")
                .isNotEmpty();
        SubscribeProcessorTest.write(compilation, classes);

        assertThat(receive(classes, "example.Outer$Listener")).isEqualTo(List.of("hello"));
    }

    @Test
    void findsTheIndexOfAClassWithADollarSignInItsName(@TempDir Path classes) throws Exception {
        var compilation = TestCompiler.javac()
                .withProcessors(SubscribeProcessorTest.processor())
                .compile(TestCompiler.source("example.Dollar$Listener", """
                        package example;

                        import be.imgn.gaia.core.event.Subscribe;
                        import java.util.ArrayList;
                        import java.util.List;
                        import java.util.function.Supplier;

                        public class Dollar$Listener implements Supplier<List<String>> {
                            private final List<String> received = new ArrayList<>();

                            @Subscribe
                            void onText(String text) {
                                received.add(text);
                            }

                            @Override
                            public List<String> get() {
                                return received;
                            }
                        }
                        """));
        assertThat(compilation)
                .succeeded()
                .generatedSourceFile("example.$Dollar$0Listener$$Subscribers")
                .isNotEmpty();
        SubscribeProcessorTest.write(compilation, classes);

        assertThat(receive(classes, "example.Dollar$Listener")).isEqualTo(List.of("hello"));
    }

    // Registers a new listener of a class loaded from the classes, publishes "hello", and returns what it received.
    private Object receive(Path classes, String binaryName) throws Exception {
        var bus = EventBus.create();
        try (var loader = new URLClassLoader(
                new URL[] {classes.toUri().toURL()}, getClass().getClassLoader())) {
            var listener =
                    (Supplier<?>) loader.loadClass(binaryName).getConstructor().newInstance();
            bus.register(listener);
            bus.publish("hello");
            return listener.get();
        }
    }
}
