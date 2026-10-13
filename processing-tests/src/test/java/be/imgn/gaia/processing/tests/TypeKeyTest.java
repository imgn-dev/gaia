package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.TypeKey;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.UnionType;
import javax.tools.JavaFileObject;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Keys of types: {@code TypeKey} equality is {@code context.isSameType}, ignoring type annotations, except where that
 * is not an equivalence relation.
 */
class TypeKeyTest {

    private static final JavaFileObject KEYS = source("example.Keys", """
            package example;

            import java.io.Serializable;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;
            import java.util.List;
            import java.util.Map;

            @Target(ElementType.TYPE_USE)
            @interface N {}

            class Outer<O> {
                class Inner {}

                class GenericInner<I> {}
            }

            class Plain {
                class Inner {}
            }

            class First {
                <T> void generic(T firstValue) {}
            }

            class Second {
                <T> void generic(T secondValue) {}
            }

            class Keys<T, U> {
                String string;
                @N String annotatedString;
                int primitive;
                @N int annotatedPrimitive;
                long longPrimitive;
                Integer boxed;
                List<String> list;
                @N List<@N String> annotatedList;
                List<Integer> listOfIntegers;
                Map<String, List<Integer>> map;
                Map<@N String, @N List<@N Integer>> annotatedMap;
                Map<String, List<Long>> mapOfLongs;
                List rawList;

                String[] array;
                @N String @N [] annotatedArray;
                String[][] arrays;
                String[] @N [] annotatedArrays;
                @N String @N [] @N [] annotatedArraysOfAnnotated;
                int[] primitives;
                @N int @N [] annotatedPrimitives;
                long[] longs;

                List<?> unbounded;
                List<@N ?> annotatedUnbounded;
                List<? extends Object> extendsObject;
                List<? extends @N Object> extendsAnnotatedObject;
                List<? super Object> superObject;
                List<? extends Number> extendsNumber;
                List<? extends @N Number> extendsAnnotatedNumber;
                List<? super Number> superNumber;
                List<? super @N Number> superAnnotatedNumber;
                List<? extends Integer> extendsInteger;
                List<List<?>> nestedUnbounded;
                List<List<? extends Object>> nestedExtendsObject;
                List<@N List<? extends @N Object>> nestedExtendsAnnotatedObject;
                List<List<? super Object>> nestedSuperObject;

                T variable;
                @N T annotatedVariable;
                U otherVariable;
                List<T> listOfVariable;
                List<@N T> listOfAnnotatedVariable;
                T[] arrayOfVariable;
                @N T @N [] annotatedArrayOfVariable;

                Outer<String>.Inner inner;
                Outer<String>.Inner sameInner;
                Outer<@N String>.@N Inner annotatedInner;
                @N Outer<@N String>.Inner annotatedOuter;
                Outer<Integer>.Inner innerOfInteger;
                Outer<T>.Inner innerOfVariable;
                Outer<U>.Inner innerOfOtherVariable;
                Outer.Inner rawInner;
                Outer<String>.GenericInner<Integer> genericInner;
                Outer<@N String>.GenericInner<@N Integer> annotatedGenericInner;
                Outer<Integer>.GenericInner<Integer> genericInnerOfInteger;
                Outer<String>.GenericInner<String> genericInnerOfString;
                List<Outer<String>.Inner> listOfInner;
                List<Outer<Integer>.Inner> listOfInnerOfInteger;
                Plain.Inner plainInner;
                Plain.@N Inner annotatedPlainInner;
                Map.Entry<String, String> entry;
                Map.@N Entry<String, String> annotatedEntry;

                <M> void method(M methodVariable, @N M annotatedMethodVariable) {}

                <A extends Number & Runnable> void intersection(A intersectionVariable) {}

                <B extends @N Number & @N Runnable> void annotatedIntersection(B annotatedIntersectionVariable) {}

                <C extends Runnable & Serializable> void interfaces(C interfacesVariable) {}

                <D extends Serializable & Runnable> void reorderedInterfaces(D reorderedInterfacesVariable) {}

                <E extends Runnable & Comparable<String>> void parameterized(E parameterizedVariable) {}

                <F extends Runnable & Comparable<@N String>> void annotatedParameterized(F annotatedValue) {}

                <G extends Runnable & Comparable<Integer>> void otherParameterized(G otherParameterizedVariable) {}

                void takesString(String stringParameter) {}

                void takesAnnotatedString(@N String annotatedStringParameter) {}

                void takesInteger(Integer integerParameter) {}

                String returnsString() {
                    return "";
                }

                @N String returnsAnnotatedString() {
                    return "";
                }

                void throwsException() throws Exception {}

                void throwsNothing() {}
            }
            """);

    private static final JavaFileObject UNRESOLVED = source("example.Unresolved", """
            package example;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;
            import java.util.List;

            @Target(ElementType.TYPE_USE)
            @interface N {}

            class Unresolved {
                Missing missing;
                @N Missing annotatedMissing;
                Missing sameMissing;
                foo.Missing qualifiedMissing;
                Other other;
                Missing<String> parameterizedMissing;
                Missing<@N String> parameterizedAnnotatedMissing;
                Missing<Integer> missingOfInteger;
                List<Missing> listOfMissing;
                List<@N Missing> listOfAnnotatedMissing;
                List<Other> listOfOther;
                Missing[] arrayOfMissing;
                @N Missing @N [] annotatedArrayOfMissing;
            }
            """);

    private static final JavaFileObject BODIES = source("example.Bodies", """
            package example;

            import java.io.FileInputStream;
            import java.io.FileNotFoundException;
            import java.io.IOException;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;

            @Target(ElementType.TYPE_USE)
            @interface N {}

            class Bodies {
                void unions() {
                    try {
                        new FileInputStream("");
                    } catch (IOException | RuntimeException union) {
                    }
                    try {
                        new FileInputStream("");
                    } catch (java.io.@N IOException | java.lang.@N RuntimeException annotatedUnion) {
                    }
                    try {
                        new FileInputStream("");
                    } catch (RuntimeException | IOException reorderedUnion) {
                    }
                    try {
                        new FileInputStream("");
                    } catch (FileNotFoundException | RuntimeException narrowerUnion) {
                    }
                }

                void first() {
                    class Local {}
                    class OtherLocal {}
                    class GenericLocal<T> {
                        T value;
                    }
                }

                void second() {
                    class Local {}
                }
            }
            """);

    // The pairs of types that differ only in type annotations, at several depths.
    private static final List<List<String>> ANNOTATED_PAIRS = List.of(
            List.of("string", "annotatedString"),
            List.of("primitive", "annotatedPrimitive"),
            List.of("list", "annotatedList"),
            List.of("map", "annotatedMap"),
            List.of("array", "annotatedArray"),
            List.of("arrays", "annotatedArrays"),
            List.of("arrays", "annotatedArraysOfAnnotated"),
            List.of("primitives", "annotatedPrimitives"),
            List.of("unbounded", "annotatedUnbounded"),
            List.of("extendsObject", "extendsAnnotatedObject"),
            List.of("extendsNumber", "extendsAnnotatedNumber"),
            List.of("superNumber", "superAnnotatedNumber"),
            List.of("nestedExtendsObject", "nestedExtendsAnnotatedObject"),
            List.of("variable", "annotatedVariable"),
            List.of("listOfVariable", "listOfAnnotatedVariable"),
            List.of("arrayOfVariable", "annotatedArrayOfVariable"),
            List.of("inner", "annotatedInner"),
            List.of("inner", "annotatedOuter"),
            List.of("genericInner", "annotatedGenericInner"),
            List.of("plainInner", "annotatedPlainInner"),
            List.of("entry", "annotatedEntry"),
            List.of("methodVariable", "annotatedMethodVariable"),
            List.of("takesString", "takesAnnotatedString"),
            List.of("returnsString", "returnsAnnotatedString"),
            List.of("parameterizedBound", "annotatedParameterizedBound"),
            List.of("intersectionBound", "annotatedIntersectionBound"));

    // Runs a check on the types of example.Keys by name: see types(ProcessingContext).
    private static void withTypes(BiConsumer<ProcessingContext, NamedTypes> check) {
        assertThat(FirstRound.run(List.of(KEYS), List.of(), context -> check.accept(context, types(context))))
                .succeeded();
    }

    /*
     * The types of the fields of example.Keys, of the parameters of its methods and of the methods that are not
     * generic, by name; the first type argument of each field of a parameterized type, such as the wildcard of
     * List<?>, as "<field>Argument"; and the bound of each type variable bounded by an intersection type, as
     * "<method>Bound".
     */
    private static NamedTypes types(ProcessingContext context) {
        Map<String, TypeMirror> types = new LinkedHashMap<>();
        for (Element member : context.elements().getTypeElement("example.Keys").getEnclosedElements()) {
            String name = member.getSimpleName().toString();
            if (member.getKind() == ElementKind.FIELD) {
                types.put(name, member.asType());
                if (member.asType() instanceof DeclaredType declared
                        && !declared.getTypeArguments().isEmpty()) {
                    types.put(name + "Argument", declared.getTypeArguments().getFirst());
                }
            } else if (member instanceof ExecutableElement method && member.getKind() == ElementKind.METHOD) {
                putMethod(method, types);
            }
        }
        return new NamedTypes(types);
    }

    private static void putMethod(ExecutableElement method, Map<String, TypeMirror> types) {
        String name = method.getSimpleName().toString();
        if (method.getTypeParameters().isEmpty()) {
            types.put(name, method.asType());
        }
        for (VariableElement parameter : method.getParameters()) {
            types.put(parameter.getSimpleName().toString(), parameter.asType());
            if (parameter.asType() instanceof TypeVariable variable
                    && variable.getUpperBound().getKind() == TypeKind.INTERSECTION) {
                types.put(name + "Bound", variable.getUpperBound());
            }
        }
    }

    // Asserts that the keys of two types are equal, both ways, with the same hash code.
    private static void assertEqualKeys(TypeMirror left, TypeMirror right, String description) {
        TypeKey leftKey = TypeKey.of(left);
        TypeKey rightKey = TypeKey.of(right);

        assertThat(leftKey).as(description).isEqualTo(rightKey).hasSameHashCodeAs(rightKey);
        assertThat(rightKey).as(description).isEqualTo(leftKey);
    }

    // Asserts that the keys of two types differ, both ways.
    private static void assertDifferentKeys(TypeMirror left, TypeMirror right, String description) {
        assertThat(TypeKey.of(left)).as(description).isNotEqualTo(TypeKey.of(right));
        assertThat(TypeKey.of(right)).as(description).isNotEqualTo(TypeKey.of(left));
    }

    @Test
    void keysInnerClassesOfDifferentParameterizationsOfTheirOuterClassApart() {
        withTypes((context, types) -> {
            assertThat(types.get("inner")).hasToString("example.Outer<java.lang.String>.Inner");
            assertDifferentKeys(types.get("inner"), types.get("innerOfInteger"), "Outer<Integer>.Inner");
            assertDifferentKeys(types.get("inner"), types.get("rawInner"), "Outer.Inner");
            assertDifferentKeys(types.get("innerOfVariable"), types.get("innerOfOtherVariable"), "Outer<U>.Inner");
            assertDifferentKeys(types.get("genericInner"), types.get("genericInnerOfInteger"), "outer argument");
            assertDifferentKeys(types.get("genericInner"), types.get("genericInnerOfString"), "inner argument");
            assertDifferentKeys(types.get("listOfInner"), types.get("listOfInnerOfInteger"), "type argument");
            assertThat(Set.of(TypeKey.of(types.get("inner")), TypeKey.of(types.get("innerOfInteger"))))
                    .hasSize(2);
        });
    }

    @Test
    void keysTheSameInnerClassOfTheSameParameterizationOfItsOuterClassEqually() {
        withTypes((context, types) -> {
            TypeElement inner = context.elements().getTypeElement("example.Outer.Inner");
            DeclaredType outerOfString = context.types()
                    .getDeclaredType(context.elements().getTypeElement("example.Outer"), context.type(String.class));
            TypeMirror built = context.types().getDeclaredType(outerOfString, inner);

            assertEqualKeys(types.get("inner"), types.get("sameInner"), "twice from source");
            assertEqualKeys(types.get("inner"), built, "built");
            assertEqualKeys(types.get("inner"), types.get("annotatedInner"), "annotated");
            assertEqualKeys(types.get("inner"), types.get("annotatedOuter"), "annotated outer");
            assertEqualKeys(types.get("plainInner"), types.get("annotatedPlainInner"), "of a plain outer class");
        });
    }

    @Test
    void keysAnUnboundedWildcardAsBoundedByObject() {
        withTypes((context, types) -> {
            TypeMirror unbounded = types.get("unboundedArgument");
            TypeMirror object = context.type(Object.class);

            assertEqualKeys(unbounded, types.get("extendsObjectArgument"), "? extends Object");
            assertEqualKeys(unbounded, types.get("extendsAnnotatedObjectArgument"), "? extends @N Object");
            assertEqualKeys(unbounded, types.get("annotatedUnboundedArgument"), "@N ?");
            assertEqualKeys(unbounded, context.types().getWildcardType(null, null), "built ?");
            assertEqualKeys(unbounded, context.types().getWildcardType(object, null), "built ? extends Object");
            assertEqualKeys(types.get("unbounded"), types.get("extendsObject"), "List<? extends Object>");
            assertEqualKeys(types.get("nestedUnbounded"), types.get("nestedExtendsObject"), "nested");
            assertEqualKeys(types.get("nestedUnbounded"), types.get("nestedExtendsAnnotatedObject"), "annotated");
        });
    }

    @Test
    void keysAnUnboundedWildcardApartFromOneWithALowerBound() {
        withTypes((context, types) -> {
            TypeMirror unbounded = types.get("unboundedArgument");

            assertDifferentKeys(unbounded, types.get("superObjectArgument"), "? super Object");
            assertDifferentKeys(unbounded, context.types().getWildcardType(null, context.type(Object.class)), "built");
            assertDifferentKeys(types.get("unbounded"), types.get("superObject"), "List<? super Object>");
            assertDifferentKeys(types.get("nestedUnbounded"), types.get("nestedSuperObject"), "nested");
            assertDifferentKeys(unbounded, types.get("extendsNumberArgument"), "? extends Number");
            assertDifferentKeys(types.get("extendsNumberArgument"), types.get("superNumberArgument"), "bounds");
            assertDifferentKeys(unbounded, context.type(Object.class), "Object");
        });
    }

    @Test
    void keysIntersectionTypesByTheirBoundsInAnyOrderIgnoringTypeAnnotations() {
        withTypes((context, types) -> {
            assertThat(types.get("annotatedIntersectionBound").toString()).contains("@example.N");
            assertThat(types.get("annotatedParameterizedBound").toString()).contains("@example.N");

            assertEqualKeys(types.get("intersectionBound"), types.get("annotatedIntersectionBound"), "annotated");
            assertEqualKeys(types.get("parameterizedBound"), types.get("annotatedParameterizedBound"), "deeper");
            assertEqualKeys(types.get("interfacesBound"), types.get("reorderedInterfacesBound"), "reordered");
            assertDifferentKeys(types.get("intersectionBound"), types.get("interfacesBound"), "other bounds");
            assertDifferentKeys(types.get("parameterizedBound"), types.get("otherParameterizedBound"), "argument");
            assertDifferentKeys(types.get("intersectionBound"), types.get("intersectionVariable"), "its type variable");
        });
    }

    @Test
    void keysTypeVariablesByTheirTypeParameter() {
        withTypes((context, types) -> {
            TypeMirror first = parameterOf(context, "example.First");
            TypeMirror second = parameterOf(context, "example.Second");

            assertThat(first).hasToString(second.toString());
            assertDifferentKeys(first, second, "of methods of the same signature in different classes");
            assertEqualKeys(first, parameterOf(context, "example.First"), "the same");
            assertDifferentKeys(types.get("variable"), types.get("otherVariable"), "of the same class");
            assertDifferentKeys(types.get("variable"), types.get("methodVariable"), "of a class and a method");
            assertEqualKeys(types.get("variable"), types.get("annotatedVariable"), "annotated");
        });
    }

    @Test
    void keysCapturedTypeVariablesByIdentity() {
        withTypes((context, types) -> {
            DeclaredType unbounded = (DeclaredType) types.get("unbounded");
            TypeMirror captured = context.types().capture(unbounded);
            TypeMirror capturedAgain = context.types().capture(unbounded);
            TypeMirror variable = ((DeclaredType) captured).getTypeArguments().getFirst();

            assertThat(variable.getKind()).isEqualTo(TypeKind.TYPEVAR);
            assertEqualKeys(captured, captured, "itself");
            assertEqualKeys(variable, variable, "its type variable");
            assertDifferentKeys(captured, capturedAgain, "another capture");
            assertDifferentKeys(captured, unbounded, "the type it captures");
        });
    }

    @Test
    void keysExecutableTypesByTheirParameterAndReturnTypes() {
        withTypes((context, types) -> {
            assertEqualKeys(types.get("takesString"), types.get("takesAnnotatedString"), "annotated parameter");
            assertEqualKeys(types.get("returnsString"), types.get("returnsAnnotatedString"), "annotated return");
            assertEqualKeys(types.get("throwsException"), types.get("throwsNothing"), "thrown types");
            assertDifferentKeys(types.get("takesString"), types.get("takesInteger"), "parameter");
            assertDifferentKeys(types.get("returnsString"), types.get("throwsNothing"), "return");
        });
    }

    @Test
    void keysGenericExecutableTypesByTheirTypeVariablesUnlikeJavac() {
        withTypes((context, types) -> {
            TypeMirror intersection = methodOf(context, "intersection");
            TypeMirror annotatedIntersection = methodOf(context, "annotatedIntersection");

            assertThat(context.isSameType(intersection, annotatedIntersection)).isTrue();
            assertDifferentKeys(intersection, annotatedIntersection, "renamed type variables");
            assertEqualKeys(intersection, methodOf(context, "intersection"), "the same");
        });
    }

    @Test
    void keysTypesThatDifferOnlyInTypeAnnotationsEqually() {
        withTypes((context, types) -> {
            for (List<String> pair : ANNOTATED_PAIRS) {
                TypeMirror plain = types.get(pair.get(0));
                TypeMirror annotated = types.get(pair.get(1));

                assertThat(annotated.toString()).as(pair.toString()).contains("@example.N");
                assertEqualKeys(plain, annotated, pair.toString());
            }
        });
    }

    @Test
    void keysPrimitivesArraysAndDeclaredTypesApart() {
        withTypes((context, types) -> {
            assertDifferentKeys(types.get("primitive"), types.get("longPrimitive"), "int and long");
            assertDifferentKeys(types.get("primitive"), types.get("boxed"), "int and Integer");
            assertDifferentKeys(types.get("primitives"), types.get("longs"), "int[] and long[]");
            assertDifferentKeys(types.get("array"), types.get("arrays"), "String[] and String[][]");
            assertDifferentKeys(types.get("array"), types.get("string"), "String[] and String");
            assertDifferentKeys(types.get("list"), types.get("listOfIntegers"), "type arguments");
            assertDifferentKeys(types.get("map"), types.get("mapOfLongs"), "nested type arguments");
            assertDifferentKeys(types.get("list"), types.get("rawList"), "raw type");
            assertDifferentKeys(
                    types.get("rawList"),
                    context.elements().getTypeElement("java.util.List").asType(),
                    "type of the declaration");
        });
    }

    @Test
    void agreesWithIsSameTypeOnEveryPairOfTypes() {
        withTypes((context, types) -> {
            Map<String, TypeMirror> all = new LinkedHashMap<>(types.types());
            DeclaredType unbounded = (DeclaredType) types.get("unbounded");
            all.put("builtString", context.type(String.class));
            all.put("builtList", context.type(List.class, String.class));
            all.put("builtUnbounded", context.types().getWildcardType(null, null));
            all.put("builtExtendsObject", context.types().getWildcardType(context.type(Object.class), null));
            all.put("builtSuperObject", context.types().getWildcardType(null, context.type(Object.class)));
            all.put(
                    "listDeclaration",
                    context.elements().getTypeElement("java.util.List").asType());
            all.put("captured", context.types().capture(unbounded));
            all.put("capturedAgain", context.types().capture(unbounded));
            all.put("firstVariable", parameterOf(context, "example.First"));
            all.put("secondVariable", parameterOf(context, "example.Second"));

            assertThat(sameTypePairs(context, all)).hasSizeGreaterThan(ANNOTATED_PAIRS.size());
        });
    }

    @Test
    void keysUnresolvedTypesByTheirNameAsWritten() {
        assertThat(FirstRound.run(List.of(UNRESOLVED), List.of(), context -> {
                    NamedTypes types = fields(context, "example.Unresolved");

                    assertThat(types.get("missing").getKind()).isEqualTo(TypeKind.ERROR);
                    assertThat(types.get("annotatedMissing").toString()).contains("@example.N");
                    assertEqualKeys(types.get("missing"), types.get("annotatedMissing"), "annotated");
                    assertEqualKeys(types.get("missing"), types.get("sameMissing"), "twice");
                    assertEqualKeys(
                            types.get("parameterizedMissing"),
                            types.get("parameterizedAnnotatedMissing"),
                            "annotated type argument");
                    assertEqualKeys(types.get("listOfMissing"), types.get("listOfAnnotatedMissing"), "nested");
                    assertEqualKeys(types.get("arrayOfMissing"), types.get("annotatedArrayOfMissing"), "array");
                    assertDifferentKeys(types.get("missing"), types.get("qualifiedMissing"), "foo.Missing");
                    assertDifferentKeys(types.get("missing"), types.get("other"), "Other");
                    assertDifferentKeys(types.get("missing"), types.get("parameterizedMissing"), "Missing<String>");
                    if (((DeclaredType) types.get("parameterizedMissing"))
                            .getTypeArguments()
                            .isEmpty()) {
                        // Some JDKs, as JDK 21, give any unresolved parameterized type as <any>.
                        assertThat(types.get("parameterizedMissing")).hasToString("<any>");
                        assertEqualKeys(types.get("parameterizedMissing"), types.get("missingOfInteger"), "both <any>");
                    } else {
                        assertDifferentKeys(
                                types.get("parameterizedMissing"), types.get("missingOfInteger"), "Missing<Integer>");
                    }
                    assertDifferentKeys(types.get("listOfMissing"), types.get("listOfOther"), "List<Other>");
                    assertDifferentKeys(types.get("missing"), context.type(String.class), "String");
                }))
                .failed();
    }

    @Test
    void keysUnresolvedTypesAsAnEquivalenceRelationWhereJavacTakesThemForAnyType() {
        assertThat(FirstRound.run(List.of(UNRESOLVED), List.of(), context -> {
                    NamedTypes types = fields(context, "example.Unresolved");
                    TypeMirror missing = types.get("missing");

                    assertThat(context.isSameType(missing, types.get("other"))).isTrue();
                    assertThat(context.isSameType(missing, context.type(String.class)))
                            .isTrue();
                    assertDifferentKeys(missing, types.get("other"), "Other");

                    for (TypeMirror left : types.types().values()) {
                        for (TypeMirror right : types.types().values()) {
                            if (TypeKey.of(left).equals(TypeKey.of(right))) {
                                assertThat(context.isSameType(left, right))
                                        .as(left + " and " + right)
                                        .isTrue();
                                assertThat(TypeKey.of(left)).hasSameHashCodeAs(TypeKey.of(right));
                            }
                        }
                    }
                }))
                .failed();
    }

    @Test
    void keysUnionTypesByTheirAlternativesInAnyOrder() {
        withBodies((context, types) -> {
            TypeMirror union = types.get("union");

            assertThat(union.getKind()).isEqualTo(TypeKind.UNION);
            assertThat(((UnionType) types.get("annotatedUnion"))
                            .getAlternatives()
                            .toString())
                    .contains("@example.N");
            assertEqualKeys(union, types.get("annotatedUnion"), "annotated alternatives");
            assertEqualKeys(union, types.get("reorderedUnion"), "reordered alternatives");
            assertThat(context.isSameType(union, types.get("annotatedUnion"))).isTrue();
            assertThat(context.isSameType(union, types.get("reorderedUnion"))).isTrue();
        });
    }

    @Test
    void keysUnionTypesWithDifferentAlternativesApartUnlikeJavac() {
        withBodies((context, types) -> {
            TypeMirror union = types.get("union");
            TypeMirror narrower = types.get("narrowerUnion");

            assertThat(context.isSameType(union, narrower)).isTrue();
            assertDifferentKeys(union, narrower, "FileNotFoundException | RuntimeException");
        });
    }

    @Test
    void keysLocalClassesApart() {
        withBodies((context, types) -> {
            TypeMirror local = types.get("first.Local");

            assertThat(context.isSameType(local, types.get("second.Local"))).isFalse();
            assertDifferentKeys(local, types.get("second.Local"), "of the same name in another method");
            assertDifferentKeys(local, types.get("first.OtherLocal"), "of another name");
            assertEqualKeys(local, local, "itself");
            assertEqualKeys(types.get("first.GenericLocal"), types.get("first.GenericLocal"), "generic");
        });
    }

    // The type variable of the generic method of a class of example.Keys, from its parameter.
    private static TypeMirror parameterOf(ProcessingContext context, String type) {
        return method(context, type, "generic").getParameters().getFirst().asType();
    }

    // The type of a generic method of example.Keys.
    private static TypeMirror methodOf(ProcessingContext context, String name) {
        return method(context, "example.Keys", name).asType();
    }

    private static ExecutableElement method(ProcessingContext context, String type, String name) {
        return context.elements().getTypeElement(type).getEnclosedElements().stream()
                .filter(member -> member.getSimpleName().contentEquals(name))
                .map(ExecutableElement.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static NamedTypes fields(ProcessingContext context, String type) {
        Map<String, TypeMirror> types = new LinkedHashMap<>();
        for (Element member : context.elements().getTypeElement(type).getEnclosedElements()) {
            if (member.getKind() == ElementKind.FIELD) {
                types.put(member.getSimpleName().toString(), member.asType());
            }
        }
        return new NamedTypes(types);
    }

    /*
     * Asserts that the keys of every pair of types are equal exactly when isSameType says the types are the same, with
     * equal hash codes then, and returns the pairs of different names that are.
     */
    private static Set<List<String>> sameTypePairs(ProcessingContext context, Map<String, TypeMirror> types) {
        Set<List<String>> same = new HashSet<>();
        for (Map.Entry<String, TypeMirror> leftEntry : types.entrySet()) {
            for (Map.Entry<String, TypeMirror> rightEntry : types.entrySet()) {
                String left = leftEntry.getKey();
                String right = rightEntry.getKey();
                TypeKey leftKey = TypeKey.of(leftEntry.getValue());
                TypeKey rightKey = TypeKey.of(rightEntry.getValue());
                boolean sameType = context.isSameType(leftEntry.getValue(), rightEntry.getValue());

                assertThat(leftKey.equals(rightKey)).as(left + " and " + right).isEqualTo(sameType);
                if (sameType) {
                    assertThat(leftKey).as(left + " and " + right).hasSameHashCodeAs(rightKey);
                    if (!left.equals(right)) {
                        same.add(List.of(left, right));
                    }
                }
            }
        }
        return same;
    }

    // Runs a check, once javac has analyzed example.Bodies, on the types of its catch parameters and local classes.
    private static void withBodies(BiConsumer<ProcessingContext, NamedTypes> check) {
        Analyzed analyzed = new Analyzed(check);

        assertThat(javac().withProcessors(analyzed).compile(BODIES)).succeeded();
        analyzed.rethrow();
    }

    /** The types of a compilation by name. */
    private record NamedTypes(Map<String, TypeMirror> types) {

        TypeMirror get(String name) {
            return Objects.requireNonNull(types.get(name), name);
        }
    }

    /**
     * Runs a check once javac has analyzed {@code example.Bodies}, on the types of its catch parameters, by name, and
     * of its local classes, as {@code <method>.<class>}, and keeps the assertion it failed.
     */
    @SupportedAnnotationTypes("*")
    private static final class Analyzed extends AbstractProcessor {

        private final BiConsumer<ProcessingContext, NamedTypes> check;
        private boolean ran;
        private @Nullable AssertionError failure;

        Analyzed(BiConsumer<ProcessingContext, NamedTypes> check) {
            this.check = check;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public synchronized void init(ProcessingEnvironment environment) {
            super.init(environment);
            Trees trees = Trees.instance(environment);
            JavacTask.instance(environment).addTaskListener(new TaskListener() {
                @Override
                public void finished(TaskEvent event) {
                    TypeElement type = event.getTypeElement();
                    if (event.getKind() == TaskEvent.Kind.ANALYZE
                            && type != null
                            && type.getQualifiedName().contentEquals("example.Bodies")) {
                        Map<String, TypeMirror> types = new LinkedHashMap<>();
                        new BodyScanner(trees, types).scan(trees.getPath(type), null);
                        run(ProcessingContext.of(environment, Analyzed.class), new NamedTypes(types));
                    }
                }
            });
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            return false;
        }

        private void run(ProcessingContext context, NamedTypes types) {
            ran = true;
            try {
                check.accept(context, types);
            } catch (AssertionError e) {
                failure = e;
            }
        }

        void rethrow() {
            if (!ran) {
                throw new AssertionError("The check did not run");
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    /** Collects the types of catch parameters and local classes. */
    private static final class BodyScanner extends TreePathScanner<Void, Void> {

        private final Trees trees;
        private final Map<String, TypeMirror> types;

        BodyScanner(Trees trees, Map<String, TypeMirror> types) {
            this.trees = trees;
            this.types = types;
        }

        @Override
        public Void visitClass(ClassTree tree, Void unused) {
            Element type = trees.getElement(getCurrentPath());
            if (type instanceof TypeElement local && local.getNestingKind() == NestingKind.LOCAL) {
                types.put(
                        Objects.requireNonNull(local.getEnclosingElement()).getSimpleName() + "."
                                + local.getSimpleName(),
                        local.asType());
            }
            return super.visitClass(tree, null);
        }

        @Override
        public Void visitVariable(VariableTree tree, Void unused) {
            Element variable = trees.getElement(getCurrentPath());
            if (variable != null && variable.getKind() == ElementKind.EXCEPTION_PARAMETER) {
                types.put(variable.getSimpleName().toString(), variable.asType());
            }
            return super.visitVariable(tree, null);
        }
    }
}
