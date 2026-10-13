package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.gaia.processing.MoreTypes;
import be.imgn.gaia.processing.ProcessingContext;
import java.lang.annotation.Retention;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.tools.JavaFileObject;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Types built from classes and names: {@code context.type(...)}. */
class TypeConstructionTest {

    private static final JavaFileObject TYPES = source("example.Box", """
            package example;

            public class Box<T> {}

            class Pair<A, B extends Number> {}

            class Outer<O> {
                class Inner {}

                class GenericInner<I> {
                    class Deepest<D> {}
                }

                static class Nested<N> {}
            }

            class Plain {
                class GenericInner<I> {}
            }
            """);

    private static void inRound(Consumer<ProcessingContext> body) {
        assertThat(FirstRound.run(List.of(TYPES), List.of(), body)).succeeded();
    }

    private static TypeMirror wildcard(
            ProcessingContext context, @Nullable TypeMirror extendsBound, @Nullable TypeMirror superBound) {
        return context.types().getWildcardType(extendsBound, superBound);
    }

    @Test
    void buildsEveryPrimitiveType() {
        inRound(context -> {
            Map<Class<?>, TypeKind> primitives = Map.of(
                    boolean.class, TypeKind.BOOLEAN,
                    byte.class, TypeKind.BYTE,
                    short.class, TypeKind.SHORT,
                    char.class, TypeKind.CHAR,
                    int.class, TypeKind.INT,
                    long.class, TypeKind.LONG,
                    float.class, TypeKind.FLOAT,
                    double.class, TypeKind.DOUBLE);
            primitives.forEach((clazz, kind) -> {
                TypeMirror type = context.type(clazz);

                assertThat(type).isInstanceOf(PrimitiveType.class).hasToString(clazz.getName());
                assertThat(type.getKind()).isEqualTo(kind);
                assertThat(context.isSameType(type, context.types().getPrimitiveType(kind)))
                        .isTrue();
            });
        });
    }

    @Test
    void buildsTheVoidTypeFromVoidButADeclaredTypeFromItsBox() {
        inRound(context -> {
            TypeMirror voidType = context.type(void.class);
            TypeMirror box = context.type(Void.class);

            assertThat(voidType).isInstanceOf(NoType.class);
            assertThat(voidType.getKind()).isEqualTo(TypeKind.VOID);
            assertThat(box.getKind()).isEqualTo(TypeKind.DECLARED);
            assertThat(box).hasToString("java.lang.Void");
        });
    }

    @Test
    void buildsArraysOfPrimitivesClassesAndRawTypesAtAnyDepth() {
        inRound(context -> {
            TypeMirror ints = context.type(int[].class);
            TypeMirror strings = context.type(String[][].class);
            TypeMirror lists = context.type(List[].class);
            TypeMirror longs = context.type(long[][][].class);

            assertThat(ints).isInstanceOf(ArrayType.class).hasToString("int[]");
            assertThat(((ArrayType) ints).getComponentType().getKind()).isEqualTo(TypeKind.INT);
            assertThat(strings).hasToString("java.lang.String[][]");
            assertThat(((ArrayType) strings).getComponentType()).hasToString("java.lang.String[]");
            assertThat(lists).hasToString("java.util.List[]");
            assertThat(((DeclaredType) ((ArrayType) lists).getComponentType()).getTypeArguments())
                    .isEmpty();
            assertThat(longs).hasToString("long[][][]");
            assertThat(MoreTypes.isType(longs, long[][][].class)).isTrue();
        });
    }

    @Test
    void buildsDeclaredTypesOfEveryKindOfClass() {
        inRound(context -> {
            assertThat(context.type(String.class)).hasToString("java.lang.String");
            assertThat(context.type(Runnable.class)).hasToString("java.lang.Runnable");
            assertThat(context.type(TimeUnit.class)).hasToString("java.util.concurrent.TimeUnit");
            assertThat(context.type(Retention.class)).hasToString("java.lang.annotation.Retention");
            assertThat(context.type(Point.class)).hasToString(Point.class.getCanonicalName());
            assertThat(context.isSameType(
                            context.type(String.class),
                            context.elements()
                                    .getTypeElement("java.lang.String")
                                    .asType()))
                    .isTrue();
        });
    }

    @Test
    void buildsTheRawTypeOfAGenericClass() {
        inRound(context -> {
            TypeElement list = context.elements().getTypeElement("java.util.List");
            DeclaredType raw = (DeclaredType) context.type(List.class);

            assertThat(raw).hasToString("java.util.List");
            assertThat(raw.getTypeArguments()).isEmpty();
            assertThat(raw.asElement()).isEqualTo(list);
            assertThat(context.isSameType(raw, context.types().erasure(list.asType())))
                    .isTrue();
            assertThat(context.isSameType(raw, list.asType())).isFalse();
        });
    }

    @Test
    void buildsNestedTypesAndTheRawTypeOfAnInnerClassOfAGenericClass() {
        inRound(context -> {
            DeclaredType entry = (DeclaredType) context.type(Map.Entry.class);
            DeclaredType nested = (DeclaredType) context.type(Point.class);
            DeclaredType inner = (DeclaredType) context.type(Generic.Inner.class);

            assertThat(entry).hasToString("java.util.Map.Entry");
            assertThat(entry.getTypeArguments()).isEmpty();
            assertThat(nested.asElement().getSimpleName()).hasToString("Point");
            assertThat(inner).hasToString(Generic.Inner.class.getCanonicalName());
            assertThat(inner.getEnclosingType()).hasToString(Generic.class.getCanonicalName());
            assertThat(((DeclaredType) inner.getEnclosingType()).getTypeArguments())
                    .isEmpty();
        });
    }

    @Test
    void parameterizesAGenericClassWithClasses() {
        inRound(context -> {
            DeclaredType map = context.type(Map.class, String.class, Integer[].class);
            DeclaredType list = context.type(List.class, int[][].class);

            assertThat(map).hasToString("java.util.Map<java.lang.String,java.lang.Integer[]>");
            assertThat(map.getTypeArguments())
                    .extracting(TypeMirror::getKind)
                    .containsExactly(TypeKind.DECLARED, TypeKind.ARRAY);
            assertThat(list).hasToString("java.util.List<int[][]>");
            assertThat(context.isSameType(
                            map,
                            context.types()
                                    .getDeclaredType(
                                            context.elements().getTypeElement("java.util.Map"),
                                            context.type(String.class),
                                            context.type(Integer[].class))))
                    .isTrue();
        });
    }

    @Test
    void parameterizesAGenericClassWithTypesOfAnyDepth() {
        inRound(context -> {
            DeclaredType nested = context.type(
                    List.class, context.type(Map.class, context.type(String.class), context.type(List.class)));
            TypeMirror variable = context.elements()
                    .getTypeElement("java.util.List")
                    .getTypeParameters()
                    .getFirst()
                    .asType();
            DeclaredType ofVariable = context.type(Map.class, variable, context.type(List.class, variable));

            assertThat(nested).hasToString("java.util.List<java.util.Map<java.lang.String,java.util.List>>");
            assertThat(ofVariable).hasToString("java.util.Map<E,java.util.List<E>>");
            assertThat(ofVariable.getTypeArguments().getFirst()).isInstanceOf(TypeVariable.class);
        });
    }

    @Test
    void parameterizesAGenericClassWithWildcards() {
        inRound(context -> {
            TypeMirror number = context.type(Number.class);
            DeclaredType unbounded = context.type(List.class, wildcard(context, null, null));
            DeclaredType upper = context.type(List.class, wildcard(context, number, null));
            DeclaredType lower = context.type(Comparable.class, wildcard(context, null, context.type(Integer.class)));
            DeclaredType mixed = context.type(
                    Map.class,
                    wildcard(context, context.type(List.class, wildcard(context, number, null)), null),
                    context.type(String.class));

            assertThat(unbounded).hasToString("java.util.List<?>");
            assertThat(unbounded.getTypeArguments().getFirst()).isInstanceOf(WildcardType.class);
            assertThat(upper).hasToString("java.util.List<? extends java.lang.Number>");
            assertThat(lower).hasToString("java.lang.Comparable<? super java.lang.Integer>");
            assertThat(mixed)
                    .hasToString(
                            "java.util.Map<? extends java.util.List<? extends java.lang.Number>,java.lang.String>");
        });
    }

    @Test
    void doesNotCheckTypeArgumentsAgainstTheBoundsOfTheTypeParameters() {
        inRound(context ->
                assertThat(context.type(Enum.class, String.class)).hasToString("java.lang.Enum<java.lang.String>"));
    }

    @Test
    void givesTheRawTypeForNoTypeArguments() {
        inRound(context -> {
            assertThat(context.type(List.class, new Class<?>[0])).hasToString("java.util.List");
            assertThat(context.type(List.class, new TypeMirror[0])).hasToString("java.util.List");
            assertThat(context.type("java.util.List")).hasToString("java.util.List");
            assertThat(context.type(String.class, new Class<?>[0])).hasToString("java.lang.String");
            assertThat(context.isSameType(context.type(List.class, new Class<?>[0]), context.type(List.class)))
                    .isTrue();
        });
    }

    @Test
    void buildsTypesByNameIncludingTypesOfTheCompiledSources() {
        inRound(context -> {
            DeclaredType box = context.type("example.Box", context.type(String.class));
            DeclaredType pair = context.type("example.Pair", context.type(String.class), context.type(Integer.class));
            DeclaredType raw = context.type("example.Box");
            DeclaredType entry = context.type("java.util.Map.Entry", context.type(String.class), box);

            assertThat(box).hasToString("example.Box<java.lang.String>");
            assertThat(pair).hasToString("example.Pair<java.lang.String,java.lang.Integer>");
            assertThat(raw).hasToString("example.Box");
            assertThat(raw.getTypeArguments()).isEmpty();
            assertThat(entry).hasToString("java.util.Map.Entry<java.lang.String,example.Box<java.lang.String>>");
        });
    }

    @Test
    void parameterizesNestedClassesThatDoNotDependOnTheTypeArgumentsOfAnEnclosingClass() {
        inRound(context -> {
            TypeMirror string = context.type(String.class);

            assertThat(context.type("example.Outer.Nested", string))
                    .hasToString("example.Outer.Nested<java.lang.String>");
            assertThat(context.type("example.Plain.GenericInner", string))
                    .hasToString("example.Plain.GenericInner<java.lang.String>");
            assertThat(context.type(Map.Entry.class, String.class, Integer.class))
                    .hasToString("java.util.Map.Entry<java.lang.String,java.lang.Integer>");
        });
    }

    @Test
    void rejectsTypeArgumentsForAnInnerClassOfAGenericClass() {
        inRound(context -> {
            TypeMirror string = context.type(String.class);

            assertThatThrownBy(() -> context.type("example.Outer.GenericInner", string))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("example.Outer.GenericInner is an inner class of the generic example.Outer<O>: use"
                            + " types().getDeclaredType(DeclaredType, TypeElement, TypeMirror...) with a"
                            + " parameterization of example.Outer");
            assertThatThrownBy(() -> context.type("example.Outer.GenericInner.Deepest", string))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("example.Outer.GenericInner.Deepest is an inner class of the generic"
                            + " example.Outer<O>.GenericInner<I>");
            assertThatThrownBy(() -> context.type(Generic.GenericInner.class, String.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("is an inner class of the generic");
            assertThat(context.type("example.Outer.GenericInner")).hasToString("example.Outer.GenericInner");
        });
    }

    @Test
    void rejectsTheWrongNumberOfTypeArguments() {
        inRound(context -> {
            TypeMirror string = context.type(String.class);

            assertThatThrownBy(() -> context.type(Map.class, String.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Wrong number of type arguments for java.util.Map<K,V>: expected 2, got 1:"
                            + " [java.lang.String]");
            assertThatThrownBy(() -> context.type(List.class, string, string))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Wrong number of type arguments for java.util.List<E>: expected 1, got 2:"
                            + " [java.lang.String, java.lang.String]");
            assertThatThrownBy(() -> context.type(String.class, String.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Wrong number of type arguments for java.lang.String: expected 0, got 1:"
                            + " [java.lang.String]");
            assertThatThrownBy(() -> context.type("example.Pair", string))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Wrong number of type arguments for example.Pair<A,B>: expected 2, got 1:"
                            + " [java.lang.String]");
            assertThatThrownBy(() -> context.type(Generic.Inner.class, String.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expected 0, got 1");
        });
    }

    @Test
    void rejectsTypeArgumentsThatAreNotReferenceTypesOrWildcards() {
        inRound(context -> {
            assertThatThrownBy(() -> context.type(List.class, int.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("int cannot be a type argument of java.util.List<E>: only a class, interface, array,"
                            + " type variable or wildcard can");
            assertThatThrownBy(() -> context.type(List.class, void.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("void cannot be a type argument of java.util.List<E>");
            assertThatThrownBy(() -> context.type(Map.class, context.type(String.class), context.type(long.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("long cannot be a type argument of java.util.Map<K,V>");
            assertThatThrownBy(() -> context.type(List.class, context.types().getNullType()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be a type argument");
            assertThatThrownBy(() -> context.type(List.class, context.types().getNoType(TypeKind.NONE)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be a type argument");
            assertThatThrownBy(() -> context.type("example.Box", context.type(char.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("char cannot be a type argument of example.Box<T>");
        });
    }

    @Test
    void rejectsPrimitivesAndArraysAsTheClassToParameterize() {
        inRound(context -> {
            assertThatThrownBy(() -> context.type(int.class, String.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("int is not a class or an interface");
            assertThatThrownBy(() -> context.type(List[].class, context.type(String.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("java.util.List[] is not a class or an interface");
            assertThatThrownBy(() -> context.type(void.class, new TypeMirror[0]))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("void is not a class or an interface");
        });
    }

    @Test
    void rejectsClassesWithoutACanonicalName() {
        class Local {}
        Object anonymous = new Object() {};

        inRound(context -> {
            assertThatThrownBy(() -> context.type(Local.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(Local.class.getName()
                            + " has no canonical name: a local, anonymous or hidden class has no type to build");
            assertThatThrownBy(() -> context.type(Local[].class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith(Local.class.getName() + " has no canonical name");
            assertThatThrownBy(() -> context.type(anonymous.getClass()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith(anonymous.getClass().getName() + " has no canonical name");
            assertThatThrownBy(() -> context.type(List.class, Local.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith(Local.class.getName() + " has no canonical name");
        });
    }

    @Test
    void rejectsTypesTheCompilationCannotSee(@TempDir Path empty) {
        String hidden = Point.class.getCanonicalName();
        List<String> withoutTheTestClasses =
                List.of("--module-path", empty.toString(), "--class-path", empty.toString());

        Consumer<ProcessingContext> body = context -> {
            String message = "The compilation sees no type named " + hidden
                    + ": it is not on its class path or module path, or several modules have one";
            assertThatThrownBy(() -> context.type(Point.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(message);
            assertThatThrownBy(() -> context.type(Point[][].class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(message);
            assertThatThrownBy(() -> context.type(List.class, Point.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(message);
            assertThatThrownBy(() -> context.type(hidden))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(message);
            assertThatThrownBy(() -> context.type("does.not.Exist", context.type(String.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("The compilation sees no type named does.not.Exist:");
        };

        assertThat(FirstRound.run(List.of(TYPES), withoutTheTestClasses, body)).succeeded();
    }

    /** A record, nested in a test class, which the compilation sees on the module path or the class path. */
    record Point(int x, int y) {}

    /** A generic class with an inner class, and a generic inner class, that both use its type parameter. */
    static final class Generic<G> {

        final class Inner {
            List<G> of(G value) {
                return List.of(value);
            }
        }

        final class GenericInner<H> {
            Map<G, H> of(G key, H value) {
                return Map.of(key, value);
            }
        }
    }
}
