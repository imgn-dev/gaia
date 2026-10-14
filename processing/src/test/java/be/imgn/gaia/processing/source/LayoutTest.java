package be.imgn.gaia.processing.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;
import javax.lang.model.element.Modifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** How code is laid out: statements over several lines, long strings, line endings, indentation and Javadoc tags. */
class LayoutTest {

    private static String write(Type type) {
        return JavaFile.newBuilder("com.example", type).build().toString();
    }

    private static Type withMethod(Method method) {
        return Type.newClassBuilder("Holder").addMethod(method).build();
    }

    @Nested
    class Statements {

        @Test
        void indentTheLinesAfterTheFirstTwice() {
            Method method = Method.newBuilder("sum")
                    .returns(int.class)
                    .addStatement("return 1\n+ 2\n+ 3")
                    .addStatement("return 4")
                    .build();

            assertThat(write(withMethod(method))).contains("""
                        int sum() {
                            return 1
                                    + 2
                                    + 3;
                            return 4;
                        }
                    """);
        }

        @Test
        void indentStatementsMarkedByHand() {
            CodeBlock code = CodeBlock.of("$[int total = first\n+ second;\n$]$[int other = 0;\n$]");

            assertThat(write(withMethod(Method.newBuilder("add").addCode(code).build())))
                    .contains("        int total = first\n                + second;\n        int other = 0;\n");
        }

        @Test
        void indentTheInitializerOfAFieldTheSameWay() {
            Type type = Type.newClassBuilder("Holder")
                    .addField(Field.newBuilder(int.class, "SUM", Modifier.STATIC, Modifier.FINAL)
                            .initializer("1\n+ 2")
                            .build())
                    .build();

            assertThat(write(type)).contains("    static final int SUM = 1\n            + 2;\n");
        }

        @Test
        void writeTheBodyOfAnAnonymousClassAsABlock() {
            Type runnable = Type.newAnonymousClassBuilder("")
                    .addSuperinterface(Runnable.class)
                    .addMethod(Method.newBuilder("run")
                            .addAnnotation(Override.class)
                            .addModifiers(Modifier.PUBLIC)
                            .addStatement("work()")
                            .build())
                    .build();
            Method method = Method.newBuilder("start")
                    .addStatement("$T task = $L", Runnable.class, runnable)
                    .build();

            assertThat(write(withMethod(method))).contains("""
                            Runnable task = new Runnable() {
                                @Override
                                public void run() {
                                    work();
                                }
                            };
                    """);
        }
    }

    @Nested
    class Strings {

        @Test
        void splitAStringOfSeveralLinesIntoOneLiteralPerLine() {
            Method method = Method.newBuilder("text")
                    .returns(String.class)
                    .addStatement("return $S", "first\nsecond\nthird")
                    .build();

            assertThat(write(withMethod(method))).contains("""
                            return "first\\n"
                                    + "second\\n"
                                    + "third";
                    """);
        }

        @Test
        void keepAStringWithATrailingNewLineWhole() {
            assertThat(CodeBlock.of("$S", "line\n")).hasToString("\"line\\n\"");
            assertThat(CodeBlock.of("$S", "\n")).hasToString("\"\\n\"");
        }

        @Test
        void splitAStringOutsideAStatementWithAContinuationIndent() {
            assertThat(CodeBlock.of("call($S)", "a\nb")).hasToString("call(\"a\\n\"\n        + \"b\")");
        }

        @Test
        void splitStringsInFieldInitializersAndAnnotations() {
            Type type = Type.newClassBuilder("Holder")
                    .addField(Field.newBuilder(String.class, "TEXT", Modifier.STATIC, Modifier.FINAL)
                            .initializer("$S", "a\nb\n")
                            .build())
                    .addAnnotation(Annotation.newBuilder(SuppressWarnings.class)
                            .addMember("value", "$S", "x\ny")
                            .build())
                    .build();

            assertThat(write(type))
                    .contains("    static final String TEXT = \"a\\n\"\n            + \"b\\n\";\n")
                    .contains("@SuppressWarnings(\"x\\n\"\n        + \"y\")\nclass Holder {");
        }

        @Test
        void keepStringsWholeInJavadocAndComments() {
            Type type = Type.newClassBuilder("Holder")
                    .addJavadoc("Says $S.\n", "a\nb")
                    .build();

            assertThat(write(type)).contains(" * Says \"a\\nb\".\n");
        }
    }

    @Nested
    class LineEndings {

        @Test
        void writeEveryLineBreakAsANewLine() {
            Type type = Type.newClassBuilder("Doc")
                    .addJavadoc("A\r\n\r\nB\r\n")
                    .addMethod(Method.newBuilder("run")
                            .addCode("first();\r\nsecond();\rthird();\n")
                            .build())
                    .build();

            String written = write(type);

            assertThat(written)
                    .contains("/**\n * A\n *\n * B\n */\nclass Doc {")
                    .contains("        first();\n        second();\n        third();\n")
                    .doesNotContain("\r")
                    .doesNotContain(" \n");
        }

        @Test
        void keepCarriageReturnsInStringsEscaped() {
            assertThat(CodeBlock.of("$S", "a\r\nb")).hasToString("\"a\\r\\n\"\n        + \"b\"");
        }
    }

    @Nested
    class Indentation {

        @Test
        void rejectAMethodWhoseCodeUnindentsMoreThanItIndents() {
            Method.Builder method = Method.newBuilder("run").addCode("$<oops();\n");

            assertThatIllegalStateException().isThrownBy(method::build).withMessageContaining("unindents");
        }

        @Test
        void rejectAMethodWhoseControlFlowIsNotClosed() {
            Method.Builder method = Method.newBuilder("run").beginControlFlow("if (ready)");

            assertThatIllegalStateException().isThrownBy(method::build).withMessageContaining("indents");
        }

        @Test
        void rejectAMethodWhoseStatementsAreNotEnded() {
            Method.Builder method = Method.newBuilder("run").addCode("$[run();\n");

            assertThatIllegalStateException().isThrownBy(method::build).withMessageContaining("$]");
        }

        @Test
        void showAnUnbalancedFragmentOnItsOwn() {
            CodeBlock fragment = CodeBlock.newBuilder()
                    .nextControlFlow("else")
                    .addStatement("other()")
                    .build();

            assertThatCode(fragment::toString).doesNotThrowAnyException();
            assertThat(fragment).hasToString("} else {\n    other();\n");
        }

        @Test
        void letCodeUnindentWhatItIndented() {
            Method method =
                    Method.newBuilder("run").addCode("first(\n$>second()$<);\n").build();

            assertThat(write(withMethod(method))).contains("        first(\n            second());\n");
        }
    }

    @Nested
    class Javadoc {

        @Test
        void writeTheJavadocOfParametersAsParamTags() {
            Method method = Method.newBuilder("add")
                    .addJavadoc("Adds two numbers.")
                    .returns(int.class)
                    .addParameter(Parameter.newBuilder(int.class, "left")
                            .addJavadoc("the first number\n")
                            .build())
                    .addParameter(int.class, "middle")
                    .addParameter(Parameter.newBuilder(int.class, "right")
                            .addJavadoc("the second number")
                            .build())
                    .addStatement("return left + right")
                    .build();

            assertThat(method.toString()).startsWith("""
                    /**
                     * Adds two numbers.
                     *
                     * @param left the first number
                     * @param right the second number
                     */
                    int add(int left, int middle, int right) {
                    """);
        }

        @Test
        void writeParamTagsAloneWithoutJavadoc() {
            Method method = Method.newBuilder("run")
                    .addParameter(Parameter.newBuilder(int.class, "times")
                            .addJavadoc("how often")
                            .build())
                    .build();

            assertThat(method.toString()).startsWith("/**\n * @param times how often\n */\nvoid run(int times) {");
        }

        @Test
        void writeTheJavadocOfRecordComponentsAsParamTagsOfTheRecord() {
            Type point = Type.newRecordBuilder("Point")
                    .addJavadoc("A point.\n")
                    .addRecordComponent(Parameter.newBuilder(int.class, "x")
                            .addJavadoc("the abscissa")
                            .build())
                    .addRecordComponent(Parameter.newBuilder(int.class, "y")
                            .addJavadoc("the ordinate")
                            .build())
                    .build();

            assertThat(write(point)).contains("""
                    /**
                     * A point.
                     *
                     * @param x the abscissa
                     * @param y the ordinate
                     */
                    record Point(int x, int y) {
                    """);
        }
    }

    @Nested
    class Parameters {

        private static final Annotation A = Annotation.of(ClassName.of("com.example", "A"));
        private static final Annotation B = Annotation.of(ClassName.of("com.example", "B"));

        @Test
        void writeTheLastDimensionOfAVarargsArrayAsAnEllipsis() {
            TypeName twoDimensions = ArrayTypeName.of(
                            ArrayTypeName.of(ClassName.OBJECT).annotated(B))
                    .annotated(A);
            Method method = Method.newBuilder("all")
                    .addParameter(twoDimensions, "rows")
                    .varargs(true)
                    .build();

            assertThat(method.toString())
                    .contains("void all(java.lang.Object @com.example.A [] @com.example.B ... rows)");
        }

        @Test
        void writeAReceiverParameter() {
            ClassName resource = ClassName.of("com.example", "Resource");
            Method method = Method.newBuilder("close")
                    .addParameter(resource.annotated(A), "this")
                    .addParameter(int.class, "code")
                    .build();
            Method innerConstructor = Method.newConstructorBuilder()
                    .addParameter(ClassName.of("com.example", "Outer"), "Outer.this")
                    .build();

            assertThat(method.toString()).contains("void close(com.example.@com.example.A Resource this, int code)");
            assertThat(method.parameters().getFirst().isReceiver()).isTrue();
            assertThat(innerConstructor.toString()).contains("Constructor(com.example.Outer Outer.this)");
        }

        @Test
        void writeAVarargsRecord() {
            Type names = Type.newRecordBuilder("Names")
                    .addRecordComponent(int.class, "count")
                    .addRecordComponent(ArrayTypeName.of(ClassName.of(String.class)), "values")
                    .varargs(true)
                    .build();

            assertThat(names.isVarargs()).isTrue();
            assertThat(write(names)).contains("record Names(int count, String... values) {");
            assertThat(names.toBuilder().build()).isEqualTo(names);
        }

        @Test
        void writeTypesOfAnyListOfParameters() {
            Method method = Method.newBuilder("many")
                    .addParameters(List.of(Parameter.of(int.class, "a"), Parameter.of(long.class, "b")))
                    .build();

            assertThat(method.toString()).contains("void many(int a, long b)");
        }
    }

    @Nested
    class Wrapping {

        @Test
        void breakLongArgumentListsAtWrapPoints() {
            CodeBlock.Builder call = CodeBlock.newBuilder().add("$T.of(", List.class);
            for (int i = 0; i < 12; i++) {
                call.add(i == 0 ? "$S" : ",$W$S", "element" + i);
            }
            Method method = Method.newBuilder("elements")
                    .returns(ClassName.of(List.class).parameterized(ClassName.of(String.class)))
                    .addStatement("return $L)", call.build())
                    .build();

            String written = write(withMethod(method));

            assertThat(written.lines())
                    .allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(100));
            assertThat(written)
                    .contains("        return List.of(\"element0\", \"element1\",")
                    .contains("\n                \"element");
        }

        @Test
        void writeASpaceOrNothingWhenTheLineFits() {
            assertThat(CodeBlock.of("a,$Wb$Zc")).hasToString("a, bc");
        }

        @Test
        void breakAtAZeroWidthPointWithoutASpace() {
            String longName = "x".repeat(90);
            Method method = Method.newBuilder("run")
                    .addStatement("call($Z$L)", longName)
                    .build();

            assertThat(write(withMethod(method))).contains("        call(\n                " + longName + ");\n");
        }

        @Test
        void stayInsideJavadoc() {
            Type type = Type.newClassBuilder("Doc")
                    .addJavadoc("$L$W$L\n", "word ".repeat(19).strip(), "next")
                    .build();

            assertThat(write(type)).contains(" * " + "word ".repeat(19).strip() + "\n * next\n");
        }
    }
}
