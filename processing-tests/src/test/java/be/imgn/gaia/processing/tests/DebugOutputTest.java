package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.ProcessingException;
import be.imgn.gaia.processing.ProcessorOption;
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
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

class DebugOutputTest {

    private static final String DEBUG = "-Agaia.debug";

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

    private static final JavaFileObject MARKED = source("example.Marked", """
            package example;

            @Mark
            class Marked {}
            """);

    private static final JavaFileObject USES = source("example.Uses", """
            package example;

            @Mark
            class Uses {
                Generated generated;
            }
            """);

    // The durations of Stopwatch.toString, which vary from run to run.
    private static final String TIME = "[0-9.]+ (ns|μs|ms|s|min|h|d)";

    private static List<String> notes(Compilation compilation) {
        return compilation.diagnostics(Diagnostic.Kind.NOTE).stream()
                .map(note -> note.getMessage(Locale.ROOT).replaceAll(TIME + "$", "<time>"))
                .toList();
    }

    private static Compilation compile(Debugged processor, String... options) {
        return javac().withProcessors(processor).withOptions(options).compile(ANNOTATIONS, SEED, MARKED, USES);
    }

    @Test
    void notesNothingByDefault() {
        Compilation compilation = compile(new Debugged(context -> List.of(new MarkStep(), new GenerateStep(context))));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation.diagnostics(Diagnostic.Kind.NOTE)).isEmpty();
    }

    @Test
    void notesNothingWhenTurnedOff() {
        Compilation compilation = compile(
                new Debugged(context -> List.of(new MarkStep(), new GenerateStep(context))), "-Agaia.debug=false");

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation.diagnostics(Diagnostic.Kind.NOTE)).isEmpty();
    }

    @Test
    void notesWhatEachStepDidInEachRoundAndTheTotals() {
        Compilation compilation =
                compile(new Debugged(context -> List.of(new MarkStep(), new GenerateStep(context))), DEBUG);

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(notes(compilation))
                .containsExactly(
                        "Debugged round 1, MarkStep: 1 element [Marked], 0 given back, 1 held back, 0 errors, <time>",
                        "Debugged round 1, GenerateStep: 1 element [Seed], 0 given back, 0 held back, 0 errors,"
                                + " <time>",
                        "Debugged round 1: 2 elements, 0 given back, 1 held back, 0 errors, <time>",
                        "Debugged round 2, MarkStep: 1 element [Uses], 0 given back, 0 held back, 0 errors, <time>",
                        "Debugged round 2: 1 element, 0 given back, 0 held back, 0 errors, <time>",
                        "Debugged round 3 (last): 0 elements still deferred, 0 errors, <time>",
                        "Debugged total, MarkStep: 2 elements in 2 rounds, 0 errors, <time>",
                        "Debugged total, GenerateStep: 1 element in 1 round, 0 errors, <time>",
                        "Debugged total: 3 rounds, 3 elements, 0 errors, <time>");
    }

    @Test
    void acceptsTheOptionWithAnyCaseAndWithoutAValue() {
        Compilation upperCase = javac().withProcessors(new Debugged(context -> List.of(new MarkStep())))
                .withOptions("-Agaia.debug=TRUE")
                .compile(ANNOTATIONS, MARKED);
        Compilation noValue = javac().withProcessors(new Debugged(context -> List.of(new MarkStep())))
                .withOptions(DEBUG)
                .compile(ANNOTATIONS, MARKED);

        assertThat(upperCase).succeeded().hadNoWarnings();
        assertThat(notes(upperCase)).last().isEqualTo("Debugged total: 2 rounds, 1 element, 0 errors, <time>");
        assertThat(noValue).succeeded().hadNoWarnings();
        assertThat(notes(noValue)).last().isEqualTo("Debugged total: 2 rounds, 1 element, 0 errors, <time>");
    }

    @Test
    void endsEveryNoteWithADuration() {
        Compilation compilation =
                compile(new Debugged(context -> List.of(new MarkStep(), new GenerateStep(context))), DEBUG);

        assertThat(compilation.diagnostics(Diagnostic.Kind.NOTE))
                .hasSize(9)
                .allSatisfy(note -> assertThat(note.getMessage(Locale.ROOT)).matches(".*, " + TIME));
    }

    @Test
    void reportsTheNotesWithoutAPlace() {
        Compilation compilation = compile(new Debugged(context -> List.of(new MarkStep())), DEBUG);

        assertThat(compilation.diagnostics(Diagnostic.Kind.NOTE))
                .isNotEmpty()
                .allSatisfy(note -> assertThat(note.getSource()).isNull());
    }

    @Test
    void countsTheElementsGivenBack() {
        List<Integer> calls = new ArrayList<>();
        Compilation compilation = compile(
                new Debugged(context -> List.of(
                        new MarkStep(elements -> {
                            calls.add(elements.all().size());
                            return calls.size() == 1 ? elements.all() : Set.of();
                        }),
                        new GenerateStep(context))),
                DEBUG);

        assertThat(compilation).succeeded();
        assertThat(notes(compilation))
                .startsWith(
                        "Debugged round 1, MarkStep: 1 element [Marked], 1 given back, 1 held back, 0 errors, <time>",
                        "Debugged round 1, GenerateStep: 1 element [Seed], 0 given back, 0 held back, 0 errors,"
                                + " <time>",
                        "Debugged round 1: 2 elements, 1 given back, 1 held back, 0 errors, <time>",
                        "Debugged round 2, MarkStep: 2 elements [Uses, Marked], 0 given back, 0 held back, 0 errors,"
                                + " <time>")
                .contains("Debugged total, MarkStep: 3 elements in 2 rounds, 0 errors, <time>");
    }

    @Test
    void countsTheElementsStillDeferredAfterTheLastRound() {
        Compilation compilation = javac().withProcessors(
                        new Debugged(context -> List.of(new MarkStep(elements -> elements.all()))))
                .withOptions(DEBUG)
                .compile(ANNOTATIONS, MARKED, USES);

        // The third error is javac's, for the missing type, and not the processor's.
        assertThat(compilation).failed().hadErrorCount(3);
        assertThat(notes(compilation))
                .containsExactly(
                        "Debugged round 1, MarkStep: 1 element [Marked], 1 given back, 1 held back, 0 errors, <time>",
                        "Debugged round 1: 1 element, 1 given back, 1 held back, 0 errors, <time>",
                        "Debugged round 2 (last): 2 elements still deferred, 2 errors, <time>",
                        "Debugged total, MarkStep: 1 element in 1 round, 0 errors, <time>",
                        "Debugged total: 2 rounds, 1 element, 2 errors, <time>");
    }

    @Test
    void countsTheElementsFoundByMetaAnnotations() {
        Compilation compilation = javac().withProcessors(
                        new Debugged(context -> List.of(new MetaStep(), new GenerateStep(context))))
                .withOptions(DEBUG)
                .compile(ANNOTATIONS, SEED, source("example.Meta", """
                        package example;

                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Target;

                        @Target(ElementType.ANNOTATION_TYPE)
                        @interface Meta {}

                        @Meta
                        @interface Tagged {}

                        @Tagged
                        class One {}

                        class Two {
                            @Tagged
                            void run() {}

                            @Tagged Generated generated;
                        }
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(notes(compilation))
                .containsExactly(
                        "Debugged round 1, MetaStep: 2 elements [One, Two.run()], 0 given back, 1 held back, 0 errors,"
                                + " <time>",
                        "Debugged round 1, GenerateStep: 1 element [Seed], 0 given back, 0 held back, 0 errors,"
                                + " <time>",
                        "Debugged round 1: 3 elements, 0 given back, 1 held back, 0 errors, <time>",
                        "Debugged round 2, MetaStep: 1 element [Two.generated], 0 given back, 0 held back, 0 errors,"
                                + " <time>",
                        "Debugged round 2: 1 element, 0 given back, 0 held back, 0 errors, <time>",
                        "Debugged round 3 (last): 0 elements still deferred, 0 errors, <time>",
                        "Debugged total, MetaStep: 3 elements in 2 rounds, 0 errors, <time>",
                        "Debugged total, GenerateStep: 1 element in 1 round, 0 errors, <time>",
                        "Debugged total: 3 rounds, 4 elements, 0 errors, <time>");
    }

    @Test
    void countsTheErrorsOfEachStep() {
        Compilation compilation = javac().withProcessors(new Debugged(context -> List.of(
                        new MarkStep(elements -> {
                            throw new ProcessingException(
                                    "escaped", elements.all().iterator().next());
                        }),
                        new OtherStep(elements -> {
                            context.forEach(elements.all(), element -> {
                                throw new ProcessingException("caught", element);
                            });
                            context.diagnostics().warning("not an error");
                            return Set.of();
                        }))))
                .withOptions(DEBUG)
                .compile(ANNOTATIONS, MARKED, source("example.Both", """
                        package example;

                        @Mark
                        class Both {}

                        @Mark
                        class Again {}
                        """));

        assertThat(compilation).failed().hadErrorCount(4);
        assertThat(notes(compilation))
                .containsExactly(
                        "Debugged round 1, MarkStep: 3 elements [Marked, Both, Again], 0 given back, 0 held back,"
                                + " 1 error, <time>",
                        "Debugged round 1, OtherStep: 3 elements [Marked, Both, Again], 0 given back, 0 held back,"
                                + " 3 errors, <time>",
                        "Debugged round 1: 6 elements, 0 given back, 0 held back, 4 errors, <time>",
                        "Debugged round 2 (last): 0 elements still deferred, 0 errors, <time>",
                        "Debugged total, MarkStep: 3 elements in 1 round, 1 error, <time>",
                        "Debugged total, OtherStep: 3 elements in 1 round, 3 errors, <time>",
                        "Debugged total: 2 rounds, 6 elements, 4 errors, <time>");
    }

    @Test
    void abbreviatesTheElementsBeyondThree() {
        Compilation compilation = javac().withProcessors(new Debugged(context -> List.of(new MarkStep())))
                .withOptions(DEBUG)
                .compile(ANNOTATIONS, source("example.Five", """
                        package example;

                        @Mark class One {}
                        @Mark class Two {}
                        @Mark class Three {}
                        @Mark class Four {}
                        @Mark class Five {}
                        """));

        assertThat(notes(compilation))
                .first()
                .isEqualTo("Debugged round 1, MarkStep: 5 elements [One, Two, Three, +2 more], 0 given back,"
                        + " 0 held back, 0 errors, <time>");
    }

    @Test
    void namesMembersAndNestedTypesAfterTheirEnclosingType() {
        Compilation compilation = javac().withProcessors(new Debugged(context -> List.of(new MarkStep())))
                .withOptions(DEBUG)
                .compile(ANNOTATIONS, source("example.Outer", """
                        package example;

                        class Outer {
                            @Mark int field;

                            void method(@Mark String parameter) {}

                            static class Inner {
                                @Mark Inner(int value) {}
                            }
                        }
                        """));

        assertThat(notes(compilation).getFirst())
                .startsWith("Debugged round 1, MarkStep: 3 elements [")
                .endsWith("], 0 given back, 0 held back, 0 errors, <time>");
        assertThat(notes(compilation).getFirst().replaceAll(".*\\[|\\].*", "").split(", "))
                .containsExactlyInAnyOrder(
                        "Outer.field", "Outer.method(java.lang.String).parameter", "Outer.Inner.Inner(int)");
    }

    @Test
    void namesAPackageByItsQualifiedName() {
        Compilation compilation = javac().withProcessors(new Debugged(context -> List.of(new MarkStep())))
                .withOptions(DEBUG)
                .compile(ANNOTATIONS, source("example.package-info", """
                        @Mark
                        package example;
                        """));

        assertThat(notes(compilation)).first().asString().contains("1 element [example]");
    }

    @Test
    void numbersStepsOfTheSameClassAndNamesAnonymousSteps() {
        Compilation compilation = javac().withProcessors(new Debugged(context -> List.of(
                        new MarkStep(),
                        new Step() {
                            @Override
                            public Set<String> annotations() {
                                return Set.of("example.Mark");
                            }

                            @Override
                            public Set<? extends Element> process(AnnotatedElements elements) {
                                return Set.of();
                            }
                        },
                        new MarkStep())))
                .withOptions(DEBUG)
                .compile(ANNOTATIONS, MARKED);

        assertThat(notes(compilation))
                .containsExactly(
                        "Debugged round 1, MarkStep#1: 1 element [Marked], 0 given back, 0 held back, 0 errors,"
                                + " <time>",
                        "Debugged round 1, DebugOutputTest$1: 1 element [Marked], 0 given back, 0 held back,"
                                + " 0 errors, <time>",
                        "Debugged round 1, MarkStep#2: 1 element [Marked], 0 given back, 0 held back, 0 errors,"
                                + " <time>",
                        "Debugged round 1: 3 elements, 0 given back, 0 held back, 0 errors, <time>",
                        "Debugged round 2 (last): 0 elements still deferred, 0 errors, <time>",
                        "Debugged total, MarkStep#1: 1 element in 1 round, 0 errors, <time>",
                        "Debugged total, DebugOutputTest$1: 1 element in 1 round, 0 errors, <time>",
                        "Debugged total, MarkStep#2: 1 element in 1 round, 0 errors, <time>",
                        "Debugged total: 2 rounds, 3 elements, 0 errors, <time>");
    }

    @Test
    void namesAStepByItsName() {
        Compilation compilation = javac().withProcessors(
                        new Debugged(context -> List.of(new NamedStep("Marking"), new MarkStep())))
                .withOptions(DEBUG)
                .compile(ANNOTATIONS, MARKED);

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(notes(compilation))
                .containsExactly(
                        "Debugged round 1, Marking: 1 element [Marked], 0 given back, 0 held back, 0 errors, <time>",
                        "Debugged round 1, MarkStep: 1 element [Marked], 0 given back, 0 held back, 0 errors, <time>",
                        "Debugged round 1: 2 elements, 0 given back, 0 held back, 0 errors, <time>",
                        "Debugged round 2 (last): 0 elements still deferred, 0 errors, <time>",
                        "Debugged total, Marking: 1 element in 1 round, 0 errors, <time>",
                        "Debugged total, MarkStep: 1 element in 1 round, 0 errors, <time>",
                        "Debugged total: 2 rounds, 2 elements, 0 errors, <time>");
    }

    @Test
    void numbersStepsWithTheSameNameWhateverTheirClass() {
        Compilation compilation = javac().withProcessors(new Debugged(context -> List.of(
                        new NamedStep("MarkStep"), new MarkStep(), new NamedStep("Marking"), new NamedStep("Marking"))))
                .withOptions(DEBUG)
                .compile(ANNOTATIONS, MARKED);

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(notes(compilation).stream().filter(note -> note.startsWith("Debugged total, ")))
                .containsExactly(
                        "Debugged total, MarkStep#1: 1 element in 1 round, 0 errors, <time>",
                        "Debugged total, MarkStep#2: 1 element in 1 round, 0 errors, <time>",
                        "Debugged total, Marking#1: 1 element in 1 round, 0 errors, <time>",
                        "Debugged total, Marking#2: 1 element in 1 round, 0 errors, <time>");
    }

    @Test
    void namesAStepByItsClassByDefault() {
        Step anonymous = new Step() {
            @Override
            public Set<String> annotations() {
                return Set.of();
            }

            @Override
            public Set<? extends Element> process(AnnotatedElements elements) {
                return Set.of();
            }
        };

        assertThat(new MarkStep().name()).isEqualTo("MarkStep");
        assertThat(new OtherStep(elements -> Set.of()).name()).isEqualTo("OtherStep");
        assertThat(anonymous.name()).matches("DebugOutputTest\\$\\d+");
    }

    @Test
    void listsAStepThatNeverRanInTheTotalsOnly() {
        Compilation compilation = javac().withProcessors(
                        new Debugged(context -> List.of(new MarkStep(), new GenerateStep(context))))
                .withOptions(DEBUG)
                .compile(ANNOTATIONS, MARKED);

        assertThat(notes(compilation))
                .containsExactly(
                        "Debugged round 1, MarkStep: 1 element [Marked], 0 given back, 0 held back, 0 errors, <time>",
                        "Debugged round 1: 1 element, 0 given back, 0 held back, 0 errors, <time>",
                        "Debugged round 2 (last): 0 elements still deferred, 0 errors, <time>",
                        "Debugged total, MarkStep: 1 element in 1 round, 0 errors, <time>",
                        "Debugged total, GenerateStep: 0 elements in 0 rounds, 0 errors, <time>",
                        "Debugged total: 2 rounds, 1 element, 0 errors, <time>");
    }

    @Test
    void supportsTheOptionWithoutDeclaringIt() {
        Debugged processor = new Debugged(context -> List.of(new MarkStep()));
        Other other = new Other(Set.of(ProcessorOption.ofInt("other.level", 1)));

        javac().withProcessors(processor, other).compile(ANNOTATIONS);

        assertThat(processor.getSupportedOptions()).containsExactly("gaia.debug");
        assertThat(other.getSupportedOptions()).containsExactlyInAnyOrder("gaia.debug", "other.level");
    }

    @Test
    void acceptsTheOptionWithoutAWarningUnderStrictLint() {
        // Without -processing, javac warns that no processor claimed @Mark: a step processor never claims.
        Compilation compilation = javac().withProcessors(new Debugged(context -> List.of(new MarkStep())))
                .withOptions(DEBUG, "-Xlint:all,-processing", "-Werror")
                .compile(ANNOTATIONS, MARKED);

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(notes(compilation)).isNotEmpty();
    }

    @Test
    void warnsAboutAnOptionNoProcessorAccepts() {
        Compilation compilation = javac().withProcessors(new Debugged(context -> List.of(new MarkStep())))
                .withOptions("-Agaia.debugging")
                .compile(ANNOTATIONS, MARKED);

        assertThat(compilation).hadWarningContaining("gaia.debugging");
        assertThat(compilation.diagnostics(Diagnostic.Kind.NOTE)).isEmpty();
    }

    @Test
    void reportsAnInvalidValueAndStaysOff() {
        Compilation compilation = javac().withProcessors(new Debugged(context -> List.of(new MarkStep())))
                .withOptions("-Agaia.debug=yes")
                .compile(ANNOTATIONS, MARKED);

        assertThat(compilation)
                .failed()
                .hadErrorCount(1)
                .hadErrorContaining("Option gaia.debug must be true or false, but is yes");
        assertThat(compilation.diagnostics(Diagnostic.Kind.NOTE)).isEmpty();
    }

    @Test
    void notesEachProcessorOnItsOwn() {
        Compilation compilation = javac().withProcessors(
                        new Debugged(context -> List.of(new MarkStep())), new Other(Set.of()))
                .withOptions(DEBUG, "-Xlint:all,-processing", "-Werror")
                .compile(ANNOTATIONS, MARKED);

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(notes(compilation))
                .containsExactlyInAnyOrder(
                        "Debugged round 1, MarkStep: 1 element [Marked], 0 given back, 0 held back, 0 errors, <time>",
                        "Debugged round 1: 1 element, 0 given back, 0 held back, 0 errors, <time>",
                        "Debugged round 2 (last): 0 elements still deferred, 0 errors, <time>",
                        "Debugged total, MarkStep: 1 element in 1 round, 0 errors, <time>",
                        "Debugged total: 2 rounds, 1 element, 0 errors, <time>",
                        "Other round 1, OtherStep: 1 element [Marked], 0 given back, 0 held back, 0 errors, <time>",
                        "Other round 1: 1 element, 0 given back, 0 held back, 0 errors, <time>",
                        "Other round 2 (last): 0 elements still deferred, 0 errors, <time>",
                        "Other total, OtherStep: 1 element in 1 round, 0 errors, <time>",
                        "Other total: 2 rounds, 1 element, 0 errors, <time>");
    }

    @Test
    void reportsAnInvalidValueOncePerProcessor() {
        Compilation compilation = javac().withProcessors(
                        new Debugged(context -> List.of(new MarkStep())), new Other(Set.of()))
                .withOptions("-Agaia.debug=on")
                .compile(ANNOTATIONS, MARKED);

        assertThat(compilation)
                .failed()
                .hadErrorCount(2)
                .hadErrorContaining("Option gaia.debug must be true or false, but is on");
        assertThat(compilation.diagnostics(Diagnostic.Kind.NOTE)).isEmpty();
    }

    @Test
    void countsTheErrorsAndTheTimeOfProcessingOverWithItsStep() {
        Compilation compilation = javac().withProcessors(
                        new Debugged(context -> List.of(new MarkStep(), new FinishingStep(context))))
                .withOptions(DEBUG)
                .compile(ANNOTATIONS, MARKED);

        assertThat(compilation).failed().hadErrorCount(1).hadErrorContaining("finished with an error");
        assertThat(notes(compilation))
                .containsExactly(
                        "Debugged round 1, MarkStep: 1 element [Marked], 0 given back, 0 held back, 0 errors, <time>",
                        "Debugged round 1, FinishingStep: 1 element [Marked], 0 given back, 0 held back, 0 errors,"
                                + " <time>",
                        "Debugged round 1: 2 elements, 0 given back, 0 held back, 0 errors, <time>",
                        "Debugged round 2, FinishingStep: 0 elements, 0 given back, 0 held back, 1 error, <time>",
                        "Debugged round 2 (last): 0 elements still deferred, 1 error, <time>",
                        "Debugged total, MarkStep: 1 element in 1 round, 0 errors, <time>",
                        "Debugged total, FinishingStep: 1 element in 1 round, 1 error, <time>",
                        "Debugged total: 2 rounds, 2 elements, 1 error, <time>");
    }

    /** A step on {@code @Mark}, returning what a function of its elements returns. */
    private static final class MarkStep implements Step {

        private final Function<AnnotatedElements, Set<? extends Element>> process;

        MarkStep() {
            this(elements -> Set.of());
        }

        MarkStep(Function<AnnotatedElements, Set<? extends Element>> process) {
            this.process = process;
        }

        @Override
        public Set<String> annotations() {
            return Set.of("example.Mark");
        }

        @Override
        public Set<? extends Element> process(AnnotatedElements elements) {
            return process.apply(elements);
        }
    }

    /** Another step on {@code @Mark}, of another class. */
    private static final class OtherStep implements Step {

        private final Function<AnnotatedElements, Set<? extends Element>> process;

        OtherStep(Function<AnnotatedElements, Set<? extends Element>> process) {
            this.process = process;
        }

        @Override
        public Set<String> annotations() {
            return Set.of("example.Mark");
        }

        @Override
        public Set<? extends Element> process(AnnotatedElements elements) {
            return process.apply(elements);
        }
    }

    /** A step on the annotations that {@code @Meta} annotates. */
    private static final class MetaStep implements Step {

        @Override
        public Set<String> annotations() {
            return Set.of();
        }

        @Override
        public Set<String> metaAnnotations() {
            return Set.of("example.Meta");
        }

        @Override
        public Set<? extends Element> process(AnnotatedElements elements) {
            return Set.of();
        }
    }

    /** A step on {@code @Mark} that reports an error when it finishes. */
    private static final class FinishingStep implements Step {

        private final ProcessingContext context;

        FinishingStep(ProcessingContext context) {
            this.context = context;
        }

        @Override
        public Set<String> annotations() {
            return Set.of("example.Mark");
        }

        @Override
        public Set<? extends Element> process(AnnotatedElements elements) {
            return Set.of();
        }

        @Override
        public void processingOver() {
            context.diagnostics().error("finished with an error");
        }
    }

    /** A step on {@code @Mark} with a name of its own. */
    private static final class NamedStep implements Step {

        private final String name;

        NamedStep(String name) {
            this.name = name;
        }

        @Override
        public Set<String> annotations() {
            return Set.of("example.Mark");
        }

        @Override
        public Set<? extends Element> process(AnnotatedElements elements) {
            return Set.of();
        }

        @Override
        public String name() {
            return name;
        }
    }

    /** Generates {@code example.Generated} for an element annotated with {@code @Generate}. */
    private static final class GenerateStep implements Step {

        private final ProcessingContext context;

        GenerateStep(ProcessingContext context) {
            this.context = context;
        }

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
    }

    /** A processor of the steps a function returns. */
    private static final class Debugged extends StepProcessor {

        private final Function<ProcessingContext, List<Step>> steps;

        Debugged(Function<ProcessingContext, List<Step>> steps) {
            this.steps = steps;
        }

        @Override
        protected List<Step> steps(ProcessingContext context) {
            return steps.apply(context);
        }
    }

    /** Another processor, with one {@link OtherStep} and options of its own. */
    private static final class Other extends StepProcessor {

        private final Set<ProcessorOption<?>> options;

        Other(Set<ProcessorOption<?>> options) {
            this.options = options;
        }

        @Override
        protected Set<ProcessorOption<?>> options() {
            return options;
        }

        @Override
        protected List<Step> steps(ProcessingContext context) {
            return List.of(new OtherStep(elements -> Set.of()));
        }
    }
}
