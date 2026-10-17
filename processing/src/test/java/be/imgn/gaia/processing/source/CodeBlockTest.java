package be.imgn.gaia.processing.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CodeBlockTest {

    @Nested
    class Placeholders {

        @Test
        void namesTypes() {
            assertThat(CodeBlock.of("$T, $T, $T", String.class, int[].class, ClassName.of("a.b", "C")))
                    .hasToString("java.lang.String, int[], a.b.C");
        }

        @Test
        void namesParametersFieldsMethodsAndTypes() {
            Parameter parameter = Parameter.of(String.class, "name");
            Field field = Field.of(PrimitiveTypeName.INT, "count");
            Method method = Method.newBuilder("run").build();
            Type type = Type.newClassBuilder("Runner").build();

            assertThat(CodeBlock.of("$N $N $N $N $N", parameter, field, method, type, "plain"))
                    .hasToString("name count run Runner plain");
        }

        @Test
        void writesStringLiterals() {
            assertThat(CodeBlock.of("$S", "plain")).hasToString("\"plain\"");
            assertThat(CodeBlock.of("$S", (Object) null)).hasToString("null");
            assertThat(CodeBlock.of("$S", 42)).hasToString("\"42\"");
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                `a"b`        | `"a\\"b"`
                `it's`       | `"it's"`
                `back\\slash` | `"back\\\\slash"`
                """)
        void escapesStringLiterals(String value, String literal) {
            assertThat(CodeBlock.of("$S", value)).hasToString(literal);
        }

        @Test
        void escapesControlCharacters() {
            assertThat(CodeBlock.of("$S", "tab\t\r\b\f\u0001 end\n")).hasToString("\"tab\\t\\r\\b\\f\\u0001 end\\n\"");
            assertThat(CodeBlock.of("$S", "unicodé ✓")).hasToString("\"unicodé ✓\"");
        }

        @Test
        void writesLiterals() {
            assertThat(CodeBlock.of("$L + $L", 1, true)).hasToString("1 + true");
            assertThat(CodeBlock.of("$L", (Object) null)).hasToString("null");
            assertThat(CodeBlock.of("($L)", CodeBlock.of("$T.MAX_VALUE", Integer.class)))
                    .hasToString("(java.lang.Integer.MAX_VALUE)");
        }

        @Test
        void takesArgumentsByPosition() {
            assertThat(CodeBlock.of("$2T $1N = new $2T()", "list", ArrayList.class))
                    .hasToString("java.util.ArrayList list = new java.util.ArrayList()");
        }

        @Test
        void writesDollarsAndIndentation() {
            assertThat(CodeBlock.of("a$$b\n$>indented\n$<back\n")).hasToString("a$b\n    indented\nback\n");
        }
    }

    @Nested
    class Rejects {

        @Test
        void missingAndUnusedArguments() {
            assertThatIllegalArgumentException().isThrownBy(() -> CodeBlock.of("$T"));
            assertThatIllegalArgumentException().isThrownBy(() -> CodeBlock.of("$L", 1, 2));
            assertThatIllegalArgumentException().isThrownBy(() -> CodeBlock.of("$2L", 1, 2));
            assertThatIllegalArgumentException().isThrownBy(() -> CodeBlock.of("$0L", 1));
        }

        @Test
        void mixedRelativeAndIndexedArguments() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> CodeBlock.of("$L $1L", 1))
                    .withMessageContaining("Cannot mix");
        }

        @Test
        void unknownAndDanglingPlaceholders() {
            assertThatIllegalArgumentException().isThrownBy(() -> CodeBlock.of("$X", 1));
            assertThatIllegalArgumentException().isThrownBy(() -> CodeBlock.of("cost: $"));
            assertThatIllegalArgumentException().isThrownBy(() -> CodeBlock.of("cost: $12"));
        }

        @Test
        void argumentsOfTheWrongKind() {
            assertThatIllegalArgumentException().isThrownBy(() -> CodeBlock.of("$T", "java.lang.String"));
            assertThatIllegalArgumentException().isThrownBy(() -> CodeBlock.of("$N", 42));
            assertThatIllegalArgumentException().isThrownBy(() -> CodeBlock.of("$N", (Object) null));
            assertThatIllegalArgumentException()
                    .isThrownBy(() ->
                            CodeBlock.of("$N", Type.newAnonymousClassBuilder("").build()));
        }

        @Test
        void commentsOfSeveralLines() {
            CodeBlock.Builder builder = CodeBlock.newBuilder();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> builder.addComment("first\nsecond"))
                    .withMessageContaining("line break");
            assertThatIllegalArgumentException().isThrownBy(() -> builder.addComment("first\rsecond"));
            assertThatIllegalArgumentException().isThrownBy(() -> builder.addComment("$L", "first\nsecond"));
            assertThatIllegalArgumentException().isThrownBy(() -> builder.addComment("costs $5"));
            assertThat(builder.isEmpty()).isTrue();
        }

        @Test
        void badCodeWithoutAddingAnyOfIt() {
            CodeBlock.Builder builder = CodeBlock.newBuilder();

            assertThatIllegalArgumentException().isThrownBy(() -> builder.addStatement("$T", "not a type"));
            assertThatIllegalArgumentException().isThrownBy(() -> builder.beginControlFlow("if ($L)"));
            assertThatIllegalArgumentException().isThrownBy(() -> builder.nextControlFlow("else if ($L)"));
            assertThatIllegalArgumentException().isThrownBy(() -> builder.endControlFlow("while ($L)"));
            assertThatIllegalArgumentException().isThrownBy(() -> builder.addNamed("$x:L", Map.of("X", 1)));
            assertThat(builder.isEmpty()).isTrue();
        }
    }

    @Nested
    class Builder {

        @Test
        void writesCommentsWithPlaceholders() {
            CodeBlock block = CodeBlock.newBuilder()
                    .addComment("$T keeps $L entries, at $$$L each", Map.class, 3, 2)
                    .addComment("from $S", "a \"quoted\" source")
                    .build();

            assertThat(block).hasToString("""
                    // java.util.Map keeps 3 entries, at $2 each
                    // from "a \\"quoted\\" source"
                    """);
        }

        @Test
        void writesStatementsAndControlFlow() {
            CodeBlock block = CodeBlock.newBuilder()
                    .addStatement("int total = 0")
                    .beginControlFlow("for (int i = 0; i < $L; i++)", 10)
                    .beginControlFlow("if (i % 2 == 0)")
                    .addStatement("total += i")
                    .nextControlFlow("else")
                    .addComment("odd numbers do not count")
                    .endControlFlow()
                    .endControlFlow()
                    .beginControlFlow("do")
                    .addStatement("total--")
                    .endControlFlow("while (total > 0)")
                    .build();

            assertThat(block).hasToString("""
                    int total = 0;
                    for (int i = 0; i < 10; i++) {
                        if (i % 2 == 0) {
                            total += i;
                        } else {
                            // odd numbers do not count
                        }
                    }
                    do {
                        total--;
                    } while (total > 0);
                    """);
        }

        @Test
        void joinsBlocks() {
            assertThat(CodeBlock.join(List.of(CodeBlock.of("a"), CodeBlock.of("$S", "b"), CodeBlock.of("c")), ", "))
                    .hasToString("a, \"b\", c");
            assertThat(CodeBlock.join(List.of(), ", ").isEmpty()).isTrue();
        }

        @Test
        void startsFromAnExistingBlock() {
            CodeBlock first = CodeBlock.of("a();\n");

            assertThat(first.toBuilder().addStatement("b()").build()).hasToString("a();\nb();\n");
            assertThat(first).hasToString("a();\n");
        }

        @Test
        void comparesBlocksByContent() {
            assertThat(CodeBlock.of("$T x", String.class)).isEqualTo(CodeBlock.of("$T x", String.class));
            assertThat(CodeBlock.of("$T x", String.class)).hasSameHashCodeAs(CodeBlock.of("$T x", String.class));
            assertThat(CodeBlock.of("$T x", String.class)).isNotEqualTo(CodeBlock.of("$T x", Integer.class));
            assertThat(CodeBlock.empty().isEmpty()).isTrue();
            assertThat(CodeBlock.newBuilder().build()).isSameAs(CodeBlock.empty());
        }

        @Test
        void embedsAnonymousClasses() {
            Type runnable = Type.newAnonymousClassBuilder("")
                    .addSuperinterface(Runnable.class)
                    .addMethod(Method.newBuilder("run")
                            .addAnnotation(Override.class)
                            .addModifiers(Modifier.PUBLIC)
                            .addStatement("work()")
                            .build())
                    .build();

            assertThat(CodeBlock.of("$T task = $L", Runnable.class, runnable)).hasToString("""
                    java.lang.Runnable task = new java.lang.Runnable() {
                        @java.lang.Override
                        public void run() {
                            work();
                        }
                    }""");
        }
    }

    @Nested
    class NamedArguments {

        @Test
        void takeArgumentsByName() {
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("list", List.class);
            arguments.put("item", String.class);
            arguments.put("name", "names");
            arguments.put("size", 10);
            arguments.put("text", "a\"b");
            arguments.put("unused", 0);

            assertThat(CodeBlock.newBuilder()
                            .addNamed("$list:T<$item:T> $name:N = $list:T.of($text:S); // $size:L$$", arguments)
                            .build())
                    .hasToString("java.util.List<java.lang.String> names = java.util.List.of(\"a\\\"b\"); // 10$");
        }

        @Test
        void acceptNamesWithDigitsAndUnderscores() {
            assertThat(CodeBlock.newBuilder()
                            .addNamed("$first_2:L$>", Map.of("first_2", "x"))
                            .build())
                    .hasToString("x");
        }

        @Test
        void rejectNamesThatDoNotStartWithALowerCaseLetter() {
            for (String name : List.of("Text", "_text", "1text", "te-xt", "")) {
                assertThatIllegalArgumentException()
                        .as(name)
                        .isThrownBy(() -> CodeBlock.newBuilder().addNamed("$text:L", Map.of(name, 1)))
                        .withMessageContaining("lower-case");
            }
        }

        @Test
        void rejectMissingArgumentsAndPlaceholdersWithoutName() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> CodeBlock.newBuilder().addNamed("$missing:L", Map.of("present", 1)))
                    .withMessageContaining("$missing");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> CodeBlock.newBuilder().addNamed("$L", Map.of("value", 1)));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> CodeBlock.newBuilder().addNamed("$1L", Map.of("value", 1)));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> CodeBlock.newBuilder().addNamed("$value", Map.of("value", 1)));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> CodeBlock.newBuilder().addNamed("$value:X", Map.of("value", 1)));
        }

        @Test
        void addNamedCodeToAMethod() {
            Method method = Method.newBuilder("run")
                    .addNamedCode("$type:T.out.println($text:S);\n", Map.of("type", System.class, "text", "hi"))
                    .build();

            assertThat(method.code()).hasToString("java.lang.System.out.println(\"hi\");\n");
        }
    }

    @Nested
    class Composition {

        @Test
        void collectBlocksWithASeparator() {
            CodeBlock arguments = Stream.of("a", "b", "c")
                    .map(name -> CodeBlock.of("$N", name))
                    .collect(CodeBlock.joining(", "));

            assertThat(arguments).hasToString("a, b, c");
        }

        @Test
        void collectBlocksBetweenAPrefixAndASuffix() {
            assertThat(Stream.of(CodeBlock.of("$S", "x"), CodeBlock.of("$L", 1))
                            .collect(CodeBlock.joining(", ", "call(", ")")))
                    .hasToString("call(\"x\", 1)");
            assertThat(Stream.<CodeBlock>empty().collect(CodeBlock.joining(", ", "call(", ")")))
                    .hasToString("call()");
            assertThat(Stream.<CodeBlock>empty()
                            .collect(CodeBlock.joining(", "))
                            .isEmpty())
                    .isTrue();
        }

        @Test
        void collectInParallelInOrder() {
            CodeBlock joined = Stream.iterate(0, i -> i + 1)
                    .limit(200)
                    .parallel()
                    .map(i -> CodeBlock.of("$L", i))
                    .collect(CodeBlock.joining(","));

            assertThat(joined.toString()).startsWith("0,1,2,").endsWith(",198,199");
        }

        @Test
        void clearABuilder() {
            CodeBlock.Builder builder = CodeBlock.newBuilder().addStatement("a()");

            assertThat(builder.clear().isEmpty()).isTrue();
            assertThat(builder.addStatement("b()").build()).hasToString("b();\n");
        }

        @Test
        void openAndCloseBlocksWithCodeBlocks() {
            CodeBlock condition = CodeBlock.of("if ($N > $L)", "count", 0);
            CodeBlock block = CodeBlock.newBuilder()
                    .beginControlFlow(condition)
                    .addStatement("positive()")
                    .nextControlFlow(CodeBlock.of("else if ($N < $L)", "count", 0))
                    .addStatement("negative()")
                    .endControlFlow()
                    .beginControlFlow(CodeBlock.of("do"))
                    .addStatement("count--")
                    .endControlFlow(CodeBlock.of("while ($N > 0)", "count"))
                    .build();

            assertThat(block).hasToString("""
                    if (count > 0) {
                        positive();
                    } else if (count < 0) {
                        negative();
                    }
                    do {
                        count--;
                    } while (count > 0);
                    """);
        }

        @Test
        void addAStatementFromACodeBlock() {
            assertThat(CodeBlock.newBuilder()
                            .addStatement(CodeBlock.of("return $L", 1))
                            .build())
                    .hasToString("return 1;\n");
            assertThat(Method.newBuilder("one")
                            .returns(int.class)
                            .addStatement(CodeBlock.of("return $L", 1))
                            .build()
                            .code())
                    .hasToString("return 1;\n");
        }
    }
}
