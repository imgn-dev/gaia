package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.StepProcessor;
import be.imgn.gaia.processing.testing.Compilation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

/** Steps that get every root element of each round, annotated or not. */
class RootElementStepTest {

    private static final JavaFileObject ANNOTATIONS = source("example.Mark", """
            package example;

            @interface Mark {}

            @interface Generate {}
            """);

    private static final JavaFileObject SEED = source("example.Seed", """
            package example;

            @Generate
            class Seed {}
            """);

    private static final JavaFileObject PLAIN = source("example.Plain", """
            package example;

            class Plain {
                class Nested {}
            }
            """);

    private static String describe(Element element) {
        return switch (element) {
            case PackageElement p -> "package " + p.getQualifiedName();
            case ModuleElement m -> "module " + m.getQualifiedName();
            case TypeElement t -> t.getQualifiedName().toString();
            default -> element.toString();
        };
    }

    @Test
    void getsTheTopLevelTypesWithoutAnyAnnotation() {
        Roots roots = new Roots(elements -> Set.of());
        Compilation compilation =
                javac().withProcessors(new TestProcessor(roots)).compile(PLAIN);

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(roots.rounds).containsExactly(List.of("example.Plain"));
    }

    @Test
    void getsThePackagesAndModulesDeclared() {
        Roots roots = new Roots(elements -> Set.of());
        Compilation compilation = javac().withProcessors(new TestProcessor(roots))
                .compile(PLAIN, source("example.package-info", """
                                package example;
                                """), source("module-info", """
                                module example {}
                                """));

        assertThat(compilation).succeeded();
        assertThat(roots.rounds.getFirst())
                .containsExactlyInAnyOrder("example.Plain", "package example", "module example");
    }

    @Test
    void getsTheTypesGeneratedInTheRoundBefore() {
        Roots roots = new Roots(elements -> Set.of());
        Compilation compilation =
                javac().withProcessors(new TestProcessor(roots)).compile(ANNOTATIONS, SEED);

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(roots.rounds)
                .containsExactly(
                        List.of("example.Mark", "example.Generate", "example.Seed"), List.of("example.Generated"));
    }

    @Test
    void holdsARootElementBackUntilItsTypesAreGenerated() {
        Roots roots = new Roots(elements -> Set.of());
        Compilation compilation = javac().withProcessors(new TestProcessor(roots))
                .compile(ANNOTATIONS, SEED, source("example.Uses", """
                        package example;

                        class Uses {
                            void use(Generated generated) {}
                        }
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(roots.rounds)
                .containsExactly(
                        List.of("example.Mark", "example.Generate", "example.Seed"),
                        List.of("example.Generated", "example.Uses"));
    }

    @Test
    void getsARootElementGivenBackInTheNextRound() {
        List<Integer> calls = new ArrayList<>();
        Roots roots = new Roots(elements -> {
            calls.add(elements.size());
            return calls.size() == 1
                    ? Set.copyOf(elements.stream()
                            .filter(element -> describe(element).equals("example.Seed"))
                            .toList())
                    : Set.of();
        });
        Compilation compilation =
                javac().withProcessors(new TestProcessor(roots)).compile(ANNOTATIONS, SEED);

        assertThat(compilation).succeeded();
        assertThat(roots.rounds)
                .containsExactly(
                        List.of("example.Mark", "example.Generate", "example.Seed"),
                        List.of("example.Generated", "example.Seed"));
    }

    @Test
    void reportsANestedTypeGivenBack() {
        Roots roots = new Roots(elements -> Set.copyOf(elements.stream()
                .flatMap(element -> ElementFilter.typesIn(element.getEnclosedElements()).stream())
                .toList()));
        Compilation compilation =
                javac().withProcessors(new TestProcessor(roots)).compile(PLAIN);

        assertThat(compilation)
                .failed()
                .hadErrorCount(1)
                .hadErrorContaining("Could not give example.Plain.Nested back to Roots: a step only gets root elements"
                        + " and elements with its annotations")
                .inFile("example/Plain.java")
                .onLine(4);
        assertThat(roots.rounds).containsExactly(List.of("example.Plain"));
    }

    @Test
    void leavesTheTypeARootElementMissesToJavac() {
        Roots roots = new Roots(elements -> Set.of());
        Compilation compilation =
                javac().withProcessors(new TestProcessor(roots)).compile(PLAIN, source("example.Broken", """
                        package example;

                        class Broken {
                            Missing missing;
                        }
                        """));

        assertThat(compilation)
                .failed()
                .hadErrorCount(1)
                .hadErrorContaining("Missing")
                .inFile("example/Broken.java");
        assertThat(roots.rounds).containsExactly(List.of("example.Plain"));
    }

    @Test
    void reportsAnAnnotatedRootElementThatStaysUnresolved() {
        Compilation compilation = javac().withProcessors(new TestProcessor(new Step() {
                    @Override
                    public Set<String> annotations() {
                        return Set.of("example.Mark");
                    }

                    @Override
                    public boolean processesRootElements() {
                        return true;
                    }

                    @Override
                    public Set<? extends Element> process(AnnotatedElements elements) {
                        return Set.of();
                    }
                }))
                .compile(ANNOTATIONS, source("example.Broken", """
                        package example;

                        @Mark
                        class Broken {
                            Missing missing;
                        }
                        """));

        assertThat(compilation)
                .failed()
                .hadErrorCount(2)
                .hadErrorContaining("Could not process example.Broken: type Missing could not be resolved");
    }

    @Test
    void getsTheAnnotatedElementsAsWell() {
        Roots roots = new Roots(elements -> Set.of());
        List<String> annotated = new ArrayList<>();
        Compilation compilation = javac().withProcessors(new TestProcessor(roots, new Step() {
                    @Override
                    public Set<String> annotations() {
                        return Set.of("example.Mark");
                    }

                    @Override
                    public boolean processesRootElements() {
                        return true;
                    }

                    @Override
                    public Set<? extends Element> process(AnnotatedElements elements) {
                        elements.annotatedWith("example.Mark").forEach(element -> annotated.add(describe(element)));
                        return Set.of();
                    }
                }))
                .compile(ANNOTATIONS, PLAIN, source("example.Marked", """
                        package example;

                        class Marked {
                            @Mark void run() {}
                        }
                        """));

        assertThat(compilation).succeeded();
        assertThat(annotated).containsExactly("run()");
    }

    @Test
    void givesNoRootElementToTheOtherSteps() {
        List<AnnotatedElements> given = new ArrayList<>();
        Compilation compilation = javac().withProcessors(new TestProcessor(new Roots(elements -> Set.of()), new Step() {
                    @Override
                    public Set<String> annotations() {
                        return Set.of("example.Mark");
                    }

                    @Override
                    public Set<? extends Element> process(AnnotatedElements elements) {
                        given.add(elements);
                        return Set.of();
                    }
                }))
                .compile(ANNOTATIONS, PLAIN, source("example.Marked", """
                        package example;

                        @Mark
                        class Marked {}
                        """));

        assertThat(compilation).succeeded();
        assertThat(given).singleElement().satisfies(elements -> {
            assertThat(elements.rootElements()).isEmpty();
            assertThat(elements.all()).map(RootElementStepTest::describe).containsExactly("example.Marked");
        });
    }

    @Test
    void isNotEmptyWithRootElementsOnly() {
        List<AnnotatedElements> given = new ArrayList<>();
        Roots roots = new Roots(elements -> Set.of()) {
            @Override
            public Set<? extends Element> process(AnnotatedElements elements) {
                given.add(elements);
                return Set.of();
            }
        };

        javac().withProcessors(new TestProcessor(roots)).compile(PLAIN);

        assertThat(given).singleElement().satisfies(elements -> {
            assertThat(elements.isEmpty()).isFalse();
            assertThat(elements.all()).isEmpty();
            assertThat(elements.annotations()).isEmpty();
            assertThat(elements).hasToString("{}, root elements [example.Plain]");
        });
    }

    @Test
    void supportsAllAnnotations() {
        TestProcessor processor = new TestProcessor(new Roots(elements -> Set.of()));

        javac().withProcessors(processor).compile(PLAIN);

        assertThat(processor.getSupportedAnnotationTypes()).containsExactly("*");
    }

    @Test
    void listsTheRootElementsInTheDebugOutput() {
        Compilation compilation = javac().withProcessors(new TestProcessor(new Roots(elements -> Set.of())))
                .withOptions("-Agaia.debug")
                .compile(PLAIN);

        assertThat(compilation.diagnostics(Diagnostic.Kind.NOTE))
                .map(note -> note.getMessage(Locale.ROOT))
                .anySatisfy(note -> assertThat(note)
                        .startsWith("TestProcessor round 1, Roots: 1 element [Plain], 0 given back, 0 held back"));
    }

    /** A step recording, each round, the root elements it gets. */
    private static class Roots implements Step {

        private final List<List<String>> rounds = new ArrayList<>();
        private final Function<Set<Element>, Set<? extends Element>> deferral;

        Roots(Function<Set<Element>, Set<? extends Element>> deferral) {
            this.deferral = deferral;
        }

        @Override
        public Set<String> annotations() {
            return Set.of();
        }

        @Override
        public boolean processesRootElements() {
            return true;
        }

        @Override
        public Set<? extends Element> process(AnnotatedElements elements) {
            rounds.add(elements.rootElements().stream()
                    .map(RootElementStepTest::describe)
                    .toList());
            return deferral.apply(elements.rootElements());
        }
    }

    /** Runs some steps, and generates {@code example.Generated} for an element annotated with {@code @Generate}. */
    private static final class TestProcessor extends StepProcessor {

        private final List<Step> steps;

        TestProcessor(Step... steps) {
            this.steps = List.of(steps);
        }

        @Override
        protected List<Step> steps(ProcessingContext context) {
            List<Step> all = new ArrayList<>(steps);
            all.add(new Step() {
                @Override
                public Set<String> annotations() {
                    return Set.of("example.Generate");
                }

                @Override
                public Set<? extends Element> process(AnnotatedElements elements) {
                    try (Writer writer = context.filer()
                            .createSourceFile("example.Generated")
                            .openWriter()) {
                        writer.write("package example;\n\nclass Generated {}\n");
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return Set.of();
                }
            });
            return all;
        }
    }
}
