package be.imgn.gaia.processing.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.lang.model.element.Modifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaFileTest {

    private static final ClassName NULLABLE = ClassName.of("org.jspecify.annotations", "Nullable");

    private static String write(Type type) {
        return JavaFile.newBuilder("com.example", type).build().toString();
    }

    @Nested
    class Imports {

        @Test
        void importsReferencedTypesSortedOnce() {
            Type type = Type.newClassBuilder("Holder")
                    .addField(
                            ClassName.of(Map.class)
                                    .parameterized(
                                            ClassName.of(String.class),
                                            ClassName.of(List.class).parameterized(ClassName.of(Integer.class))),
                            "map")
                    .addField(ClassName.of(List.class).parameterized(ClassName.of(String.class)), "list")
                    .addField(ClassName.of("com.example.other", "Thing"), "thing")
                    .build();

            assertThat(write(type)).isEqualTo("""
                    package com.example;

                    import com.example.other.Thing;
                    import java.util.List;
                    import java.util.Map;

                    class Holder {
                        Map<String, List<Integer>> map;
                        List<String> list;
                        Thing thing;
                    }
                    """);
        }

        @Test
        void doesNotImportJavaLangOrTheSamePackage() {
            Type type = Type.newClassBuilder("Holder")
                    .addField(String.class, "text")
                    .addField(ClassName.of("com.example", "Sibling"), "sibling")
                    .build();

            assertThat(write(type)).isEqualTo("""
                    package com.example;

                    class Holder {
                        String text;
                        Sibling sibling;
                    }
                    """);
        }

        @Test
        void qualifiesTypesThatShareASimpleName() {
            Type type = Type.newClassBuilder("Holder")
                    .addField(ClassName.of(List.class), "utilList")
                    .addField(ClassName.of("java.awt", "List"), "awtList")
                    .build();

            assertThat(write(type)).isEqualTo("""
                    package com.example;

                    class Holder {
                        java.util.List utilList;
                        java.awt.List awtList;
                    }
                    """);
        }

        @Test
        void qualifiesTypesShadowedByDeclaredTypes() {
            Type type = Type.newClassBuilder("Holder")
                    .addField(ClassName.of(Map.Entry.class), "entry")
                    .addField(ClassName.of("com.example", "Holder", "Entry"), "own")
                    .addField(ClassName.of(String.class), "text")
                    .addType(Type.newClassBuilder("Entry").build())
                    .addType(Type.newClassBuilder("String").build())
                    .build();

            assertThat(write(type)).isEqualTo("""
                    package com.example;

                    import java.util.Map;

                    class Holder {
                        Map.Entry entry;
                        Entry own;
                        java.lang.String text;

                        class Entry {
                        }

                        class String {
                        }
                    }
                    """);
        }

        @Test
        void qualifiesTypesShadowedByTypeVariables() {
            Type type = Type.newClassBuilder("Box")
                    .addTypeVariable(TypeVariableName.of("T"))
                    .addField(TypeVariableName.of("T"), "value")
                    .addField(ClassName.of("com.example.other", "T"), "other")
                    .build();

            assertThat(write(type)).isEqualTo("""
                    package com.example;

                    class Box<T> {
                        T value;
                        com.example.other.T other;
                    }
                    """);
        }

        @Test
        void keepsTheSimpleNameForTheOwnType() {
            Type type = Type.newClassBuilder("List")
                    .addField(ClassName.of("com.example", "List"), "self")
                    .addField(ClassName.of(List.class), "other")
                    .build();

            assertThat(write(type)).isEqualTo("""
                    package com.example;

                    class List {
                        List self;
                        java.util.List other;
                    }
                    """);
        }

        @Test
        void writesTypeAnnotationsOnQualifiedNames() {
            Type type = Type.newClassBuilder("Holder")
                    .addField(ClassName.of(String.class).annotated(Annotation.of(NULLABLE)), "text")
                    .addField(ClassName.of("java.awt", "List").annotated(Annotation.of(NULLABLE)), "awt")
                    .addField(ClassName.of(List.class).annotated(Annotation.of(NULLABLE)), "util")
                    .build();

            assertThat(write(type)).isEqualTo("""
                    package com.example;

                    import org.jspecify.annotations.Nullable;

                    class Holder {
                        @Nullable String text;
                        java.awt.@Nullable List awt;
                        java.util.@Nullable List util;
                    }
                    """);
        }

        @Test
        void writesStaticImportsAFileCommentAndAnIndent() {
            Type type = Type.newClassBuilder("Checks")
                    .addMethod(Method.newBuilder("check")
                            .addParameter(Object.class, "value")
                            .addStatement("requireNonNull(value)")
                            .build())
                    .build();
            JavaFile file = JavaFile.newBuilder("com.example", type)
                    .addFileComment("Generated by a test.\nDo not edit.")
                    .addStaticImport(ClassName.of(Objects.class), "requireNonNull")
                    .addStaticImport(ClassName.of("java.util", "Collections"), "*")
                    .indent("\t")
                    .build();

            assertThat(file).hasToString("""
                    // Generated by a test.
                    // Do not edit.

                    package com.example;

                    import static java.util.Collections.*;
                    import static java.util.Objects.requireNonNull;

                    class Checks {
                    \tvoid check(Object value) {
                    \t\trequireNonNull(value);
                    \t}
                    }
                    """);
        }

        @Test
        void writesAFileInTheUnnamedPackage() {
            assertThat(JavaFile.newBuilder("", Type.newClassBuilder("Top").build())
                            .build())
                    .hasToString("""
                            class Top {
                            }
                            """);
        }

        @Test
        void importsTypesNamedInComments() {
            Type type = Type.newClassBuilder("Top")
                    .addMethod(Method.newBuilder("run")
                            .addComment("fills a $T", List.class)
                            .build())
                    .build();

            assertThat(write(type)).isEqualTo("""
                    package com.example;

                    import java.util.List;

                    class Top {
                        void run() {
                            // fills a List
                        }
                    }
                    """);
        }

        @Test
        void writesToAnAppendable() throws IOException {
            StringBuilder out = new StringBuilder();

            JavaFile.newBuilder("com.example", Type.newClassBuilder("Top").build())
                    .build()
                    .writeTo(out);

            assertThat(out).hasToString("package com.example;\n\nclass Top {\n}\n");
        }

        @Test
        void writesUnderASourceRootInUtf8(@TempDir Path root) throws IOException {
            JavaFile file = JavaFile.newBuilder(
                            "com.example",
                            Type.newClassBuilder("Top")
                                    .addField(Field.newBuilder(String.class, "GREETING", Modifier.STATIC)
                                            .initializer("$S", "Salut, ça va ?")
                                            .build())
                                    .build())
                    .build();

            Path written = file.writeTo(root);

            assertThat(written).isEqualTo(root.resolve("com").resolve("example").resolve("Top.java"));
            assertThat(Files.readString(written, StandardCharsets.UTF_8)).isEqualTo(file.toString());
            assertThat(file.toString()).contains("\"Salut, ça va ?\"");
        }

        @Test
        void writesUnderASourceRootInACharset(@TempDir Path root) throws IOException {
            JavaFile file = JavaFile.newBuilder(
                            "com.example",
                            Type.newClassBuilder("Top")
                                    .addField(Field.newBuilder(String.class, "CAFE", Modifier.STATIC)
                                            .initializer("$S", "café")
                                            .build())
                                    .build())
                    .build();

            Path written = file.writeTo(root, StandardCharsets.ISO_8859_1);

            assertThat(Files.readString(written, StandardCharsets.ISO_8859_1)).isEqualTo(file.toString());
            assertThat(Files.size(written)).isEqualTo(file.toString().length());
            assertThatExceptionOfType(UnmappableCharacterException.class)
                    .isThrownBy(() -> file.writeTo(root, StandardCharsets.US_ASCII));
        }

        @Test
        void writesTheUnnamedPackageAtTheSourceRoot(@TempDir Path root) throws IOException {
            Path written = JavaFile.newBuilder("", Type.newClassBuilder("Top").build())
                    .build()
                    .writeTo(root);

            assertThat(written).isEqualTo(root.resolve("Top.java"));
            assertThat(written).hasContent("class Top {\n}\n");
        }

        @Test
        void replacesAnExistingFile(@TempDir Path root) throws IOException {
            Path existing = Files.createDirectories(root.resolve("com").resolve("example"))
                    .resolve("Top.java");
            Files.writeString(existing, "class Old { /* a longer file than the new one */ }\n");

            JavaFile.newBuilder("com.example", Type.newClassBuilder("Top").build())
                    .build()
                    .writeTo(root);

            assertThat(existing).hasContent("package com.example;\n\nclass Top {\n}\n");
        }
    }

    @Nested
    class Classes {

        @Test
        void writesMembersInOrderWithJavadoc() {
            Type type = Type.newClassBuilder("Counter")
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .addJavadoc("Counts things.\n\n<p>Not thread-safe.\n")
                    .addSuperinterface(Serializable.class)
                    .addField(Field.newBuilder(
                                    PrimitiveTypeName.LONG,
                                    "serialVersionUID",
                                    Modifier.PRIVATE,
                                    Modifier.STATIC,
                                    Modifier.FINAL)
                            .initializer("$LL", 1)
                            .build())
                    .addField(Field.newBuilder(PrimitiveTypeName.INT, "count", Modifier.PRIVATE)
                            .addJavadoc("The count.\n")
                            .build())
                    .addStaticBlock(CodeBlock.of("$T.out.println($S);\n", System.class, "loaded"))
                    .addInitializerBlock(CodeBlock.of("count = 1;\n"))
                    .addMethod(Method.newBuilder("increment")
                            .addModifiers(Modifier.PUBLIC)
                            .returns(int.class)
                            .addStatement("return ++count")
                            .build())
                    .addMethod(Method.newConstructorBuilder()
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(PrimitiveTypeName.INT, "start", Modifier.FINAL)
                            .addStatement("this.count = start")
                            .build())
                    .build();

            assertThat(write(type)).isEqualTo("""
                    package com.example;

                    import java.io.Serializable;

                    /**
                     * Counts things.
                     *
                     * <p>Not thread-safe.
                     */
                    public final class Counter implements Serializable {
                        private static final long serialVersionUID = 1L;
                        /**
                         * The count.
                         */
                        private int count;

                        static {
                            System.out.println("loaded");
                        }

                        {
                            count = 1;
                        }

                        public Counter(final int start) {
                            this.count = start;
                        }

                        public int increment() {
                            return ++count;
                        }
                    }
                    """);
        }

        @Test
        void writesGenericsBoundsVarargsAndExceptions() {
            TypeVariableName t =
                    TypeVariableName.of("T", ClassName.of(Comparable.class).parameterized(TypeVariableName.of("T")));
            Type type = Type.newClassBuilder("Sorter")
                    .addModifiers(Modifier.ABSTRACT)
                    .addTypeVariable(
                            TypeVariableName.of("E", ClassName.of(Number.class), ClassName.of(Serializable.class)))
                    .superclass(ClassName.of("com.example", "Base").parameterized(TypeVariableName.of("E")))
                    .addMethod(Method.newBuilder("max")
                            .addModifiers(Modifier.STATIC)
                            .addTypeVariable(t)
                            .returns(t)
                            .addParameter(ArrayTypeName.of(t), "values")
                            .varargs(true)
                            .addException(IOException.class)
                            .addStatement("return values[0]")
                            .build())
                    .addMethod(Method.newBuilder("sort")
                            .addModifiers(Modifier.PROTECTED, Modifier.ABSTRACT)
                            .addParameter(
                                    ClassName.of(List.class)
                                            .parameterized(WildcardTypeName.supertypeOf(TypeVariableName.of("E"))),
                                    "list")
                            .build())
                    .build();

            assertThat(write(type)).isEqualTo("""
                    package com.example;

                    import java.io.IOException;
                    import java.io.Serializable;
                    import java.util.List;

                    abstract class Sorter<E extends Number & Serializable> extends Base<E> {
                        static <T extends Comparable<T>> T max(T... values) throws IOException {
                            return values[0];
                        }

                        protected abstract void sort(List<? super E> list);
                    }
                    """);
        }

        @Test
        void writesAnnotatedVarargsAndArrays() {
            Annotation nullable = Annotation.of(NULLABLE);
            Type type = Type.newClassBuilder("Arrays")
                    .addMethod(Method.newBuilder("of")
                            .addParameter(
                                    ArrayTypeName.of(ClassName.of(String.class).annotated(nullable))
                                            .annotated(nullable),
                                    "values")
                            .varargs(true)
                            .build())
                    .addField(ArrayTypeName.of(ArrayTypeName.of(PrimitiveTypeName.INT)), "grid")
                    .build();

            assertThat(write(type)).isEqualTo("""
                    package com.example;

                    import org.jspecify.annotations.Nullable;

                    class Arrays {
                        int[][] grid;

                        void of(@Nullable String @Nullable ... values) {
                        }
                    }
                    """);
        }

        @Test
        void writesAnnotationsOnTypesMethodsFieldsAndParameters() {
            Type type = Type.newClassBuilder("Annotated")
                    .addAnnotation(Annotation.newBuilder(SuppressWarnings.class)
                            .addMember("value", "$S", "all")
                            .build())
                    .addAnnotation(Deprecated.class)
                    .addField(Field.newBuilder(String.class, "name")
                            .addAnnotation(Deprecated.class)
                            .build())
                    .addMethod(Method.newBuilder("toString")
                            .addAnnotation(Override.class)
                            .addModifiers(Modifier.PUBLIC)
                            .returns(String.class)
                            .addParameter(Parameter.newBuilder(int.class, "unused")
                                    .addAnnotation(Deprecated.class)
                                    .build())
                            .addStatement("return name")
                            .build())
                    .build();

            assertThat(write(type)).isEqualTo("""
                    package com.example;

                    @SuppressWarnings("all")
                    @Deprecated
                    class Annotated {
                        @Deprecated
                        String name;

                        @Override
                        public String toString(@Deprecated int unused) {
                            return name;
                        }
                    }
                    """);
        }
    }

    @Nested
    class Kinds {

        @Test
        void writesAnInterfaceWithoutImplicitModifiers() {
            Type type = Type.newInterfaceBuilder("Shape")
                    .addModifiers(Modifier.PUBLIC, Modifier.SEALED)
                    .addSuperinterface(Comparable.class)
                    .addPermittedSubclass(ClassName.of("com.example", "Circle"))
                    .addPermittedSubclass(ClassName.of("com.example", "Square"))
                    .addField(Field.newBuilder(
                                    PrimitiveTypeName.INT, "SIDES", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                            .initializer("$L", 0)
                            .build())
                    .addMethod(Method.newBuilder("area")
                            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                            .returns(double.class)
                            .build())
                    .addMethod(Method.newBuilder("describe")
                            .addModifiers(Modifier.DEFAULT)
                            .returns(String.class)
                            .addStatement("return $S + area()", "area ")
                            .build())
                    .addMethod(Method.newBuilder("unit")
                            .addModifiers(Modifier.STATIC)
                            .returns(String.class)
                            .addStatement("return $S", "m²")
                            .build())
                    .addMethod(Method.newBuilder("helper")
                            .addModifiers(Modifier.PRIVATE)
                            .build())
                    .addType(Type.newClassBuilder("Inner")
                            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                            .build())
                    .build();

            assertThat(write(type)).isEqualTo("""
                    package com.example;

                    public sealed interface Shape extends Comparable permits Circle, Square {
                        int SIDES = 0;

                        double area();

                        default String describe() {
                            return "area " + area();
                        }

                        static String unit() {
                            return "m²";
                        }

                        private void helper() {
                        }

                        class Inner {
                        }
                    }
                    """);
        }

        @Test
        void writesARecordWithComponentsAndACompactConstructor() {
            Type type = Type.newRecordBuilder("Point")
                    .addModifiers(Modifier.PUBLIC)
                    .addTypeVariable(TypeVariableName.of("T"))
                    .addRecordComponent(PrimitiveTypeName.INT, "x")
                    .addRecordComponent(
                            Parameter.newBuilder(ClassName.of(String.class).annotated(Annotation.of(NULLABLE)), "label")
                                    .addAnnotation(Deprecated.class)
                                    .build())
                    .addSuperinterface(Serializable.class)
                    .addField(Field.newBuilder(
                                    ClassName.of("com.example", "Point"),
                                    "ORIGIN",
                                    Modifier.PUBLIC,
                                    Modifier.STATIC,
                                    Modifier.FINAL)
                            .initializer("new Point(0, null)")
                            .build())
                    .addMethod(Method.newCompactConstructorBuilder()
                            .addModifiers(Modifier.PUBLIC)
                            .beginControlFlow("if (x < 0)")
                            .addStatement("throw new $T($S)", IllegalArgumentException.class, "negative")
                            .endControlFlow()
                            .build())
                    .addMethod(Method.newConstructorBuilder()
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(int.class, "x")
                            .addStatement("this(x, null)")
                            .build())
                    .addType(Type.newRecordBuilder("Pair")
                            .addModifiers(Modifier.STATIC)
                            .addRecordComponent(int.class, "a")
                            .build())
                    .build();

            assertThat(write(type)).isEqualTo("""
                    package com.example;

                    import java.io.Serializable;
                    import org.jspecify.annotations.Nullable;

                    public record Point<T>(int x, @Deprecated @Nullable String label) implements Serializable {
                        public static final Point ORIGIN = new Point(0, null);

                        public Point {
                            if (x < 0) {
                                throw new IllegalArgumentException("negative");
                            }
                        }

                        public Point(int x) {
                            this(x, null);
                        }

                        record Pair(int a) {
                        }
                    }
                    """);
        }

        @Test
        void writesAnEnumWithArgumentsAndBodies() {
            Type type = Type.newEnumBuilder("Operation")
                    .addModifiers(Modifier.PUBLIC)
                    .addEnumConstant(
                            "PLUS",
                            Type.newAnonymousClassBuilder("$S", "+")
                                    .addMethod(Method.newBuilder("apply")
                                            .addAnnotation(Override.class)
                                            .returns(int.class)
                                            .addParameter(int.class, "a")
                                            .addParameter(int.class, "b")
                                            .addStatement("return a + b")
                                            .build())
                                    .build())
                    .addEnumConstant(
                            "ZERO", Type.newAnonymousClassBuilder("$S", "0").build())
                    .addField(String.class, "symbol", Modifier.PRIVATE, Modifier.FINAL)
                    .addMethod(Method.newConstructorBuilder()
                            .addParameter(String.class, "symbol")
                            .addStatement("this.symbol = symbol")
                            .build())
                    .addMethod(Method.newBuilder("apply")
                            .returns(int.class)
                            .addParameter(int.class, "a")
                            .addParameter(int.class, "b")
                            .addStatement("return 0")
                            .build())
                    .build();

            assertThat(write(type)).isEqualTo("""
                    package com.example;

                    public enum Operation {
                        PLUS("+") {
                            @Override
                            int apply(int a, int b) {
                                return a + b;
                            }
                        },
                        ZERO("0");

                        private final String symbol;

                        Operation(String symbol) {
                            this.symbol = symbol;
                        }

                        int apply(int a, int b) {
                            return 0;
                        }
                    }
                    """);
        }

        @Test
        void writesSimpleAndEmptyEnums() {
            assertThat(write(Type.newEnumBuilder("Color")
                            .addEnumConstant("RED")
                            .addEnumConstant("GREEN")
                            .build()))
                    .isEqualTo("""
                            package com.example;

                            enum Color {
                                RED,
                                GREEN
                            }
                            """);
            assertThat(write(Type.newEnumBuilder("Nothing")
                            .addMethod(Method.newBuilder("run")
                                    .addModifiers(Modifier.STATIC)
                                    .build())
                            .build()))
                    .isEqualTo("""
                            package com.example;

                            enum Nothing {
                                ;

                                static void run() {
                                }
                            }
                            """);
        }

        @Test
        void writesAnAnnotationTypeWithDefaults() {
            Type type = Type.newAnnotationBuilder("Mapping")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Annotation.newBuilder(ClassName.of("java.lang.annotation", "Retention"))
                            .addMember("value", "$T.RUNTIME", ClassName.of("java.lang.annotation", "RetentionPolicy"))
                            .build())
                    .addMethod(Method.newBuilder("value")
                            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                            .returns(String.class)
                            .build())
                    .addMethod(Method.newBuilder("aliases")
                            .returns(ArrayTypeName.of(ClassName.of(String.class)))
                            .defaultValue("{}")
                            .build())
                    .addMethod(Method.newBuilder("target")
                            .returns(ClassName.of(Class.class).parameterized(WildcardTypeName.unbounded()))
                            .defaultValue("$T.class", Object.class)
                            .build())
                    .build();

            assertThat(write(type)).isEqualTo("""
                    package com.example;

                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;

                    @Retention(RetentionPolicy.RUNTIME)
                    public @interface Mapping {
                        String value();

                        String[] aliases() default {};

                        Class<?> target() default Object.class;
                    }
                    """);
        }

        @Test
        void writesSealedAndNonSealedClasses() {
            Type type = Type.newClassBuilder("Vehicle")
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT, Modifier.SEALED)
                    .addPermittedSubclass(ClassName.of("com.example", "Vehicle", "Car"))
                    .addType(Type.newClassBuilder("Car")
                            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.NON_SEALED)
                            .superclass(ClassName.of("com.example", "Vehicle"))
                            .build())
                    .build();

            assertThat(write(type)).isEqualTo("""
                    package com.example;

                    public abstract sealed class Vehicle permits Vehicle.Car {
                        public static non-sealed class Car extends Vehicle {
                        }
                    }
                    """);
        }
    }

    @Nested
    class Edges {

        @Test
        void importsTypesNamedInJavadoc() {
            Type type = Type.newClassBuilder("Documented")
                    .addJavadoc("Wraps a {@link $T}.\n", Map.class)
                    .build();

            assertThat(write(type)).isEqualTo("""
                    package com.example;

                    import java.util.Map;

                    /**
                     * Wraps a {@link Map}.
                     */
                    class Documented {
                    }
                    """);
        }

        @Test
        void indentsAnonymousClassesInsideMethodBodies() {
            Type task = Type.newAnonymousClassBuilder("")
                    .addSuperinterface(Runnable.class)
                    .addMethod(Method.newBuilder("run")
                            .addAnnotation(Override.class)
                            .addModifiers(Modifier.PUBLIC)
                            .beginControlFlow("if (ready)")
                            .addStatement("go()")
                            .endControlFlow()
                            .build())
                    .build();
            Type type = Type.newClassBuilder("Scheduler")
                    .addMethod(Method.newBuilder("schedule")
                            .addParameter(boolean.class, "ready")
                            .addStatement("$T task = $L", Runnable.class, task)
                            .addStatement("task.run()")
                            .build())
                    .build();

            assertThat(write(type)).isEqualTo("""
                    package com.example;

                    class Scheduler {
                        void schedule(boolean ready) {
                            Runnable task = new Runnable() {
                                @Override
                                public void run() {
                                    if (ready) {
                                        go();
                                    }
                                }
                            };
                            task.run();
                        }
                    }
                    """);
        }

        @Test
        void qualifiesTypesShadowedByMethodTypeVariables() {
            Type type = Type.newClassBuilder("Converter")
                    .addMethod(Method.newBuilder("convert")
                            .addTypeVariable(TypeVariableName.of("Entry"))
                            .returns(TypeVariableName.of("Entry"))
                            .addParameter(ClassName.of(Map.Entry.class), "raw")
                            .addParameter(ClassName.of("com.example.other", "Entry"), "other")
                            .addStatement("return null")
                            .build())
                    .build();

            assertThat(write(type)).isEqualTo("""
                    package com.example;

                    import java.util.Map;

                    class Converter {
                        <Entry> Entry convert(Map.Entry raw, com.example.other.Entry other) {
                            return null;
                        }
                    }
                    """);
        }

        @Test
        void leavesOutTheImplicitModifiersOfTypesNestedInAnInterface() {
            Type type = Type.newInterfaceBuilder("Api")
                    .addType(Type.newEnumBuilder("Mode")
                            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                            .addEnumConstant("ON")
                            .build())
                    .addType(Type.newRecordBuilder("Value")
                            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                            .addRecordComponent(int.class, "v")
                            .build())
                    .addType(Type.newInterfaceBuilder("Listener")
                            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                            .build())
                    .build();

            assertThat(write(type)).isEqualTo("""
                    package com.example;

                    interface Api {
                        enum Mode {
                            ON
                        }

                        record Value(int v) {
                        }

                        interface Listener {
                        }
                    }
                    """);
        }

        @Test
        void writesEmptyAndBlankJavadocLinesWithoutTrailingSpaces() {
            Type type =
                    Type.newClassBuilder("Doc").addJavadoc("First.\n\nSecond.").build();

            assertThat(write(type))
                    .contains("/**\n * First.\n *\n * Second.\n */\n")
                    .doesNotContain(" \n");
        }

        @Test
        void leavesOutModifiersImpliedByTheKindOfType() {
            assertThat(write(Type.newEnumBuilder("Mode")
                            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                            .addEnumConstant("ON")
                            .build()))
                    .contains("public enum Mode {");
            assertThat(write(Type.newRecordBuilder("Value")
                            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                            .build()))
                    .contains("public record Value() {");
            assertThat(write(Type.newInterfaceBuilder("Api")
                            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                            .build()))
                    .contains("public interface Api {");
            assertThat(write(Type.newClassBuilder("Base")
                            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                            .build()))
                    .contains("public abstract class Base {");
        }
    }
}
