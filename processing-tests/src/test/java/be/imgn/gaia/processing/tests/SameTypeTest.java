package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.TypeKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

/**
 * Types compared ignoring type annotations: {@code context.isSameType}. Each annotated type is checked against
 * {@code Types.isSameType} too, which shows that javac ignores type annotations, on each JDK the build runs on.
 */
class SameTypeTest {

    private static final JavaFileObject FIELDS = source("example.Fields", """
            package example;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;
            import java.util.List;
            import java.util.Map;

            @Target(ElementType.TYPE_USE)
            @interface N {}

            class Outer<O> {
                class Inner {}
            }

            class Fields<T, U> {
                String string;
                @N String nullableString;
                int primitive;
                @N int annotatedPrimitive;
                List<String> list;
                @N List<String> annotatedList;
                List<@N String> listOfAnnotated;
                @N List<@N String> annotatedListOfAnnotated;
                Map<String, List<Integer>> map;
                Map<@N String, List<@N Integer>> deeplyAnnotatedMap;

                String[] array;
                @N String[] arrayOfAnnotated;
                String @N [] annotatedArray;
                @N String @N [] annotatedArrayOfAnnotated;
                String[][] arrays;
                String[] @N [] arraysWithAnnotatedComponent;
                int[] primitives;
                @N int @N [] annotatedPrimitives;
                List<String[]> listOfArrays;
                List<String @N []> listOfAnnotatedArrays;

                List<? extends Number> extendsNumber;
                List<? extends @N Number> extendsAnnotatedNumber;
                List<@N ? extends Number> annotatedExtendsNumber;
                List<? super Integer> superInteger;
                List<? super @N Integer> superAnnotatedInteger;
                List<?> unbounded;
                List<@N ?> annotatedUnbounded;
                List<? extends Object> extendsObject;
                List<? super Object> superObject;
                List<? extends Integer> extendsInteger;

                T variable;
                @N T annotatedVariable;
                List<T> listOfVariable;
                List<@N T> listOfAnnotatedVariable;
                List<? extends @N T> extendsAnnotatedVariable;
                U otherVariable;

                Map.Entry<String, String> entry;
                Map.@N Entry<String, String> annotatedEntry;
                Outer<String>.Inner inner;
                Outer<@N String>.Inner innerOfAnnotated;
                Outer<String>.@N Inner annotatedInner;
                Outer<Integer>.Inner innerOfInteger;

                List<Integer> listOfIntegers;
                List rawList;

                <T> void method(T methodVariable, @N T annotatedMethodVariable) {}
            }
            """);

    private static final List<List<String>> ANNOTATED_PAIRS = List.of(
            List.of("string", "nullableString"),
            List.of("primitive", "annotatedPrimitive"),
            List.of("list", "annotatedList"),
            List.of("list", "listOfAnnotated"),
            List.of("list", "annotatedListOfAnnotated"),
            List.of("map", "deeplyAnnotatedMap"),
            List.of("array", "arrayOfAnnotated"),
            List.of("array", "annotatedArray"),
            List.of("array", "annotatedArrayOfAnnotated"),
            List.of("arrays", "arraysWithAnnotatedComponent"),
            List.of("primitives", "annotatedPrimitives"),
            List.of("listOfArrays", "listOfAnnotatedArrays"),
            List.of("extendsNumber", "extendsAnnotatedNumber"),
            List.of("extendsNumber", "annotatedExtendsNumber"),
            List.of("superInteger", "superAnnotatedInteger"),
            List.of("unbounded", "annotatedUnbounded"),
            List.of("variable", "annotatedVariable"),
            List.of("listOfVariable", "listOfAnnotatedVariable"),
            List.of("entry", "annotatedEntry"),
            List.of("inner", "innerOfAnnotated"),
            List.of("inner", "annotatedInner"),
            List.of("methodVariable", "annotatedMethodVariable"));

    // Runs a check on the types of the fields and method parameters of example.Fields, by name.
    private static void withTypes(BiConsumer<ProcessingContext, Fields> check) {
        assertThat(FirstRound.run(List.of(FIELDS), List.of(), context -> check.accept(context, types(context))))
                .succeeded();
    }

    private static Fields types(ProcessingContext context) {
        Map<String, TypeMirror> types = new LinkedHashMap<>();
        for (Element member :
                context.elements().getTypeElement("example.Fields").getEnclosedElements()) {
            if (member.getKind() == ElementKind.FIELD) {
                types.put(member.getSimpleName().toString(), member.asType());
            } else if (member.getKind() == ElementKind.METHOD) {
                for (VariableElement parameter : ((ExecutableElement) member).getParameters()) {
                    types.put(parameter.getSimpleName().toString(), parameter.asType());
                }
            }
        }
        return new Fields(types);
    }

    // The first type argument of a field's type, such as the wildcard of List<? extends Number>.
    private static TypeMirror argument(Fields types, String field) {
        return ((DeclaredType) types.get(field)).getTypeArguments().getFirst();
    }

    @Test
    void comparesTypesThatCarryTheirTypeAnnotations() {
        withTypes((context, types) -> {
            for (List<String> pair : ANNOTATED_PAIRS) {
                assertThat(types.get(pair.get(0)).toString()).doesNotContain("@example.N");
                assertThat(types.get(pair.get(1)).toString()).as(pair.get(1)).contains("@example.N");
            }
            assertThat(types.get("nullableString").getAnnotationMirrors()).hasSize(1);
            assertThat(argument(types, "listOfAnnotated").getAnnotationMirrors())
                    .hasSize(1);
        });
    }

    @Test
    void ignoresTypeAnnotationsAtEveryDepth() {
        withTypes((context, types) -> {
            for (List<String> pair : ANNOTATED_PAIRS) {
                TypeMirror plain = types.get(pair.get(0));
                TypeMirror annotated = types.get(pair.get(1));

                assertThat(context.isSameType(plain, annotated))
                        .as(pair.toString())
                        .isTrue();
                assertThat(context.isSameType(annotated, plain))
                        .as(pair.toString())
                        .isTrue();
                assertThat(context.isSameType(annotated, annotated))
                        .as(pair.toString())
                        .isTrue();
                assertThat(context.types().isSameType(plain, annotated))
                        .as("javac on " + pair)
                        .isTrue();
            }
        });
    }

    @Test
    void ignoresTypeAnnotationsOnTypeArgumentsBoundToTypeVariables() {
        withTypes((context, types) -> {
            TypeMirror variable = types.get("variable");

            assertThat(context.isSameType(
                            argument(types, "extendsAnnotatedVariable"),
                            context.types().getWildcardType(variable, null)))
                    .isTrue();
            assertThat(context.isSameType(types.get("listOfAnnotatedVariable"), context.type(List.class, variable)))
                    .isTrue();
        });
    }

    @Test
    void comparesBuiltTypesWithTypesFromSource() {
        withTypes((context, types) -> {
            assertThat(context.isSameType(context.type(String.class), types.get("nullableString")))
                    .isTrue();
            assertThat(context.isSameType(context.type(int.class), types.get("annotatedPrimitive")))
                    .isTrue();
            assertThat(context.isSameType(
                            context.type(List.class, String.class), types.get("annotatedListOfAnnotated")))
                    .isTrue();
            assertThat(context.isSameType(context.type(String[][].class), types.get("arraysWithAnnotatedComponent")))
                    .isTrue();
            assertThat(context.isSameType(context.type(int[].class), types.get("annotatedPrimitives")))
                    .isTrue();
            assertThat(context.isSameType(context.type(List.class), types.get("rawList")))
                    .isTrue();
        });
    }

    @Test
    void tellsDifferentTypesApart() {
        withTypes((context, types) -> {
            assertThat(context.isSameType(types.get("list"), types.get("listOfIntegers")))
                    .isFalse();
            assertThat(context.isSameType(types.get("nullableString"), types.get("annotatedList")))
                    .isFalse();
            assertThat(context.isSameType(types.get("array"), types.get("arrays")))
                    .isFalse();
            assertThat(context.isSameType(types.get("array"), types.get("string")))
                    .isFalse();
            assertThat(context.isSameType(types.get("primitives"), types.get("array")))
                    .isFalse();
            assertThat(context.isSameType(types.get("primitive"), context.type(long.class)))
                    .isFalse();
            assertThat(context.isSameType(types.get("primitive"), context.type(Integer.class)))
                    .isFalse();
            assertThat(context.isSameType(types.get("extendsNumber"), types.get("superInteger")))
                    .isFalse();
            assertThat(context.isSameType(types.get("extendsNumber"), types.get("extendsInteger")))
                    .isFalse();
            assertThat(context.isSameType(types.get("inner"), types.get("innerOfInteger")))
                    .isFalse();
            assertThat(context.isSameType(context.type(void.class), types.get("primitive")))
                    .isFalse();
        });
    }

    @Test
    void tellsRawTypesFromParameterizedTypesAndFromTheTypeOfTheirDeclaration() {
        withTypes((context, types) -> {
            TypeMirror declaration =
                    context.elements().getTypeElement("java.util.List").asType();

            assertThat(context.isSameType(types.get("rawList"), types.get("list")))
                    .isFalse();
            assertThat(context.isSameType(types.get("rawList"), declaration)).isFalse();
            assertThat(context.isSameType(types.get("list"), declaration)).isFalse();
            assertThat(context.isSameType(declaration, declaration)).isTrue();
        });
    }

    @Test
    void comparesTypeVariablesByIdentityNotByName() {
        withTypes((context, types) -> {
            assertThat(context.isSameType(types.get("variable"), types.get("variable")))
                    .isTrue();
            assertThat(context.isSameType(types.get("variable"), types.get("otherVariable")))
                    .isFalse();
            assertThat(types.get("methodVariable")).hasToString("T");
            assertThat(context.isSameType(types.get("variable"), types.get("methodVariable")))
                    .isFalse();
            assertThat(context.isSameType(types.get("annotatedVariable"), types.get("annotatedMethodVariable")))
                    .isFalse();
        });
    }

    @Test
    void comparesWildcardsByTheirBoundsWhereJavacSaysTheyAreNeverTheSame() {
        withTypes((context, types) -> {
            TypeMirror extendsNumber = argument(types, "extendsNumber");

            assertThat(context.types().isSameType(extendsNumber, extendsNumber)).isFalse();
            assertThat(context.isSameType(extendsNumber, extendsNumber)).isTrue();
            assertThat(context.isSameType(extendsNumber, argument(types, "extendsAnnotatedNumber")))
                    .isTrue();
            assertThat(context.isSameType(extendsNumber, argument(types, "annotatedExtendsNumber")))
                    .isTrue();
            assertThat(context.isSameType(argument(types, "superInteger"), argument(types, "superAnnotatedInteger")))
                    .isTrue();
            assertThat(context.isSameType(argument(types, "unbounded"), argument(types, "annotatedUnbounded")))
                    .isTrue();
            assertThat(context.isSameType(
                            argument(types, "unbounded"), context.types().getWildcardType(null, null)))
                    .isTrue();
        });
    }

    @Test
    void comparesAnUnboundedWildcardAsBoundedByObjectAsJavacDoesForTypeArguments() {
        withTypes((context, types) -> {
            assertThat(context.types().isSameType(types.get("unbounded"), types.get("extendsObject")))
                    .isTrue();
            assertThat(context.isSameType(argument(types, "unbounded"), argument(types, "extendsObject")))
                    .isTrue();
            assertThat(context.isSameType(argument(types, "extendsObject"), argument(types, "unbounded")))
                    .isTrue();
            assertThat(context.types().isSameType(types.get("unbounded"), types.get("superObject")))
                    .isFalse();
            assertThat(context.isSameType(argument(types, "unbounded"), argument(types, "superObject")))
                    .isFalse();
            assertThat(context.isSameType(argument(types, "superObject"), argument(types, "unbounded")))
                    .isFalse();
        });
    }

    @Test
    void tellsWildcardsWithDifferentBoundsApart() {
        withTypes((context, types) -> {
            TypeMirror extendsNumber = argument(types, "extendsNumber");
            TypeMirror number = context.type(Number.class);

            assertThat(context.isSameType(extendsNumber, argument(types, "extendsInteger")))
                    .isFalse();
            assertThat(context.isSameType(extendsNumber, context.types().getWildcardType(null, number)))
                    .isFalse();
            assertThat(context.isSameType(context.types().getWildcardType(null, number), extendsNumber))
                    .isFalse();
            assertThat(context.isSameType(extendsNumber, argument(types, "unbounded")))
                    .isFalse();
            assertThat(context.isSameType(argument(types, "superInteger"), argument(types, "superObject")))
                    .isFalse();
        });
    }

    @Test
    void neverTakesAWildcardForATypeThatIsNotOne() {
        withTypes((context, types) -> {
            TypeMirror extendsNumber = argument(types, "extendsNumber");
            TypeMirror number = context.type(Number.class);
            TypeMirror object = context.type(Object.class);

            assertThat(context.isSameType(extendsNumber, number)).isFalse();
            assertThat(context.isSameType(number, extendsNumber)).isFalse();
            assertThat(context.isSameType(argument(types, "unbounded"), object)).isFalse();
            assertThat(context.isSameType(object, argument(types, "unbounded"))).isFalse();
        });
    }

    @Test
    void keysTypesThatDifferOnlyInTypeAnnotationsEqually() {
        withTypes((context, types) -> {
            for (List<String> pair : ANNOTATED_PAIRS) {
                TypeKey plain = TypeKey.of(types.get(pair.get(0)));
                TypeKey annotated = TypeKey.of(types.get(pair.get(1)));

                assertThat(annotated).as(pair.toString()).isEqualTo(plain).hasSameHashCodeAs(plain);
            }
        });
    }

    @Test
    void takesAnUnresolvedTypeForAnyTypeAsJavacDoes() {
        JavaFileObject unresolved = source("example.Unresolved", """
                package example;

                class Unresolved {
                    Missing missing;
                }
                """);

        assertThat(FirstRound.run(List.of(unresolved), List.of(), context -> {
                    TypeMirror missing =
                            context.elements().getTypeElement("example.Unresolved").getEnclosedElements().stream()
                                    .filter(member -> member.getKind() == ElementKind.FIELD)
                                    .findFirst()
                                    .orElseThrow()
                                    .asType();

                    assertThat(missing.getKind()).isEqualTo(TypeKind.ERROR);
                    assertThat(context.isSameType(missing, context.type(String.class)))
                            .isTrue();
                    assertThat(context.isSameType(context.type(int.class), missing))
                            .isTrue();
                }))
                .failed();
    }

    /** The types of the fields and method parameters of {@code example.Fields}, by name. */
    private record Fields(Map<String, TypeMirror> types) {

        TypeMirror get(String name) {
            return Objects.requireNonNull(types.get(name), name);
        }
    }
}
