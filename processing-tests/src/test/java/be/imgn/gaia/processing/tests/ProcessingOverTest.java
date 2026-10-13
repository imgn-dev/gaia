package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.ProcessingException;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.StepProcessor;
import be.imgn.gaia.processing.source.ClassName;
import be.imgn.gaia.processing.testing.Compilation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;

class ProcessingOverTest {

    private static final ClassName SERVICE = ClassName.of("example", "Service");

    private static final String SERVICE_FILE = "META-INF/services/example.Service";

    private static final JavaFileObject ANNOTATIONS = source("example.Mark", """
            package example;

            public @interface Mark {}

            @interface Other {}
            """);

    private static final JavaFileObject SERVICE_SOURCE = source("example.Service", """
            package example;

            public interface Service {}
            """);

    private static final JavaFileObject MARKED = source("example.Marked", """
            package example;

            @Mark
            public final class Marked implements Service {}
            """);

    private static final JavaFileObject LATE = source("example.Late", """
            package example;

            public final class Late implements Service {}
            """);

    private static Compilation compile(Function<ProcessingContext, List<Step>> steps, JavaFileObject... sources) {
        List<JavaFileObject> all = new ArrayList<>(List.of(ANNOTATIONS, SERVICE_SOURCE));
        all.addAll(List.of(sources));
        return javac().withProcessors(new TestProcessor(steps)).compile(all);
    }

    private static List<String> messages(Compilation compilation) {
        return compilation.diagnostics().stream()
                .map(diagnostic -> diagnostic.getKind() + " " + diagnostic.getMessage(Locale.ROOT))
                .toList();
    }

    private static TypeElement marked(ProcessingContext context) {
        return Objects.requireNonNull(context.elements().getTypeElement("example.Marked"));
    }

    @Test
    void finishesEachStepOnceInTurnAfterItsLastRound() {
        List<String> events = new ArrayList<>();
        Compilation compilation = compile(
                context -> List.of(
                        // Generates a type in the first round, so that there is a second one to give the element back.
                        new TestStep(
                                "example.Mark",
                                elements -> {
                                    events.add("process " + elements.all());
                                    if (events.size() == 1) {
                                        context.sources()
                                                .write("example.Generated", scope -> "final class Generated {}\n");
                                        return elements.all();
                                    }
                                    return Set.of();
                                },
                                () -> events.add("first over")),
                        new TestStep(
                                "example.Other",
                                elements -> {
                                    events.add("never processed");
                                    return Set.of();
                                },
                                () -> events.add("second over"))),
                MARKED);

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(events)
                .containsExactly("process [example.Marked]", "process [example.Marked]", "first over", "second over");
    }

    @Test
    void finishesAfterReportingTheElementsStillDeferred() {
        List<Integer> errorCounts = new ArrayList<>();
        Compilation compilation = compile(
                context -> List.of(new TestStep("example.Mark", AnnotatedElements::all, () -> {
                    errorCounts.add(context.diagnostics().errorCount());
                    context.diagnostics().note("finished");
                })),
                MARKED);

        assertThat(compilation).failed().hadErrorCount(1);
        assertThat(errorCounts).containsExactly(1);
        assertThat(messages(compilation))
                .containsExactly(
                        "ERROR Could not process example.Marked: it was still deferred after the last round",
                        "NOTE finished");
    }

    @Test
    void writesTheServiceFilesAfterwardsWithTheImplementationsItRegisters() {
        Compilation compilation = compile(
                context -> List.of(new TestStep(
                        "example.Mark",
                        elements -> {
                            elements.all()
                                    .forEach(element ->
                                            context.services().add(SERVICE, ClassName.of((TypeElement) element)));
                            return Set.of();
                        },
                        () -> context.services().add(SERVICE, ClassName.of("example", "Late")))),
                MARKED,
                LATE);

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE)
                .isEqualTo("example.Late\nexample.Marked\n");
    }

    @Test
    void writesNoFileForTheServicesItDiscards() {
        ClassName other = ClassName.of(Object.class);
        Compilation compilation = compile(
                context -> List.of(new TestStep(
                        "example.Mark",
                        elements -> {
                            elements.all().forEach(element -> {
                                context.services().add(SERVICE, ClassName.of((TypeElement) element), element);
                                context.services().add(other, ClassName.of((TypeElement) element), element);
                            });
                            return Set.of();
                        },
                        () -> context.services().discard(SERVICE))),
                MARKED);

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE))
                .isEmpty();
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/services/java.lang.Object")
                .isEqualTo("example.Marked\n");
    }

    @Test
    void reportsItsDiagnosticsAtTheirPlaceAndCountsItsErrors() {
        Compilation compilation = compile(
                context -> List.of(new TestStep("example.Mark", elements -> Set.of(), () -> {
                    context.diagnostics().on(marked(context)).error("checked at the end");
                    context.diagnostics().note("provides example.Service with example.Marked;");
                })),
                MARKED);

        assertThat(compilation)
                .failed()
                .hadErrorCount(1)
                .hadErrorContaining("checked at the end")
                .inFile("example/Marked.java")
                .onLine(4);
        assertThat(compilation).hadNoteContaining("provides example.Service with example.Marked;");
        assertThat(compilation.diagnostics(Diagnostic.Kind.NOTE).getFirst().getSource())
                .isNull();
    }

    @Test
    void reportsAProcessingExceptionThenFinishesTheNextStepsAndWritesTheServiceFiles() {
        List<String> finished = new ArrayList<>();
        Compilation compilation = compile(
                context -> List.of(
                        new TestStep(
                                "example.Mark",
                                elements -> {
                                    elements.all()
                                            .forEach(element -> context.services()
                                                    .add(SERVICE, ClassName.of((TypeElement) element), element));
                                    return Set.of();
                                },
                                () -> {
                                    throw new ProcessingException("invalid at the end", marked(context));
                                }),
                        new TestStep("example.Other", elements -> Set.of(), () -> finished.add("second"))),
                MARKED);

        assertThat(compilation)
                .failed()
                .hadErrorCount(1)
                .hadErrorContaining("invalid at the end")
                .inFile("example/Marked.java")
                .onLine(4);
        assertThat(finished).containsExactly("second");
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE)
                .isEqualTo("example.Marked\n");
    }

    @Test
    void letsOtherExceptionsPropagate() {
        IllegalStateException failure = new IllegalStateException("a bug");
        List<String> finished = new ArrayList<>();

        assertThatThrownBy(() -> compile(
                        context -> List.of(
                                new TestStep("example.Mark", elements -> Set.of(), () -> {
                                    throw failure;
                                }),
                                new TestStep("example.Other", elements -> Set.of(), () -> finished.add("second"))),
                        MARKED))
                .rootCause()
                .isSameAs(failure);
        assertThat(finished).isEmpty();
    }

    @Test
    void isNotCalledWhenJavacNeverRunsTheProcessor() {
        List<String> finished = new ArrayList<>();
        Compilation compilation = compile(
                context -> List.of(new TestStep("example.Mark", elements -> Set.of(), () -> finished.add("over"))),
                LATE);

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(finished).isEmpty();
    }

    /** A step on one annotation, processing its elements with a function, and finishing with an action. */
    private static final class TestStep implements Step {

        private final String annotation;
        private final Function<AnnotatedElements, Set<? extends Element>> process;
        private final Runnable over;

        TestStep(String annotation, Function<AnnotatedElements, Set<? extends Element>> process, Runnable over) {
            this.annotation = annotation;
            this.process = process;
            this.over = over;
        }

        @Override
        public Set<String> annotations() {
            return Set.of(annotation);
        }

        @Override
        public Set<? extends Element> process(AnnotatedElements elements) {
            return process.apply(elements);
        }

        @Override
        public void processingOver() {
            over.run();
        }
    }

    /** A processor of the steps a function returns. */
    private static final class TestProcessor extends StepProcessor {

        private final Function<ProcessingContext, List<Step>> steps;

        TestProcessor(Function<ProcessingContext, List<Step>> steps) {
            this.steps = steps;
        }

        @Override
        protected List<Step> steps(ProcessingContext context) {
            return steps.apply(context);
        }
    }
}
