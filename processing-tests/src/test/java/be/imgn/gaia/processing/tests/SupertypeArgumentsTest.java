package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.source.ClassName;
import be.imgn.gaia.processing.source.JavaFile;
import be.imgn.gaia.processing.source.ParameterizedTypeName;
import be.imgn.gaia.processing.source.Type;
import be.imgn.gaia.processing.source.TypeName;
import be.imgn.gaia.processing.source.TypeVariableName;
import be.imgn.gaia.processing.source.WildcardTypeName;
import be.imgn.gaia.processing.testing.Compilation;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

/**
 * The supertypes of a type with the type arguments its hierarchy gives them: {@code asSupertype} and
 * {@code superclass}.
 */
class SupertypeArgumentsTest {

    private static final JavaFileObject MARK = source("example.Mark", """
            package example;

            public @interface Mark {}
            """);

    private static final JavaFileObject CONVERTERS = source("example.Converter", """
            package example;

            import java.util.List;

            public interface Converter<T> {}

            abstract class AbstractConverter<X> implements Converter<X> {}

            class StringConverter extends AbstractConverter<String> {}

            class ListConverter<Y> extends AbstractConverter<List<Y>> {}

            final class IntegerListConverter extends ListConverter<Integer> {}
            """);

    private static Compilation process(BiConsumer<ProcessingContext, Element> step, JavaFileObject... sources) {
        List<JavaFileObject> all = new ArrayList<>(List.of(MARK));
        all.addAll(List.of(sources));
        return javac().withProcessors(new Running(step)).compile(all);
    }

    // The supertype of each element annotated with @Mark, by simple name, as javac writes it, or "none".
    private static Found supertypes(String qualifiedName, JavaFileObject... sources) {
        Map<String, String> results = new LinkedHashMap<>();
        Compilation compilation = process(
                (context, element) -> results.put(
                        element.getSimpleName().toString(),
                        describe(context.asSupertype(element.asType(), qualifiedName))),
                sources);
        assertThat(compilation).succeeded();
        return new Found(results);
    }

    private static String describe(Optional<DeclaredType> supertype) {
        return supertype.map(TypeMirror::toString).orElse("none");
    }

    private static TypeMirror typeOf(ProcessingContext context, String qualifiedName) {
        return context.elements().getTypeElement(qualifiedName).asType();
    }

    @Test
    void findsTheTypeArgumentsOfASupertypeThroughAGenericChain() {
        Found converters = supertypes("example.Converter", CONVERTERS, source("example.Uses", """
                package example;

                class Uses {
                    @Mark StringConverter string;
                    @Mark IntegerListConverter integers;
                    @Mark ListConverter<Boolean> booleans;
                    @Mark AbstractConverter<Double> doubles;
                }
                """));

        assertThat(converters.byElement())
                .containsExactly(
                        Map.entry("string", "example.Converter<java.lang.String>"),
                        Map.entry("integers", "example.Converter<java.util.List<java.lang.Integer>>"),
                        Map.entry("booleans", "example.Converter<java.util.List<java.lang.Boolean>>"),
                        Map.entry("doubles", "example.Converter<java.lang.Double>"));
    }

    @Test
    void findsTheConvertedTypeAsTheFirstTypeArgument() {
        Map<String, TypeMirror> converted = new LinkedHashMap<>();
        Compilation compilation = process(
                (context, element) -> converted.put(
                        element.getSimpleName().toString(),
                        context.asSupertype(element.asType(), "example.Converter")
                                .orElseThrow()
                                .getTypeArguments()
                                .getFirst()),
                CONVERTERS,
                source("example.Uses", """
                        package example;

                        class Uses {
                            @Mark StringConverter string;
                            @Mark IntegerListConverter integers;
                        }
                        """));

        assertThat(compilation).succeeded();
        assertThat(converted.get("string")).hasToString("java.lang.String");
        assertThat(converted.get("integers")).hasToString("java.util.List<java.lang.Integer>");
    }

    @Test
    void leavesTheTypeVariablesOfAGenericDeclarationInTheResult() {
        Found converters = supertypes("example.Converter", CONVERTERS, source("example.Generic", """
                package example;

                @Mark
                abstract class Generic<Z> extends AbstractConverter<Z> {}

                @Mark
                abstract class Doubled<W> extends ListConverter<W[]> {}
                """));

        assertThat(converters.get("Generic")).isEqualTo("example.Converter<Z>");
        assertThat(converters.get("Doubled")).isEqualTo("example.Converter<java.util.List<W[]>>");
    }

    @Test
    void givesATypeItselfAsItsOwnSupertype() {
        Found converters = supertypes("example.Converter", CONVERTERS, source("example.Uses", """
                package example;

                class Uses {
                    @Mark Converter<String> parameterized;
                    @Mark Converter<?> wildcard;
                    @Mark Converter raw;
                }

                @Mark
                interface Unrelated {}
                """));

        assertThat(converters.byElement())
                .containsExactly(
                        Map.entry("parameterized", "example.Converter<java.lang.String>"),
                        Map.entry("wildcard", "example.Converter<?>"),
                        Map.entry("raw", "example.Converter"),
                        Map.entry("Unrelated", "none"));
    }

    @Test
    void findsAnInterfaceReachedThroughSeveralPaths() {
        Found sources = supertypes("example.Source", source("example.Source", """
                package example;

                interface Source<T> {}

                interface Left<L> extends Source<L> {}

                interface Right<R> extends Source<R> {}

                @Mark
                interface Both<B> extends Left<B>, Right<B> {}

                abstract class Base implements Source<Integer> {}

                @Mark
                abstract class Child extends Base implements Left<Integer>, Right<Integer> {}

                @Mark
                abstract class Diamonds implements Both<String>, Left<String> {}
                """));

        assertThat(sources.byElement())
                .containsExactly(
                        Map.entry("Both", "example.Source<B>"),
                        Map.entry("Child", "example.Source<java.lang.Integer>"),
                        Map.entry("Diamonds", "example.Source<java.lang.String>"));
    }

    @Test
    void followsTypeArgumentsThatChangePlaces() {
        JavaFileObject swaps = source("example.Swap", """
                package example;

                import java.util.List;
                import java.util.Map;

                interface Pair<K, V> {}

                abstract class Swap<A, B> implements Pair<B, A>, Map<B, A> {}

                abstract class Twice<C> extends Swap<C, List<C>> {}

                @Mark
                abstract class Concrete extends Twice<String> {}

                class Uses {
                    @Mark Swap<String, Integer> swapped;
                    @Mark Swap<Swap<Long, Short>, Byte> nested;
                }
                """);

        Found pairs = supertypes("example.Pair", swaps);
        Found maps = supertypes("java.util.Map", swaps);

        assertThat(pairs.byElement())
                .containsExactly(
                        Map.entry("Concrete", "example.Pair<java.util.List<java.lang.String>,java.lang.String>"),
                        Map.entry("swapped", "example.Pair<java.lang.Integer,java.lang.String>"),
                        Map.entry(
                                "nested", "example.Pair<java.lang.Byte,example.Swap<java.lang.Long,java.lang.Short>>"));
        assertThat(maps.get("swapped")).isEqualTo("java.util.Map<java.lang.Integer,java.lang.String>");
    }

    @Test
    void substitutesTypeArgumentsInsideNestedGenericTypes() {
        Found iterables = supertypes("java.lang.Iterable", source("example.Table", """
                package example;

                import java.util.List;
                import java.util.Map;

                interface Table<K, V> extends Iterable<List<Map<K, V>>> {}

                interface Grid<G> extends Table<G, Table<G, G[]>> {}

                class Uses {
                    @Mark Table<String, Integer> table;
                    @Mark Grid<Long> grid;
                }
                """));

        assertThat(iterables.byElement())
                .containsExactly(
                        Map.entry(
                                "table",
                                "java.lang.Iterable<java.util.List<java.util.Map<java.lang.String,"
                                        + "java.lang.Integer>>>"),
                        Map.entry(
                                "grid",
                                "java.lang.Iterable<java.util.List<java.util.Map<java.lang.Long,"
                                        + "example.Table<java.lang.Long,java.lang.Long[]>>>>"));
    }

    @Test
    void startsFromAParameterizedTypeThroughTheLibraryHierarchy() {
        JavaFileObject lists = source("example.MyList", """
                package example;

                import java.util.ArrayList;

                class MyList<E> extends ArrayList<E> {}

                class Uses {
                    @Mark MyList<Integer> numbers;
                }
                """);

        assertThat(supertypes("java.lang.Iterable", lists).get("numbers"))
                .isEqualTo("java.lang.Iterable<java.lang.Integer>");
        assertThat(supertypes("java.util.Collection", lists).get("numbers"))
                .isEqualTo("java.util.Collection<java.lang.Integer>");
        assertThat(supertypes("java.util.AbstractList", lists).get("numbers"))
                .isEqualTo("java.util.AbstractList<java.lang.Integer>");
        assertThat(supertypes("java.io.Serializable", lists).get("numbers")).isEqualTo("java.io.Serializable");
        assertThat(supertypes("java.lang.Object", lists).get("numbers")).isEqualTo("java.lang.Object");
    }

    @Test
    void leavesWildcardsAndTypeVariablesAsTheyAre() {
        Found iterables = supertypes("java.lang.Iterable", source("example.Holder", """
                package example;

                import java.util.ArrayList;
                import java.util.List;

                class MyList<E> extends ArrayList<E> {}

                class Holder<T> {
                    @Mark MyList<? extends Number> upper;
                    @Mark MyList<? super Integer> lower;
                    @Mark MyList<?> unbounded;
                    @Mark MyList<T> variable;
                    @Mark MyList<List<? extends T>> nested;
                }
                """));

        assertThat(iterables.byElement())
                .containsExactly(
                        Map.entry("upper", "java.lang.Iterable<? extends java.lang.Number>"),
                        Map.entry("lower", "java.lang.Iterable<? super java.lang.Integer>"),
                        Map.entry("unbounded", "java.lang.Iterable<?>"),
                        Map.entry("variable", "java.lang.Iterable<T>"),
                        Map.entry("nested", "java.lang.Iterable<java.util.List<? extends T>>"));
    }

    @Test
    void givesARawSupertypeForARawTypeOrARawHierarchy() {
        Map<String, Optional<DeclaredType>> found = new LinkedHashMap<>();
        Compilation compilation = process(
                (context, element) -> found.put(
                        element.getSimpleName().toString(), context.asSupertype(element.asType(), Comparable.class)),
                source("example.Legacy", """
                        package example;

                        @SuppressWarnings("rawtypes")
                        abstract class Legacy implements Comparable {}

                        @Mark
                        abstract class LegacyChild extends Legacy {}

                        abstract class Ordered<T> implements Comparable<T> {}

                        @SuppressWarnings("rawtypes")
                        @Mark
                        abstract class RawParent extends Ordered {}

                        @SuppressWarnings("rawtypes")
                        class Uses {
                            @Mark Ordered rawField;
                            @Mark Ordered<String> parameterizedField;
                        }
                        """));

        assertThat(compilation).succeeded();
        assertThat(found.get("LegacyChild")).hasValueSatisfying(raw -> {
            assertThat(raw).hasToString("java.lang.Comparable");
            assertThat(raw.getTypeArguments()).isEmpty();
        });
        assertThat(found.get("RawParent")).hasValueSatisfying(raw -> {
            assertThat(raw).hasToString("java.lang.Comparable");
            assertThat(raw.getTypeArguments()).isEmpty();
        });
        assertThat(found.get("rawField")).hasValueSatisfying(raw -> {
            assertThat(raw).hasToString("java.lang.Comparable");
            assertThat(raw.getTypeArguments()).isEmpty();
        });
        assertThat(found.get("parameterizedField"))
                .hasValueSatisfying(parameterized -> assertThat(parameterized.getTypeArguments())
                        .singleElement()
                        .hasToString("java.lang.String"));
    }

    @Test
    void givesTheErasureOfAGenericTypeRawSupertypes() {
        Map<String, String> found = new LinkedHashMap<>();
        Compilation compilation = process(
                (context, element) -> found.put(
                        element.getSimpleName().toString(),
                        describe(context.asSupertype(context.types().erasure(element.asType()), "example.Converter"))),
                CONVERTERS,
                source("example.Uses", """
                        package example;

                        class Uses {
                            @Mark ListConverter<String> erased;
                        }
                        """));

        assertThat(compilation).succeeded();
        assertThat(found).containsEntry("erased", "example.Converter");
    }

    @Test
    void findsTheSupertypesOfTypeVariablesThroughTheirBounds() {
        Map<String, String> found = new LinkedHashMap<>();
        Compilation compilation = process(
                (context, element) -> {
                    for (String supertype : List.of("Converter", "Comparable", "Number", "Iterable")) {
                        String name = "Converter".equals(supertype) ? "example.Converter" : "java.lang." + supertype;
                        found.put(
                                element.getSimpleName() + " as " + supertype,
                                describe(context.asSupertype(element.asType(), name)));
                    }
                },
                CONVERTERS,
                source("example.Bounded", """
                        package example;

                        import java.util.List;

                        class Bounded<C extends StringConverter, N extends Number & Comparable<N>, M extends N> {
                            <T extends Converter<String>> void convert(
                                    @Mark T converter, @Mark N number, @Mark M more) {}

                            <U extends List<String>, V extends U> void list(@Mark V list) {}

                            <O> void open(@Mark O object) {}

                            @Mark C bounded;
                        }
                        """));

        assertThat(compilation).succeeded();
        assertThat(found)
                .containsEntry("converter as Converter", "example.Converter<java.lang.String>")
                .containsEntry("converter as Comparable", "none")
                .containsEntry("number as Comparable", "java.lang.Comparable<N>")
                .containsEntry("number as Number", "java.lang.Number")
                .containsEntry("number as Converter", "none")
                .containsEntry("more as Comparable", "java.lang.Comparable<N>")
                .containsEntry("more as Number", "java.lang.Number")
                .containsEntry("list as Iterable", "java.lang.Iterable<java.lang.String>")
                .containsEntry("object as Converter", "none")
                .containsEntry("bounded as Converter", "example.Converter<java.lang.String>");
    }

    @Test
    void findsTheSupertypesOfAnIntersectionTypeThroughItsBounds() {
        Map<String, String> found = new LinkedHashMap<>();
        Compilation compilation = process(
                (context, element) -> {
                    TypeMirror intersection = ((TypeVariable) element.asType()).getUpperBound();
                    assertThat(intersection.getKind()).isEqualTo(TypeKind.INTERSECTION);
                    found.put("Comparable", describe(context.asSupertype(intersection, Comparable.class)));
                    found.put("Converter", describe(context.asSupertype(intersection, "example.Converter")));
                    found.put("Number", describe(context.asSupertype(intersection, Number.class)));
                    found.put("List", describe(context.asSupertype(intersection, List.class)));
                },
                CONVERTERS,
                source("example.Both", """
                        package example;

                        class Both {
                            <T extends Number & Comparable<T> & Converter<T>> void both(@Mark T value) {}
                        }
                        """));

        assertThat(compilation).succeeded();
        assertThat(found)
                .containsExactly(
                        Map.entry("Comparable", "java.lang.Comparable<T>"),
                        Map.entry("Converter", "example.Converter<T>"),
                        Map.entry("Number", "java.lang.Number"),
                        Map.entry("List", "none"));
    }

    @Test
    void isEmptyForATypeThatIsNotASubtype() {
        Found found = supertypes("java.util.List", CONVERTERS, source("example.Uses", """
                package example;

                class Uses {
                    @Mark StringConverter string;
                    @Mark java.util.Collection<String> collection;
                    @Mark java.util.Set<String> set;
                }
                """));

        assertThat(found.byElement())
                .containsExactly(
                        Map.entry("string", "none"), Map.entry("collection", "none"), Map.entry("set", "none"));
    }

    @Test
    void isEmptyForPrimitivesArraysAndOtherKindsOfTypes() {
        List<Optional<DeclaredType>> found = new ArrayList<>();
        Compilation compilation = process(
                (context, element) -> {
                    ExecutableElement method = (ExecutableElement) element;
                    TypeMirror object = typeOf(context, "java.lang.Object");
                    found.add(context.asSupertype(context.types().getPrimitiveType(TypeKind.INT), Comparable.class));
                    found.add(context.asSupertype(context.types().getPrimitiveType(TypeKind.INT), Object.class));
                    found.add(context.asSupertype(
                            method.getParameters().getFirst().asType(), Object.class));
                    found.add(context.asSupertype(method.getParameters().get(1).asType(), Cloneable.class));
                    found.add(context.asSupertype(method.getParameters().get(1).asType(), Serializable.class));
                    found.add(context.asSupertype(context.types().getArrayType(object), Object.class));
                    found.add(context.asSupertype(context.types().getNoType(TypeKind.VOID), Object.class));
                    found.add(context.asSupertype(context.types().getNoType(TypeKind.NONE), Object.class));
                    found.add(context.asSupertype(context.types().getNullType(), Object.class));
                    found.add(context.asSupertype(method.asType(), Object.class));
                    found.add(context.asSupertype(context.types().getWildcardType(object, null), Object.class));
                },
                source("example.Kinds", """
                        package example;

                        class Kinds {
                            @Mark
                            void kinds(int primitive, String[] array) {}
                        }
                        """));

        assertThat(compilation).succeeded();
        assertThat(found)
                .hasSize(11)
                .allSatisfy(supertype -> assertThat(supertype).isEmpty());
    }

    @Test
    void findsTheSupertypeByElementNameOrClass() {
        List<String> found = new ArrayList<>();
        Compilation compilation = process(
                (context, element) -> {
                    TypeMirror type = element.asType();
                    found.add(describe(
                            context.asSupertype(type, context.elements().getTypeElement("java.util.Map"))));
                    found.add(describe(context.asSupertype(type, "java.util.Map")));
                    found.add(describe(context.asSupertype(type, Map.class)));
                    found.add(describe(context.asSupertype(type, Map.Entry.class)));
                    found.add(describe(context.asSupertype(type, "java.util.Map.Entry")));
                    found.add(describe(context.asSupertype(type, "example.Nowhere")));
                    found.add(describe(context.asSupertype(type, int.class)));
                    found.add(describe(context.asSupertype(type, Object[].class)));
                },
                source("example.Uses", """
                        package example;

                        import java.util.HashMap;
                        import java.util.List;
                        import java.util.Map;

                        abstract class Registry<V> extends HashMap<String, List<V>> implements Map.Entry<V, V> {}

                        class Uses {
                            @Mark Registry<Integer> registry;
                        }
                        """));

        assertThat(compilation).succeeded();
        assertThat(found)
                .containsExactly(
                        "java.util.Map<java.lang.String,java.util.List<java.lang.Integer>>",
                        "java.util.Map<java.lang.String,java.util.List<java.lang.Integer>>",
                        "java.util.Map<java.lang.String,java.util.List<java.lang.Integer>>",
                        "java.util.Map.Entry<java.lang.Integer,java.lang.Integer>",
                        "java.util.Map.Entry<java.lang.Integer,java.lang.Integer>",
                        "none",
                        "none",
                        "none");
    }

    @Test
    void keepsTheTypeArgumentsOfTheEnclosingTypeOfAnInnerClass() {
        List<DeclaredType> found = new ArrayList<>();
        Compilation compilation = process(
                (context, element) -> found.add(context.asSupertype(element.asType(), "example.Outer.Inner")
                        .orElseThrow()),
                source("example.Outer", """
                        package example;

                        class Outer<O> {
                            class Inner<I> {}

                            class Middle extends Inner<O[]> {}
                        }

                        @Mark
                        class Sub extends Outer<String>.Middle {
                            Sub(Outer<String> outer) {
                                outer.super();
                            }
                        }
                        """));

        assertThat(compilation).succeeded();
        assertThat(found).singleElement().satisfies(inner -> {
            assertThat(inner).hasToString("example.Outer<java.lang.String>.Inner<java.lang.String[]>");
            assertThat(inner.getEnclosingType()).hasToString("example.Outer<java.lang.String>");
            assertThat(TypeName.of(inner))
                    .isEqualTo(new ParameterizedTypeName(
                            ParameterizedTypeName.of(ClassName.of("example", "Outer"), ClassName.of(String.class)),
                            ClassName.of("example", "Outer", "Inner"),
                            List.of(TypeName.of(String[].class)),
                            List.of()));
        });
    }

    @Test
    void ignoresSupertypesJavacCouldNotResolve() {
        Map<String, String> found = new LinkedHashMap<>();
        Compilation compilation = process(
                (context, element) -> {
                    String name = element.getSimpleName().toString();
                    found.put(name, describe(context.asSupertype(element.asType(), "example.Converter")));
                    found.put(name + " as Object", describe(context.asSupertype(element.asType(), Object.class)));
                },
                CONVERTERS,
                source("example.Broken", """
                        package example;

                        @Mark
                        abstract class Broken extends Missing<String> implements Unknown, Converter<Integer> {}

                        class Uses {
                            @Mark Missing<String> missing;
                            @Mark ListConverter<Absent> absent;
                        }
                        """));

        assertThat(compilation).failed();
        assertThat(compilation.errors()).isNotEmpty();
        assertThat(found)
                .containsEntry("Broken", "example.Converter<java.lang.Integer>")
                .containsEntry("missing", "none")
                .containsEntry("missing as Object", "none")
                .containsEntry("absent as Object", "java.lang.Object")
                .hasEntrySatisfying(
                        "absent",
                        absent -> assertThat(absent)
                                .startsWith("example.Converter<java.util.List<")
                                .contains("Absent"));
    }

    @Test
    void findsTheSuperclassWithTheTypeArgumentsTheClassGivesIt() {
        Map<String, String> found = superclasses(source("example.Uses", """
                package example;

                import java.util.ArrayList;

                class Uses<Z> {
                    @Mark StringConverter string;
                    @Mark IntegerListConverter integers;
                    @Mark ListConverter<Z> variable;
                    @Mark ArrayList<String> strings;
                    @SuppressWarnings("rawtypes")
                    @Mark ArrayList raw;
                    @Mark Thread.State constant;
                    @Mark Point point;
                }

                record Point(int x, int y) {}
                """));

        assertThat(found)
                .containsExactly(
                        Map.entry("string", "example.AbstractConverter<java.lang.String>"),
                        Map.entry("integers", "example.ListConverter<java.lang.Integer>"),
                        Map.entry("variable", "example.AbstractConverter<java.util.List<Z>>"),
                        Map.entry("strings", "java.util.AbstractList<java.lang.String>"),
                        Map.entry("raw", "java.util.AbstractList"),
                        Map.entry("constant", "java.lang.Enum<java.lang.Thread.State>"),
                        Map.entry("point", "java.lang.Record"));
    }

    @Test
    void leavesOutObjectAsASuperclass() {
        Map<String, String> found = superclasses(source("example.Uses", """
                package example;

                class Uses {
                    @Mark AbstractConverter<String> direct;
                    @Mark Converter<String> notAClass;
                    @Mark Object object;
                    @Mark Mark annotation;
                }
                """));

        assertThat(found)
                .containsExactly(
                        Map.entry("direct", "none"),
                        Map.entry("notAClass", "none"),
                        Map.entry("object", "none"),
                        Map.entry("annotation", "none"));
    }

    @Test
    void walksUpTheHierarchyToObject() {
        Map<String, List<String>> chains = new LinkedHashMap<>();
        Compilation compilation = process(
                (context, element) -> {
                    List<String> chain = new ArrayList<>();
                    for (Optional<DeclaredType> current = context.superclass((DeclaredType) element.asType());
                            current.isPresent();
                            current = context.superclass(current.get())) {
                        chain.add(current.get().toString());
                    }
                    chains.put(element.getSimpleName().toString(), chain);
                },
                CONVERTERS,
                source("example.Uses", """
                        package example;

                        class Uses {
                            @Mark IntegerListConverter integers;
                        }
                        """));

        assertThat(compilation).succeeded();
        assertThat(chains)
                .containsExactly(Map.entry(
                        "integers",
                        List.of(
                                "example.ListConverter<java.lang.Integer>",
                                "example.AbstractConverter<java.util.List<java.lang.Integer>>")));
    }

    @Test
    void hasNoSuperclassJavacCouldNotResolve() {
        Map<String, String> found = new LinkedHashMap<>();
        Compilation compilation = process(
                (context, element) ->
                        found.put(element.getSimpleName().toString(), describe(context.superclass((DeclaredType)
                                element.asType()))),
                source("example.Broken", """
                        package example;

                        @Mark
                        class Broken extends Missing {}
                        """));

        assertThat(compilation).failed();
        assertThat(found).containsExactly(Map.entry("Broken", "none"));
    }

    // The superclass of the type of each element annotated with @Mark, by simple name, as javac writes it, or "none".
    private static Map<String, String> superclasses(JavaFileObject uses) {
        Map<String, String> results = new LinkedHashMap<>();
        Compilation compilation = process(
                (context, element) ->
                        results.put(element.getSimpleName().toString(), describe(context.superclass((DeclaredType)
                                element.asType()))),
                CONVERTERS,
                uses);
        assertThat(compilation).succeeded();
        return results;
    }

    @Test
    void namesTheFoundSupertypeAndItsTypeArgumentsInGeneratedCode() {
        List<TypeName> names = new ArrayList<>();
        Compilation compilation = process(
                (context, element) -> {
                    DeclaredType converter = context.asSupertype(element.asType(), "example.Converter")
                            .orElseThrow();
                    names.add(TypeName.of(converter));
                    converter.getTypeArguments().forEach(argument -> names.add(TypeName.of(argument)));
                    if (!element.getSimpleName().contentEquals("Plain")) {
                        return;
                    }
                    context.sources()
                            .write(
                                    JavaFile.newBuilder(
                                                    "example",
                                                    Type.newClassBuilder("PlainCopy")
                                                            .addModifiers(Modifier.ABSTRACT)
                                                            .addSuperinterface(converter)
                                                            .addField(
                                                                    TypeName.of(converter
                                                                            .getTypeArguments()
                                                                            .getFirst()),
                                                                    "converted")
                                                            .build())
                                            .build(),
                                    element);
                },
                CONVERTERS,
                source("example.Wild", """
                        package example;

                        import java.util.ArrayList;

                        @Mark
                        abstract class Wild<T extends Number> extends AbstractConverter<ArrayList<? super T>> {}

                        @Mark
                        abstract class Plain extends ListConverter<String> {}
                        """));

        assertThat(compilation).succeeded();
        ClassName converter = ClassName.of("example", "Converter");
        TypeName wildList = ParameterizedTypeName.of(
                ClassName.of(ArrayList.class), WildcardTypeName.supertypeOf(TypeVariableName.of("T")));
        TypeName stringList = ParameterizedTypeName.of(ClassName.of(List.class), ClassName.of(String.class));
        assertThat(names)
                .containsExactly(
                        ParameterizedTypeName.of(converter, wildList),
                        wildList,
                        ParameterizedTypeName.of(converter, stringList),
                        stringList);
        assertThat(compilation)
                .generatedSourceFile("example.PlainCopy")
                .contains("abstract class PlainCopy implements Converter<List<String>>")
                .contains("List<String> converted;");
    }

    private record Found(Map<String, String> byElement) {

        String get(String element) {
            return requireNonNull(byElement.get(element), element);
        }
    }

    /** Runs a step on each element annotated with {@code @Mark}, in the first round that has them. */
    @SupportedAnnotationTypes("example.Mark")
    private static final class Running extends AbstractProcessor {

        private final BiConsumer<ProcessingContext, Element> step;

        Running(BiConsumer<ProcessingContext, Element> step) {
            this.step = step;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            ProcessingContext context = ProcessingContext.of(processingEnv, Running.class);
            for (TypeElement annotation : annotations) {
                round.getElementsAnnotatedWith(annotation).forEach(element -> step.accept(context, element));
            }
            return false;
        }
    }
}
