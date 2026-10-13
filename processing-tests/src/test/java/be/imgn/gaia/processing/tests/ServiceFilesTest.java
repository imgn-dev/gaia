package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.StepProcessor;
import be.imgn.gaia.processing.source.ClassName;
import be.imgn.gaia.processing.testing.Compilation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServiceFilesTest {

    private static final ClassName SERVICE = ClassName.of("example", "Service");

    private static final String SERVICE_FILE = "META-INF/services/example.Service";

    private static final JavaFileObject SERVICE_SOURCE = source("example.Service", """
            package example;

            public interface Service {
                String name();
            }
            """);

    private static final JavaFileObject PROVIDE = source("example.Provide", """
            package example;

            public @interface Provide {}
            """);

    private static final JavaFileObject GENERATE = source("example.Generate", """
            package example;

            public @interface Generate {}
            """);

    private static JavaFileObject implementation(String simpleName) {
        return source("example." + simpleName, """
                package example;

                @Provide
                public final class %1$s implements Service {
                    @Override
                    public String name() {
                        return "%1$s";
                    }
                }
                """.formatted(simpleName));
    }

    /** Registers a type annotated with {@code @Provide} as an implementation of {@code example.Service}. */
    private static void asService(ProcessingContext context, TypeElement type) {
        context.services().add(SERVICE, ClassName.of(type), type);
    }

    private static Compilation compile(
            BiConsumer<ProcessingContext, TypeElement> registration, JavaFileObject... sources) {
        List<JavaFileObject> all = new ArrayList<>(List.of(SERVICE_SOURCE, PROVIDE, GENERATE));
        all.addAll(List.of(sources));
        return javac().withProcessors(new Registering(registration))
                .withOptions("-Xlint:all,-processing", "-Werror")
                .compile(all);
    }

    // Compiles classes into the class output, as an earlier build did, without putting it on the class path later.
    private static void compileEarlier(Path classOutput, JavaFileObject... implementations) {
        List<JavaFileObject> all = new ArrayList<>(List.of(SERVICE_SOURCE, PROVIDE));
        all.addAll(List.of(implementations));
        assertThat(javac().withOptions("-d", classOutput.toString()).compile(all))
                .succeeded();
    }

    @Test
    void writesTheImplementationsSortedOnePerLineAfterTheLastRound() {
        Compilation compilation = compile(
                ServiceFilesTest::asService, implementation("Zulu"), implementation("Alpha"), implementation("Mike"));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE)
                .isEqualTo("example.Alpha\nexample.Mike\nexample.Zulu\n");
    }

    @Test
    void collectsTheImplementationsOfEveryRoundInOneFile() {
        Compilation compilation =
                compile(ServiceFilesTest::asService, implementation("Written"), source("example.Seed", """
                package example;

                @Generate
                class Seed {}
                """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE)
                .isEqualTo("example.Generated\nexample.Written\n");
    }

    @Test
    void listsAnImplementationRegisteredSeveralTimesOnce() {
        Compilation compilation = compile(
                (context, type) -> {
                    asService(context, type);
                    context.services().add(SERVICE, ClassName.of(type));
                },
                implementation("Twice"));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE)
                .isEqualTo("example.Twice\n");
    }

    @Test
    void writesNestedServicesAndImplementationsWithTheirBinaryNames() {
        Compilation compilation = javac().withProcessors(new Registering((context, type) ->
                        context.services().add(ClassName.of("example", "Api", "Plugin"), ClassName.of(type), type)))
                .withOptions("-Xlint:all,-processing", "-Werror")
                .compile(PROVIDE, GENERATE, source("example.Api", """
                        package example;

                        public final class Api {
                            public interface Plugin {}

                            public static final class Outer {
                                @Provide
                                public static final class Inner implements Plugin {}
                            }

                            @Provide
                            public static final class Simple implements Plugin {}
                        }
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/services/example.Api$Plugin")
                .isEqualTo("example.Api$Outer$Inner\nexample.Api$Simple\n");
    }

    @Test
    void writesOneFilePerService() {
        Compilation compilation = compile(
                (context, type) -> {
                    asService(context, type);
                    context.services().add(ClassName.of(Object.class), ClassName.of(type), type);
                },
                implementation("Both"),
                source("example.Other", """
                        package example;

                        public interface Other {}
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE)
                .isEqualTo("example.Both\n");
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/services/java.lang.Object")
                .isEqualTo("example.Both\n");
    }

    @Test
    void writesNoFileWhenNothingIsRegistered() {
        Compilation compilation = compile(ServiceFilesTest::asService, source("example.Plain", """
                package example;

                public final class Plain {}
                """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation.generatedFiles())
                .noneMatch(file -> file.getName().contains("META-INF"));
    }

    @Test
    void keepsTheEntriesOfAFileAlreadyInTheClassOutput(@TempDir Path classOutput) throws IOException {
        compileEarlier(classOutput, implementation("Old"), implementation("Indented"));
        Path existing = classOutput.resolve(SERVICE_FILE);
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, """
                # Written by an earlier build
                example.Old   # a comment after an entry

                example.Fresh
                \texample.Indented\t\r
                example.Old
                """, StandardCharsets.UTF_8);

        Compilation compilation = javac().withProcessors(new Registering(ServiceFilesTest::asService))
                .withOptions("-Xlint:all,-processing", "-Werror", "-d", classOutput.toString())
                .compile(SERVICE_SOURCE, PROVIDE, GENERATE, implementation("Fresh"), implementation("New"));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(Files.readString(existing, StandardCharsets.UTF_8))
                .isEqualTo("example.Fresh\nexample.Indented\nexample.New\nexample.Old\n");
    }

    @Test
    void dropsTheEntriesOfClassesThatNoLongerExist(@TempDir Path classOutput) throws IOException {
        compileEarlier(classOutput, implementation("Compiled"));
        Path existing = classOutput.resolve(SERVICE_FILE);
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, """
                example.Compiled
                example.Deleted
                example.Holder$Nested
                example.Holder$Gone
                example.Gone$Nested
                example.Dollar$Name
                example.Dollar$Name$Inner
                java.util.ArrayList
                java.util.Map$Entry
                not a class name
                """, StandardCharsets.UTF_8);

        Compilation compilation = javac().withProcessors(new Registering(ServiceFilesTest::asService))
                .withOptions("-Xlint:all,-processing", "-Werror", "-d", classOutput.toString())
                .compile(
                        SERVICE_SOURCE,
                        PROVIDE,
                        GENERATE,
                        implementation("Fresh"),
                        source("example.Holder", """
                        package example;

                        public final class Holder {
                            private Holder() {}

                            public static final class Nested {}
                        }
                        """),
                        source("example.Dollar$Name", """
                        package example;

                        public final class Dollar$Name {
                            private Dollar$Name() {}

                            public static final class Inner {}
                        }
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(Files.readString(existing, StandardCharsets.UTF_8)).isEqualTo("""
                        example.Compiled
                        example.Dollar$Name
                        example.Dollar$Name$Inner
                        example.Fresh
                        example.Holder$Nested
                        java.util.ArrayList
                        java.util.Map$Entry
                        not a class name
                        """);
    }

    @Test
    void keepsTheEntryOfATypeGeneratedInTheCompilationWithoutRegisteringItAgain(@TempDir Path classOutput)
            throws IOException {
        Path existing = classOutput.resolve(SERVICE_FILE);
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, "example.Generated\nexample.Deleted\n", StandardCharsets.UTF_8);

        Compilation compilation = javac().withProcessors(new Registering((context, type) -> {
                    if (!type.getSimpleName().contentEquals("Generated")) {
                        asService(context, type);
                    }
                }))
                .withOptions("-Xlint:all,-processing", "-Werror", "-d", classOutput.toString())
                .compile(SERVICE_SOURCE, PROVIDE, GENERATE, implementation("Fresh"), source("example.Seed", """
                        package example;

                        @Generate
                        class Seed {}
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(Files.readString(existing, StandardCharsets.UTF_8)).isEqualTo("example.Fresh\nexample.Generated\n");
    }

    @Test
    void leavesTheFileOfAServiceWithNoNewImplementationAlone(@TempDir Path classOutput) throws IOException {
        Path existing = classOutput.resolve("META-INF/services/example.Unrelated");
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, "# kept as it is\nexample.Z\nexample.A\n", StandardCharsets.UTF_8);

        Compilation compilation = javac().withProcessors(new Registering(ServiceFilesTest::asService))
                .withOptions("-Xlint:all,-processing", "-Werror", "-d", classOutput.toString())
                .compile(SERVICE_SOURCE, PROVIDE, GENERATE, implementation("Fresh"));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(Files.readString(existing, StandardCharsets.UTF_8))
                .isEqualTo("# kept as it is\nexample.Z\nexample.A\n");
        assertThat(Files.readString(classOutput.resolve(SERVICE_FILE), StandardCharsets.UTF_8))
                .isEqualTo("example.Fresh\n");
    }

    @Test
    void writesFilesThatServiceLoaderReads(@TempDir Path classOutput) throws Exception {
        Compilation compilation = javac().withProcessors(new Registering(ServiceFilesTest::asService))
                .withOptions("-Xlint:all,-processing", "-Werror", "-d", classOutput.toString())
                .compile(
                        SERVICE_SOURCE,
                        PROVIDE,
                        GENERATE,
                        implementation("Plain"),
                        source("example.Holder", """
                                package example;

                                public final class Holder {
                                    @Provide
                                    public static final class Nested implements Service {
                                        public Nested() {}

                                        @Override
                                        public String name() {
                                            return "Nested";
                                        }
                                    }
                                }
                                """),
                        source("example.Seed", """
                                package example;

                                @Generate
                                class Seed {}
                                """),
                        source("example.Loader", """
                                package example;

                                import java.util.ArrayList;
                                import java.util.List;
                                import java.util.ServiceLoader;

                                public final class Loader {
                                    private Loader() {}

                                    public static List<String> names() {
                                        List<String> names = new ArrayList<>();
                                        ServiceLoader.load(Service.class, Loader.class.getClassLoader())
                                                .forEach(service -> names.add(service.name()));
                                        return names;
                                    }
                                }
                                """));
        assertThat(compilation).succeeded().hadNoWarnings();

        try (URLClassLoader loader =
                new URLClassLoader(new URL[] {classOutput.toUri().toURL()}, ServiceFilesTest.class.getClassLoader())) {
            Object names = loader.loadClass("example.Loader").getMethod("names").invoke(null);

            assertThat(names).isEqualTo(List.of("Generated", "Nested", "Plain"));
        }
    }

    @Test
    void acceptsARecordAndAClassWithAnExplicitPublicConstructor() {
        Compilation compilation = compile(
                ServiceFilesTest::asService, source("example.Recorded", """
                        package example;

                        @Provide
                        public record Recorded() implements Service {
                            @Override
                            public String name() {
                                return "Recorded";
                            }
                        }
                        """), source("example.Constructed", """
                        package example;

                        @Provide
                        public class Constructed implements Service {
                            public Constructed() {}

                            public Constructed(String ignored) {}

                            @Override
                            public String name() {
                                return "Constructed";
                            }
                        }
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE)
                .isEqualTo("example.Constructed\nexample.Recorded\n");
    }

    @Test
    void reportsAnImplementationThatIsNotPublic() {
        Compilation compilation = compile(ServiceFilesTest::asService, source("example.Hidden", """
                package example;

                @Provide
                final class Hidden implements Service {
                    @Override
                    public String name() {
                        return "Hidden";
                    }
                }
                """));

        assertThat(compilation)
                .failed()
                .hadErrorCount(1)
                .hadErrorContaining("example.Hidden")
                .inFile("example/Hidden.java")
                .onLine(4);
        assertThat(compilation.errors().getFirst().getMessage(Locale.ROOT))
                .isEqualTo("ServiceLoader cannot load example.Hidden as an implementation of example.Service: it is not"
                        + " public");
    }

    @Test
    void reportsEveryProblemOfAnImplementationInOneError() {
        Compilation compilation = compile(ServiceFilesTest::asService, source("example.Broken", """
                package example;

                @Provide
                abstract class Broken {
                    Broken(int value) {}
                }
                """));

        assertThat(compilation)
                .failed()
                .hadErrorCount(1)
                .hadErrorContaining("ServiceLoader cannot load example.Broken as an implementation of example.Service:"
                        + " it is not public, it is abstract, it has no public constructor without parameters,"
                        + " it is not a subtype of example.Service")
                .onLine(4);
    }

    @Test
    void reportsAnImplementationWithoutAPublicConstructorWithoutParameters() {
        Compilation compilation = compile(ServiceFilesTest::asService, source("example.Private", """
                package example;

                @Provide
                public final class Private implements Service {
                    private Private() {}

                    public Private(String name) {}

                    @Override
                    public String name() {
                        return "Private";
                    }
                }
                """));

        assertThat(compilation)
                .failed()
                .hadErrorContaining("example.Private as an implementation of example.Service: it has no public"
                        + " constructor without parameters")
                .onLine(4);
    }

    @Test
    void reportsAnInnerClass() {
        Compilation compilation = compile(ServiceFilesTest::asService, source("example.Outer", """
                package example;

                public final class Outer {
                    @Provide
                    public final class Inner implements Service {
                        @Override
                        public String name() {
                            return "Inner";
                        }
                    }
                }
                """));

        assertThat(compilation)
                .failed()
                .hadErrorContaining("example.Outer.Inner as an implementation of example.Service: it is an inner"
                        + " class, not a static one")
                .onLine(5);
    }

    @Test
    void reportsAnInterfaceAndAnEnum() {
        Compilation compilation =
                compile(ServiceFilesTest::asService, source("example.Contract", """
                        package example;

                        @Provide
                        public interface Contract extends Service {}
                        """), source("example.Constant", """
                        package example;

                        @Provide
                        public enum Constant implements Service {
                            INSTANCE;

                            @Override
                            public String name() {
                                return "Constant";
                            }
                        }
                        """));

        assertThat(compilation).failed().hadErrorCount(2);
        assertThat(compilation)
                .hadErrorContaining("example.Contract as an implementation of example.Service: it is not a class")
                .inFile("example/Contract.java");
        assertThat(compilation)
                .hadErrorContaining("example.Constant as an implementation of example.Service: it is not a class")
                .inFile("example/Constant.java");
    }

    @Test
    void reportsAnImplementationThatDoesNotExistOnTheOriginatingElement() {
        Compilation compilation = compile(
                (context, type) -> context.services().add(SERVICE, ClassName.of("example", "Missing"), type),
                implementation("Present"));

        assertThat(compilation)
                .failed()
                .hadErrorContaining("Cannot register example.Missing as an implementation of example.Service: type"
                        + " example.Missing does not exist")
                .inFile("example/Present.java")
                .onLine(4);
    }

    @Test
    void reportsAServiceThatDoesNotExistWithoutAnElement() {
        Compilation compilation = compile(
                (context, type) -> context.services().add(ClassName.of("example", "Unknown"), ClassName.of(type)),
                implementation("Present"));

        assertThat(compilation)
                .failed()
                .hadErrorContaining("Cannot register example.Present as an implementation of example.Unknown: type"
                        + " example.Unknown does not exist");
        assertThat(compilation.errors().getFirst().getSource()).isNull();
    }

    @Test
    void warnsAboutAnImplementationItsModuleDoesNotProvide() {
        Compilation compilation = javac().withProcessors(new Registering(ServiceFilesTest::asService))
                .compile(
                        source("module-info", "module example {}"),
                        SERVICE_SOURCE,
                        PROVIDE,
                        GENERATE,
                        implementation("Unprovided"));

        assertThat(compilation).succeeded();
        assertThat(compilation)
                .hadWarningContaining("Module example does not declare provides example.Service with"
                        + " example.Unprovided: ServiceLoader ignores META-INF/services in a named module on the"
                        + " module path")
                .inFile("example/Unprovided.java")
                .onLine(4);
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE)
                .isEqualTo("example.Unprovided\n");
    }

    @Test
    void skipsTheModuleCheckOfAServiceWhenAsked() {
        Compilation compilation = javac().withProcessors(new Registering((context, type) -> {
                    context.services().skipModuleCheck(SERVICE);
                    asService(context, type);
                }))
                .withOptions("-Xlint:all,-processing", "-Werror")
                .compile(
                        source("module-info", "module example {}"),
                        SERVICE_SOURCE,
                        PROVIDE,
                        GENERATE,
                        implementation("Unprovided"));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE)
                .isEqualTo("example.Unprovided\n");
    }

    @Test
    void skipsTheModuleCheckOfAServiceBeforeAnyImplementationIsRegistered() {
        Compilation compilation = javac().withProcessors(new Registering(ServiceFilesTest::asService) {
                    @Override
                    protected List<Step> steps(ProcessingContext context) {
                        context.services().skipModuleCheck(SERVICE);
                        return super.steps(context);
                    }
                })
                .withOptions("-Xlint:all,-processing", "-Werror")
                .compile(
                        source("module-info", "module example {}"),
                        SERVICE_SOURCE,
                        PROVIDE,
                        GENERATE,
                        implementation("Unprovided"),
                        source("example.Seed", """
                                package example;

                                @Generate
                                class Seed {}
                                """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE)
                .isEqualTo("example.Generated\nexample.Unprovided\n");
    }

    @Test
    void stillWarnsAboutTheServicesWhoseModuleCheckIsNotSkipped() {
        Compilation compilation = javac().withProcessors(new Registering((context, type) -> {
                    context.services().skipModuleCheck(ClassName.of("example", "Other"));
                    asService(context, type);
                    context.services().add(ClassName.of("example", "Other"), ClassName.of(type), type);
                }))
                .compile(
                        source("module-info", "module example {}"),
                        SERVICE_SOURCE,
                        PROVIDE,
                        GENERATE,
                        source("example.Other", """
                                package example;

                                public interface Other {}
                                """),
                        source("example.Both", """
                                package example;

                                @Provide
                                public final class Both implements Service, Other {
                                    @Override
                                    public String name() {
                                        return "Both";
                                    }
                                }
                                """));

        assertThat(compilation).succeeded();
        assertThat(compilation.diagnostics(Diagnostic.Kind.WARNING))
                .singleElement()
                .satisfies(warning -> assertThat(warning.getMessage(Locale.ROOT))
                        .startsWith("Module example does not declare provides example.Service with example.Both:"));
    }

    @Test
    void stillReportsTheOtherProblemsOfAnImplementationWhenTheModuleCheckIsSkipped() {
        Compilation compilation = javac().withProcessors(new Registering((context, type) -> {
                    context.services().skipModuleCheck(SERVICE);
                    asService(context, type);
                }))
                .compile(
                        source("module-info", "module example {}"),
                        SERVICE_SOURCE,
                        PROVIDE,
                        GENERATE,
                        source("example.Hidden", """
                        package example;

                        @Provide
                        final class Hidden implements Service {
                            @Override
                            public String name() {
                                return "Hidden";
                            }
                        }
                        """));

        assertThat(compilation)
                .failed()
                .hadErrorCount(1)
                .hadErrorContaining("ServiceLoader cannot load example.Hidden as an implementation of example.Service:"
                        + " it is not public");
        assertThat(compilation.diagnostics(Diagnostic.Kind.WARNING)).isEmpty();
    }

    @Test
    void acceptsAnImplementationItsModuleProvides() {
        Compilation compilation = javac().withProcessors(new Registering(ServiceFilesTest::asService))
                .withOptions("-Xlint:all,-processing,-missing-explicit-ctor", "-Werror")
                .compile(source("module-info", """
                                module example {
                                    exports example;

                                    provides example.Service with example.Provided;
                                }
                                """), SERVICE_SOURCE, PROVIDE, GENERATE, implementation("Provided"));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE)
                .isEqualTo("example.Provided\n");
    }

    @Test
    void reportsAFileAnotherProcessorCreated() {
        Compilation compilation = javac().withProcessors(new Registering(ServiceFilesTest::asService), new Competing())
                .compile(SERVICE_SOURCE, PROVIDE, GENERATE, implementation("Lost"));

        assertThat(compilation)
                .failed()
                .hadErrorContaining("Cannot write " + SERVICE_FILE + ": it was already created in this compilation");
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE)
                .isEqualTo("example.Competitor\n");
    }

    @Test
    void writesTheFilesOfAnyProcessorThatAsksAfterTheLastRound() {
        Plain processor = new Plain();
        Compilation compilation = javac().withProcessors(processor)
                .withOptions("-Xlint:all,-processing", "-Werror")
                .compile(SERVICE_SOURCE, PROVIDE, implementation("Manual"));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE)
                .isEqualTo("example.Manual\n");
        assertThat(processor.failure)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(SERVICE_FILE
                        + " was already written: register its implementations before writing the service files");
    }

    @Test
    void refusesToSkipTheModuleCheckOfAServiceWhoseFileWasWritten() {
        List<RuntimeException> failures = new ArrayList<>();
        Compilation compilation = javac().withProcessors(new Plain() {
                    @Override
                    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                        super.process(annotations, roundEnv);
                        if (roundEnv.processingOver()) {
                            try {
                                context().services().skipModuleCheck(SERVICE);
                            } catch (IllegalStateException e) {
                                failures.add(e);
                            }
                            context().services().skipModuleCheck(ClassName.of("example", "Unwritten"));
                        }
                        return false;
                    }
                })
                .compile(SERVICE_SOURCE, PROVIDE, implementation("Once"));

        assertThat(compilation).succeeded().hadErrorCount(0);
        assertThat(failures)
                .singleElement(InstanceOfAssertFactories.THROWABLE)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        SERVICE_FILE + " was already written: skip the module check before writing the service files");
    }

    @Test
    void writesNothingWhenAskedAgain() {
        Compilation compilation = javac().withProcessors(new Plain() {
                    @Override
                    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                        super.process(annotations, roundEnv);
                        if (roundEnv.processingOver()) {
                            context().services().write();
                        }
                        return false;
                    }
                })
                .compile(SERVICE_SOURCE, PROVIDE, implementation("Once"));

        assertThat(compilation).succeeded().hadErrorCount(0);
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE)
                .isEqualTo("example.Once\n");
    }

    @Test
    void writesNoFileForADiscardedServiceAndStillWritesTheOthers() {
        Compilation compilation = compile(
                (context, type) -> {
                    asService(context, type);
                    context.services().add(ClassName.of(Object.class), ClassName.of(type), type);
                    context.services().discard(SERVICE);
                },
                implementation("Dropped"));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation.generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE))
                .isEmpty();
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/services/java.lang.Object")
                .isEqualTo("example.Dropped\n");
    }

    @Test
    void writesOnlyTheImplementationsRegisteredAfterADiscard() {
        Compilation compilation = compile(
                (context, type) -> {
                    if (type.getSimpleName().contentEquals("Generated")) {
                        context.services().discard(SERVICE);
                    }
                    asService(context, type);
                },
                implementation("Dropped"),
                source("example.Seed", """
                        package example;

                        @Generate
                        class Seed {}
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE)
                .isEqualTo("example.Generated\n");
    }

    @Test
    void doesNothingWhenDiscardingAServiceWithoutImplementations() {
        Compilation compilation = compile(
                (context, type) -> {
                    context.services().discard(ClassName.of("example", "Unregistered"));
                    asService(context, type);
                },
                implementation("Kept"));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE)
                .isEqualTo("example.Kept\n");
    }

    @Test
    void leavesTheFileAlreadyInTheClassOutputOfADiscardedServiceAlone(@TempDir Path classOutput) throws IOException {
        Path existing = classOutput.resolve(SERVICE_FILE);
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, "# by hand\nexample.Deleted\n", StandardCharsets.UTF_8);

        Compilation compilation = javac().withProcessors(new Registering((context, type) -> {
                    asService(context, type);
                    context.services().discard(SERVICE);
                }))
                .withOptions("-Xlint:all,-processing", "-Werror", "-d", classOutput.toString())
                .compile(SERVICE_SOURCE, PROVIDE, GENERATE, implementation("Fresh"));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(Files.readString(existing, StandardCharsets.UTF_8)).isEqualTo("# by hand\nexample.Deleted\n");
    }

    @Test
    void keepsTheModuleCheckSkippedAfterADiscard() {
        Compilation compilation = javac().withProcessors(new Registering((context, type) -> {
                    context.services().skipModuleCheck(SERVICE);
                    context.services().add(SERVICE, ClassName.of("example", "Discarded"));
                    context.services().discard(SERVICE);
                    asService(context, type);
                }))
                .withOptions("-Xlint:all,-processing", "-Werror")
                .compile(
                        source("module-info", "module example {}"),
                        SERVICE_SOURCE,
                        PROVIDE,
                        GENERATE,
                        implementation("Unprovided"));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE)
                .isEqualTo("example.Unprovided\n");
    }

    @Test
    void refusesToDiscardAServiceWhoseFileWasWritten() {
        List<RuntimeException> failures = new ArrayList<>();
        Compilation compilation = javac().withProcessors(new Plain() {
                    @Override
                    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                        super.process(annotations, roundEnv);
                        if (roundEnv.processingOver()) {
                            try {
                                context().services().discard(SERVICE);
                            } catch (IllegalStateException e) {
                                failures.add(e);
                            }
                            context().services().discard(ClassName.of("example", "Unwritten"));
                        }
                        return false;
                    }
                })
                .compile(SERVICE_SOURCE, PROVIDE, implementation("Once"));

        assertThat(compilation).succeeded().hadErrorCount(0);
        assertThat(failures)
                .singleElement(InstanceOfAssertFactories.THROWABLE)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(SERVICE_FILE
                        + " was already written: discard its implementations before writing the service files");
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICE_FILE)
                .isEqualTo("example.Once\n");
    }

    /**
     * Registers, through a function, each type annotated with {@code @Provide}, and generates
     * {@code example.Generated}, annotated with {@code @Provide}, for a type annotated with {@code @Generate}.
     */
    private static class Registering extends StepProcessor {

        private final BiConsumer<ProcessingContext, TypeElement> registration;

        Registering(BiConsumer<ProcessingContext, TypeElement> registration) {
            this.registration = registration;
        }

        @Override
        protected List<Step> steps(ProcessingContext context) {
            return List.of(
                    new Step() {
                        @Override
                        public Set<String> annotations() {
                            return Set.of("example.Provide");
                        }

                        @Override
                        public Set<? extends Element> process(AnnotatedElements elements) {
                            elements.annotatedWith("example.Provide")
                                    .forEach(element -> registration.accept(context, (TypeElement) element));
                            return Set.of();
                        }
                    },
                    new Step() {
                        @Override
                        public Set<String> annotations() {
                            return Set.of("example.Generate");
                        }

                        @Override
                        public Set<? extends Element> process(AnnotatedElements elements) {
                            context.sources().write("example.Generated", scope -> """
                                    @Provide
                                    public final class Generated implements Service {
                                        @Override
                                        public String name() {
                                            return "Generated";
                                        }
                                    }
                                    """);
                            return Set.of();
                        }
                    });
        }
    }

    /** Creates the service file in the first round, before the processor that registers implementations can. */
    @SupportedAnnotationTypes("*")
    private static final class Competing extends AbstractProcessor {

        private boolean done;

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (!done) {
                done = true;
                try (Writer writer = processingEnv
                        .getFiler()
                        .createResource(StandardLocation.CLASS_OUTPUT, "", SERVICE_FILE)
                        .openWriter()) {
                    writer.write("example.Competitor\n");
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return false;
        }
    }

    /**
     * A processor that is not a {@link StepProcessor}: it registers the types annotated with {@code @Provide}, writes
     * the service files after the last round, and records the failure of a registration after that.
     */
    private static class Plain extends AbstractProcessor {

        private @Nullable ProcessingContext context;
        private @Nullable RuntimeException failure;

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("example.Provide");
        }

        @Override
        public synchronized void init(ProcessingEnvironment processingEnv) {
            super.init(processingEnv);
            context = ProcessingContext.of(processingEnv, Plain.class);
        }

        ProcessingContext context() {
            return Objects.requireNonNull(context);
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            ProcessingContext current = context();
            if (roundEnv.processingOver()) {
                current.services().write();
                try {
                    current.services().add(SERVICE, ClassName.of("example", "Late"));
                } catch (IllegalStateException e) {
                    failure = e;
                }
            } else {
                for (TypeElement annotation : annotations) {
                    for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                        current.services().add(SERVICE, ClassName.of((TypeElement) element), element);
                    }
                }
            }
            return false;
        }
    }
}
