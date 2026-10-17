package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import be.imgn.gaia.processing.ProcessingContext;
import java.util.List;
import java.util.function.Consumer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

/** The types of fields and parameters as members of a type: {@code ProcessingContext.asMemberOf}. */
class MemberTypesTest {

    private static final JavaFileObject MEMBERS = source("example.Box", """
            package example;

            import java.util.List;
            import java.util.function.Function;

            class Box<T> {
                T value;
                List<T> values;

                Box(T initial) {}

                void put(T item, int count) {}

                <U> U map(Function<? super T, ? extends U> mapper) {
                    return null;
                }
            }

            class StringBox extends Box<String> {
                StringBox() {
                    super("");
                }
            }

            record Pair<A, B>(A first, B second) {}

            enum Color {
                RED
            }

            class Unrelated {
                int other;

                void take(String unrelated) {}
            }
            """);

    private static void check(Consumer<ProcessingContext> body) {
        assertThat(FirstRound.run(List.of(MEMBERS), List.of(), body)).succeeded();
    }

    private static VariableElement field(ProcessingContext context, String type, String name) {
        return ElementFilter.fieldsIn(members(context, type)).stream()
                .filter(field -> field.getSimpleName().contentEquals(name))
                .findFirst()
                .orElseThrow();
    }

    private static VariableElement parameter(ProcessingContext context, String type, String name) {
        return members(context, type).stream()
                .filter(ExecutableElement.class::isInstance)
                .flatMap(executable -> ((ExecutableElement) executable).getParameters().stream())
                .filter(parameter -> parameter.getSimpleName().contentEquals(name))
                .findFirst()
                .orElseThrow();
    }

    private static List<? extends Element> members(ProcessingContext context, String type) {
        return context.elements().getTypeElement("example." + type).getEnclosedElements();
    }

    private static DeclaredType boxOf(ProcessingContext context, Class<?> argument) {
        return context.type("example.Box", context.type(argument));
    }

    @Test
    void givesFieldsTheTypeArgumentsOfTheContainer() {
        check(context -> {
            assertThat(context.asMemberOf(boxOf(context, String.class), field(context, "Box", "value")))
                    .hasToString("java.lang.String");
            assertThat(context.asMemberOf(boxOf(context, String.class), field(context, "Box", "values")))
                    .hasToString("java.util.List<java.lang.String>");
            assertThat(context.asMemberOf(
                            context.type("example.Pair", context.type(String.class), context.type(Integer.class)),
                            field(context, "Pair", "second")))
                    .hasToString("java.lang.Integer");
            assertThat(context.asMemberOf(context.type("example.Color"), field(context, "Color", "RED")))
                    .hasToString("example.Color");
        });
    }

    @Test
    void givesParametersTheTypesTheirMethodHasAsAMember() {
        check(context -> {
            DeclaredType strings = context.type("example.StringBox");

            assertThat(context.asMemberOf(strings, parameter(context, "Box", "item")))
                    .hasToString("java.lang.String");
            assertThat(context.asMemberOf(strings, parameter(context, "Box", "count")))
                    .hasToString("int");
            assertThat(context.asMemberOf(boxOf(context, Integer.class), parameter(context, "Box", "initial")))
                    .hasToString("java.lang.Integer");
            assertThat(context.asMemberOf(strings, parameter(context, "Box", "mapper")))
                    .hasToString("java.util.function.Function<? super java.lang.String,? extends U>");
        });
    }

    @Test
    void rejectsVariablesThatAreNotMembersOfTheContainer() {
        check(context -> {
            DeclaredType box = boxOf(context, String.class);
            VariableElement other = field(context, "Unrelated", "other");
            VariableElement unrelated = parameter(context, "Unrelated", "unrelated");

            assertThatIllegalArgumentException().isThrownBy(() -> context.asMemberOf(box, other));
            assertThatIllegalArgumentException().isThrownBy(() -> context.asMemberOf(box, unrelated));
        });
    }
}
