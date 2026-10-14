package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.SourceScope;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.StepProcessor;
import be.imgn.gaia.processing.source.ClassName;
import be.imgn.gaia.processing.source.Field;
import be.imgn.gaia.processing.source.JavaFile;
import be.imgn.gaia.processing.source.Type;
import be.imgn.gaia.processing.testing.Compilation;
import com.sun.source.tree.ClassTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;

class GeneratedSourcesTest {

    private static final JavaFileObject GEN = source("example.Gen", """
            package example;

            @interface Gen {}
            """);

    private static final JavaFileObject SEED = source("example.Seed", """
            package example;

            @Gen
            class Seed {}
            """);

    private static final String GENERATED = "@Generated(\"" + Generating.class.getName() + "\")";

    private static final String BACKSLASH = "\\";

    // Loads a class the compilation compiled, from its class output.
    private static Class<?> load(Compilation compilation, String binaryName) throws ClassNotFoundException {
        ClassLoader loader = new ClassLoader(GeneratedSourcesTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String className) throws ClassNotFoundException {
                JavaFileObject file = compilation
                        .generatedFile(StandardLocation.CLASS_OUTPUT, className.replace('.', '/') + ".class")
                        .orElseThrow(() -> new ClassNotFoundException(className));
                try (InputStream in = file.openInputStream()) {
                    byte[] bytes = in.readAllBytes();
                    return defineClass(className, bytes, 0, bytes.length);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        return loader.loadClass(binaryName);
    }

    private static Object constant(Class<?> type, String name) throws ReflectiveOperationException {
        return type.getField(name).get(null);
    }

    @Test
    void writesThePackageAndTheImportsTheBodyNeeds() {
        Compilation compilation = javac().withProcessors(new Generating((context, scope) -> """
                        %s
                        final class Out {
                            %s strings;
                            %s<?, ?> entry;
                            %s text;
                            %s seed;
                            %s awtList;
                        }
                        """.formatted(
                                scope.generated(),
                                scope.name(context.types()
                                        .getDeclaredType(
                                                context.elements().getTypeElement("java.util.List"),
                                                context.elements()
                                                        .getTypeElement("java.lang.String")
                                                        .asType())),
                                scope.name("java.util.Map.Entry"),
                                scope.name(String.class),
                                scope.name("example.Seed"),
                                scope.name("java.awt.List"))))
                .withOptions("-Xlint:all,-processing", "-Werror")
                .compile(GEN, SEED);

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation).generatedSourceFile("example.Out").isEqualTo("""
                package example;

                import java.util.List;
                import java.util.Map;
                import javax.annotation.processing.Generated;

                %s
                final class Out {
                    List<String> strings;
                    Map.Entry<?, ?> entry;
                    String text;
                    Seed seed;
                    java.awt.List awtList;
                }
                """.formatted(GENERATED));
    }

    @Test
    void namesClassesPrimitivesAndArrays() {
        Compilation compilation = javac().withProcessors(new Generating(
                        (context, scope) -> """
                        final class Out {
                            %s numbers;
                            %s maps;
                        }
                        """.formatted(scope.name(int[].class), scope.name(Map[].class))))
                .compile(GEN, SEED);

        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("example.Out")
                .contains("import java.util.Map;")
                .contains("int[] numbers;")
                .contains("Map[] maps;");
    }

    @Test
    void writesAFileOnceAndReportsTheSecondAttempt() {
        Generating processor = new Generating((context, scope) -> "final class Out {}\n");
        Compilation compilation = javac().withProcessors(processor).compile(GEN, SEED, source("example.Seed2", """
                package example;

                @Gen
                class Seed2 {}
                """));

        assertThat(compilation).succeeded().hadErrorCount(0);
        assertThat(processor.results).containsExactly(true, false);
    }

    @Test
    void rejectsAnInvalidName() {
        List<String> names = List.of(
                "", "Out.", ".Out", "example..Out", "example.class", "example.1Out", "example/Out", "example.Out-1");

        Compilation compilation = FirstRound.run(List.of(GEN, SEED), List.of(), context -> {
            for (String name : names) {
                assertThatIllegalArgumentException()
                        .isThrownBy(() -> context.sources().write(name, scope -> "final class Out {}\n"))
                        .withMessage("Invalid qualified name " + name
                                + ": it must be Java identifiers, none of them a keyword, separated by dots");
            }
        });

        assertThat(compilation).succeeded();
    }

    @Test
    void writesAFileAgainAfterItsBodyFailed() {
        IllegalStateException failure = new IllegalStateException("no body");

        Compilation compilation = FirstRound.run(List.of(GEN, SEED), List.of(), context -> {
            assertThatThrownBy(() -> context.sources().write("example.Out", scope -> {
                        throw failure;
                    }))
                    .isSameAs(failure);
            assertThat(context.sources().isAvailable(ClassName.of("example", "Out")))
                    .isTrue();
            assertThat(context.sources().write("example.Out", scope -> "final class Out {}\n"))
                    .isTrue();
        });

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("example.Out").contains("final class Out {}");
    }

    @Test
    void rejectsNamesThatCannotBeWritten() {
        class Local {}
        Object anonymous = new Object() {};

        Compilation compilation = FirstRound.run(List.of(GEN, SEED), List.of(), context -> {
            TypeMirror constructor = ElementFilter.constructorsIn(
                            context.elements().getTypeElement("example.Seed").getEnclosedElements())
                    .getFirst()
                    .asType();
            TypeMirror pkg = context.elements().getPackageElement("example").asType();
            Map<Function<SourceScope, String>, String> messages = new LinkedHashMap<>();
            messages.put(
                    scope -> scope.name(Local.class),
                    Local.class.getName() + " has no canonical name, as a local, anonymous or hidden class");
            messages.put(
                    scope -> scope.name(Local[][].class),
                    Local.class.getName() + " has no canonical name, as a local, anonymous or hidden class");
            messages.put(
                    scope -> scope.name(anonymous.getClass()),
                    anonymous.getClass().getName() + " has no canonical name, as a local, anonymous or hidden class");
            messages.put(
                    scope -> scope.name("java.util."),
                    "Invalid qualified name java.util.: it must be Java identifiers, none of them a keyword, separated"
                            + " by dots");
            messages.put(
                    scope -> scope.name("example.class"),
                    "Invalid qualified name example.class: it must be Java identifiers, none of them a keyword,"
                            + " separated by dots");
            messages.put(scope -> scope.name(constructor), "Cannot name a type of kind EXECUTABLE: ()void");
            messages.put(scope -> scope.name(pkg), "Cannot name a type of kind PACKAGE: example");

            messages.forEach((name, message) -> assertThatIllegalArgumentException()
                    .isThrownBy(() -> context.sources().write("example.Out", name))
                    .withMessage(message));
            assertThat(context.sources().write("example.Out", scope -> "final class Out {}\n"))
                    .isTrue();
        });

        assertThat(compilation).succeeded();
    }

    @Test
    void rejectsLocalAndAnonymousClassesAndTheirMembers() {
        LocalClasses processor = new LocalClasses();

        Compilation compilation = javac().withProcessors(processor).compile(source("example.Locals", """
                package example;

                class Locals {
                    void run() {
                        class Local {
                            class Member {}
                        }
                        Object anonymous = new Object() {};
                    }
                }
                """));

        assertThat(compilation).succeeded();
        assertThat(processor.thrown).containsOnlyKeys(NestingKind.LOCAL, NestingKind.MEMBER, NestingKind.ANONYMOUS);
        assertThat(processor.thrown.values())
                .allSatisfy(exception -> assertThat(exception)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageStartingWith("Cannot name ")
                        .hasMessageEndingWith(": it is a local or anonymous class, or is nested in one"));
    }

    @Test
    void leavesOutGeneratedInAModuleThatDoesNotReadJavaCompiler() {
        Compilation compilation = javac().withProcessors(
                        new Generating((context, scope) -> """
                        %s
                        final class Out {}
                        """.formatted(scope.generated())))
                .compile(source("module-info", "module example {}"), GEN, SEED);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("example.Out").doesNotContain("Generated");
    }

    @Test
    void addsGeneratedInAModuleThatReadsJavaCompiler() {
        Compilation compilation = javac().withProcessors(
                        new Generating((context, scope) -> """
                        %s
                        final class Out {}
                        """.formatted(scope.generated())))
                .compile(source("module-info", "module example { requires static java.compiler; }"), GEN, SEED);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("example.Out").contains(GENERATED);
    }

    @Test
    void writesTextInAsciiThatCompilesToTheSameCharactersUnderAnyEncoding() throws ReflectiveOperationException {
        DocComments docComments = new DocComments();
        Compilation compilation = javac().withProcessors(new Generating((context, scope) -> """
                        /** Out: café 日本 🙂, one backslash \\é, two \\\\é. */
                        public final class Out {
                            // été 🙂 \\é
                            public static final String SUN = "日";
                            public static final String MOON = "月";
                            public static final String EMOJI = "🙂";
                            public static final String ONE_BACKSLASH = "\\\\é";
                            public static final String TWO_BACKSLASHES = "\\\\\\\\é";
                            public static final char SHARP_S = 'ß';
                            public static final String BLOCK = \"""
                                    ünï \\\\ 月
                                    \""";

                            private Out() {}

                            /* Café: ça marche. */
                            public static final class Café {
                                private Café() {}

                                public static String naïve() {
                                    return "naïve";
                                }
                            }
                        }
                        """), docComments)
                .withOptions("-encoding", "US-ASCII", "-Xlint:all,-processing", "-Werror")
                .compile(GEN, SEED);

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("example.Out")
                .matches("\\p{ASCII}*")
                .contains(BACKSLASH + "u65e5");
        Class<?> out = load(compilation, "example.Out");
        assertThat(constant(out, "SUN")).isEqualTo("日").isNotEqualTo(constant(out, "MOON"));
        assertThat(constant(out, "MOON")).isEqualTo("月");
        assertThat(constant(out, "EMOJI")).isEqualTo("\uD83D\uDE42");
        assertThat(constant(out, "ONE_BACKSLASH")).isEqualTo("\\é");
        assertThat(constant(out, "TWO_BACKSLASHES")).isEqualTo("\\\\é");
        assertThat(constant(out, "SHARP_S")).isEqualTo('ß');
        assertThat(constant(out, "BLOCK")).isEqualTo("ünï \\ 月\n");
        Class<?> cafe = load(compilation, "example.Out$Café");
        assertThat(cafe.getSimpleName()).isEqualTo("Café");
        assertThat(cafe.getMethod("naïve").invoke(null)).isEqualTo("naïve");
        assertThat(docComments.comments).containsEntry("Out", "Out: café 日本 🙂, one backslash \\é, two \\\\é. ");
    }

    @Test
    void writesAJavaFileInAsciiAndKeepsItsTextReadable() throws ReflectiveOperationException {
        Type cafe = Type.newClassBuilder("Café")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addJavadoc("Écrit à la main: $L.\n", "日本")
                .addField(Field.newBuilder(String.class, "MOON", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .addJavadoc("The moon, \\ü.\n")
                        .initializer("$S", "月")
                        .build())
                .addField(Field.newBuilder(String.class, "SUN", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("$S", "日\\é")
                        .build())
                .build();
        JavaFile file = JavaFile.newBuilder("example", cafe).build();
        DocComments docComments = new DocComments();
        Compilation compilation = javac().withProcessors(new GeneratingFile(file), docComments)
                .withOptions("-encoding", "ISO-8859-1", "-Xlint:all,-processing", "-Werror")
                .compile(GEN, SEED);

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(file.toString()).contains("\"月\"", "Écrit à la main: 日本.");
        assertThat(compilation)
                .generatedSourceFile("example.Café")
                .matches("\\p{ASCII}*")
                .contains("\"" + BACKSLASH + "u6708\"");
        Class<?> type = load(compilation, "example.Café");
        assertThat(constant(type, "MOON")).isEqualTo("月");
        assertThat(constant(type, "SUN")).isEqualTo("日\\é");
        assertThat(docComments.comments)
                .containsEntry("Café", " Écrit à la main: 日本.\n")
                .containsEntry("MOON", " The moon, \\ü.\n");
    }

    /** Writes {@code example.Out} for each element annotated with {@code @Gen}, recording whether it could. */
    private static final class Generating extends StepProcessor {

        private final BiFunction<ProcessingContext, SourceScope, String> body;
        private final List<Boolean> results = new ArrayList<>();

        Generating(BiFunction<ProcessingContext, SourceScope, String> body) {
            this.body = body;
        }

        @Override
        protected List<Step> steps(ProcessingContext context) {
            return List.of(new Step() {
                @Override
                public Set<String> annotations() {
                    return Set.of("example.Gen");
                }

                @Override
                public Set<? extends Element> process(AnnotatedElements elements) {
                    for (Element element : elements.annotatedWith("example.Gen")) {
                        results.add(
                                context.sources().write("example.Out", scope -> body.apply(context, scope), element));
                    }
                    return Set.of();
                }
            });
        }
    }
    /** Writes a {@link JavaFile} for the first element annotated with {@code @Gen}. */
    private static final class GeneratingFile extends StepProcessor {

        private final JavaFile file;

        GeneratingFile(JavaFile file) {
            this.file = file;
        }

        @Override
        protected List<Step> steps(ProcessingContext context) {
            return List.of(new Step() {
                @Override
                public Set<String> annotations() {
                    return Set.of("example.Gen");
                }

                @Override
                public Set<? extends Element> process(AnnotatedElements elements) {
                    context.sources().write(file, elements.all().iterator().next());
                    return Set.of();
                }
            });
        }
    }

    /**
     * Names each class declared in {@code example.Locals} but itself, once javac has analyzed it, through the scope of
     * a source file, and records what that throws by the nesting kind of the class.
     */
    @SupportedAnnotationTypes("*")
    private static final class LocalClasses extends AbstractProcessor {

        private final Map<NestingKind, Throwable> thrown = new LinkedHashMap<>();

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public synchronized void init(ProcessingEnvironment environment) {
            super.init(environment);
            JavacTask task = JavacTask.instance(environment);
            Trees trees = Trees.instance(task);
            ProcessingContext context = ProcessingContext.of(environment, LocalClasses.class);
            task.addTaskListener(new TaskListener() {
                @Override
                public void finished(TaskEvent event) {
                    TypeElement type = event.getTypeElement();
                    if (event.getKind() == TaskEvent.Kind.ANALYZE && type != null) {
                        new TreePathScanner<Void, Void>() {
                            @Override
                            public Void visitClass(ClassTree tree, Void unused) {
                                TypeElement declared = (TypeElement) trees.getElement(getCurrentPath());
                                if (!declared.equals(type)) {
                                    thrown.put(
                                            declared.getNestingKind(),
                                            catchThrowable(() -> context.sources()
                                                    .write("example.Out", scope -> scope.name(declared))));
                                }
                                return super.visitClass(tree, null);
                            }
                        }.scan(trees.getPath(type), null);
                    }
                }
            });
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            return false;
        }
    }

    /** Records the doc comment of each type and field of the root elements, by simple name, as javac reads it. */
    @SupportedAnnotationTypes("*")
    private static final class DocComments extends AbstractProcessor {

        private final Map<String, String> comments = new LinkedHashMap<>();

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            for (TypeElement type : ElementFilter.typesIn(roundEnv.getRootElements())) {
                record(type);
                ElementFilter.fieldsIn(type.getEnclosedElements()).forEach(this::record);
                ElementFilter.typesIn(type.getEnclosedElements()).forEach(this::record);
            }
            return false;
        }

        private void record(Element element) {
            String comment = processingEnv.getElementUtils().getDocComment(element);
            if (comment != null) {
                comments.put(element.getSimpleName().toString(), comment);
            }
        }
    }
}
