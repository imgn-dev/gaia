package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.ProcessingException;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.StepProcessor;
import be.imgn.gaia.processing.testing.Compilation;
import be.imgn.gaia.processing.testing.TestCompiler;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

class ProcessingExceptionTest {

    private static final String MARK = "example.Mark";

    private static final JavaFileObject ANNOTATIONS = source("example.Mark", """
            package example;

            @interface Mark {
                int level() default 0;
                String name() default "";
            }

            @interface Generate {}
            """);

    private static final JavaFileObject FLAGGED = source("example.Flagged", """
            package example;

            @Mark(level = 3)
            class Flagged {}
            """);

    private static final JavaFileObject HOLDER = source("example.Holder", """
            package example;

            class Holder {
                @Mark(level = 1) int first;
                @Mark(level = 2) int second;
                @Mark(level = 3) int third;
            }
            """);

    private static final JavaFileObject SEED = source("example.Seed", """
            package example;

            @Generate
            class Seed {}
            """);

    private static Compilation failing(Function<Element, ProcessingException> failure) {
        return compile(context -> List.of(markStep(elements -> {
                    throw failure.apply(elements.annotatedWith(MARK).iterator().next());
                })))
                .compile(ANNOTATIONS, FLAGGED);
    }

    private static TestCompiler compile(Function<ProcessingContext, List<Step>> steps) {
        return javac().withProcessors(new TestProcessor(steps));
    }

    private static Step markStep(Function<AnnotatedElements, Set<? extends Element>> process) {
        return new Step() {
            @Override
            public Set<String> annotations() {
                return Set.of(MARK);
            }

            @Override
            public Set<? extends Element> process(AnnotatedElements elements) {
                return process.apply(elements);
            }
        };
    }

    private static Step eachMarked(ProcessingContext context, Consumer<Element> action) {
        return markStep(elements -> {
            context.forEach(elements.annotatedWith(MARK), action);
            return Set.of();
        });
    }

    /** A step generating {@code example.Generated} for an element annotated with {@code @Generate}. */
    private static Step generating(ProcessingContext context) {
        return new Step() {
            @Override
            public Set<String> annotations() {
                return Set.of("example.Generate");
            }

            @Override
            public Set<? extends Element> process(AnnotatedElements elements) {
                try (Writer writer =
                        context.filer().createSourceFile("example.Generated").openWriter()) {
                    writer.write("package example;\n\nclass Generated {}\n");
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return Set.of();
            }
        };
    }

    private static AnnotationMirror mark(Element element) {
        return element.getAnnotationMirrors().getFirst();
    }

    @Test
    void reportsOnTheElement() {
        assertThat(failing(element -> new ProcessingException("on the class", element)))
                .failed()
                .hadErrorCount(1)
                .hadErrorContaining("on the class")
                .inFile("example/Flagged.java")
                .onLine(4);
    }

    @Test
    void reportsOnAnAnnotation() {
        assertThat(failing(element -> new ProcessingException("on the annotation", element, mark(element))))
                .hadErrorContaining("on the annotation")
                .inFile("example/Flagged.java")
                .onLine(3)
                .atColumn(1);
    }

    @Test
    void reportsOnAnAnnotationFoundByName() {
        assertThat(failing(element -> new ProcessingException("on the annotation", element, MARK)))
                .hadErrorContaining("on the annotation")
                .inFile("example/Flagged.java")
                .onLine(3)
                .atColumn(1);
    }

    @Test
    void reportsOnAnAttributeValue() {
        assertThat(failing(element -> new ProcessingException("on the value", element, mark(element), "level")))
                .hadErrorContaining("on the value")
                .inFile("example/Flagged.java")
                .onLine(3)
                .atColumn(15);
    }

    @Test
    void reportsOnAnAttributeValueOfAnAnnotationFoundByName() {
        assertThat(failing(element -> new ProcessingException("on the value", element, MARK, "level")))
                .hadErrorContaining("on the value")
                .inFile("example/Flagged.java")
                .onLine(3)
                .atColumn(15);
    }

    @Test
    void reportsOnTheAnnotationForAnAttributeLeftToItsDefault() {
        assertThat(failing(element -> new ProcessingException("on the default", element, MARK, "name")))
                .hadErrorContaining("on the default")
                .onLine(3)
                .atColumn(1);
    }

    @Test
    void reportsWhatForEachCatchesAtItsPlace() {
        Compilation compilation = compile(context -> List.of(eachMarked(context, element -> {
                    throw new ProcessingException("in the loop", element, MARK, "level");
                })))
                .compile(ANNOTATIONS, FLAGGED);

        assertThat(compilation)
                .failed()
                .hadErrorCount(1)
                .hadErrorContaining("in the loop")
                .inFile("example/Flagged.java")
                .onLine(3)
                .atColumn(15);
    }

    @Test
    void goesOnWithTheNextElementAfterOneFails() {
        List<String> done = new ArrayList<>();
        Compilation compilation = compile(context -> List.of(eachMarked(context, element -> {
                    if (element.getSimpleName().contentEquals("second")) {
                        throw new ProcessingException("second fails", element);
                    }
                    done.add(element.toString());
                })))
                .compile(ANNOTATIONS, HOLDER);

        assertThat(compilation)
                .failed()
                .hadErrorCount(1)
                .hadErrorContaining("second fails")
                .inFile("example/Holder.java")
                .onLine(5);
        assertThat(done).containsExactly("first", "third");
    }

    @Test
    void reportsEveryElementThatFails() {
        List<Integer> counts = new ArrayList<>();
        Compilation compilation = compile(context -> List.of(markStep(elements -> {
                    context.forEach(elements.annotatedWith(MARK), element -> {
                        if (!element.getSimpleName().contentEquals("second")) {
                            throw new ProcessingException(element + " fails", element, MARK, "level");
                        }
                    });
                    counts.add(context.diagnostics().errorCount());
                    return Set.of();
                })))
                .compile(ANNOTATIONS, HOLDER);

        assertThat(compilation).failed().hadErrorCount(2);
        assertThat(compilation)
                .hadErrorContaining("first fails")
                .inFile("example/Holder.java")
                .onLine(4)
                .atColumn(19);
        assertThat(compilation)
                .hadErrorContaining("third fails")
                .inFile("example/Holder.java")
                .onLine(6)
                .atColumn(19);
        assertThat(counts).containsExactly(2);
    }

    @Test
    void goesOnWithTheNextStepAfterAStepFails() {
        List<String> failing = new ArrayList<>();
        List<String> next = new ArrayList<>();
        Compilation compilation = compile(context -> List.of(
                        markStep(elements -> {
                            Element first =
                                    elements.annotatedWith(MARK).iterator().next();
                            failing.add(first.toString());
                            throw new ProcessingException("the step fails", first);
                        }),
                        markStep(elements -> {
                            elements.annotatedWith(MARK).forEach(element -> next.add(element.toString()));
                            return Set.of();
                        })))
                .compile(ANNOTATIONS, HOLDER);

        // The elements of the failed step are not deferred: none is reported as still deferred after the last round.
        assertThat(compilation)
                .failed()
                .hadErrorCount(1)
                .hadErrorContaining("the step fails")
                .inFile("example/Holder.java")
                .onLine(4);
        assertThat(failing).containsExactly("first");
        assertThat(next).containsExactly("first", "second", "third");
    }

    @Test
    void reportsTheErrorsOfForEachAndOfAStepThatFailsAfterIt() {
        Compilation compilation = compile(context -> List.of(markStep(elements -> {
                    context.forEach(elements.annotatedWith(MARK), element -> {
                        throw new ProcessingException(element + " fails", element);
                    });
                    throw new ProcessingException(
                            "the step fails", elements.all().iterator().next(), MARK);
                })))
                .compile(ANNOTATIONS, HOLDER);

        assertThat(compilation).failed().hadErrorCount(4);
        assertThat(compilation).hadErrorContaining("third fails").onLine(6);
        assertThat(compilation).hadErrorContaining("the step fails").onLine(4).atColumn(5);
    }

    @Test
    void reportsAnElementThatFailsOnceTheTypesItWaitedForAreGenerated() {
        List<TypeKind> fieldTypes = new ArrayList<>();
        Compilation compilation = compile(context -> List.of(
                        eachMarked(context, element -> {
                            ElementFilter.fieldsIn(element.getEnclosedElements())
                                    .forEach(field ->
                                            fieldTypes.add(field.asType().getKind()));
                            throw new ProcessingException("fails once resolved", element);
                        }),
                        generating(context)))
                .compile(ANNOTATIONS, SEED, source("example.Uses", """
                        package example;

                        @Mark
                        class Uses {
                            Generated generated;
                        }
                        """));

        assertThat(compilation)
                .failed()
                .hadErrorCount(1)
                .hadErrorContaining("fails once resolved")
                .inFile("example/Uses.java")
                .onLine(4);
        assertThat(fieldTypes).containsExactly(TypeKind.DECLARED);
    }

    @Test
    void reportsAnElementThatFailsAfterTheStepGaveItBack() {
        List<Integer> calls = new ArrayList<>();
        Compilation compilation = compile(context -> List.of(
                        markStep(elements -> {
                            calls.add(elements.all().size());
                            if (calls.size() == 1) {
                                return elements.all();
                            }
                            context.forEach(elements.all(), element -> {
                                throw new ProcessingException("fails when given back", element);
                            });
                            return Set.of();
                        }),
                        generating(context)))
                .compile(ANNOTATIONS, SEED, FLAGGED);

        assertThat(compilation)
                .failed()
                .hadErrorCount(1)
                .hadErrorContaining("fails when given back")
                .inFile("example/Flagged.java")
                .onLine(4);
        assertThat(calls).containsExactly(1, 1);
    }

    @Test
    void reportsFromAnyProcessor() {
        Compilation compilation = javac().withProcessors(new PlainProcessor(context -> {
                    TypeElement mark = Objects.requireNonNull(context.elements().getTypeElement(MARK));
                    return element -> {
                        try {
                            throw new ProcessingException("caught", element, MARK, "level");
                        } catch (ProcessingException e) {
                            context.diagnostics().error(e);
                        }
                        context.forEach(List.of(element, mark), each -> {
                            throw new ProcessingException("looped over " + each, each);
                        });
                    };
                }))
                .compile(ANNOTATIONS, FLAGGED);

        assertThat(compilation).failed().hadErrorCount(3);
        assertThat(compilation)
                .hadErrorContaining("caught")
                .inFile("example/Flagged.java")
                .onLine(3)
                .atColumn(15);
        assertThat(compilation)
                .hadErrorContaining("looped over example.Flagged")
                .inFile("example/Flagged.java")
                .onLine(4);
        assertThat(compilation)
                .hadErrorContaining("looped over example.Mark")
                .inFile("example/Mark.java")
                .onLine(3);
    }

    @Test
    void letsOtherExceptionsOfAStepPropagate() {
        IllegalStateException failure = new IllegalStateException("a bug");
        List<String> next = new ArrayList<>();

        assertThatThrownBy(() -> compile(context -> List.of(
                                markStep(elements -> {
                                    throw failure;
                                }),
                                markStep(elements -> {
                                    elements.all().forEach(element -> next.add(element.toString()));
                                    return Set.of();
                                })))
                        .compile(ANNOTATIONS, FLAGGED))
                .rootCause()
                .isSameAs(failure);
        assertThat(next).isEmpty();
    }

    @Test
    void letsOtherExceptionsOfForEachPropagateAndStopsIt() {
        IllegalStateException failure = new IllegalStateException("a bug");
        List<String> done = new ArrayList<>();

        assertThatThrownBy(() -> compile(context -> List.of(eachMarked(context, element -> {
                            done.add(element.toString());
                            if (element.getSimpleName().contentEquals("second")) {
                                throw failure;
                            }
                        })))
                        .compile(ANNOTATIONS, HOLDER))
                .rootCause()
                .isSameAs(failure);
        assertThat(done).containsExactly("first", "second");
    }

    @Test
    void carriesTheElementTheAnnotationAndTheAttribute() {
        List<ProcessingException> exceptions = new ArrayList<>();
        compile(context -> List.of(markStep(elements -> {
                    Element element = elements.all().iterator().next();
                    exceptions.add(new ProcessingException("element", element));
                    exceptions.add(new ProcessingException("annotation", element, mark(element)));
                    exceptions.add(new ProcessingException("by name", element, MARK));
                    exceptions.add(new ProcessingException("attribute", element, mark(element), "level"));
                    exceptions.add(new ProcessingException("default", element, MARK, "name"));
                    return Set.of();
                })))
                .compile(ANNOTATIONS, FLAGGED);

        assertThat(exceptions)
                .extracting(
                        ProcessingException::getMessage,
                        exception -> exception.element().toString(),
                        exception -> exception
                                .annotation()
                                .map(annotation ->
                                        annotation.getAnnotationType().toString())
                                .orElse("none"),
                        exception -> exception.attribute().orElse("none"))
                .containsExactly(
                        tuple("element", "example.Flagged", "none", "none"),
                        tuple("annotation", "example.Flagged", MARK, "none"),
                        tuple("by name", "example.Flagged", MARK, "none"),
                        tuple("attribute", "example.Flagged", MARK, "level"),
                        tuple("default", "example.Flagged", MARK, "name"));
        assertThat(exceptions).allSatisfy(exception -> assertThat(exception).hasNoCause());
    }

    @Test
    void rejectsAnAnnotationOrAnAttributeThatDoesNotExist() {
        List<Throwable> thrown = new ArrayList<>();
        Compilation compilation = compile(context -> List.of(markStep(elements -> {
                    Element element = elements.all().iterator().next();
                    thrown.add(catchThrowable(() -> new ProcessingException("m", element, "example.Generate")));
                    thrown.add(catchThrowable(() -> new ProcessingException("m", element, "example.Generate", "x")));
                    thrown.add(catchThrowable(() -> new ProcessingException("m", element, MARK, "missing")));
                    thrown.add(catchThrowable(() -> new ProcessingException("m", element, mark(element), "missing")));
                    return Set.of();
                })))
                .compile(ANNOTATIONS, FLAGGED);

        assertThat(compilation).succeeded();
        assertThat(thrown)
                .allSatisfy(exception -> assertThat(exception).isInstanceOf(IllegalArgumentException.class))
                .extracting(Throwable::getMessage)
                .containsExactly(
                        "example.Flagged has no @example.Generate",
                        "example.Flagged has no @example.Generate",
                        "@example.Mark has no attribute missing",
                        "@example.Mark has no attribute missing");
    }

    @Test
    void rejectsAnAnnotationOfAnotherElement() {
        List<Throwable> thrown = new ArrayList<>();
        Compilation compilation = compile(context -> List.of(markStep(elements -> {
                    List<Element> fields = List.copyOf(elements.annotatedWith(MARK));
                    AnnotationMirror first = mark(fields.getFirst());
                    Element second = fields.get(1);
                    thrown.add(catchThrowable(() -> new ProcessingException("m", second, first)));
                    thrown.add(catchThrowable(() -> new ProcessingException("m", second, first, "level")));
                    return Set.of();
                })))
                .compile(ANNOTATIONS, HOLDER);

        assertThat(compilation).succeeded();
        assertThat(thrown)
                .allSatisfy(exception -> assertThat(exception).isInstanceOf(IllegalArgumentException.class))
                .extracting(Throwable::getMessage)
                .containsExactly(
                        "@example.Mark(level=1) is not an annotation of second",
                        "@example.Mark(level=1) is not an annotation of second");
    }

    @Test
    void cannotBeSerialized() {
        List<ProcessingException> exceptions = new ArrayList<>();
        compile(context -> List.of(markStep(elements -> {
                    exceptions.add(new ProcessingException(
                            "m", elements.all().iterator().next(), MARK, "level"));
                    return Set.of();
                })))
                .compile(ANNOTATIONS, FLAGGED);

        assertThatThrownBy(() -> {
                    try (ObjectOutputStream out = new ObjectOutputStream(new ByteArrayOutputStream())) {
                        out.writeObject(exceptions.getFirst());
                    }
                })
                .isInstanceOf(NotSerializableException.class)
                .hasMessage(ProcessingException.class.getName());
    }

    /** A step processor with the steps a function gives. */
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

    /** A processor that is not a step processor, running an action on each element annotated with {@code @Mark}. */
    private static final class PlainProcessor extends AbstractProcessor {

        private final Function<ProcessingContext, Consumer<Element>> action;

        PlainProcessor(Function<ProcessingContext, Consumer<Element>> action) {
            this.action = action;
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of(MARK);
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            ProcessingContext context = ProcessingContext.of(processingEnv, getClass());
            TypeElement mark = context.elements().getTypeElement(MARK);
            if (mark != null) {
                roundEnv.getElementsAnnotatedWith(mark).forEach(action.apply(context));
            }
            return false;
        }
    }
}
