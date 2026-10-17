package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.processing.MoreTypes;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

/** Whether casting an {@code Object} to a type is unchecked: {@code MoreTypes.isCastFromObjectUnchecked}. */
class UncheckedCastTest {

    private static final JavaFileObject CASTS = source("example.Casts", """
            package example;

            import java.util.List;
            import java.util.Map;

            class Casts<T> {
                class Inner {}

                static class Nested {}

                T variable;
                T[] variables;
                List<String> parameterized;
                List<? super Object> lowerBounded;
                List<? extends Number> upperBounded;
                Casts<String>.Inner innerOfParameterized;
                List<String>[] parameterizedArray;

                List<?> wildcard;
                List<? extends Object> objects;
                List<?>[] wildcardArray;
                Map.Entry<?, ?> entry;
                @SuppressWarnings("rawtypes")
                List raw;
                Nested nested;
                String string;
                String[] strings;
                int primitive;

                <I extends Comparable<String> & Runnable> void parameterizedBound(I value) {}

                <W extends Comparable<?> & Runnable> void wildcardBound(W value) {}
            }
            """);

    private static final List<String> UNCHECKED = List.of(
            "variable",
            "variables",
            "parameterized",
            "lowerBounded",
            "upperBounded",
            "innerOfParameterized",
            "parameterizedArray");

    private static final List<String> CHECKED =
            List.of("wildcard", "objects", "wildcardArray", "entry", "raw", "nested", "string", "strings", "primitive");

    @Test
    void tellsUncheckedCastsFromCheckedOnes() {
        assertThat(FirstRound.run(List.of(CASTS), List.of(), context -> {
                    TypeElement casts = context.elements().getTypeElement("example.Casts");
                    for (VariableElement field : ElementFilter.fieldsIn(casts.getEnclosedElements())) {
                        String name = field.getSimpleName().toString();
                        assertThat(MoreTypes.isCastFromObjectUnchecked(field.asType()))
                                .as(name)
                                .isEqualTo(UNCHECKED.contains(name));
                    }
                    assertThat(ElementFilter.fieldsIn(casts.getEnclosedElements()))
                            .hasSize(UNCHECKED.size() + CHECKED.size());
                }))
                .succeeded();
    }

    @Test
    void checksEachBoundOfAnIntersection() {
        assertThat(FirstRound.run(List.of(CASTS), List.of(), context -> {
                    TypeElement casts = context.elements().getTypeElement("example.Casts");
                    for (ExecutableElement method : ElementFilter.methodsIn(casts.getEnclosedElements())) {
                        TypeMirror intersection = ((TypeVariable)
                                        method.getTypeParameters().getFirst().asType())
                                .getUpperBound();
                        assertThat(MoreTypes.isCastFromObjectUnchecked(intersection))
                                .as(method.toString())
                                .isEqualTo(method.getSimpleName().contentEquals("parameterizedBound"));
                    }
                }))
                .succeeded();
    }
}
