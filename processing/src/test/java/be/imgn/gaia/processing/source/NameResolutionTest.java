package be.imgn.gaia.processing.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.lang.model.element.Modifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** How a file names the types it refers to: imports, static imports, nested scopes and inherited member types. */
class NameResolutionTest {

    private static final ClassName TIME_UNIT = ClassName.of(TimeUnit.class);

    private static Method method(String name, String format, Object... args) {
        return Method.newBuilder(name).addStatement(format, args).build();
    }

    private static JavaFile.Builder file(Type type) {
        return JavaFile.newBuilder("com.example", type);
    }

    @Nested
    class StaticImports {

        @Test
        void writeAMemberReachedThroughItsTypeAlone() {
            Type type = Type.newClassBuilder("Clock")
                    .addMethod(method("convert", "long seconds = $1T.SECONDS.convert(5, $1T.MINUTES)", TIME_UNIT))
                    .build();

            assertThat(file(type).addStaticImport(TIME_UNIT, "SECONDS").build()).hasToString("""
                            package com.example;

                            import static java.util.concurrent.TimeUnit.SECONDS;

                            import java.util.concurrent.TimeUnit;

                            class Clock {
                                void convert() {
                                    long seconds = SECONDS.convert(5, TimeUnit.MINUTES);
                                }
                            }
                            """);
        }

        @Test
        void dropTheImportOfATypeNoLongerNamed() {
            Type type = Type.newClassBuilder("Clock")
                    .addMethod(method("convert", "long seconds = $1T.SECONDS.convert(5, $1T.MINUTES)", TIME_UNIT))
                    .build();

            assertThat(file(type)
                            .addStaticImport(TIME_UNIT, "SECONDS", "MINUTES")
                            .build()
                            .toString())
                    .doesNotContain("import java.util.concurrent.TimeUnit;")
                    .contains("long seconds = SECONDS.convert(5, MINUTES);");
        }

        @Test
        void coverEveryMemberWithAWildcard() {
            Type type = Type.newClassBuilder("Clock")
                    .addMethod(Method.newBuilder("tick")
                            .addStatement("$T.gc()", System.class)
                            .addStatement("$T.out.println($T.HOURS)", System.class, TIME_UNIT)
                            .build())
                    .build();

            assertThat(file(type)
                            .addStaticImport(System.class, "*")
                            .addStaticImport(TimeUnit.class, "*")
                            .build())
                    .hasToString("""
                            package com.example;

                            import static java.lang.System.*;
                            import static java.util.concurrent.TimeUnit.*;

                            class Clock {
                                void tick() {
                                    gc();
                                    out.println(HOURS);
                                }
                            }
                            """);
        }

        @Test
        void importEnumConstantsAndMembersOfNestedTypes() {
            ClassName state = ClassName.of(Thread.State.class);
            Type type = Type.newClassBuilder("States")
                    .addMethod(Method.newBuilder("check")
                            .addParameter(ArrayTypeName.of(state), "states")
                            .varargs(true)
                            .addStatement("assert $1T.valueOf($2S) == $1T.BLOCKED", state, "BLOCKED")
                            .build())
                    .build();

            assertThat(file(type)
                            .addStaticImport(Thread.State.BLOCKED)
                            .addStaticImport(state, "valueOf")
                            .build()
                            .toString())
                    .contains("import static java.lang.Thread.State.BLOCKED;\n")
                    .contains("import static java.lang.Thread.State.valueOf;\n")
                    .contains("void check(Thread.State... states) {")
                    .contains("assert valueOf(\"BLOCKED\") == BLOCKED;");
        }

        @Test
        void leaveTypesThatAreNotFollowedByAStaticMemberAlone() {
            Type type = Type.newClassBuilder("Clock")
                    .addMethod(Method.newBuilder("units")
                            .addStatement("$T unit = $T.valueOf($S)", TIME_UNIT, TIME_UNIT, "DAYS")
                            .addStatement("$T<?> type = $T.class", Class.class, TIME_UNIT)
                            .addStatement("$T.$N()", TIME_UNIT, "values")
                            .addStatement("$T.<$T>of()", ClassName.of(List.class), TIME_UNIT)
                            .build())
                    .build();

            String written = file(type).addStaticImport(TIME_UNIT, "*").build().toString();

            assertThat(written)
                    .contains("TimeUnit unit = valueOf(\"DAYS\");")
                    .contains("Class<?> type = TimeUnit.class;")
                    .contains("TimeUnit.values();")
                    .contains("List.<TimeUnit>of();");
        }

        @Test
        void acceptPlaceholdersRightNextToEachOther() {
            Method method = Method.newBuilder("method").build();
            ClassName runtime = ClassName.of(Runtime.class);
            Type type = Type.newClassBuilder("Odd")
                    .addMethod(Method.newBuilder("odd")
                            .addCode("$T", runtime)
                            .addCode("$T.a()", runtime)
                            .addCode("$T$T", runtime, runtime)
                            .addCode("$T.$T", runtime, runtime)
                            .addCode("$1T$1T", runtime)
                            .addCode("$1T$2L$2S$1T$3N$1T", runtime, "?", method)
                            .addCode("$T$S", runtime, "text")
                            .addCode("$T$N", runtime, method)
                            .build())
                    .build();

            assertThatCode(() ->
                            file(type).addStaticImport(runtime, "*").build().toString())
                    .doesNotThrowAnyException();
        }

        @Test
        void qualifyTypesNamedLikeAStaticallyImportedMember() {
            Type type = Type.newClassBuilder("Entries")
                    .addField(ClassName.of("com.other", "Entry"), "entry")
                    .build();

            assertThat(file(type)
                            .addStaticImport(ClassName.of(Map.class), "Entry")
                            .build()
                            .toString())
                    .contains("com.other.Entry entry;")
                    .doesNotContain("import com.other.Entry;");
        }
    }

    @Nested
    class Scopes {

        @Test
        void nameTheTypesOfTheFileByTheShortestNameInScope() {
            ClassName taco = ClassName.of("com.example", "Taco");
            ClassName builder = taco.nested("Builder");
            Type type = Type.newClassBuilder("Taco")
                    .addSuperinterface(ClassName.of(Comparable.class).parameterized(taco))
                    .addMethod(Method.newBuilder("compareTo")
                            .addModifiers(Modifier.PUBLIC)
                            .returns(int.class)
                            .addParameter(taco, "other")
                            .addStatement("return 0")
                            .build())
                    .addMethod(Method.newBuilder("builder")
                            .addModifiers(Modifier.STATIC)
                            .returns(builder)
                            .addStatement("return new $T()", builder)
                            .build())
                    .addType(Type.newClassBuilder("Builder")
                            .addModifiers(Modifier.STATIC)
                            .addMethod(Method.newBuilder("build")
                                    .returns(taco)
                                    .addStatement("return new $T()", taco)
                                    .build())
                            .build())
                    .build();

            assertThat(file(type).build()).hasToString("""
                    package com.example;

                    class Taco implements Comparable<Taco> {
                        public int compareTo(Taco other) {
                            return 0;
                        }

                        static Builder builder() {
                            return new Builder();
                        }

                        static class Builder {
                            Taco build() {
                                return new Taco();
                            }
                        }
                    }
                    """);
        }

        // Outer { First { Twin, Inner { field } }, Twin { Leaf } }: in Inner, Twin is First.Twin.
        @Test
        void qualifyANestedTypeThatANearerOneHides() {
            ClassName outer = ClassName.of("com.example", "Outer");
            Type type = Type.newClassBuilder("Outer")
                    .addType(Type.newClassBuilder("First")
                            .addType(Type.newClassBuilder("Twin").build())
                            .addType(Type.newClassBuilder("Inner")
                                    .addField(outer.nested("Twin").nested("Leaf"), "leaf")
                                    .addField(outer.nested("First").nested("Twin"), "twin")
                                    .build())
                            .build())
                    .addType(Type.newClassBuilder("Twin")
                            .addType(Type.newClassBuilder("Leaf").build())
                            .build())
                    .build();

            assertThat(file(type).build().toString())
                    .contains("            Outer.Twin.Leaf leaf;\n")
                    .contains("            Twin twin;\n");
        }

        @Test
        void useTheShortNameWhenTheHidingTypeIsOutOfScope() {
            ClassName outer = ClassName.of("com.example", "Outer");
            Type type = Type.newClassBuilder("Outer")
                    .addType(Type.newClassBuilder("First")
                            .addType(Type.newClassBuilder("Inner")
                                    .addField(outer.nested("Twin").nested("Leaf"), "leaf")
                                    .addType(Type.newClassBuilder("Deeper")
                                            .addType(
                                                    Type.newClassBuilder("Twin").build())
                                            .build())
                                    .build())
                            .build())
                    .addType(Type.newClassBuilder("Twin")
                            .addType(Type.newClassBuilder("Leaf").build())
                            .build())
                    .build();

            assertThat(file(type).build().toString()).contains("            Twin.Leaf leaf;\n");
        }

        @Test
        void qualifyTypesHiddenByTypeVariables() {
            ClassName taco = ClassName.of("com.example", "Taco");
            Type type = Type.newClassBuilder("Taco")
                    .addMethod(Method.newBuilder("node")
                            .addTypeVariable(TypeVariableName.of("Node"))
                            .returns(taco.nested("Node"))
                            .addParameter(TypeVariableName.of("Node"), "ignored")
                            .addStatement("return null")
                            .build())
                    .addMethod(Method.newBuilder("self")
                            .addTypeVariable(TypeVariableName.of("Taco"))
                            .returns(taco)
                            .addStatement("return null")
                            .build())
                    .addType(Type.newClassBuilder("Node").build())
                    .build();

            assertThat(file(type).build().toString())
                    .contains("<Node> Taco.Node node(Node ignored) {")
                    .contains("<Taco> com.example.Taco self() {");
        }

        @Test
        void seeTheMembersOfARecordInItsHeaderOnly() {
            ClassName pair = ClassName.of("com.example", "Pair");
            Type type = Type.newRecordBuilder("Pair")
                    .addRecordComponent(pair.nested("Side"), "side")
                    .addSuperinterface(ClassName.of(Comparable.class).parameterized(pair.nested("Side")))
                    .addMethod(Method.newBuilder("compareTo")
                            .addModifiers(Modifier.PUBLIC)
                            .returns(int.class)
                            .addParameter(pair.nested("Side"), "other")
                            .addStatement("return 0")
                            .build())
                    .addType(Type.newEnumBuilder("Side").addEnumConstant("LEFT").build())
                    .build();

            assertThat(file(type).build().toString())
                    .contains("record Pair(Side side) implements Comparable<Side> {")
                    .contains("public int compareTo(Side other) {");
        }

        @Test
        void writeAnInnerClassOfAParameterizedTypeOfTheFile() {
            ClassName outer = ClassName.of("com.example", "Outer");
            Type type = Type.newClassBuilder("Outer")
                    .addTypeVariable(TypeVariableName.of("T"))
                    .addField(outer.parameterized(ClassName.of(String.class)).nested("Inner"), "inner")
                    .addType(Type.newClassBuilder("Inner").build())
                    .build();

            assertThat(file(type).build().toString()).contains("Outer<String>.Inner inner;");
        }
    }

    @Nested
    class InheritedMemberTypes {

        @Test
        void qualifyTypesThatAnInheritedMemberTypeHides() {
            Type type = Type.newClassBuilder("Entries")
                    .addModifiers(Modifier.ABSTRACT)
                    .addSuperinterface(Map.class)
                    .addMethod(Method.newBuilder("entry")
                            .addModifiers(Modifier.ABSTRACT)
                            .returns(ClassName.of("com.other", "Entry"))
                            .build())
                    .build();

            assertThat(type.alwaysQualifiedNames()).contains("Entry");
            assertThat(file(type).build().toString())
                    .contains("abstract com.other.Entry entry();")
                    .doesNotContain("import com.other.Entry;");
        }

        @Test
        void knowWhichSupertypeBringsWhichMemberType() {
            ClassName optional = ClassName.of(Optional.class);
            ClassName pattern = ClassName.of(Pattern.class);
            Type.Builder child = Type.newClassBuilder("Child")
                    .addMethod(Method.newBuilder("optional")
                            .returns(optional.parameterized(ClassName.of(String.class)))
                            .addStatement("return $T.empty()", optional)
                            .build())
                    .addMethod(Method.newBuilder("pattern")
                            .returns(pattern)
                            .addStatement("return null")
                            .build());

            String implementing = file(child.build().toBuilder()
                            .addSuperinterface(ParentInterface.class)
                            .build())
                    .build()
                    .toString();
            String extending =
                    file(child.superclass(Parent.class).build()).build().toString();

            assertThat(implementing)
                    .contains("java.util.Optional<String> optional() {")
                    .contains("return java.util.Optional.empty();")
                    .contains("import java.util.regex.Pattern;")
                    .contains("Pattern pattern() {");
            assertThat(extending)
                    .contains("java.util.Optional<String> optional() {")
                    .contains("java.util.regex.Pattern pattern() {")
                    .doesNotContain("import java.util");
        }

        @Test
        void qualifyNamesOnRequest() {
            ClassName nestedA = ClassName.of("com.other", "NestedTypeA");
            ClassName nestedC = ClassName.of("com.other", "NestedTypeC");
            Type type = Type.newClassBuilder("Taco")
                    .avoidClashesWithNestedClasses(Parent.class)
                    .addField(nestedA, "a")
                    .addField(nestedC, "c")
                    .addField(ClassName.of("com.other", "Pattern"), "pattern")
                    .addField(Thread.class, "thread")
                    .build();

            String written = file(type).alwaysQualify("Thread").build().toString();

            assertThat(written)
                    .contains("com.other.Pattern pattern;")
                    .contains("java.lang.Thread thread;")
                    .contains("import com.other.NestedTypeA;")
                    .contains("import com.other.NestedTypeC;");
        }

        @Test
        void qualifyJavaLangTypesThatAnInheritedMemberTypeHides() {
            Type type = Type.newClassBuilder("Worker")
                    .superclass(Thread.class)
                    .addField(ClassName.of("com.other", "State"), "state")
                    .addField(ClassName.of(Thread.State.class), "own")
                    .build();

            assertThat(file(type).build().toString())
                    .contains("com.other.State state;")
                    .contains("Thread.State own;");
        }
    }

    /** A supertype declaring a member type named like a type of {@code java.util}. */
    interface ParentInterface {

        /** Hides {@link java.util.Optional}. */
        class Optional {}
    }

    /** A supertype declaring, and inheriting, member types named like types of {@code java.util}. */
    static class Parent implements ParentInterface {

        /** Hides {@link java.util.regex.Pattern}. */
        static class Pattern {}
    }
}
