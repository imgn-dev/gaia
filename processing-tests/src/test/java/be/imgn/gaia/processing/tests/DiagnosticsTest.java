package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.Diagnostics;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.StepProcessor;
import be.imgn.gaia.processing.testing.Compilation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

class DiagnosticsTest {

    private static final JavaFileObject MARK = source("example.Mark", """
            package example;

            @interface Mark {
                int level() default 0;
                String name() default "";
            }
            """);

    private static final JavaFileObject FLAGGED = source("example.Flagged", """
            package example;

            @Mark(level = 3)
            class Flagged {}
            """);

    private static Compilation report(BiConsumer<Diagnostics, Element> reports) {
        return javac().withProcessors(new Reporting(reports)).compile(MARK, FLAGGED);
    }

    @Test
    void reportsOnAnElement() {
        assertThat(report((diagnostics, element) -> diagnostics.on(element).error("on the class")))
                .hadErrorContaining("on the class")
                .inFile("example/Flagged.java")
                .onLine(4);
    }

    @Test
    void reportsOnAnAnnotation() {
        assertThat(report((diagnostics, element) ->
                        diagnostics.on(element).annotation("example.Mark").error("on the annotation")))
                .hadErrorContaining("on the annotation")
                .onLine(3)
                .atColumn(1);
    }

    @Test
    void reportsOnAnAttributeValue() {
        assertThat(report((diagnostics, element) -> diagnostics
                        .on(element)
                        .annotation("example.Mark")
                        .attribute("level")
                        .error("on the value")))
                .hadErrorContaining("on the value")
                .onLine(3)
                .atColumn(15);
    }

    @Test
    void reportsOnTheAnnotationForAnAttributeLeftToItsDefault() {
        assertThat(report((diagnostics, element) -> diagnostics
                        .on(element)
                        .annotation("example.Mark")
                        .attribute("name")
                        .error("on the default")))
                .hadErrorContaining("on the default")
                .onLine(3)
                .atColumn(1);
    }

    @Test
    void reportsWarningsAndNotes() {
        Compilation compilation = report((diagnostics, element) -> {
            diagnostics.on(element).warning("a warning");
            diagnostics.on(element).note("a note");
        });

        assertThat(compilation).succeeded().hadWarningContaining("a warning").onLine(4);
        assertThat(compilation).hadNoteContaining("a note").onLine(4);
    }

    @Test
    void countsTheErrors() {
        List<Integer> counts = new ArrayList<>();
        Compilation compilation = report((diagnostics, element) -> {
            counts.add(diagnostics.errorCount());
            diagnostics.on(element).error("first");
            diagnostics.on(element).warning("not an error");
            diagnostics.on(element).error("second");
            counts.add(diagnostics.errorCount());
            assertThat(diagnostics.hasErrors()).isTrue();
        });

        assertThat(compilation).failed().hadErrorCount(2);
        assertThat(counts).containsExactly(0, 2);
    }

    /** Reports diagnostics on each element annotated with {@code @Mark}. */
    private static final class Reporting extends StepProcessor {

        private final BiConsumer<Diagnostics, Element> reports;

        Reporting(BiConsumer<Diagnostics, Element> reports) {
            this.reports = reports;
        }

        @Override
        protected List<Step> steps(ProcessingContext context) {
            return List.of(new Step() {
                @Override
                public Set<String> annotations() {
                    return Set.of("example.Mark");
                }

                @Override
                public Set<? extends Element> process(AnnotatedElements elements) {
                    elements.annotatedWith("example.Mark")
                            .forEach(element -> reports.accept(context.diagnostics(), element));
                    return Set.of();
                }
            });
        }
    }
}
