package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.StepProcessor;
import be.imgn.gaia.processing.testing.Compilation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

class StepProcessorTest {

    private static final JavaFileObject ANNOTATIONS = source("example.Mark", """
            package example;

            @interface Mark {}

            @interface Other {}

            @interface Generate {}
            """);

    private static final JavaFileObject SEED = source("example.Seed", """
            package example;

            @Generate
            class Seed {}
            """);

    @Test
    void groupsTheElementsByAnnotation() {
        Recorder recorder = new Recorder(elements -> Set.of());
        Compilation compilation =
                javac().withProcessors(new TestProcessor(recorder)).compile(ANNOTATIONS, source("example.Marked", """
                        package example;

                        @Mark
                        class Marked {
                            @Mark @Other void both() {}

                            @Other int other;
                        }
                        """));

        assertThat(compilation).succeeded();
        assertThat(recorder.rounds)
                .containsExactly(Map.of(
                        "example.Mark", List.of("example.Marked", "both()"),
                        "example.Other", List.of("both()", "other")));
    }

    @Test
    void defersAnElementUntilItsTypesAreGenerated() {
        List<TypeKind> fieldTypes = new ArrayList<>();
        Recorder recorder = new Recorder(elements -> {
            elements.forEach(element -> ElementFilter.fieldsIn(element.getEnclosedElements())
                    .forEach(field -> fieldTypes.add(field.asType().getKind())));
            return Set.of();
        });
        Compilation compilation = javac().withProcessors(new TestProcessor(recorder))
                .compile(ANNOTATIONS, SEED, source("example.Uses", """
                        package example;

                        @Mark
                        class Uses {
                            Generated generated;
                        }
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(recorder.rounds).containsExactly(Map.of("example.Mark", List.of("example.Uses")));
        assertThat(fieldTypes).containsExactly(TypeKind.DECLARED);
    }

    @Test
    void reportsAnElementWhoseTypesNeverResolve() {
        Recorder recorder = new Recorder(elements -> Set.of());
        Compilation compilation =
                javac().withProcessors(new TestProcessor(recorder)).compile(ANNOTATIONS, source("example.Broken", """
                        package example;

                        @Mark
                        class Broken {
                            Missing missing;
                        }
                        """));

        assertThat(compilation)
                .failed()
                .hadErrorContaining("Could not process example.Broken: type Missing could not be resolved")
                .inFile("example/Broken.java")
                .onLine(4);
        assertThat(recorder.rounds).isEmpty();
    }

    @Test
    void namesTheUnresolvedTypeOfADeferredElementWithoutItsTypeAnnotations() {
        Recorder recorder = new Recorder(elements -> Set.of());
        Compilation compilation =
                javac().withProcessors(new TestProcessor(recorder)).compile(ANNOTATIONS, source("example.Broken", """
                        package example;

                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Target;

                        @Target(ElementType.TYPE_USE)
                        @interface N {}

                        @Mark
                        class Annotated {
                            @N Missing missing;
                        }

                        @Mark
                        class Qualified {
                            com.example.@N Missing missing;
                        }

                        @Mark
                        class Argument {
                            java.util.List<@N Missing> missing;
                        }
                        """));

        assertThat(compilation).failed();
        assertThat(compilation.diagnostics(Diagnostic.Kind.ERROR).stream()
                        .map(error -> error.getMessage(Locale.ROOT))
                        .filter(message -> message.startsWith("Could not process")))
                .containsExactlyInAnyOrder(
                        "Could not process example.Annotated: type Missing could not be resolved",
                        "Could not process example.Qualified: type com.example.Missing could not be resolved",
                        "Could not process example.Argument: type Missing could not be resolved");
        assertThat(recorder.rounds).isEmpty();
    }

    @Test
    void givesADeferredElementAgainInTheNextRound() {
        List<Integer> calls = new ArrayList<>();
        Recorder recorder = new Recorder(elements -> {
            calls.add(elements.size());
            return calls.size() == 1 ? elements : Set.of();
        });
        Compilation compilation = javac().withProcessors(new TestProcessor(recorder))
                .compile(ANNOTATIONS, SEED, source("example.Marked", """
                        package example;

                        @Mark
                        class Marked {}
                        """));

        assertThat(compilation).succeeded();
        assertThat(recorder.rounds)
                .containsExactly(
                        Map.of("example.Mark", List.of("example.Marked")),
                        Map.of("example.Mark", List.of("example.Marked")));
    }

    @Test
    void reportsAnElementDeferredPastTheLastRound() {
        Recorder recorder = new Recorder(elements -> elements);
        Compilation compilation =
                javac().withProcessors(new TestProcessor(recorder)).compile(ANNOTATIONS, source("example.Marked", """
                        package example;

                        @Mark
                        class Marked {}
                        """));

        assertThat(compilation)
                .failed()
                .hadErrorContaining("Could not process example.Marked: it was still deferred after the last round");
    }

    @Test
    void reportsNoDeferredElementOnTopOfAnError() {
        Recorder recorder = new Recorder(elements -> elements);
        AbstractProcessor failing = new AbstractProcessor() {
            @Override
            public Set<String> getSupportedAnnotationTypes() {
                return Set.of("*");
            }

            @Override
            public SourceVersion getSupportedSourceVersion() {
                return SourceVersion.latestSupported();
            }

            @Override
            public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
                if (!round.processingOver()) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "the real error");
                }
                return false;
            }
        };
        Compilation compilation = javac().withProcessors(new TestProcessor(recorder), failing)
                .compile(ANNOTATIONS, source("example.Marked", """
                        package example;

                        @Mark
                        class Marked {}
                        """));

        assertThat(compilation).failed().hadErrorCount(1).hadErrorContaining("the real error");
    }

    @Test
    void reportsAnElementGivenBackThatTheStepWouldNotGetAgain() {
        Recorder recorder = new Recorder(
                elements -> Set.of(requireNonNull(elements.iterator().next().getEnclosingElement())));
        Compilation compilation =
                javac().withProcessors(new TestProcessor(recorder)).compile(ANNOTATIONS, source("example.Marked", """
                        package example;

                        class Marked {
                            @Mark void run() {}
                        }
                        """));

        assertThat(compilation)
                .failed()
                .hadErrorCount(1)
                .hadErrorContaining("Could not give example.Marked back to Recorder: a step only gets elements"
                        + " with its annotations")
                .inFile("example/Marked.java")
                .onLine(3);
        assertThat(recorder.rounds).containsExactly(Map.of("example.Mark", List.of("run()")));
    }

    @Test
    void supportsTheAnnotationsOfItsStepsAndTheLatestSourceVersion() {
        TestProcessor processor = new TestProcessor(new Recorder(elements -> Set.of()));

        javac().withProcessors(processor).compile(ANNOTATIONS);

        assertThat(processor.getSupportedAnnotationTypes())
                .containsExactlyInAnyOrder("example.Mark", "example.Other", "example.Generate");
        assertThat(processor.getSupportedSourceVersion()).isEqualTo(SourceVersion.latestSupported());
    }

    @Test
    void cannotBeInitializedTwice() {
        Counting processor = new Counting();
        javac().withProcessors(processor).compile(ANNOTATIONS);

        assertThatIllegalStateException()
                .isThrownBy(() -> processor.init(processor.environment()))
                .withMessage("Cannot call init more than once.");
        assertThat(processor.created).isEqualTo(1);
    }

    @Test
    void cannotProcessARoundBeforeItIsInitialized() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new Counting().process(Set.of(), new UnusedRound()))
                .withMessage("The processor is not initialized");
    }

    /** A step processor without steps, counting the times it creates them. */
    private static final class Counting extends StepProcessor {

        private int created;

        @Override
        protected List<Step> steps(ProcessingContext context) {
            created++;
            return List.of();
        }

        ProcessingEnvironment environment() {
            return processingEnv;
        }
    }

    /** A round that a processor must not look at. */
    private static final class UnusedRound implements RoundEnvironment {

        @Override
        public boolean processingOver() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean errorRaised() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<? extends Element> getRootElements() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<? extends Element> getElementsAnnotatedWith(TypeElement annotation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<? extends Element> getElementsAnnotatedWith(Class<? extends Annotation> annotation) {
            throw new UnsupportedOperationException();
        }
    }

    /** A step recording, each round, the simple names of the elements it gets by annotation. */
    private static final class Recorder implements Step {

        private final List<Map<String, List<String>>> rounds = new ArrayList<>();
        private final Function<Set<Element>, Set<? extends Element>> deferral;

        Recorder(Function<Set<Element>, Set<? extends Element>> deferral) {
            this.deferral = deferral;
        }

        @Override
        public Set<String> annotations() {
            return Set.of("example.Mark", "example.Other");
        }

        @Override
        public Set<? extends Element> process(AnnotatedElements elements) {
            Map<String, List<String>> round = new LinkedHashMap<>();
            elements.annotations()
                    .forEach(name -> round.put(
                            name,
                            elements.annotatedWith(name).stream()
                                    .map(Object::toString)
                                    .toList()));
            rounds.add(round);
            return deferral.apply(elements.annotatedWith("example.Mark"));
        }
    }

    /** Runs a recorder, and generates {@code example.Generated} for an element annotated with {@code @Generate}. */
    private static final class TestProcessor extends StepProcessor {

        private final Recorder recorder;

        TestProcessor(Recorder recorder) {
            this.recorder = recorder;
        }

        @Override
        protected List<Step> steps(ProcessingContext context) {
            return List.of(recorder, new Step() {
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
        }
    }
}
