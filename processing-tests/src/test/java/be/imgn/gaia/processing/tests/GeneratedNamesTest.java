package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.StepProcessor;
import be.imgn.gaia.processing.source.ClassName;
import be.imgn.gaia.processing.source.JavaFile;
import be.imgn.gaia.processing.source.Method;
import be.imgn.gaia.processing.source.Type;
import be.imgn.gaia.processing.source.TypeName;
import be.imgn.gaia.processing.testing.Compilation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The names of generated types: {@code ClassName.generatedName} and {@code GeneratedSources.isAvailable}. */
class GeneratedNamesTest {

    private static final String GEN_NAME = "example.Gen";

    private static final JavaFileObject GEN = source("example.Gen", """
            package example;

            public @interface Gen {}
            """);

    private static final JavaFileObject DESCRIBED = source("example.Described", """
            package example;

            public interface Described {
                String describe();
            }
            """);

    private static Compilation generate(JavaFileObject... sources) {
        List<JavaFileObject> all = new ArrayList<>(List.of(GEN, DESCRIBED));
        all.addAll(List.of(sources));
        return javac().withProcessors(new Describing())
                .withOptions("-Xlint:all,-processing", "-Werror")
                .compile(all);
    }

    private static Compilation check(BiConsumer<ProcessingContext, RoundEnvironment> check, JavaFileObject... sources) {
        List<JavaFileObject> all = new ArrayList<>(List.of(GEN));
        all.addAll(List.of(sources));
        return javac().withProcessors(new Checking(check)).compile(all);
    }

    @Test
    void generatesCompilingTypesUnderTheirGeneratedNames() {
        Compilation compilation = generate(
                source("example.Outer", """
                        package example;

                        @Gen
                        public class Outer {
                            @Gen
                            public static class Inner {
                                @Gen
                                public interface Deep {
                                    @Gen
                                    enum Deeper {}
                                }
                            }
                        }
                        """),
                source("example.Dollar$Case", """
                        package example;

                        public class Dollar$Case {
                            @Gen
                            public record In$Side() {}
                        }
                        """),
                source("example.other.Uses", """
                        package example.other;

                        import example.Described;

                        class Uses {
                            Described[] all = {
                                new example.$Outer$$Described(),
                                new example.$Outer$Inner$$Described(),
                                new example.$Outer$Inner$Deep$$Described(),
                                new example.$Outer$Inner$Deep$Deeper$$Described(),
                                new example.$Dollar$0Case$In$0Side$$Described(),
                            };
                        }
                        """),
                source("Default", """
                        @example.Gen
                        public class Default {
                            example.Described described = new $Default$$Described();
                        }
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("example.$Outer$Inner$Deep$Deeper$$Described")
                .isEqualTo("""
                package example;

                import javax.annotation.processing.Generated;

                @Generated("%s")
                public final class $Outer$Inner$Deep$Deeper$$Described implements Described {
                    @Override
                    public String describe() {
                        return "example.Outer.Inner.Deep.Deeper";
                    }
                }
                """.formatted(Describing.class.getName()));
        assertThat(compilation).generatedSourceFile("example.$Outer$$Described").contains("\"example.Outer\"");
        assertThat(compilation)
                .generatedSourceFile("example.$Outer$Inner$$Described")
                .isNotEmpty();
        assertThat(compilation)
                .generatedSourceFile("example.$Outer$Inner$Deep$$Described")
                .isNotEmpty();
        assertThat(compilation)
                .generatedSourceFile("example.$Dollar$0Case$In$0Side$$Described")
                .contains("\"example.Dollar$Case.In$Side\"");
        assertThat(compilation)
                .generatedSourceFile("$Default$$Described")
                .doesNotContain("package")
                .contains("\"Default\"");
    }

    @Test
    void reportsATypeTheUserWroteUnderTheGeneratedName() {
        Compilation compilation =
                generate(source("example.Outer", """
                        package example;

                        public class Outer {
                            @Gen
                            public static class Inner {}
                        }
                        """), source("example.$Outer$Inner$$Described", """
                        package example;

                        public class $Outer$Inner$$Described {}
                        """));

        assertThat(compilation).failed().hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("Cannot generate example.$Outer$Inner$$Described for example.Outer.Inner: "
                        + "a type with that name already exists")
                .inFile("example/Outer.java")
                .onLine(5);
        assertThat(compilation.generatedSourceFiles()).isEmpty();
    }

    @Test
    void reportsATypeTheUserWroteUnderTheGeneratedNameInANamedModule() {
        Compilation compilation = generate(
                source("module-info", """
                        module example {
                            requires static java.compiler;
                        }
                        """), source("example.Outer", """
                        package example;

                        @Gen
                        public class Outer {}
                        """), source("example.$Outer$$Described", """
                        package example;

                        public class $Outer$$Described {}
                        """));

        assertThat(compilation).failed().hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("Cannot generate example.$Outer$$Described for example.Outer")
                .inFile("example/Outer.java");
    }

    @Test
    void findsNoTypeUnderAFreshName() {
        List<Boolean> available = new ArrayList<>();
        Compilation compilation = check(
                (context, round) -> {
                    if (!round.processingOver()) {
                        available.add(context.sources().isAvailable(ClassName.of("example", "Seed_Mapper")));
                        available.add(context.sources().isAvailable(ClassName.of("", "Seed_Mapper")));
                    }
                },
                source("example.Seed", """
                        package example;

                        @Gen
                        class Seed {}
                        """));

        assertThat(compilation).succeeded();
        assertThat(available).containsExactly(true, true);
    }

    @Test
    void findsTypesOfTheSourcesTheClassPathAndThePlatform() {
        List<Boolean> available = new ArrayList<>();
        Compilation compilation = check(
                (context, round) -> {
                    if (!round.processingOver()) {
                        available.add(context.sources().isAvailable(ClassName.of("example", "Seed")));
                        available.add(context.sources().isAvailable(ClassName.of("example", "Seed", "Nested")));
                        available.add(context.sources().isAvailable(ClassName.of("", "Default")));
                        available.add(context.sources().isAvailable(ClassName.of(List.class)));
                        available.add(context.sources().isAvailable(ClassName.of(Map.Entry.class)));
                        available.add(context.sources().isAvailable(ClassName.of(ClassName.class)));
                    }
                },
                source("example.Seed", """
                        package example;

                        @Gen
                        class Seed {
                            static class Nested {}
                        }
                        """),
                source("Default", "class Default {}"));

        assertThat(compilation).succeeded();
        assertThat(available).containsExactly(false, false, false, false, false, false);
    }

    @Test
    void findsATypeWrittenInTheSameRound() {
        ClassName name = ClassName.of("example", "Seed_Mapper");
        List<Boolean> available = new ArrayList<>();
        Compilation compilation = check(
                (context, round) -> {
                    if (annotates(context, round)) {
                        available.add(context.sources().isAvailable(name));
                        context.sources().write("example.Seed_Mapper", scope -> "class Seed_Mapper {}\n");
                        available.add(context.sources().isAvailable(name));
                        available.add(context.sources().isAvailable(ClassName.of("example", "Seed_Other")));
                    }
                },
                source("example.Seed", """
                        package example;

                        @Gen
                        class Seed {}
                        """));

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("example.Seed_Mapper").isNotEmpty();
        assertThat(available).containsExactly(true, false, true);
    }

    @Test
    void findsATypeWrittenInAnEarlierRoundByAnyProcessor() {
        ClassName name = ClassName.of("example", "Seed_Mapper");
        List<String> rounds = new ArrayList<>();
        Checking checking = new Checking((context, round) -> {
            ProcessingContext fresh = ProcessingContext.of(context.environment(), Checking.class);
            if (annotates(context, round)) {
                context.sources().write("example.Seed_Mapper", scope -> "class Seed_Mapper {}\n");
            } else {
                rounds.add("same context: %s, other context: %s, processing over: %s"
                        .formatted(
                                context.sources().isAvailable(name),
                                fresh.sources().isAvailable(name),
                                round.processingOver()));
            }
        });
        Compilation compilation = javac().withProcessors(checking).compile(GEN, source("example.Seed", """
                package example;

                @Gen
                class Seed {}
                """));

        assertThat(compilation).succeeded();
        assertThat(rounds)
                .containsExactly(
                        "same context: false, other context: false, processing over: false",
                        "same context: false, other context: false, processing over: true");
    }

    @Test
    void findsATypeAnotherProcessorWroteInAnEarlierRound() {
        List<Boolean> available = new ArrayList<>();
        Checking checking = new Checking((context, round) -> {
            if (!annotates(context, round) && !round.processingOver()) {
                available.add(context.sources().isAvailable(ClassName.of("example", "$Seed$$Described")));
            }
        });
        Compilation compilation =
                javac().withProcessors(new Describing(), checking).compile(GEN, DESCRIBED, source("example.Seed", """
                        package example;

                        @Gen
                        class Seed {}
                        """));

        assertThat(compilation).succeeded();
        assertThat(available).containsExactly(false);
    }

    @Test
    void reportsATypeAnotherProcessorGeneratedInAnEarlierRoundUnderTheGeneratedName() {
        Checking generating = new Checking((context, round) -> {
            if (annotates(context, round)) {
                context.sources().write("example.Late", scope -> "@Gen\npublic class Late {}\n");
                context.sources().write("example.$Late$$Described", scope -> "public class $Late$$Described {}\n");
            }
        });
        Compilation compilation = javac().withProcessors(generating, new Describing())
                .compile(GEN, DESCRIBED, source("example.Seed", """
                        package example;

                        @Gen
                        class Seed {}
                        """));

        assertThat(compilation).failed().hadErrorCount(1);
        assertThat(compilation)
                .hadErrorContaining("Cannot generate example.$Late$$Described for example.Late: "
                        + "a type with that name already exists")
                .inFile("example/Late.java");
        assertThat(compilation).generatedSourceFile("example.$Seed$$Described").isNotEmpty();
    }

    @Test
    void leavesAFileAnotherProcessorWritesInTheSameRoundToTheFiler() {
        Compilation compilation = javac().withProcessors(new Describing(), new Describing() {})
                .compile(GEN, DESCRIBED, source("example.Seed", """
                        package example;

                        @Gen
                        class Seed {}
                        """));

        assertThat(compilation).succeeded();
        assertThat(compilation.generatedSourceFiles()).hasSize(1);
    }

    @Test
    void namesTypesForMethodsAfterTheirTypeAndThemNumberingOverloads() {
        List<String> names = new ArrayList<>();
        Compilation compilation = check(
                (context, round) -> {
                    if (annotates(context, round)) {
                        TypeElement inner = requireNonNull(context.elements().getTypeElement("example.Host.Inner"));
                        for (ExecutableElement method : ElementFilter.methodsIn(inner.getEnclosedElements())) {
                            names.add(ClassName.generatedName(method, "Factory").canonicalName());
                        }
                        for (ExecutableElement constructor :
                                ElementFilter.constructorsIn(inner.getEnclosedElements())) {
                            try {
                                ClassName.generatedName(constructor, "Factory");
                            } catch (IllegalArgumentException e) {
                                names.add(e.getMessage());
                            }
                        }
                    }
                },
                source("example.Host", """
                        package example;

                        @Gen
                        class Host {
                            static class Inner {
                                Inner() {}

                                void create() {}

                                void other() {}

                                void create(int count) {}

                                static void create(String name) {}
                            }
                        }
                        """));

        assertThat(compilation).succeeded();
        assertThat(names)
                .containsExactly(
                        "example.$Host$Inner$$create$1$$Factory",
                        "example.$Host$Inner$$other$$Factory",
                        "example.$Host$Inner$$create$2$$Factory",
                        "example.$Host$Inner$$create$3$$Factory",
                        "Not a method: Inner()");
    }

    // Whether the round has types annotated with @Gen: only the first round has, unless a generated type is annotated.
    private static boolean annotates(ProcessingContext context, RoundEnvironment round) {
        return !round.getElementsAnnotatedWith(context.elements().getTypeElement(GEN_NAME))
                .isEmpty();
    }

    /**
     * Generates, for each type annotated with {@code @Gen}, an implementation of {@code example.Described} named after
     * it, reporting an error on the type if a type has that name already.
     */
    private static class Describing extends StepProcessor {

        @Override
        protected List<Step> steps(ProcessingContext context) {
            return List.of(new Step() {
                @Override
                public Set<String> annotations() {
                    return Set.of(GEN_NAME);
                }

                @Override
                public Set<? extends Element> process(AnnotatedElements elements) {
                    for (Element element : elements.annotatedWith(GEN_NAME)) {
                        TypeElement type = (TypeElement) element;
                        ClassName source = ClassName.of(type);
                        ClassName name = source.generatedName("Described");
                        if (!context.sources().isAvailable(name)) {
                            context.diagnostics()
                                    .on(type)
                                    .error("Cannot generate " + name + " for " + source
                                            + ": a type with that name already exists");
                            continue;
                        }
                        Type described = Type.newClassBuilder(name.simpleName())
                                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                                .addSuperinterface(ClassName.of("example", "Described"))
                                .addMethod(Method.newBuilder("describe")
                                        .addAnnotation(Override.class)
                                        .addModifiers(Modifier.PUBLIC)
                                        .returns(TypeName.of(String.class))
                                        .addStatement("return $S", source.canonicalName())
                                        .build())
                                .build();
                        context.sources()
                                .write(
                                        JavaFile.newBuilder(name.packageName(), described)
                                                .build(),
                                        type);
                    }
                    return Set.of();
                }
            });
        }
    }

    /** Runs a check every round, including the last one, with one context for all rounds. */
    private static final class Checking extends AbstractProcessor {

        private final BiConsumer<ProcessingContext, RoundEnvironment> check;
        private @Nullable ProcessingContext context;

        Checking(BiConsumer<ProcessingContext, RoundEnvironment> check) {
            this.check = check;
        }

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
            ProcessingContext current = context;
            if (current == null) {
                current = ProcessingContext.of(processingEnv, Checking.class);
                context = current;
            }
            check.accept(current, round);
            return false;
        }
    }
}
