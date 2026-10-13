package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.StepProcessor;
import be.imgn.gaia.processing.source.Annotation;
import be.imgn.gaia.processing.source.ClassName;
import be.imgn.gaia.processing.source.JavaFile;
import be.imgn.gaia.processing.source.Method;
import be.imgn.gaia.processing.source.Parameter;
import be.imgn.gaia.processing.source.Type;
import be.imgn.gaia.processing.source.TypeName;
import be.imgn.gaia.processing.testing.Compilation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

/** Builds declarations from elements javac compiled, and checks they write back as the source said. */
class SourceModelRoundTripTest {

    private static final List<JavaFileObject> ANNOTATIONS = List.of(
            source("example.Mark", """
                    package example;

                    @interface Mark {}
                    """),
            source("example.Nullable", """
                    package example;

                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Target;

                    @Target(ElementType.TYPE_USE)
                    @interface Nullable {}
                    """),
            source("example.Nested", """
                    package example;

                    @interface Nested {
                        String value();
                    }
                    """),
            source("example.Everything", """
                    package example;

                    import java.lang.annotation.RetentionPolicy;

                    @interface Everything {
                        String text();
                        int number();
                        long big();
                        float ratio();
                        double precise();
                        char letter();
                        boolean flag();
                        byte small();
                        Class<?> type();
                        Class<?>[] types();
                        RetentionPolicy policy();
                        Nested nested();
                        Nested[] nesteds();
                        String[] empty();
                        double nan();
                    }
                    """));

    private static Compilation process(BiConsumer<ProcessingContext, TypeElement> step, JavaFileObject... sources) {
        List<JavaFileObject> all = new ArrayList<>(ANNOTATIONS);
        all.addAll(List.of(sources));
        return javac().withProcessors(new Running(step))
                .withOptions("-Xlint:all,-processing", "-Werror")
                .compile(all);
    }

    @Test
    void namesEveryKindOfType() {
        Map<String, String> names = new LinkedHashMap<>();
        Compilation compilation = process(
                (context, type) -> ElementFilter.fieldsIn(type.getEnclosedElements())
                        .forEach(field -> names.put(
                                field.getSimpleName().toString(),
                                TypeName.of(field.asType()).toString())),
                source("example.Types", """
                        package example;

                        import java.util.List;
                        import java.util.Map;

                        @Mark
                        class Types<T extends Comparable<T>> {
                            int primitive;
                            String text;
                            List<String> list;
                            Map<String, List<? extends Number>> nested;
                            Map.Entry<? super Integer, ?> entry;
                            int[][] grid;
                            T variable;
                            @Nullable String annotated;
                            List<@Nullable String> annotatedArgument;
                            String @Nullable [] annotatedArray;
                        }
                        """));

        assertThat(compilation).succeeded();
        assertThat(names)
                .containsExactly(
                        Map.entry("primitive", "int"),
                        Map.entry("text", "java.lang.String"),
                        Map.entry("list", "java.util.List<java.lang.String>"),
                        Map.entry(
                                "nested",
                                "java.util.Map<java.lang.String, java.util.List<? extends java.lang.Number>>"),
                        Map.entry("entry", "java.util.Map.Entry<? super java.lang.Integer, ?>"),
                        Map.entry("grid", "int[][]"),
                        Map.entry("variable", "T"),
                        Map.entry("annotated", "java.lang.@example.Nullable String"),
                        Map.entry("annotatedArgument", "java.util.List<java.lang.@example.Nullable String>"),
                        Map.entry("annotatedArray", "java.lang.String @example.Nullable []"));
    }

    @Test
    void copiesAnnotationsWithEveryKindOfValueAndTheCopyCompiles() {
        List<String> copies = new ArrayList<>();
        Compilation compilation = process(
                (context, type) -> {
                    Annotation everything = context.annotation(type, "example.Everything")
                            .orElseThrow()
                            .toAnnotation();
                    copies.add(everything.toString());
                    context.sources()
                            .write(
                                    JavaFile.newBuilder(
                                                    "example",
                                                    Type.newClassBuilder("Copy")
                                                            .addAnnotation(everything)
                                                            .build())
                                            .build(),
                                    type);
                },
                source("example.Annotated", """
                        package example;

                        import java.lang.annotation.RetentionPolicy;

                        @Mark
                        @Everything(
                                text = "say \\"hi\\"",
                                number = 42,
                                big = 7L,
                                ratio = 1.5f,
                                precise = 2.5,
                                letter = '\\'',
                                flag = true,
                                small = 3,
                                type = String.class,
                                types = {Integer.class, Long.class},
                                policy = RetentionPolicy.RUNTIME,
                                nested = @Nested("inner"),
                                nesteds = {@Nested("a"), @Nested("b")},
                                empty = {},
                                nan = Double.NaN)
                        class Annotated {}
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(copies)
                .containsExactly(String.join(
                        ", ",
                        "@example.Everything(text = \"say \\\"hi\\\"\"",
                        "number = 42",
                        "big = 7L",
                        "ratio = 1.5f",
                        "precise = 2.5",
                        "letter = '\\''",
                        "flag = true",
                        "small = 3",
                        "type = java.lang.String.class",
                        "types = {java.lang.Integer.class, java.lang.Long.class}",
                        "policy = java.lang.annotation.RetentionPolicy.RUNTIME",
                        "nested = @example.Nested(\"inner\")",
                        "nesteds = {@example.Nested(\"a\"), @example.Nested(\"b\")}",
                        "empty = {}",
                        "nan = java.lang.Double.NaN)"));
        assertThat(compilation)
                .generatedSourceFile("example.Copy")
                .contains("@Everything(")
                .contains("RetentionPolicy.RUNTIME");
    }

    @Test
    void overridesTheMethodsOfAGenericInterface() {
        Compilation compilation = process(
                (context, type) -> {
                    DeclaredType ofString = context.types()
                            .getDeclaredType(
                                    type,
                                    context.elements()
                                            .getTypeElement("java.lang.String")
                                            .asType());
                    Type.Builder implementation = Type.newClassBuilder("StringRepository")
                            .addModifiers(Modifier.FINAL)
                            .addSuperinterface(TypeName.of(ofString));
                    for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
                        if (method.getModifiers().contains(Modifier.ABSTRACT)) {
                            implementation.addMethod(Method.overriding(method, ofString, context.types())
                                    .addStatement("throw new $T()", UnsupportedOperationException.class)
                                    .build());
                        }
                    }
                    context.sources()
                            .write(
                                    JavaFile.newBuilder("example", implementation.build())
                                            .build(),
                                    type);
                },
                source("example.Repository", """
                        package example;

                        import java.io.IOException;
                        import java.util.List;
                        import java.util.function.Function;

                        @Mark
                        interface Repository<T> {
                            T find(String id) throws IOException;

                            <R extends Comparable<R>> List<R> map(Function<? super T, R> mapper);

                            void save(String... items);

                            default void close() {}
                        }
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("example.StringRepository")
                .contains("final class StringRepository implements Repository<String> {")
                .contains("    @Override\n    public String find(String id) throws IOException {")
                .contains("    public <R extends Comparable<R>> List<R> map(Function<? super String, R> mapper) {")
                .contains("    public void save(String... items) {")
                .doesNotContain("close");
    }

    @Test
    void writesAJavaFileWithGeneratedOnceAndOnlyOnce() {
        List<Boolean> results = new ArrayList<>();
        Compilation compilation = process(
                (context, type) -> {
                    JavaFile file = JavaFile.newBuilder(
                                    "example", Type.newClassBuilder("Out").build())
                            .build();
                    results.add(context.sources().write(file, type));
                    results.add(context.sources().write(file, type));
                    Type annotated = Type.newClassBuilder("Already")
                            .addAnnotation(
                                    Annotation.newBuilder(ClassName.of("javax.annotation.processing", "Generated"))
                                            .addMember("value", "$S", "elsewhere")
                                            .build())
                            .build();
                    results.add(context.sources()
                            .write(JavaFile.newBuilder("example", annotated).build(), type));
                },
                source("example.Seed", """
                        package example;

                        @Mark
                        class Seed {}
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(results).containsExactly(true, false, true);
        assertThat(compilation).generatedSourceFile("example.Out").isEqualTo("""
                        package example;

                        import javax.annotation.processing.Generated;

                        @Generated("%s")
                        class Out {
                        }
                        """.formatted(Running.class.getName()));
        assertThat(compilation)
                .generatedSourceFile("example.Already")
                .contains("@Generated(\"elsewhere\")")
                .doesNotContain(Running.class.getName());
    }

    @Test
    void leavesOutGeneratedForAModuleThatCannotSeeIt() {
        Compilation compilation = process(
                (context, type) -> context.sources()
                        .write(
                                JavaFile.newBuilder(
                                                "example",
                                                Type.newClassBuilder("Out").build())
                                        .build(),
                                type),
                source("module-info", "module example {}"),
                source("example.Seed", """
                        package example;

                        @Mark
                        class Seed {}
                        """));

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("example.Out").isEqualTo("package example;\n\nclass Out {\n}\n");
    }

    @Test
    void convertsReadAnnotationsForGeneratedCode() {
        List<Boolean> same = new ArrayList<>();
        process(
                (context, type) -> {
                    var reader = context.annotation(type, "example.Nested").orElseThrow();
                    same.add(reader.toAnnotation().equals(Annotation.of(reader.mirror())));
                    same.add(reader.toAnnotation().toString().equals("@example.Nested(\"x\")"));
                },
                source("example.Seed", """
                        package example;

                        @Mark
                        @Nested("x")
                        class Seed {}
                        """));

        assertThat(same).containsExactly(true, true);
    }

    @Test
    void overridesGenericMethodsWithTheBoundsOfTheParameterizedType() {
        List<String> rejected = new ArrayList<>();
        Compilation compilation = process(
                (context, type) -> {
                    DeclaredType ofDouble = context.types()
                            .getDeclaredType(
                                    type,
                                    context.elements()
                                            .getTypeElement("java.lang.Double")
                                            .asType());
                    Type.Builder implementation =
                            Type.newClassBuilder("Doubles").addSuperinterface(ofDouble);
                    for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
                        if (method.getModifiers().contains(Modifier.ABSTRACT)) {
                            implementation.addMethod(Method.overriding(method, ofDouble, context.types())
                                    .addStatement("throw new $T()", UnsupportedOperationException.class)
                                    .build());
                        } else {
                            try {
                                Method.overriding(method);
                            } catch (IllegalArgumentException e) {
                                rejected.add(method.getSimpleName().toString());
                            }
                        }
                    }
                    context.sources()
                            .write(
                                    JavaFile.newBuilder("example", implementation.build())
                                            .build(),
                                    type);
                },
                source("example.Extensible", """
                        package example;

                        import java.util.List;

                        @Mark
                        interface Extensible<T extends Number> {
                            <S extends T> void add(S value);

                            <S extends Comparable<S> & Runnable> List<? super T> mix(S value, T other) throws Exception;

                            static void helper() {}

                            private void hidden() {}
                        }
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("example.Doubles")
                .contains("    public <S extends Double> void add(S value) {")
                .contains("    public <S extends Comparable<S> & Runnable> List<? super Double> mix("
                        + "S value, Double other) throws Exception {");
        assertThat(rejected).containsExactlyInAnyOrder("helper", "hidden");
    }

    @Test
    void refusesToOverrideMethodsOfFinalTypesAndFinalMethods() {
        List<String> messages = new ArrayList<>();
        process(
                (context, type) -> {
                    for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
                        try {
                            Method.overriding(method);
                        } catch (IllegalArgumentException e) {
                            messages.add(method.getSimpleName() + ": " + e.getMessage());
                        }
                    }
                    for (ExecutableElement constructor : ElementFilter.constructorsIn(type.getEnclosedElements())) {
                        try {
                            Method.overriding(constructor);
                        } catch (IllegalArgumentException e) {
                            messages.add("constructor: " + e.getMessage());
                        }
                    }
                },
                source("example.Sealed", """
                        package example;

                        @Mark
                        final class Sealed {
                            public void open() {}
                        }
                        """),
                source("example.Closed", """
                        package example;

                        @Mark
                        class Closed {
                            public final void close() {}
                        }
                        """));

        assertThat(messages)
                .anySatisfy(
                        message -> assertThat(message).startsWith("open: Cannot override a method of the final type"))
                .anySatisfy(message -> assertThat(message).startsWith("close: Cannot override a final method"))
                .anySatisfy(message -> assertThat(message).startsWith("constructor: Only a method can be overridden"));
    }

    @Test
    void namesInnerClassesOfParameterizedTypesAndCopiesThem() {
        Map<String, String> names = new LinkedHashMap<>();
        Compilation compilation = process(
                (context, type) -> {
                    Type.Builder copy = Type.newClassBuilder("Copy");
                    for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
                        TypeName name = TypeName.of(field.asType());
                        names.put(field.getSimpleName().toString(), name.toString());
                        copy.addField(name, field.getSimpleName().toString());
                    }
                    context.sources()
                            .write(JavaFile.newBuilder("example", copy.build()).build(), type);
                },
                source("example.Outer", """
                        package example;

                        public class Outer<T> {
                            public class Inner {}

                            public class Pair<U> {}

                            public static class Static {}
                        }
                        """),
                source("example.Fields", """
                        package example;

                        @Mark
                        class Fields {
                            Outer<String>.Inner inner;
                            Outer<String>.Pair<Integer> pair;
                            Outer.Static plain;
                            Outer<@Nullable String>.@Nullable Inner annotated;
                        }
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(names)
                .containsExactly(
                        Map.entry("inner", "example.Outer<java.lang.String>.Inner"),
                        Map.entry("pair", "example.Outer<java.lang.String>.Pair<java.lang.Integer>"),
                        Map.entry("plain", "example.Outer.Static"),
                        Map.entry(
                                "annotated",
                                "example.Outer<java.lang.@example.Nullable String>.@example.Nullable Inner"));
        assertThat(compilation)
                .generatedSourceFile("example.Copy")
                .contains("    Outer<String>.Inner inner;\n")
                .contains("    Outer<String>.Pair<Integer> pair;\n");
    }

    @Test
    void qualifiesTypesThatMemberTypesOfMirroredSupertypesHide() {
        Compilation compilation = process(
                (context, type) -> {
                    ClassName optional = ClassName.of(Optional.class);
                    Type child = Type.newClassBuilder("Child")
                            .superclass(type.asType())
                            .addSuperinterface(context.elements()
                                    .getTypeElement("example.Contract")
                                    .asType())
                            .addMethod(Method.newBuilder("optional")
                                    .returns(optional.parameterized(ClassName.of(String.class)))
                                    .addStatement("return $T.empty()", optional)
                                    .build())
                            .addMethod(Method.newBuilder("pattern")
                                    .returns(Pattern.class)
                                    .addStatement("return $T.compile($S)", Pattern.class, "x")
                                    .build())
                            .build();
                    context.sources()
                            .write(JavaFile.newBuilder("example", child).build(), type);
                },
                source("example.Contract", """
                        package example;

                        interface Contract {
                            class Pattern {}
                        }
                        """),
                source("example.Parent", """
                        package example;

                        @Mark
                        class Parent extends Grandparent {}
                        """),
                source("example.Grandparent", """
                        package example;

                        class Grandparent {
                            static class Optional {}
                        }
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("example.Child")
                .contains("java.util.Optional<String> optional() {")
                .contains("java.util.regex.Pattern pattern() {");
    }

    @Test
    void qualifiesJavaLangTypesThatTypesOfThePackageHide() {
        Compilation compilation = process(
                (context, type) -> context.sources()
                        .write(
                                JavaFile.newBuilder(
                                                "example",
                                                Type.newClassBuilder("Shown")
                                                        .addField(String.class, "text")
                                                        .addMethod(Method.newBuilder("toString")
                                                                .addAnnotation(Override.class)
                                                                .addModifiers(Modifier.PUBLIC)
                                                                .returns(String.class)
                                                                .addStatement("return text")
                                                                .build())
                                                        .build())
                                        .build(),
                                type),
                source("example.Override", """
                        package example;

                        @Mark
                        class Override {}
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(compilation)
                .generatedSourceFile("example.Shown")
                .contains("    @java.lang.Override\n")
                .contains("    String text;");
    }

    @Test
    void copiesAnnotationsWithTheirDefaultValues() {
        List<String> copies = new ArrayList<>();
        Compilation compilation = process(
                (context, type) -> {
                    AnnotationMirror mirror = context.annotation(type, "example.Defaults")
                            .orElseThrow()
                            .mirror();
                    copies.add(Annotation.of(mirror).toString());
                    copies.add(Annotation.ofWithDefaults(mirror).toString());
                },
                source("example.Defaults", """
                        package example;

                        @interface Defaults {
                            String name();
                            int size() default 3;
                            String[] tags() default {"a", "b"};
                        }
                        """),
                source("example.Seed", """
                        package example;

                        @Mark
                        @Defaults(name = "x")
                        class Seed {}
                        """));

        assertThat(compilation).succeeded();
        assertThat(copies)
                .containsExactly(
                        "@example.Defaults(name = \"x\")",
                        "@example.Defaults(name = \"x\", size = 3, tags = {\"a\", \"b\"})");
    }

    @Test
    void copiesParametersButNotOtherVariables() {
        List<String> results = new ArrayList<>();
        process(
                (context, type) -> {
                    ExecutableElement method =
                            ElementFilter.methodsIn(type.getEnclosedElements()).getFirst();
                    results.add(Parameter.of(method.getParameters().getFirst()).toString());
                    VariableElement field =
                            ElementFilter.fieldsIn(type.getEnclosedElements()).getFirst();
                    try {
                        Parameter.of(field);
                    } catch (IllegalArgumentException e) {
                        results.add(e.getMessage());
                    }
                },
                source("example.Seed", """
                        package example;

                        @Mark
                        class Seed {
                            int count;

                            void run(final @Nullable String name) {}
                        }
                        """));

        assertThat(results)
                .containsExactly("final java.lang.@example.Nullable String name", "Not a parameter but a FIELD: count");
    }

    /** Runs a step on each type annotated with {@code @Mark}. */
    private static final class Running extends StepProcessor {

        private final BiConsumer<ProcessingContext, TypeElement> step;

        Running(BiConsumer<ProcessingContext, TypeElement> step) {
            this.step = step;
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
                    ElementFilter.typesIn(elements.annotatedWith("example.Mark"))
                            .forEach(type -> step.accept(context, type));
                    return Set.of();
                }
            });
        }
    }
}
