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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Steps that select their elements by the meta-annotations of the annotations they have. */
class MetaAnnotationStepTest {

    private static final String INJECTING = "example.Injecting";

    private static final String INTERCEPTING = "example.Intercepting";

    private static final String NAMED = "example.Named";

    private static final JavaFileObject ANNOTATIONS = source("example.Injecting", """
            package example;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Inherited;
            import java.lang.annotation.Repeatable;
            import java.lang.annotation.Target;

            @Target(ElementType.ANNOTATION_TYPE)
            @interface Injecting {}

            @Target(ElementType.ANNOTATION_TYPE)
            @interface Intercepting {}

            @Injecting
            @interface Named {
                String value() default "";
            }

            @Intercepting
            @interface Logged {}

            @Injecting
            @Intercepting
            @interface Both {}

            @interface Plain {}

            @Plain
            @interface NotMeta {}

            @Injecting
            @Repeatable(Tags.class)
            @interface Tag {
                String value();
            }

            @interface Tags {
                Tag[] value();
            }

            @Inherited
            @Injecting
            @interface Inheritable {}

            @interface Mark {}

            @interface Generate {}
            """);

    private static final JavaFileObject SEED = source("example.Seed", """
            package example;

            @Generate
            class Seed {}
            """);

    private static Compilation compile(Function<ProcessingContext, List<Step>> steps, JavaFileObject... sources) {
        return compile(steps, List.of(), sources);
    }

    private static Compilation compile(
            Function<ProcessingContext, List<Step>> steps, List<String> options, JavaFileObject... sources) {
        List<JavaFileObject> all = new ArrayList<>(List.of(ANNOTATIONS));
        all.addAll(List.of(sources));
        return javac().withProcessors(new Processing(steps))
                .withOptions(options.toArray(String[]::new))
                .compile(all);
    }

    @Test
    void findsTheElementsOfEveryKindAnnotatedWithAMetaAnnotatedAnnotation() {
        Recorder recorder = Recorder.of(Set.of(), Set.of(INJECTING));
        Compilation compilation = compile(
                context -> List.of(recorder), source("example.package-info", """
                @Named
                package example;
                """), source("example.Service", """
                package example;

                @Named
                class Service<@Named T> {
                    @Named String field;

                    @Named
                    Service(@Named String parameter) {}

                    @Named
                    <@Named M> void method() {}

                    @Named
                    record Pair(@Named String component) {}

                    @Named
                    enum Kind {
                        @Named ONE
                    }

                    @Named
                    interface Contract {}

                    @Named
                    @interface Annotation {}
                }
                """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(recorder.kinds)
                .containsExactlyInAnyOrder(
                        "PACKAGE example",
                        "CLASS example.Service",
                        "TYPE_PARAMETER T",
                        "FIELD field",
                        "CONSTRUCTOR Service(java.lang.String)",
                        "PARAMETER parameter",
                        "METHOD <M>method()",
                        "TYPE_PARAMETER M",
                        "RECORD example.Service.Pair",
                        "RECORD_COMPONENT component",
                        "FIELD component",
                        "METHOD component()",
                        "PARAMETER component",
                        "ENUM example.Service.Kind",
                        "ENUM_CONSTANT ONE",
                        "INTERFACE example.Service.Contract",
                        "ANNOTATION_TYPE example.Service.Annotation");
        assertThat(recorder.rounds).singleElement().satisfies(round -> {
            assertThat(round.byAnnotation().keySet()).containsExactly(NAMED);
            assertThat(round.byMetaAnnotation()).containsOnlyKeys(INJECTING);
            assertThat(round.byMetaAnnotation().get(INJECTING))
                    .containsExactlyElementsOf(round.byAnnotation().get(NAMED))
                    .hasSize(17);
        });
    }

    @Test
    void groupsTheElementsByMetaAnnotationAndByTheAnnotationTheyHave() {
        Recorder recorder = Recorder.of(Set.of(), Set.of(INJECTING, INTERCEPTING));
        Compilation compilation = compile(context -> List.of(recorder), source("example.Mixed", """
                package example;

                class Mixed {
                    @Named("a") String injected;

                    @Logged
                    void logged() {}

                    @Both
                    void both() {}

                    @Named("b")
                    @Logged
                    void namedAndLogged() {}

                    @Transitive
                    void transitive() {}

                    @NotMeta
                    void notMeta() {}

                    @Plain
                    void plain() {}

                    void none() {}
                }

                @Named
                @interface Transitive {}
                """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(recorder.rounds)
                .containsExactly(new Round(
                        Map.of(
                                NAMED,
                                List.of("injected", "namedAndLogged()", "example.Transitive"),
                                "example.Logged",
                                List.of("logged()", "namedAndLogged()"),
                                "example.Both",
                                List.of("both()")),
                        Map.of(
                                INJECTING, List.of("injected", "namedAndLogged()", "example.Transitive", "both()"),
                                INTERCEPTING, List.of("logged()", "namedAndLogged()", "both()"))));
    }

    @Test
    void findsAnnotationsWhoseTypesAreReadFromClassFiles(@TempDir Path classOutput) {
        assertThat(javac().withOptions("-d", classOutput.toString())
                        .compile(
                                source("dependency.Injecting", """
                                        package dependency;

                                        public @interface Injecting {}
                                        """),
                                source("dependency.SourceOnly", """
                                        package dependency;

                                        import java.lang.annotation.Retention;
                                        import java.lang.annotation.RetentionPolicy;

                                        @Retention(RetentionPolicy.SOURCE)
                                        public @interface SourceOnly {}
                                        """),
                                source("dependency.Named", """
                                        package dependency;

                                        @Injecting
                                        public @interface Named {}
                                        """),
                                source("dependency.Hidden", """
                                        package dependency;

                                        @SourceOnly
                                        public @interface Hidden {}
                                        """)))
                .succeeded();
        Recorder recorder = Recorder.of(Set.of(), Set.of("dependency.Injecting", "dependency.SourceOnly"));
        Compilation compilation = compile(
                context -> List.of(recorder),
                List.of("-classpath", classOutput.toString()),
                source("example.User", """
                        package example;

                        import dependency.Hidden;
                        import dependency.Named;

                        class User {
                            @Named String injected;

                            @Hidden String hidden;
                        }
                        """));

        assertThat(compilation).succeeded();
        assertThat(recorder.rounds)
                .containsExactly(new Round(
                        Map.of("dependency.Named", List.of("injected")),
                        Map.of("dependency.Injecting", List.of("injected"))));
    }

    @Test
    void findsTheElementsOfAnAnnotationTypeGeneratedWithThem() {
        Recorder recorder = Recorder.of(Set.of(), Set.of(INJECTING));
        Compilation compilation = compile(
                context -> List.of(
                        recorder, new Generating(context, Map.of("example.Late", """
                        package example;

                        @Injecting
                        @interface Late {}
                        """, "example.LateUser", """
                        package example;

                        @Late
                        class LateUser {}
                        """))),
                SEED);

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(recorder.rounds)
                .containsExactly(new Round(
                        Map.of("example.Late", List.of("example.LateUser")),
                        Map.of(INJECTING, List.of("example.LateUser"))));
    }

    @Test
    void neverFindsAnElementWhoseAnnotationTypeIsGeneratedInALaterRound() {
        Recorder recorder = Recorder.of(Set.of(), Set.of(INJECTING));
        Compilation compilation = compile(
                context -> List.of(recorder, new Generating(context, Map.of("example.Late", """
                        package example;

                        @Injecting
                        @interface Late {}
                        """))),
                SEED,
                source("example.Early", """
                        package example;

                        @Late
                        class Early {}
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(recorder.rounds).isEmpty();
    }

    @Test
    void holdsBackAnElementUntilTheTypesOfItsSignatureAreGenerated() {
        Recorder recorder = Recorder.of(Set.of(), Set.of(INJECTING));
        Compilation compilation = compile(
                context -> List.of(recorder, new Generating(context, Map.of("example.Generated", """
                        package example;

                        class Generated {}
                        """))),
                SEED,
                source("example.Uses", """
                        package example;

                        @Named
                        class Uses {
                            Generated generated;
                        }
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(recorder.rounds)
                .containsExactly(
                        new Round(Map.of(NAMED, List.of("example.Uses")), Map.of(INJECTING, List.of("example.Uses"))));
    }

    @Test
    void givesAnElementGivenBackAgainWithTheSameAnnotations() {
        Recorder once = new Recorder(Set.of(), Set.of(INJECTING), new Function<>() {
            private boolean called;

            @Override
            public Set<Element> apply(AnnotatedElements elements) {
                Set<Element> givenBack = called ? Set.of() : elements.all();
                called = true;
                return givenBack;
            }
        });
        Compilation compilation = compile(
                context -> List.of(once, new Generating(context, Map.of("example.Generated", """
                        package example;

                        class Generated {}
                        """))),
                SEED,
                source("example.Uses", """
                        package example;

                        @Named
                        @Logged
                        class Uses {}
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        Round expected = new Round(Map.of(NAMED, List.of("example.Uses")), Map.of(INJECTING, List.of("example.Uses")));
        assertThat(once.rounds).containsExactly(expected, expected);
    }

    @Test
    void reportsAnElementWhoseTypesNeverResolve() {
        Recorder recorder = Recorder.of(Set.of(), Set.of(INJECTING));
        Compilation compilation = compile(context -> List.of(recorder), source("example.Broken", """
                package example;

                @Named
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
    void mixesAnnotationsAndMetaAnnotationsInOneStep() {
        Recorder recorder = Recorder.of(Set.of("example.Mark", NAMED), Set.of(INJECTING));
        Compilation compilation = compile(context -> List.of(recorder), source("example.Marked", """
                package example;

                @Mark
                class Marked {}

                @Named
                class NamedOnly {}

                @Mark
                @Both
                class MarkedAndBoth {}
                """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(recorder.rounds)
                .containsExactly(new Round(
                        Map.of(
                                "example.Mark",
                                List.of("example.Marked", "example.MarkedAndBoth"),
                                NAMED,
                                List.of("example.NamedOnly"),
                                "example.Both",
                                List.of("example.MarkedAndBoth")),
                        Map.of(INJECTING, List.of("example.NamedOnly", "example.MarkedAndBoth"))));
    }

    @Test
    void givesEachStepTheElementsOfItsOwnAnnotationsAndMetaAnnotations() {
        Recorder injecting = Recorder.of(Set.of(), Set.of(INJECTING));
        Recorder marking = Recorder.of(Set.of("example.Mark"), Set.of());
        Recorder intercepting = Recorder.of(Set.of(NAMED), Set.of(INTERCEPTING));
        Compilation compilation =
                compile(context -> List.of(injecting, marking, intercepting), source("example.Types", """
                package example;

                @Named
                class N {}

                @Logged
                class L {}

                @Mark
                class M {}

                @Both
                class B {}
                """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(injecting.rounds)
                .containsExactly(new Round(
                        Map.of(NAMED, List.of("example.N"), "example.Both", List.of("example.B")),
                        Map.of(INJECTING, List.of("example.N", "example.B"))));
        assertThat(marking.rounds).containsExactly(new Round(Map.of("example.Mark", List.of("example.M")), Map.of()));
        assertThat(intercepting.rounds)
                .containsExactly(new Round(
                        Map.of(
                                NAMED,
                                List.of("example.N"),
                                "example.Logged",
                                List.of("example.L"),
                                "example.Both",
                                List.of("example.B")),
                        Map.of(INTERCEPTING, List.of("example.L", "example.B"))));
    }

    @Test
    void findsTheAnnotationsTheContainerOfARepeatableAnnotationWraps() {
        Recorder recorder = Recorder.of(Set.of(), Set.of(INJECTING));
        Compilation compilation = compile(context -> List.of(recorder), source("example.Tagged", """
                package example;

                @Tag("a")
                @Tag("b")
                class Tagged {}

                @Tags(@Tag("c"))
                class Explicit {}

                @Tag("d")
                class Once {}
                """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(recorder.rounds)
                .containsExactly(new Round(
                        Map.of("example.Tag", List.of("example.Tagged", "example.Explicit", "example.Once")),
                        Map.of(INJECTING, List.of("example.Tagged", "example.Explicit", "example.Once"))));
    }

    @Test
    void findsTheClassesThatInheritAMetaAnnotatedAnnotation() {
        Recorder recorder = Recorder.of(Set.of(), Set.of(INJECTING));
        Compilation compilation = compile(context -> List.of(recorder), source("example.Base", """
                package example;

                @Inheritable
                class Base {}

                class Sub extends Base {}

                class Unrelated {}
                """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(recorder.rounds)
                .containsExactly(new Round(
                        Map.of("example.Inheritable", List.of("example.Base", "example.Sub")),
                        Map.of(INJECTING, List.of("example.Base", "example.Sub"))));
    }

    @Test
    void supportsAllAnnotationsOnlyWhenAStepHasMetaAnnotations() {
        Processing withMeta = new Processing(context ->
                List.of(Recorder.of(Set.of("example.Mark"), Set.of()), Recorder.of(Set.of(), Set.of(INJECTING))));
        Processing withoutMeta = new Processing(context ->
                List.of(Recorder.of(Set.of("example.Mark"), Set.of()), Recorder.of(Set.of(NAMED), Set.of())));

        javac().withProcessors(withMeta, withoutMeta).compile(ANNOTATIONS);

        assertThat(withMeta.getSupportedAnnotationTypes()).containsExactly("*");
        assertThat(withoutMeta.getSupportedAnnotationTypes()).containsExactlyInAnyOrder("example.Mark", NAMED);
    }

    @Test
    void claimsNoAnnotationForOtherProcessors() {
        Recorder recorder = Recorder.of(Set.of("example.Mark"), Set.of(INJECTING));
        Claiming after = new Claiming();
        Compilation compilation = javac().withProcessors(new Processing(context -> List.of(recorder)), after)
                .compile(ANNOTATIONS, source("example.Annotated", """
                        package example;

                        @Named
                        @Mark
                        class Annotated {
                            @Logged
                            void logged() {}
                        }
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(recorder.rounds).hasSize(1);
        assertThat(after.annotations).contains("example.Mark", NAMED, "example.Logged", "example.Injecting");
    }

    /** The elements a step got in a round, by the names of their annotations and of its meta-annotations. */
    private record Round(Map<String, List<String>> byAnnotation, Map<String, List<String>> byMetaAnnotation) {}

    /** A step recording, each round, the elements it gets, and giving back what a function of them returns. */
    private static final class Recorder implements Step {

        private final List<Round> rounds = new ArrayList<>();
        private final List<String> kinds = new ArrayList<>();
        private final Set<String> annotations;
        private final Set<String> metaAnnotations;
        private final Function<AnnotatedElements, Set<Element>> givenBack;

        Recorder(
                Set<String> annotations,
                Set<String> metaAnnotations,
                Function<AnnotatedElements, Set<Element>> givenBack) {
            this.annotations = annotations;
            this.metaAnnotations = metaAnnotations;
            this.givenBack = givenBack;
        }

        static Recorder of(Set<String> annotations, Set<String> metaAnnotations) {
            return new Recorder(annotations, metaAnnotations, elements -> Set.of());
        }

        private static List<String> names(Set<Element> elements) {
            return elements.stream().map(Object::toString).toList();
        }

        @Override
        public Set<String> annotations() {
            return annotations;
        }

        @Override
        public Set<String> metaAnnotations() {
            return metaAnnotations;
        }

        @Override
        public Set<? extends Element> process(AnnotatedElements elements) {
            Map<String, List<String>> byAnnotation = new LinkedHashMap<>();
            elements.annotations().forEach(name -> byAnnotation.put(name, names(elements.annotatedWith(name))));
            Map<String, List<String>> byMetaAnnotation = new LinkedHashMap<>();
            for (String meta : metaAnnotations) {
                Set<Element> found = elements.withAnnotationsAnnotatedWith(meta);
                if (!found.isEmpty()) {
                    byMetaAnnotation.put(meta, names(found));
                }
            }
            rounds.add(new Round(byAnnotation, byMetaAnnotation));
            elements.all().forEach(element -> kinds.add(element.getKind() + " " + element));
            return givenBack.apply(elements);
        }
    }

    /** Generates source files, given as text by qualified name, for the elements annotated with {@code @Generate}. */
    private static final class Generating implements Step {

        private final ProcessingContext context;
        private final Map<String, String> files;

        Generating(ProcessingContext context, Map<String, String> files) {
            this.context = context;
            this.files = files;
        }

        @Override
        public Set<String> annotations() {
            return Set.of("example.Generate");
        }

        @Override
        public Set<? extends Element> process(AnnotatedElements elements) {
            files.forEach((name, text) -> {
                try (Writer writer = context.filer().createSourceFile(name).openWriter()) {
                    writer.write(text);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            return Set.of();
        }
    }

    /** A processor of the steps a function returns. */
    private static final class Processing extends StepProcessor {

        private final Function<ProcessingContext, List<Step>> steps;

        Processing(Function<ProcessingContext, List<Step>> steps) {
            this.steps = steps;
        }

        @Override
        protected List<Step> steps(ProcessingContext context) {
            return steps.apply(context);
        }
    }

    /** Records the annotations javac gives it, which it would not get if a processor before it claimed them. */
    @SupportedAnnotationTypes("*")
    private static final class Claiming extends AbstractProcessor {

        private final Set<String> annotations = new LinkedHashSet<>();

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> present, RoundEnvironment roundEnv) {
            present.forEach(
                    annotation -> annotations.add(annotation.getQualifiedName().toString()));
            return false;
        }
    }
}
