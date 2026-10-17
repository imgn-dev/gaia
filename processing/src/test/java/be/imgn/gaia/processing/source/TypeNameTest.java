package be.imgn.gaia.processing.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TypeNameTest {

    private static final Annotation NULLABLE = Annotation.of(ClassName.of("org.example", "Nullable"));
    private static final ClassName OUTER = ClassName.of("org.example", "Outer");

    static Stream<Arguments> names() {
        ClassName string = ClassName.of(String.class);
        return Stream.of(
                Arguments.of(PrimitiveTypeName.INT, "int"),
                Arguments.of(PrimitiveTypeName.VOID, "void"),
                Arguments.of(string, "java.lang.String"),
                Arguments.of(ClassName.of(Map.Entry.class), "java.util.Map.Entry"),
                Arguments.of(ClassName.of("", "Default"), "Default"),
                Arguments.of(ClassName.of(List.class).parameterized(string), "java.util.List<java.lang.String>"),
                Arguments.of(
                        ParameterizedTypeName.of(
                                ClassName.of(Map.class),
                                string,
                                ClassName.of(List.class)
                                        .parameterized(WildcardTypeName.subtypeOf(ClassName.of(Number.class)))),
                        "java.util.Map<java.lang.String, java.util.List<? extends java.lang.Number>>"),
                Arguments.of(WildcardTypeName.unbounded(), "?"),
                Arguments.of(WildcardTypeName.supertypeOf(ClassName.of(Integer.class)), "? super java.lang.Integer"),
                Arguments.of(ArrayTypeName.of(PrimitiveTypeName.INT), "int[]"),
                Arguments.of(ArrayTypeName.of(ArrayTypeName.of(string)), "java.lang.String[][]"),
                Arguments.of(TypeVariableName.of("T", ClassName.of(Number.class)), "T"),
                Arguments.of(string.annotated(NULLABLE), "java.lang.@org.example.Nullable String"),
                Arguments.of(
                        ClassName.of(Map.Entry.class).annotated(NULLABLE), "java.util.Map.@org.example.Nullable Entry"),
                Arguments.of(
                        ClassName.of(List.class)
                                .parameterized(string.annotated(NULLABLE))
                                .annotated(NULLABLE),
                        "java.util.@org.example.Nullable List<java.lang.@org.example.Nullable String>"),
                Arguments.of(PrimitiveTypeName.INT.annotated(NULLABLE), "@org.example.Nullable int"),
                Arguments.of(ArrayTypeName.of(string).annotated(NULLABLE), "java.lang.String @org.example.Nullable []"),
                Arguments.of(
                        ArrayTypeName.of(ArrayTypeName.of(PrimitiveTypeName.INT)
                                        .annotated(Annotation.of(ClassName.of("org.example", "B"))))
                                .annotated(Annotation.of(ClassName.of("org.example", "A"))),
                        "int @org.example.A [] @org.example.B []"),
                Arguments.of(TypeVariableName.of("T").annotated(NULLABLE), "@org.example.Nullable T"),
                Arguments.of(WildcardTypeName.unbounded().annotated(NULLABLE), "@org.example.Nullable ?"),
                Arguments.of(WildcardTypeName.subtypeOf(ClassName.OBJECT), "?"),
                Arguments.of(
                        WildcardTypeName.subtypeOf(ClassName.OBJECT.annotated(NULLABLE)),
                        "? extends java.lang.@org.example.Nullable Object"),
                Arguments.of(OUTER.parameterized(string).nested("Inner"), "org.example.Outer<java.lang.String>.Inner"),
                Arguments.of(
                        OUTER.parameterized(string).nested("Inner", ClassName.of(Integer.class)),
                        "org.example.Outer<java.lang.String>.Inner<java.lang.Integer>"),
                Arguments.of(
                        OUTER.parameterized(string).nested("Inner").annotated(NULLABLE),
                        "org.example.Outer<java.lang.String>.@org.example.Nullable Inner"),
                Arguments.of(
                        new ParameterizedTypeName(
                                OUTER.annotated(NULLABLE), OUTER.nested("Inner"), List.of(string), List.of()),
                        "org.example.@org.example.Nullable Outer.Inner<java.lang.String>"));
    }

    @ParameterizedTest
    @MethodSource("names")
    void writesTheName(TypeName name, String expected) {
        assertThat(name).hasToString(expected);
    }

    @Nested
    class FromClasses {

        @Test
        void namesPrimitivesVoidAndArrays() {
            assertThat(TypeName.of(int.class)).isEqualTo(PrimitiveTypeName.INT);
            assertThat(TypeName.of(void.class)).isEqualTo(PrimitiveTypeName.VOID);
            assertThat(TypeName.of(long[][].class))
                    .isEqualTo(ArrayTypeName.of(ArrayTypeName.of(PrimitiveTypeName.LONG)));
            assertThat(TypeName.of(String[].class)).isEqualTo(ArrayTypeName.of(ClassName.of(String.class)));
        }

        @Test
        void namesNestedClasses() {
            ClassName entry = ClassName.of(Map.Entry.class);

            assertThat(entry.packageName()).isEqualTo("java.util");
            assertThat(entry.simpleNames()).containsExactly("Map", "Entry");
            assertThat(entry.simpleName()).isEqualTo("Entry");
            assertThat(entry.canonicalName()).isEqualTo("java.util.Map.Entry");
            assertThat(entry.topLevel()).isEqualTo(ClassName.of(Map.class));
            assertThat(entry.enclosing()).contains(ClassName.of(Map.class));
            assertThat(ClassName.of(Map.class).enclosing()).isEmpty();
            assertThat(ClassName.of(Map.class).nested("Entry")).isEqualTo(entry);
            assertThat(entry.peer("Builder")).isEqualTo(ClassName.of("java.util", "Map", "Builder"));
            assertThat(ClassName.of(Map.class).peer("List")).isEqualTo(ClassName.of(List.class));
        }

        @Test
        void giveTheBinaryNameForReflection() {
            assertThat(ClassName.of(Map.Entry.class).reflectionName()).isEqualTo("java.util.Map$Entry");
            assertThat(ClassName.of(String.class).reflectionName()).isEqualTo("java.lang.String");
            assertThat(ClassName.of("", "Default", "Nested").reflectionName()).isEqualTo("Default$Nested");
        }

        @Test
        void dropTheAnnotationsOfTheEnclosingType() {
            assertThat(ClassName.of(Map.class).annotated(NULLABLE).nested("Entry"))
                    .isEqualTo(ClassName.of(Map.Entry.class));
            assertThat(ClassName.of(Map.Entry.class).annotated(NULLABLE).topLevel())
                    .isEqualTo(ClassName.of(Map.class));
        }

        @Test
        void rejectsClassesWithoutCanonicalName() {
            Object anonymous = new Object() {};

            assertThatIllegalArgumentException().isThrownBy(() -> ClassName.of(anonymous.getClass()));
            assertThatIllegalArgumentException().isThrownBy(() -> ClassName.of(int.class));
            assertThatIllegalArgumentException().isThrownBy(() -> ClassName.of(String[].class));
        }
    }

    @Nested
    class FromReflection {

        @Test
        void namesClassesAndParameterizedTypes() {
            assertThat(TypeName.of(returnType("string"))).isEqualTo(ClassName.of(String.class));
            assertThat(TypeName.of(returnType("ints"))).isEqualTo(ArrayTypeName.of(PrimitiveTypeName.INT));
            assertThat(TypeName.of(returnType("map")))
                    .hasToString("java.util.Map<java.lang.String, java.util.List<? extends java.lang.Number>>");
            assertThat(TypeName.of(returnType("entry")))
                    .hasToString("java.util.Map.Entry<java.lang.String, java.lang.Integer>");
        }

        @Test
        void namesTypeVariablesGenericArraysAndWildcards() {
            assertThat(TypeName.of(returnType("variable"))).isEqualTo(TypeVariableName.of("T"));
            assertThat(TypeName.of(returnType("lists"))).hasToString("java.util.List<T>[]");
            assertThat(TypeName.of(returnType("lower"))).hasToString("java.util.List<? super java.lang.Integer>");
            assertThat(TypeName.of(returnType("unbounded"))).hasToString("java.util.List<?>");
            assertThat(TypeName.of(returnType("objects"))).isEqualTo(TypeName.of(returnType("unbounded")));
        }

        @Test
        void keepAParameterizedOwnerType() {
            assertThat(TypeName.of(returnType("inner")))
                    .isEqualTo(new ParameterizedTypeName(
                            ClassName.of(Outer.class).parameterized(ClassName.of(String.class)),
                            ClassName.of(Outer.Inner.class),
                            List.of(),
                            List.of()));
        }

        @Test
        void buildNamesFromReflectiveTypes() {
            assertThat(ParameterizedTypeName.of(Map.class, String.class, returnType("lower")))
                    .hasToString("java.util.Map<java.lang.String, java.util.List<? super java.lang.Integer>>");
            assertThat(WildcardTypeName.subtypeOf(returnType("variable"))).hasToString("? extends T");
            assertThat(WildcardTypeName.supertypeOf(Integer.class))
                    .isEqualTo(WildcardTypeName.supertypeOf(ClassName.of(Integer.class)));
            assertThat(ArrayTypeName.of(returnType("unbounded"))).hasToString("java.util.List<?>[]");
            assertThatIllegalArgumentException().isThrownBy(() -> ParameterizedTypeName.of(int.class, String.class));
            assertThatIllegalArgumentException().isThrownBy(() -> ParameterizedTypeName.of(List.class));
        }

        @Test
        void declareTypeVariablesWithTheirBounds() throws NoSuchMethodException {
            var max = Collections.class.getMethod("max", Collection.class).getTypeParameters()[0];

            assertThat(TypeVariableName.of(max))
                    .isEqualTo(TypeVariableName.of(
                            "T",
                            ClassName.of(Comparable.class)
                                    .parameterized(WildcardTypeName.supertypeOf(TypeVariableName.of("T")))));
            assertThat(TypeVariableName.of(List.class.getTypeParameters()[0])).isEqualTo(TypeVariableName.of("E"));
            assertThat(TypeVariableName.of(Samples.class.getTypeParameters()[0]))
                    .isEqualTo(TypeVariableName.of("T", ClassName.of(Number.class)));
        }

        @Test
        void serveAsTypesInCode() {
            assertThat(CodeBlock.of("$T values", returnType("map")))
                    .hasToString("java.util.Map<java.lang.String, java.util.List<? extends java.lang.Number>> values");
        }

        @Test
        void rejectLocalClassesAndUnknownKinds() {
            class Local {}
            java.lang.reflect.Type local = Local.class;
            java.lang.reflect.Type listOfLocal =
                    new ArrayList<Local>() {}.getClass().getGenericSuperclass();
            java.lang.reflect.Type unknown = new java.lang.reflect.Type() {};

            assertThatIllegalArgumentException().isThrownBy(() -> TypeName.of(local));
            assertThatIllegalArgumentException().isThrownBy(() -> TypeName.of(listOfLocal));
            assertThatIllegalArgumentException().isThrownBy(() -> TypeName.of(unknown));
        }

        private static java.lang.reflect.Type returnType(String method) {
            try {
                return Samples.class.getMethod(method).getGenericReturnType();
            } catch (NoSuchMethodException e) {
                throw new AssertionError(e);
            }
        }

        interface Samples<T extends Number> {

            String string();

            int[] ints();

            Map<String, List<? extends Number>> map();

            Map.Entry<String, Integer> entry();

            T variable();

            List<T>[] lists();

            List<? super Integer> lower();

            List<?> unbounded();

            List<? extends Object> objects();

            Outer<String>.Inner inner();
        }

        static class Outer<T> {

            abstract class Inner {

                abstract T value();
            }
        }
    }

    @Nested
    class Values {

        @Test
        void areEqualWhenTheyNameTheSameTypeWithTheSameAnnotations() {
            TypeName list = ClassName.of(List.class).parameterized(ClassName.of(String.class));

            assertThat(list)
                    .isEqualTo(ParameterizedTypeName.of(
                            ClassName.of("java.util", "List"), ClassName.of("java.lang", "String")));
            assertThat(list).hasSameHashCodeAs(ClassName.of(List.class).parameterized(ClassName.of(String.class)));
            assertThat(list.annotated(NULLABLE)).isNotEqualTo(list);
            assertThat(list.annotated(NULLABLE).withoutAnnotations()).isEqualTo(list);
        }

        @Test
        void orderClassNamesByPackageNamesAndAnnotations() {
            ClassName string = ClassName.of(String.class);
            ClassName entry = ClassName.of(Map.Entry.class);
            List<ClassName> names = List.of(
                    entry,
                    string.annotated(NULLABLE),
                    ClassName.of(Map.class),
                    string,
                    ClassName.of("", "Default"),
                    ClassName.of("java", "util", "Map"),
                    entry.annotated(NULLABLE));

            assertThat(names.stream().sorted())
                    .containsExactly(
                            ClassName.of("", "Default"),
                            ClassName.of("java", "util", "Map"),
                            string,
                            string.annotated(NULLABLE),
                            ClassName.of(Map.class),
                            entry,
                            entry.annotated(NULLABLE));
            assertThat(new TreeSet<>(names)).hasSameSizeAs(names);
            assertThat(string.compareTo(ClassName.of(String.class))).isZero();
        }

        @Test
        void addBoundsToATypeVariable() {
            TypeVariableName variable =
                    TypeVariableName.of("T", ClassName.of(Number.class)).annotated(NULLABLE);
            TypeName comparable = ClassName.of(Comparable.class).parameterized(TypeVariableName.of("T"));

            TypeVariableName bounded = variable.withBounds(comparable);

            assertThat(bounded.bounds()).containsExactly(ClassName.of(Number.class), comparable);
            assertThat(bounded.annotations()).containsExactly(NULLABLE);
            assertThat(variable.withBounds(List.of())).isEqualTo(variable);
            assertThatIllegalArgumentException().isThrownBy(() -> variable.withBounds(PrimitiveTypeName.INT));
        }

        @Test
        void accumulateAnnotations() {
            Annotation other = Annotation.of(ClassName.of("org.example", "Other"));

            assertThat(ClassName.of(String.class)
                            .annotated(NULLABLE)
                            .annotated(other)
                            .annotations())
                    .containsExactly(NULLABLE, other);
        }

        @Test
        void boxPrimitivesKeepingAnnotations() {
            assertThat(PrimitiveTypeName.INT.box()).isEqualTo(ClassName.of(Integer.class));
            assertThat(PrimitiveTypeName.CHAR.box()).isEqualTo(ClassName.of(Character.class));
            assertThat(PrimitiveTypeName.VOID.box()).isEqualTo(ClassName.of(Void.class));
            assertThat(PrimitiveTypeName.BOOLEAN.annotated(NULLABLE).box())
                    .isEqualTo(ClassName.of(Boolean.class).annotated(NULLABLE));
            assertThat(ClassName.of(String.class).box()).isEqualTo(ClassName.of(String.class));
            assertThat(PrimitiveTypeName.INT.isPrimitive()).isTrue();
            assertThat(PrimitiveTypeName.VOID.isPrimitive()).isFalse();
            assertThat(ClassName.of(Integer.class).isPrimitive()).isFalse();
        }

        @Test
        void unboxEveryBoxBackKeepingAnnotations() {
            for (Class<?> primitive : List.of(
                    boolean.class,
                    byte.class,
                    short.class,
                    int.class,
                    long.class,
                    char.class,
                    float.class,
                    double.class,
                    void.class)) {
                TypeName name = TypeName.of(primitive).annotated(NULLABLE);

                assertThat(name.box().unbox()).isEqualTo(name);
                assertThat(name.unbox()).isEqualTo(name);
                assertThat(name.box().isBoxedPrimitive()).isEqualTo(primitive != void.class);
                assertThat(name.isBoxedPrimitive()).isFalse();
            }
        }

        @Test
        void unboxOnlyBoxes() {
            assertThat(ClassName.of(String.class).isBoxedPrimitive()).isFalse();
            assertThat(ClassName.of("org.example", "Integer").isBoxedPrimitive())
                    .isFalse();
            assertThat(ClassName.of("java.lang", "Map", "Integer").isBoxedPrimitive())
                    .isFalse();
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> ClassName.of(String.class).unbox());
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> ArrayTypeName.of(PrimitiveTypeName.INT).unbox());
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> ClassName.of("org.example", "Integer").unbox());
        }

        @Test
        void tellWhetherTheyAreAnnotated() {
            TypeName list = ClassName.of(List.class)
                    .parameterized(ClassName.of(String.class).annotated(NULLABLE));

            assertThat(list.isAnnotated()).isFalse();
            assertThat(list.annotated(NULLABLE).isAnnotated()).isTrue();
            assertThat(list.annotated(NULLABLE).withoutAnnotations()).isEqualTo(list);
            assertThat(ArrayTypeName.of(PrimitiveTypeName.INT.annotated(NULLABLE))
                            .isAnnotated())
                    .isFalse();
        }

        @Test
        void keepTheAnnotationsOfARawTypeOnTheParameterizedType() {
            ParameterizedTypeName list =
                    ParameterizedTypeName.of(ClassName.of(List.class).annotated(NULLABLE), ClassName.of(String.class));

            assertThat(list.rawType()).isEqualTo(ClassName.of(List.class));
            assertThat(list.annotations()).containsExactly(NULLABLE);
            assertThat(list)
                    .isEqualTo(ClassName.of(List.class).annotated(NULLABLE).parameterized(ClassName.of(String.class)));
        }

        @Test
        void normalizeAWildcardBoundedByObject() {
            assertThat(WildcardTypeName.subtypeOf(ClassName.OBJECT)).isEqualTo(WildcardTypeName.unbounded());
            assertThat(WildcardTypeName.subtypeOf(TypeName.of(Object.class)).upperBound())
                    .isNull();
            assertThat(WildcardTypeName.supertypeOf(ClassName.OBJECT).lowerBound())
                    .isEqualTo(ClassName.OBJECT);
        }

        @Test
        void areEqualAcrossEveryKind() {
            List<TypeName> names = List.of(
                    PrimitiveTypeName.INT,
                    ClassName.of(Integer.class),
                    ArrayTypeName.of(PrimitiveTypeName.INT),
                    ClassName.of(List.class).parameterized(ClassName.of(Integer.class)),
                    OUTER.parameterized(ClassName.of(Integer.class)).nested("Inner"),
                    TypeVariableName.of("Integer"),
                    WildcardTypeName.subtypeOf(ClassName.of(Integer.class)),
                    WildcardTypeName.supertypeOf(ClassName.of(Integer.class)));

            for (int i = 0; i < names.size(); i++) {
                for (int j = 0; j < names.size(); j++) {
                    assertThat(names.get(i).equals(names.get(j))).isEqualTo(i == j);
                }
            }
            assertThat(TypeVariableName.of("T", ClassName.of(Number.class))).isNotEqualTo(TypeVariableName.of("T"));
            assertThat(TypeName.of(int[].class)).hasSameHashCodeAs(ArrayTypeName.of(PrimitiveTypeName.INT));
        }

        @Test
        void guessThePackageFromTheCase() {
            assertThat(ClassName.bestGuess("java.util.Map.Entry")).isEqualTo(ClassName.of(Map.Entry.class));
            assertThat(ClassName.bestGuess("java.lang.String")).isEqualTo(ClassName.of(String.class));
            assertThat(ClassName.bestGuess("Default")).isEqualTo(ClassName.of("", "Default"));
            assertThat(ClassName.bestGuess("Default.Nested")).isEqualTo(ClassName.of("", "Default", "Nested"));
            assertThat(ClassName.bestGuess("org.example.ÉtéDouble"))
                    .isEqualTo(ClassName.of("org.example", "ÉtéDouble"));
            // A mathematical bold capital A lies outside the Basic Multilingual Plane: it starts a class name too.
            assertThat(ClassName.bestGuess("org.example.\uD835\uDC00lpha"))
                    .isEqualTo(ClassName.of("org.example", "\uD835\uDC00lpha"));
        }

        @Test
        void refuseToGuessWithoutAClearClassName() {
            for (String name : List.of(
                    "",
                    ".Map",
                    "java.util.",
                    "java..util.Map",
                    "java.util",
                    "java.util.Map.entry",
                    "java.util.Map..Entry",
                    "!@#$gibberish%^&*")) {
                assertThatIllegalArgumentException().as(name).isThrownBy(() -> ClassName.bestGuess(name));
            }
        }
    }

    @Nested
    class Rejects {

        @Test
        void invalidNames() {
            assertThatIllegalArgumentException().isThrownBy(() -> ClassName.of("java.util", "1List"));
            assertThatIllegalArgumentException().isThrownBy(() -> ClassName.of("java.util", "class"));
            assertThatIllegalArgumentException().isThrownBy(() -> ClassName.of("java..util", "List"));
            assertThatIllegalArgumentException().isThrownBy(() -> TypeVariableName.of("T-1"));
            assertThatIllegalArgumentException().isThrownBy(() -> PrimitiveTypeName.of("integer"));
        }

        @Test
        void primitiveTypeArgumentsAndBounds() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> ClassName.of(List.class).parameterized(PrimitiveTypeName.INT));
            assertThatIllegalArgumentException().isThrownBy(() -> WildcardTypeName.subtypeOf(PrimitiveTypeName.INT));
            assertThatIllegalArgumentException().isThrownBy(() -> TypeVariableName.of("T", PrimitiveTypeName.INT));
        }

        @Test
        void malformedTypes() {
            assertThatIllegalArgumentException().isThrownBy(() -> ParameterizedTypeName.of(ClassName.of(List.class)));
            assertThatIllegalArgumentException().isThrownBy(() -> ArrayTypeName.of(PrimitiveTypeName.VOID));
            ClassName number = ClassName.of(Number.class);
            assertThatIllegalArgumentException().isThrownBy(() -> new WildcardTypeName(number, number, List.of()));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ParameterizedTypeName(
                            null, ClassName.of(List.class).annotated(NULLABLE), List.of(ClassName.OBJECT), List.of()));
        }

        @Test
        void ownerTypesThatDoNotEncloseTheType() {
            ClassName inner = OUTER.nested("Inner");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ParameterizedTypeName(ClassName.of(List.class), inner, List.of(), List.of()));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ParameterizedTypeName(
                            OUTER.parameterized(ClassName.OBJECT), inner.nested("Deeper"), List.of(), List.of()));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ParameterizedTypeName(ArrayTypeName.of(OUTER), inner, List.of(), List.of()));
        }

        @Test
        void wildcardsWhereATypeIsExpected() {
            WildcardTypeName wildcard = WildcardTypeName.unbounded();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> ArrayTypeName.of(wildcard))
                    .withMessageContaining("wildcard elements");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> WildcardTypeName.subtypeOf(wildcard))
                    .withMessageContaining("cannot be a wildcard");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> WildcardTypeName.supertypeOf(wildcard))
                    .withMessageContaining("cannot be a wildcard");
            assertThatIllegalArgumentException().isThrownBy(() -> TypeVariableName.of("T", wildcard));
        }

        @Test
        void boundsThatATypeVariableCannotHave() {
            ArrayTypeName strings = ArrayTypeName.of(ClassName.of(String.class));

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> TypeVariableName.of("T", strings))
                    .withMessageContaining("an array");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> TypeVariableName.of("U", TypeVariableName.of("T"), ClassName.of(Runnable.class)))
                    .withMessageContaining("other bounds");
            assertThat(TypeVariableName.of("U", TypeVariableName.of("T")).bounds())
                    .containsExactly(TypeVariableName.of("T"));
            assertThat(WildcardTypeName.subtypeOf(strings)).hasToString("? extends java.lang.String[]");
        }
    }
}
