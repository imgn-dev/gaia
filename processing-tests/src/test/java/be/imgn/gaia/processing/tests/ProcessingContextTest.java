package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.ProcessorOption;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.StepProcessor;
import be.imgn.gaia.processing.testing.Compilation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

class ProcessingContextTest {

    enum Mode {
        SAFE,
        FAST
    }

    private static final ProcessorOption<Boolean> VERBOSE = ProcessorOption.ofBoolean("test.verbose", false);
    private static final ProcessorOption<Integer> COUNT = ProcessorOption.ofInt("test.count", 1);
    private static final ProcessorOption<Mode> MODE = ProcessorOption.ofEnum("test.mode", Mode.class, Mode.SAFE);
    private static final ProcessorOption<String> PREFIX = ProcessorOption.ofString("test.prefix", "none");

    private static final JavaFileObject SEED = source("example.Seed", """
            package example;

            @Deprecated
            class Seed {}
            """);

    private static List<Object> run(Function<ProcessingContext, List<Object>> reads, String... options) {
        Probe probe = new Probe(reads);
        assertThat(javac().withProcessors(probe).withOptions(options).compile(SEED))
                .succeeded();
        return probe.results;
    }

    @Test
    void readsTypedOptions() {
        Probe probe = new Probe(context ->
                List.of(context.option(VERBOSE), context.option(COUNT), context.option(MODE), context.option(PREFIX)));

        Compilation compilation = javac().withProcessors(probe)
                .withOptions("-Atest.verbose", "-Atest.count=3", "-Atest.mode=fast")
                .compile(SEED);

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(probe.results).containsExactly(true, 3, Mode.FAST, "none");
    }

    @Test
    void usesTheDefaultsOfOptionsNotGiven() {
        assertThat(run(context -> List.of(context.option(VERBOSE), context.option(COUNT), context.option(MODE))))
                .containsExactly(false, 1, Mode.SAFE);
    }

    @Test
    void reportsAnInvalidOptionAndUsesItsDefault() {
        Probe probe = new Probe(context -> List.of(context.option(COUNT), context.option(MODE)));

        Compilation compilation = javac().withProcessors(probe)
                .withOptions("-Atest.count=many", "-Atest.mode=slow")
                .compile(SEED);

        assertThat(compilation).failed().hadErrorCount(2);
        assertThat(compilation).hadErrorContaining("Option test.count must be an integer, but is many");
        assertThat(compilation).hadErrorContaining("Option test.mode must be one of safe, fast, but is slow");
        assertThat(probe.results).containsExactly(1, Mode.SAFE);
    }

    @Test
    void checksSubtypesAgainstClassesAndNames() {
        assertThat(run(context -> {
                    TypeMirror arrayList = context.types()
                            .getDeclaredType(
                                    context.elements().getTypeElement("java.util.ArrayList"),
                                    context.elements()
                                            .getTypeElement("java.lang.String")
                                            .asType());
                    TypeMirror string = context.elements()
                            .getTypeElement("java.lang.String")
                            .asType();
                    return List.of(
                            context.isSubtype(arrayList, List.class),
                            context.isSubtype(arrayList, "java.util.Collection"),
                            context.isSubtype(string, CharSequence.class),
                            context.isSubtype(string, Integer.class),
                            context.isSubtype(string, "does.not.Exist"),
                            context.isSubtype(context.types().getPrimitiveType(TypeKind.INT), int.class));
                }))
                .containsExactly(true, true, true, false, false, true);
    }

    /** Runs reads on its context in the first round, and accepts the test options. */
    private static final class Probe extends StepProcessor {

        private final Function<ProcessingContext, List<Object>> reads;
        private final List<Object> results = new ArrayList<>();

        Probe(Function<ProcessingContext, List<Object>> reads) {
            this.reads = reads;
        }

        @Override
        protected Set<ProcessorOption<?>> options() {
            return Set.of(VERBOSE, COUNT, MODE, PREFIX);
        }

        @Override
        protected List<Step> steps(ProcessingContext context) {
            return List.of(new Step() {
                @Override
                public Set<String> annotations() {
                    return Set.of("java.lang.Deprecated");
                }

                @Override
                public Set<? extends Element> process(AnnotatedElements elements) {
                    results.addAll(reads.apply(context));
                    return Set.of();
                }
            });
        }
    }
}
