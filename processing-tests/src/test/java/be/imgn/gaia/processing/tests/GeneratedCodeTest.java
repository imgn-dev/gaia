package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.gaia.processing.source.Annotation;
import be.imgn.gaia.processing.source.ArrayTypeName;
import be.imgn.gaia.processing.source.ClassName;
import be.imgn.gaia.processing.source.CodeBlock;
import be.imgn.gaia.processing.source.Field;
import be.imgn.gaia.processing.source.JavaFile;
import be.imgn.gaia.processing.source.Method;
import be.imgn.gaia.processing.source.Parameter;
import be.imgn.gaia.processing.source.PrimitiveTypeName;
import be.imgn.gaia.processing.source.Type;
import be.imgn.gaia.processing.source.TypeName;
import be.imgn.gaia.processing.source.TypeVariableName;
import be.imgn.gaia.processing.source.WildcardTypeName;
import be.imgn.gaia.processing.testing.Compilation;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;

/** Compiles generated files with every warning enabled, then loads and runs the compiled classes. */
class GeneratedCodeTest {

    private static final ClassName NULLABLE = ClassName.of("example", "Nullable");

    private static final JavaFileObject NULLABLE_SOURCE = source("example.Nullable", """
            package example;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Target(ElementType.TYPE_USE)
            @Retention(RetentionPolicy.CLASS)
            public @interface Nullable {}
            """);

    private static Compilation compile(JavaFile... files) {
        List<JavaFileObject> sources = new ArrayList<>(List.of(NULLABLE_SOURCE));
        for (JavaFile file : files) {
            sources.add(file.toJavaFileObject());
        }
        Compilation compilation = javac().withOptions("-Xlint:all", "-Werror").compile(sources);
        assertThat(compilation).succeeded().hadNoWarnings();
        return compilation;
    }

    private static ClassLoader loader(Compilation compilation) {
        return new ClassLoader(GeneratedCodeTest.class.getClassLoader()) {
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
    }

    private static Class<?> load(Compilation compilation, String name) throws ClassNotFoundException {
        return loader(compilation).loadClass(name);
    }

    private static Object call(Class<?> type, String method, Class<?>[] parameterTypes, Object... args)
            throws ReflectiveOperationException {
        return type.getMethod(method, parameterTypes).invoke(null, args);
    }

    @Test
    void generatesAGenericUtilityClassThatRuns() throws ReflectiveOperationException {
        TypeVariableName t = TypeVariableName.of(
                "T",
                ClassName.of(Comparable.class).parameterized(WildcardTypeName.supertypeOf(TypeVariableName.of("T"))));
        Type calculator = Type.newClassBuilder("Calculator")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(Method.newConstructorBuilder()
                        .addModifiers(Modifier.PRIVATE)
                        .build())
                .addMethod(Method.newBuilder("max")
                        .addAnnotation(SafeVarargs.class)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .addTypeVariable(t)
                        .returns(t)
                        .addParameter(ArrayTypeName.of(t), "values")
                        .varargs(true)
                        .addStatement("$T max = values[0]", t)
                        .beginControlFlow("for ($T value : values)", t)
                        .beginControlFlow("if (value.compareTo(max) > 0)")
                        .addStatement("max = value")
                        .endControlFlow()
                        .endControlFlow()
                        .addStatement("return max")
                        .build())
                .addMethod(Method.newBuilder("describe")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(String.class)
                        .addParameter(Parameter.of(
                                ClassName.of(Map.class)
                                        .parameterized(
                                                ClassName.of(String.class),
                                                ClassName.of(List.class)
                                                        .parameterized(ClassName.of(Integer.class)
                                                                .annotated(Annotation.of(NULLABLE)))),
                                "map",
                                Modifier.FINAL))
                        .addStatement("$T<$T> lines = new $T<>()", List.class, String.class, ArrayList.class)
                        .beginControlFlow("for ($T.Entry<$T, ?> entry : map.entrySet())", Map.class, String.class)
                        .addStatement("lines.add(entry.getKey() + $S + entry.getValue())", "=")
                        .endControlFlow()
                        .addStatement("return $T.join($S, lines)", String.class, "; ")
                        .build())
                .build();

        Class<?> type = load(compile(JavaFile.newBuilder("example", calculator).build()), "example.Calculator");

        assertThat(call(type, "max", new Class<?>[] {Comparable[].class}, (Object) new Integer[] {3, 9, 2}))
                .isEqualTo(9);
        assertThat(call(type, "describe", new Class<?>[] {Map.class}, Map.of("a", List.of(1, 2))))
                .isEqualTo("a=[1, 2]");
    }

    @Test
    void generatesRecordsAndASealedHierarchyThatRun() throws ReflectiveOperationException {
        ClassName shape = ClassName.of("example", "Shape");
        ClassName circle = shape.nested("Circle");
        ClassName square = shape.nested("Square");
        Type circleType = Type.newRecordBuilder("Circle")
                .addModifiers(Modifier.PUBLIC)
                .addRecordComponent(double.class, "radius")
                .addSuperinterface(shape)
                .addMethod(Method.newCompactConstructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .beginControlFlow("if (radius < 0)")
                        .addStatement("throw new $T($S + radius)", IllegalArgumentException.class, "negative radius: ")
                        .endControlFlow()
                        .build())
                .build();
        Type squareType = Type.newRecordBuilder("Square")
                .addModifiers(Modifier.PUBLIC)
                .addRecordComponent(double.class, "side")
                .addSuperinterface(shape)
                .build();
        Type shapeType = Type.newInterfaceBuilder("Shape")
                .addModifiers(Modifier.PUBLIC, Modifier.SEALED)
                .addPermittedSubclass(circle)
                .addPermittedSubclass(square)
                .addMethod(Method.newBuilder("area")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(double.class)
                        .addParameter(shape, "shape")
                        .addCode(CodeBlock.newBuilder()
                                .add("return switch (shape) {\n$>")
                                .addStatement("case $T c -> $T.PI * c.radius() * c.radius()", circle, Math.class)
                                .addStatement("case $T s -> s.side() * s.side()", square)
                                .add("$<};\n")
                                .build())
                        .build())
                .addType(circleType)
                .addType(squareType)
                .build();

        ClassLoader loader =
                loader(compile(JavaFile.newBuilder("example", shapeType).build()));
        Class<?> type = loader.loadClass("example.Shape");
        Class<?> squareClass = loader.loadClass("example.Shape$Square");
        Object unitSquare = squareClass.getConstructor(double.class).newInstance(2.0);
        Class<?> circleClass = loader.loadClass("example.Shape$Circle");

        assertThat(squareClass.isRecord()).isTrue();
        assertThat(type.isSealed()).isTrue();
        assertThat(call(type, "area", new Class<?>[] {type}, unitSquare)).isEqualTo(4.0);
        assertThat(unitSquare).hasToString("Square[side=2.0]");
        assertThatThrownBy(() -> circleClass.getConstructor(double.class).newInstance(-1.0))
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("negative radius: -1.0");
    }

    @Test
    void generatesAnEnumWithConstantBodiesThatRuns() throws ReflectiveOperationException {
        ClassName binaryOperator = ClassName.of("java.util.function", "IntBinaryOperator");
        Type operation = Type.newEnumBuilder("Operation")
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(binaryOperator)
                .addEnumConstant(
                        "PLUS",
                        Type.newAnonymousClassBuilder("$S", "+")
                                .addMethod(applyAsInt("return left + right"))
                                .build())
                .addEnumConstant(
                        "TIMES",
                        Type.newAnonymousClassBuilder("$S", "*")
                                .addMethod(applyAsInt("return left * right"))
                                .build())
                .addField(String.class, "symbol", Modifier.PRIVATE, Modifier.FINAL)
                .addMethod(Method.newConstructorBuilder()
                        .addParameter(String.class, "symbol")
                        .addStatement("this.symbol = symbol")
                        .build())
                .addMethod(Method.newBuilder("symbol")
                        .addModifiers(Modifier.PUBLIC)
                        .returns(String.class)
                        .addStatement("return symbol")
                        .build())
                .build();

        Class<?> type = load(compile(JavaFile.newBuilder("example", operation).build()), "example.Operation");
        Object times = type.getMethod("valueOf", String.class).invoke(null, "TIMES");

        assertThat(type.getMethod("applyAsInt", int.class, int.class).invoke(times, 6, 7))
                .isEqualTo(42);
        assertThat(type.getMethod("symbol").invoke(times)).isEqualTo("*");
        assertThat(type.getEnumConstants()).hasSize(2);
    }

    private static Method applyAsInt(String statement) {
        return Method.newBuilder("applyAsInt")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addParameter(int.class, "left")
                .addParameter(int.class, "right")
                .addStatement(statement)
                .build();
    }

    @Test
    void generatesAnAnnotationTypeAnInterfaceAndAnonymousClassesThatCompile() throws ReflectiveOperationException {
        Type mapping = Type.newAnnotationBuilder("Mapping")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Annotation.newBuilder(ClassName.of("java.lang.annotation", "Retention"))
                        .addMember("value", "$T.RUNTIME", ClassName.of("java.lang.annotation", "RetentionPolicy"))
                        .build())
                .addMethod(Method.newBuilder("value").returns(String.class).build())
                .addMethod(Method.newBuilder("aliases")
                        .returns(ArrayTypeName.of(ClassName.of(String.class)))
                        .defaultValue("{$S, $S}", "a", "b")
                        .build())
                .addMethod(Method.newBuilder("weight")
                        .returns(double.class)
                        .defaultValue("1.5")
                        .build())
                .addMethod(Method.newBuilder("target")
                        .returns(ClassName.of(Class.class).parameterized(WildcardTypeName.unbounded()))
                        .defaultValue("$T.class", Object.class)
                        .build())
                .build();
        Type greeter = Type.newInterfaceBuilder("Greeter")
                .addModifiers(Modifier.PUBLIC)
                .addField(Field.newBuilder(
                                ClassName.of(Supplier.class).parameterized(ClassName.of(String.class)), "DEFAULT")
                        .initializer(
                                "$L",
                                Type.newAnonymousClassBuilder("")
                                        .addSuperinterface(
                                                ClassName.of(Supplier.class).parameterized(ClassName.of(String.class)))
                                        .addMethod(Method.newBuilder("get")
                                                .addAnnotation(Override.class)
                                                .addModifiers(Modifier.PUBLIC)
                                                .returns(String.class)
                                                .addStatement("return $S", "Hello")
                                                .build())
                                        .build())
                        .build())
                .addMethod(Method.newBuilder("name").returns(String.class).build())
                .addMethod(Method.newBuilder("greet")
                        .addModifiers(Modifier.DEFAULT)
                        .returns(String.class)
                        .addStatement("return prefix() + $S + name()", ", ")
                        .build())
                .addMethod(Method.newBuilder("prefix")
                        .addModifiers(Modifier.PRIVATE)
                        .returns(String.class)
                        .addStatement("return DEFAULT.get()")
                        .build())
                .addMethod(Method.newBuilder("of")
                        .addModifiers(Modifier.STATIC)
                        .returns(ClassName.of("example", "Greeter"))
                        .addParameter(String.class, "name")
                        .addStatement("return () -> name")
                        .build())
                .build();

        Compilation compilation = compile(
                JavaFile.newBuilder("example", mapping).build(),
                JavaFile.newBuilder("example", greeter).build());
        Class<?> type = load(compilation, "example.Greeter");
        Object world = call(type, "of", new Class<?>[] {String.class}, "World");

        assertThat(type.getMethod("greet").invoke(world)).isEqualTo("Hello, World");
        assertThat(load(compilation, "example.Mapping").isAnnotation()).isTrue();
    }

    @Test
    void resolvesNameClashesThatCompile() {
        Type clashing = Type.newClassBuilder("List")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addTypeVariable(TypeVariableName.of("String"))
                .addField(ClassName.of(List.class).parameterized(TypeVariableName.of("String")), "utilList")
                .addField(ClassName.of("java.awt", "List").annotated(Annotation.of(NULLABLE)), "awtList")
                .addField(ClassName.of(String.class).annotated(Annotation.of(NULLABLE)), "text")
                .addField(
                        ClassName.of("example", "List")
                                .parameterized(ClassName.of(Object.class))
                                .annotated(Annotation.of(NULLABLE)),
                        "self")
                .addField(
                        ClassName.of(Map.Entry.class)
                                .parameterized(TypeVariableName.of("String"), ClassName.of(Integer.class)),
                        "entry")
                .addMethod(Method.newConstructorBuilder()
                        .addStatement("this.utilList = new $T<>()", ArrayList.class)
                        .addStatement("this.entry = $T.entry(null, 0)", Map.class)
                        .build())
                .build();

        JavaFile file = JavaFile.newBuilder("example", clashing).build();
        compile(file);

        assertThat(file.toString())
                .contains("java.util.List<String> utilList;")
                .contains("java.awt.@Nullable List awtList;")
                .contains("java.lang.@Nullable String text;")
                .contains("@Nullable List<Object> self;")
                .contains("Map.Entry<String, Integer> entry;");
    }

    @Test
    void writesPrimitiveAndBoxedTypesThatCompile() throws ReflectiveOperationException {
        Type boxes = Type.newClassBuilder("Boxes")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(Method.newBuilder("box")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(PrimitiveTypeName.INT.box())
                        .addParameter(TypeName.of(int.class), "value")
                        .addStatement("return value")
                        .build())
                .addMethod(Method.newBuilder("text")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(String.class)
                        .addStatement("return $S", "tab\there \"quoted\" \\ é")
                        .build())
                .build();

        Class<?> type = load(compile(JavaFile.newBuilder("example", boxes).build()), "example.Boxes");

        assertThat(call(type, "box", new Class<?>[] {int.class}, 3)).isEqualTo(3);
        assertThat(call(type, "text", new Class<?>[0])).isEqualTo("tab\there \"quoted\" \\ é");
    }

    @Test
    void keepsTheSameOutputForTheSameModel() {
        Function<String, JavaFile> file = name -> JavaFile.newBuilder(
                        "example",
                        Type.newClassBuilder(name)
                                .addField(ClassName.of(List.class).parameterized(ClassName.of(String.class)), "names")
                                .build())
                .build();

        assertThat(file.apply("A").toString()).isEqualTo(file.apply("A").toString());
    }

    @Test
    void rewritesStaticallyImportedMembersIntoCodeThatRuns() throws ReflectiveOperationException {
        ClassName timeUnit = ClassName.of(TimeUnit.class);
        ClassName state = ClassName.of(Thread.State.class);
        Type clock = Type.newClassBuilder("Clock")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(Method.newBuilder("seconds")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(long.class)
                        .addStatement("return $1T.SECONDS.convert(2, $1T.MINUTES)", timeUnit)
                        .build())
                .addMethod(Method.newBuilder("blocked")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(boolean.class)
                        .addParameter(state, "state")
                        .addStatement("return state == $T.BLOCKED && $T.nanoTime() > 0", state, System.class)
                        .build())
                .build();
        JavaFile file = JavaFile.newBuilder("example", clock)
                .addStaticImport(timeUnit, "*")
                .addStaticImport(Thread.State.BLOCKED)
                .addStaticImport(System.class, "nanoTime")
                .build();

        Class<?> type = load(compile(file), "example.Clock");

        assertThat(file.toString())
                .contains("return SECONDS.convert(2, MINUTES);")
                .contains("return state == BLOCKED && nanoTime() > 0;")
                .doesNotContain("import java.util.concurrent.TimeUnit;");
        assertThat(call(type, "seconds", new Class<?>[0])).isEqualTo(120L);
        assertThat(call(type, "blocked", new Class<?>[] {Thread.State.class}, Thread.State.BLOCKED))
                .isEqualTo(true);
    }

    @Test
    void qualifiesTypesThatInheritedMemberTypesWouldCapture() throws ReflectiveOperationException {
        ClassName entry = ClassName.of("example.other", "Entry");
        ClassName state = ClassName.of("example.other", "State");
        TypeVariableName k = TypeVariableName.of("K");
        TypeVariableName v = TypeVariableName.of("V");
        Type entries = Type.newClassBuilder("Entries")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addTypeVariable(k)
                .addTypeVariable(v)
                .superclass(ClassName.of(AbstractMap.class).parameterized(k, v))
                .avoidClashesWithNestedClasses(AbstractMap.class)
                .addMethod(Method.newBuilder("entry")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(entry)
                        .addStatement("return new $T()", entry)
                        .build())
                .build();
        Type worker = Type.newClassBuilder("Worker")
                .addModifiers(Modifier.PUBLIC)
                .superclass(Thread.class)
                .addMethod(Method.newBuilder("state")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(state)
                        .addStatement("return new $T()", state)
                        .build())
                .build();

        Compilation compilation = compile(
                JavaFile.newBuilder(
                                "example.other",
                                Type.newClassBuilder("Entry")
                                        .addModifiers(Modifier.PUBLIC)
                                        .build())
                        .build(),
                JavaFile.newBuilder(
                                "example.other",
                                Type.newClassBuilder("State")
                                        .addModifiers(Modifier.PUBLIC)
                                        .build())
                        .build(),
                JavaFile.newBuilder("example", entries).build(),
                JavaFile.newBuilder("example", worker).build());

        assertThat(call(load(compilation, "example.Entries"), "entry", new Class<?>[0])
                        .getClass()
                        .getName())
                .isEqualTo("example.other.Entry");
        assertThat(call(load(compilation, "example.Worker"), "state", new Class<?>[0])
                        .getClass()
                        .getName())
                .isEqualTo("example.other.State");

        // Given as a TypeName, the superclass brings no names: State is imported, and Thread.State hides it.
        Type unaware = Type.newClassBuilder("Worker")
                .addModifiers(Modifier.PUBLIC)
                .superclass(ClassName.of(Thread.class))
                .addMethods(worker.methods())
                .build();
        Compilation failing = javac().compile(
                        JavaFile.newBuilder(
                                        "example.other",
                                        Type.newClassBuilder("State")
                                                .addModifiers(Modifier.PUBLIC)
                                                .build())
                                .build()
                                .toJavaFileObject(),
                        JavaFile.newBuilder("example", unaware).build().toJavaFileObject());
        assertThat(failing).failed();
    }

    @Test
    void namesNestedTypesByTheirShortestNameInCodeThatCompiles() {
        ClassName outer = ClassName.of("example", "Outer");
        ClassName leaf = outer.nested("Twin").nested("Leaf");
        ClassName nearTwin = outer.nested("First").nested("Twin");
        Type type = Type.newClassBuilder("Outer")
                .addModifiers(Modifier.PUBLIC)
                .addMethod(Method.newBuilder("leaf")
                        .addModifiers(Modifier.STATIC)
                        .returns(leaf)
                        .addStatement("return new $T()", leaf)
                        .build())
                .addType(Type.newClassBuilder("First")
                        .addModifiers(Modifier.STATIC)
                        .addType(Type.newClassBuilder("Twin")
                                .addModifiers(Modifier.STATIC)
                                .build())
                        .addType(Type.newClassBuilder("Inner")
                                .addModifiers(Modifier.STATIC)
                                .addField(Field.newBuilder(leaf, "leaf")
                                        .initializer("new $T()", leaf)
                                        .build())
                                .addField(Field.newBuilder(nearTwin, "twin")
                                        .initializer("new $T()", nearTwin)
                                        .build())
                                .build())
                        .build())
                .addType(Type.newClassBuilder("Twin")
                        .addModifiers(Modifier.STATIC)
                        .addType(Type.newClassBuilder("Leaf")
                                .addModifiers(Modifier.STATIC)
                                .build())
                        .build())
                .build();
        JavaFile file = JavaFile.newBuilder("example", type).build();

        compile(file);

        assertThat(file.toString())
                .contains("    static Twin.Leaf leaf() {")
                .contains("            Outer.Twin.Leaf leaf = new Outer.Twin.Leaf();")
                .contains("            Twin twin = new Twin();");
    }

    @Test
    void writesInnerClassesOfParameterizedTypesThatCompile() throws ReflectiveOperationException {
        ClassName outer = ClassName.of("example", "Outer");
        TypeVariableName t = TypeVariableName.of("T");
        TypeName ofString = outer.parameterized(ClassName.of(String.class));
        Type type = Type.newClassBuilder("Outer")
                .addModifiers(Modifier.PUBLIC)
                .addTypeVariable(t)
                .addMethod(Method.newBuilder("inner")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(outer.parameterized(ClassName.of(String.class)).nested("Inner"))
                        .addStatement("$T outer = new $T<>()", ofString, outer)
                        .addStatement("return outer.new Inner()")
                        .build())
                .addType(Type.newClassBuilder("Inner")
                        .addModifiers(Modifier.PUBLIC)
                        .build())
                .build();
        JavaFile file = JavaFile.newBuilder("example", type).build();

        Class<?> loaded = load(compile(file), "example.Outer");

        assertThat(file.toString()).contains("public static Outer<String>.Inner inner() {");
        assertThat(call(loaded, "inner", new Class<?>[0]).getClass().getName()).isEqualTo("example.Outer$Inner");
    }

    @Test
    void writesStatementsAndStringsOverSeveralLinesThatRun() throws ReflectiveOperationException {
        String text = "first line\nsecond \"line\"\n\nlast";
        Type type = Type.newClassBuilder("Texts")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addField(Field.newBuilder(String.class, "TEXT", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("$S", text)
                        .build())
                .addMethod(Method.newBuilder("text")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(String.class)
                        .addStatement("return $S", text)
                        .build())
                .addMethod(Method.newBuilder("sum")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(int.class)
                        .addStatement("return 1\n+ 2\n+ 3")
                        .build())
                .build();
        JavaFile file = JavaFile.newBuilder("example", type).build();

        Class<?> loaded = load(compile(file), "example.Texts");

        assertThat(file.toString()).contains("            + \"second \\\"line\\\"\\n\"\n");
        assertThat(call(loaded, "text", new Class<?>[0])).isEqualTo(text);
        assertThat(loaded.getField("TEXT").get(null)).isEqualTo(text);
        assertThat(call(loaded, "sum", new Class<?>[0])).isEqualTo(6);
    }

    @Test
    void annotatesTheDimensionsOfVarargsAsJavaDoes() throws ReflectiveOperationException {
        JavaFileObject outerSource = runtimeTypeAnnotation("Outer");
        JavaFileObject innerSource = runtimeTypeAnnotation("Inner");
        TypeName rows = ArrayTypeName.of(
                        ArrayTypeName.of(ClassName.OBJECT).annotated(Annotation.of(ClassName.of("example", "Inner"))))
                .annotated(Annotation.of(ClassName.of("example", "Outer")));
        ClassName holder = ClassName.of("example", "Holder");
        Type type = Type.newClassBuilder("Holder")
                .addModifiers(Modifier.PUBLIC)
                .addMethod(Method.newBuilder("count")
                        .addModifiers(Modifier.PUBLIC)
                        .returns(int.class)
                        .addParameter(holder.annotated(Annotation.of(ClassName.of("example", "Outer"))), "this")
                        .addParameter(rows, "rows")
                        .varargs(true)
                        .addStatement("return rows.length")
                        .build())
                .build();
        JavaFile file = JavaFile.newBuilder("example", type).build();
        List<JavaFileObject> sources = List.of(outerSource, innerSource, file.toJavaFileObject());
        Compilation compilation = javac().withOptions("-Xlint:all", "-Werror").compile(sources);
        assertThat(compilation).succeeded().hadNoWarnings();

        Class<?> loaded = load(compilation, "example.Holder");
        java.lang.reflect.Method count = loaded.getMethod("count", Object[][].class);
        AnnotatedArrayType parameter = (AnnotatedArrayType) count.getAnnotatedParameterTypes()[0];
        AnnotatedType component = parameter.getAnnotatedGenericComponentType();

        assertThat(count.isVarArgs()).isTrue();
        assertThat(parameter.getAnnotations())
                .singleElement()
                .extracting(a -> a.annotationType().getSimpleName())
                .isEqualTo("Outer");
        assertThat(component.getAnnotations())
                .singleElement()
                .extracting(a -> a.annotationType().getSimpleName())
                .isEqualTo("Inner");
        assertThat(count.getAnnotatedReceiverType().getAnnotations()).hasSize(1);
        Object instance = loaded.getConstructor().newInstance();
        assertThat(count.invoke(instance, (Object) new Object[][] {{1}, {2}})).isEqualTo(2);
    }

    private static JavaFileObject runtimeTypeAnnotation(String name) {
        return source("example." + name, """
                package example;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.TYPE_USE)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface %s {}
                """.formatted(name));
    }

    @Test
    void generatesAVarargsRecordWithDocumentedComponentsThatRuns() throws ReflectiveOperationException {
        Type names = Type.newRecordBuilder("Names")
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Names, counted.\n")
                .addRecordComponent(Parameter.newBuilder(int.class, "count")
                        .addJavadoc("how many")
                        .build())
                .addRecordComponent(Parameter.newBuilder(ArrayTypeName.of(ClassName.of(String.class)), "values")
                        .addJavadoc("the names")
                        .build())
                .varargs(true)
                .build();
        JavaFile file = JavaFile.newBuilder("example", names).build();

        Class<?> loaded = load(compile(file), "example.Names");
        Constructor<?> constructor = loaded.getConstructor(int.class, String[].class);
        Object record = constructor.newInstance(2, new String[] {"a", "b"});

        assertThat(file.toString()).contains(" * @param count how many\n * @param values the names\n");
        assertThat(constructor.isVarArgs()).isTrue();
        assertThat(loaded.getMethod("count").invoke(record)).isEqualTo(2);
        assertThat(loaded.getMethod("values").invoke(record)).isEqualTo(new String[] {"a", "b"});
    }

    @Test
    void writesAConstructorThatJavacAsksForInAnExportedPackage() {
        Type plain = Type.newClassBuilder("Plain")
                .addModifiers(Modifier.PUBLIC)
                .addType(Type.newClassBuilder("Nested")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .build())
                .build();

        Compilation compilation = javac().withOptions("-Xlint:all", "-Werror")
                .compile(
                        source("module-info", """
                                module example {
                                    exports example;
                                }
                                """),
                        JavaFile.newBuilder("example", plain).build().toJavaFileObject());

        assertThat(compilation).succeeded().hadNoWarnings();
    }
}
