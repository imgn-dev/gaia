package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.StepProcessor;
import be.imgn.gaia.processing.testing.Compilation;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FromSourceTest {

    private static final JavaFileObject OUTER = source("example.Outer", """
            package example;

            public class Outer<T> {
                String field;

                <M> void method(int parameter) {}

                public static class Nested {
                    enum Choice { ONE }
                }

                class Inner {}

                record Point(int x) {}
            }
            """);

    private static final JavaFileObject PACKAGE_INFO = source("example.package-info", """
            package example;
            """);

    private static final JavaFileObject UNNAMED = source("Unnamed", """
            class Unnamed {
                static class Nested {}
            }
            """);

    private static final JavaFileObject PLAIN = source("other.Plain", """
            package other;

            class Plain {}
            """);

    private static TypeElement type(ProcessingContext context, String canonicalName) {
        return requireNonNull(context.elements().getTypeElement(canonicalName), canonicalName);
    }

    // Compiles sources into a directory, as an earlier build or a library would have been.
    private static void compileInto(Path directory, JavaFileObject... sources) {
        assertThat(javac().withOptions("-d", directory.toString()).compile(sources))
                .succeeded();
    }

    @Test
    void isTrueForTheSourcesOfTheCompilationAndWhatTheyDeclare() {
        Map<String, Boolean> results = new LinkedHashMap<>();

        Compilation compilation = FirstRound.run(List.of(OUTER, PACKAGE_INFO, UNNAMED, PLAIN), List.of(), context -> {
            TypeElement outer = type(context, "example.Outer");
            ExecutableElement method =
                    ElementFilter.methodsIn(outer.getEnclosedElements()).getFirst();
            VariableElement field =
                    ElementFilter.fieldsIn(outer.getEnclosedElements()).getFirst();
            TypeElement point = type(context, "example.Outer.Point");
            Map<String, Element> elements = new LinkedHashMap<>();
            elements.put("Outer", outer);
            elements.put("Outer.Nested", type(context, "example.Outer.Nested"));
            elements.put("Outer.Nested.Choice", type(context, "example.Outer.Nested.Choice"));
            elements.put(
                    "Outer.Nested.Choice.ONE",
                    type(context, "example.Outer.Nested.Choice")
                            .getEnclosedElements()
                            .getFirst());
            elements.put("Outer.Inner", type(context, "example.Outer.Inner"));
            elements.put("Outer.Point", point);
            elements.put("Outer.Point.x", point.getRecordComponents().getFirst());
            elements.put("Outer.field", field);
            elements.put("Outer.method", method);
            elements.put("Outer.method.parameter", method.getParameters().getFirst());
            elements.put("Outer.method.M", method.getTypeParameters().getFirst());
            elements.put("Outer.T", outer.getTypeParameters().getFirst());
            elements.put("Unnamed", type(context, "Unnamed"));
            elements.put("Unnamed.Nested", type(context, "Unnamed.Nested"));
            elements.put("other.Plain", type(context, "other.Plain"));
            elements.put("package example", context.elements().getPackageElement("example"));
            elements.put("package other", context.elements().getPackageElement("other"));
            elements.put("unnamed module", context.elements().getModuleOf(outer));
            elements.forEach((name, element) -> results.put(name, context.isFromSource(element)));
        });

        assertThat(compilation).succeeded();
        assertThat(results)
                .containsExactly(
                        entry("Outer", true),
                        entry("Outer.Nested", true),
                        entry("Outer.Nested.Choice", true),
                        entry("Outer.Nested.Choice.ONE", true),
                        entry("Outer.Inner", true),
                        entry("Outer.Point", true),
                        entry("Outer.Point.x", true),
                        entry("Outer.field", true),
                        entry("Outer.method", true),
                        entry("Outer.method.parameter", true),
                        entry("Outer.method.M", true),
                        entry("Outer.T", true),
                        entry("Unnamed", true),
                        entry("Unnamed.Nested", true),
                        entry("other.Plain", true),
                        entry("package example", true),
                        entry("package other", false),
                        entry("unnamed module", false));
    }

    @Test
    void isFalseForThePlatform() {
        Map<String, Boolean> results = new LinkedHashMap<>();

        Compilation compilation = FirstRound.run(List.of(PLAIN), List.of(), context -> {
            TypeElement string = type(context, "java.lang.String");
            results.put("String", context.isFromSource(string));
            results.put("Map.Entry", context.isFromSource(type(context, "java.util.Map.Entry")));
            results.put(
                    "String.length()",
                    context.isFromSource(ElementFilter.methodsIn(string.getEnclosedElements()).stream()
                            .filter(method -> method.getSimpleName().contentEquals("length"))
                            .findFirst()
                            .orElseThrow()));
            results.put(
                    "package java.lang", context.isFromSource(context.elements().getPackageOf(string)));
            results.put(
                    "module java.base", context.isFromSource(context.elements().getModuleOf(string)));
        });

        assertThat(compilation).succeeded();
        assertThat(results)
                .containsExactly(
                        entry("String", false),
                        entry("Map.Entry", false),
                        entry("String.length()", false),
                        entry("package java.lang", false),
                        entry("module java.base", false));
    }

    @Test
    void isFalseForTheClassesOfADirectoryOrAJarOnTheClassPath(@TempDir Path directory) throws IOException {
        Path classes = directory.resolve("classes");
        compileInto(
                classes,
                source("dependency.Compiled", """
                        package dependency;

                        public class Compiled {
                            public static class Nested {}

                            public void method() {}
                        }
                        """),
                source("dependency.package-info", """
                        package dependency;
                        """),
                source("Loose", """
                        public class Loose {}
                        """));
        Path jar = directory.resolve("library.jar");
        Path jarClasses = directory.resolve("jar-classes");
        compileInto(jarClasses, source("jarred.Packed", """
                package jarred;

                public class Packed {}
                """));
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream out = new JarOutputStream(file);
                InputStream in = Files.newInputStream(jarClasses.resolve("jarred/Packed.class"))) {
            out.putNextEntry(new JarEntry("jarred/Packed.class"));
            in.transferTo(out);
            out.closeEntry();
        }
        Map<String, Boolean> results = new LinkedHashMap<>();

        Compilation compilation =
                FirstRound.run(List.of(PLAIN), List.of("-classpath", classes + File.pathSeparator + jar), context -> {
                    TypeElement compiled = type(context, "dependency.Compiled");
                    results.put("Compiled", context.isFromSource(compiled));
                    results.put("Compiled.Nested", context.isFromSource(type(context, "dependency.Compiled.Nested")));
                    results.put(
                            "Compiled.method",
                            context.isFromSource(ElementFilter.methodsIn(compiled.getEnclosedElements())
                                    .getFirst()));
                    results.put(
                            "package dependency",
                            context.isFromSource(context.elements().getPackageOf(compiled)));
                    results.put("Loose", context.isFromSource(type(context, "Loose")));
                    results.put("Packed", context.isFromSource(type(context, "jarred.Packed")));
                    results.put("Plain", context.isFromSource(type(context, "other.Plain")));
                });

        assertThat(compilation).succeeded();
        assertThat(results)
                .containsExactly(
                        entry("Compiled", false),
                        entry("Compiled.Nested", false),
                        entry("Compiled.method", false),
                        entry("package dependency", false),
                        entry("Loose", false),
                        entry("Packed", false),
                        entry("Plain", true));
    }

    @Test
    void isFalseForTheTypesOfAModuleOnTheModulePath(@TempDir Path library) {
        compileInto(library, source("module-info", """
                        module library {
                            exports library;
                        }
                        """), source("library.Shared", """
                        package library;

                        public class Shared {
                            public static class Nested {}
                        }
                        """));
        String modulePath = library
                + File.pathSeparator
                + requireNonNull(System.getProperty("jdk.module.path"), "tests run on the module path");
        Map<String, Boolean> results = new LinkedHashMap<>();

        Compilation compilation = FirstRound.run(
                List.of(source("module-info", """
                                module example {
                                    requires library;
                                }
                                """), source("example.Uses", """
                                package example;

                                public class Uses {
                                    library.Shared shared;
                                }
                                """)),
                List.of("--module-path", modulePath),
                context -> {
                    TypeElement shared = type(context, "library.Shared");
                    TypeElement uses = type(context, "example.Uses");
                    results.put("Shared", context.isFromSource(shared));
                    results.put("Shared.Nested", context.isFromSource(type(context, "library.Shared.Nested")));
                    results.put(
                            "module library",
                            context.isFromSource(context.elements().getModuleOf(shared)));
                    results.put("Uses", context.isFromSource(uses));
                    results.put(
                            "module example",
                            context.isFromSource(context.elements().getModuleOf(uses)));
                });

        assertThat(compilation).succeeded();
        assertThat(results)
                .containsExactly(
                        entry("Shared", false),
                        entry("Shared.Nested", false),
                        entry("module library", false),
                        entry("Uses", true),
                        entry("module example", true));
    }

    @Test
    void isTrueForATypeJavacFindsOnTheSourcePath(@TempDir Path sourcePath) throws IOException {
        Path lazy = sourcePath.resolve("lazy/Lazy.java");
        Files.createDirectories(lazy.getParent());
        Files.writeString(lazy, "package lazy;\n\npublic class Lazy {}\n", StandardCharsets.UTF_8);
        Map<String, Boolean> results = new LinkedHashMap<>();

        Compilation compilation = FirstRound.run(
                List.of(PLAIN),
                List.of("-sourcepath", sourcePath.toString()),
                context -> results.put("Lazy", context.isFromSource(type(context, "lazy.Lazy"))));

        assertThat(compilation).succeeded();
        assertThat(results).containsExactly(entry("Lazy", true));
    }

    @Test
    void isFalseForATypeJavacCouldNotResolve() {
        Map<String, Boolean> results = new LinkedHashMap<>();

        FirstRound.run(List.of(source("example.Broken", """
                        package example;

                        class Broken {
                            Missing missing;

                            Missing.Inner inner;

                            com.example.Missing qualified;
                        }
                        """)), List.of(), context -> {
            for (VariableElement field :
                    ElementFilter.fieldsIn(type(context, "example.Broken").getEnclosedElements())) {
                results.put(
                        field.asType().toString(),
                        context.isFromSource(context.types().asElement(field.asType())));
            }
        });

        assertThat(results)
                .containsExactly(
                        entry("Missing", false), entry("Missing.Inner", false), entry("com.example.Missing", false));
    }

    @Test
    void isTheSameInEveryRoundForGeneratedSourcesAndClassFiles() {
        Compilation binary = javac().compile(source("example.Binary", """
                package example;

                public final class Binary {}
                """));
        byte[] bytes = readClassFile(binary, "example/Binary.class");
        Map<String, Map<String, Boolean>> rounds = new LinkedHashMap<>();

        Compilation compilation = javac().withProcessors(new Generating(bytes, rounds))
                .compile(source("example.Mark", """
                                package example;

                                @interface Mark {}

                                @interface Generate {}
                                """), source("example.Seed", """
                                package example;

                                @Generate
                                class Seed {}
                                """), source("example.Uses", """
                                package example;

                                @Mark
                                class Uses {
                                    Generated generated;
                                }
                                """));

        assertThat(compilation).succeeded();
        Map<String, Boolean> expected = Map.of(
                "Seed", true,
                "Generated", true,
                "Generated.Inner", true,
                "Binary", false,
                "String", false);
        assertThat(rounds).containsOnlyKeys("held back to a later round", "last round");
        assertThat(rounds.get("held back to a later round")).isEqualTo(expected);
        assertThat(rounds.get("last round")).isEqualTo(expected);
    }

    private static byte[] readClassFile(Compilation compilation, String path) {
        try (InputStream in = compilation
                .generatedFile(StandardLocation.CLASS_OUTPUT, path)
                .orElseThrow()
                .openInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Generates, for an element annotated with {@code @Generate}, the source of {@code example.Generated} and the class
     * file of {@code example.Binary}, and records which types come from a source, for an element annotated with
     * {@code @Mark}, held back until {@code example.Generated} exists, and again in the last round.
     */
    private static final class Generating extends StepProcessor {

        private final byte[] binary;
        private final Map<String, Map<String, Boolean>> rounds;

        Generating(byte[] binary, Map<String, Map<String, Boolean>> rounds) {
            this.binary = binary.clone();
            this.rounds = rounds;
        }

        private static Map<String, Boolean> record(ProcessingContext context) {
            Map<String, Boolean> results = new LinkedHashMap<>();
            for (String name : List.of("Seed", "Generated", "Generated.Inner", "Binary")) {
                results.put(name, context.isFromSource(type(context, "example." + name)));
            }
            results.put("String", context.isFromSource(type(context, "java.lang.String")));
            return results;
        }

        @Override
        protected List<Step> steps(ProcessingContext context) {
            return List.of(
                    new Step() {
                        @Override
                        public Set<String> annotations() {
                            return Set.of("example.Generate");
                        }

                        @Override
                        public Set<? extends Element> process(AnnotatedElements elements) {
                            context.sources().write("example.Generated", scope -> """
                                    final class Generated {
                                        static final class Inner {}
                                    }
                                    """);
                            try (OutputStream out = context.filer()
                                    .createClassFile("example.Binary")
                                    .openOutputStream()) {
                                out.write(binary);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                            return Set.of();
                        }
                    },
                    new Step() {
                        @Override
                        public Set<String> annotations() {
                            return Set.of("example.Mark");
                        }

                        @Override
                        public Set<? extends Element> process(AnnotatedElements elements) {
                            rounds.put("held back to a later round", record(context));
                            return Set.of();
                        }

                        @Override
                        public void processingOver() {
                            rounds.put("last round", record(context));
                        }
                    });
        }
    }
}
