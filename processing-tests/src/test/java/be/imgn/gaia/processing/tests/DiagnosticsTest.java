package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

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
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
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

    // The annotation of a type that an element has, or that one of its annotations wraps in its value attribute.
    private static AnnotationMirror annotation(Element element, String type, int index) {
        return element.getAnnotationMirrors().stream()
                .filter(mirror -> mirror.getAnnotationType().toString().equals(type))
                .flatMap(mirror -> mirror.getElementValues().values().stream())
                .map(value -> (List<?>) value.getValue())
                .map(values -> (AnnotationMirror) ((AnnotationValue) values.get(index)).getValue())
                .findFirst()
                .orElseThrow();
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

    @Test
    void reportsOnAnAnnotationInItsContainer() {
        Compilation compilation = javac().withProcessors(new Reporting((diagnostics, element) -> {
                    Diagnostics.Report second =
                            diagnostics.on(element).annotation(annotation(element, "example.Tags", 1));
                    second.error("on the second tag");
                    second.attribute("value").error("on its value");
                }))
                .compile(MARK, source("example.Tag", """
                        package example;

                        import java.lang.annotation.Repeatable;

                        @Repeatable(Tags.class)
                        @interface Tag {
                            String value();
                        }

                        @interface Tags {
                            Tag[] value();
                        }
                        """), source("example.Tagged", """
                        package example;

                        @Mark
                        @Tags({@Tag("first"), @Tag("second")})
                        class Tagged {}
                        """));

        assertThat(compilation)
                .hadErrorContaining("on the second tag")
                .inFile("example/Tagged.java")
                .onLine(4)
                .atColumn(23);
        assertThat(compilation).hadErrorContaining("on its value").onLine(4).atColumn(28);
    }

    @Test
    void rejectsAnAnnotationOfAnotherElement() {
        List<AnnotationMirror> seen = new ArrayList<>();
        List<Throwable> thrown = new ArrayList<>();
        Compilation compilation = javac().withProcessors(new Reporting((diagnostics, element) -> {
                    if (!seen.isEmpty()) {
                        thrown.add(catchThrowable(() -> diagnostics.on(element).annotation(seen.getFirst())));
                    }
                    seen.add(element.getAnnotationMirrors().getFirst());
                }))
                .compile(MARK, FLAGGED, source("example.Other", """
                        package example;

                        @Mark(level = 1)
                        class Other {}
                        """));

        assertThat(compilation).succeeded();
        assertThat(thrown)
                .singleElement()
                .satisfies(exception -> assertThat(exception)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("@example.Mark(level=3) is not an annotation of example.Other"));
    }

    @Test
    void rejectsAnAttributeThatIsNotAnAnnotationElement() {
        List<Throwable> thrown = new ArrayList<>();
        Compilation compilation = javac().withProcessors(new Reporting((diagnostics, element) -> {
                    Diagnostics.Report limited = diagnostics.on(element).annotation("example.Limited");
                    thrown.add(catchThrowable(() -> limited.attribute("MAX")));
                    thrown.add(catchThrowable(() -> limited.attribute("Unit")));
                }))
                .compile(MARK, source("example.Limited", """
                        package example;

                        @interface Limited {
                            int MAX = 3;

                            enum Unit { ONE }

                            int value() default MAX;
                        }
                        """), source("example.Bounded", """
                        package example;

                        @Mark
                        @Limited
                        class Bounded {}
                        """));

        assertThat(compilation).succeeded();
        assertThat(thrown)
                .allSatisfy(exception -> assertThat(exception).isInstanceOf(IllegalArgumentException.class))
                .extracting(Throwable::getMessage)
                .containsExactly("@example.Limited has no attribute MAX", "@example.Limited has no attribute Unit");
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
