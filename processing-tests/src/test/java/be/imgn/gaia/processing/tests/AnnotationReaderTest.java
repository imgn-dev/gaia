package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.AnnotationReader;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.StepProcessor;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import org.junit.jupiter.api.Test;

class AnnotationReaderTest {

    private static void read(Consumer<AnnotationReader> assertions) {
        assertThat(javac().withProcessors(new Reading(assertions))
                        .compile(source("example.Everything", """
                        package example;

                        import java.lang.annotation.RetentionPolicy;

                        @interface Nested {
                            String value();
                        }

                        @interface Everything {
                            String text() default "default";
                            int number();
                            Class<?> type();
                            Class<?>[] types() default {};
                            RetentionPolicy policy();
                            RetentionPolicy[] policies() default {RetentionPolicy.SOURCE, RetentionPolicy.CLASS};
                            Nested nested();
                            Nested[] nesteds() default {};
                            String[] texts() default {"a", "b"};
                            long[] numbers() default {1, 2};
                        }
                        """), source("example.Annotated", """
                        package example;

                        import java.lang.annotation.RetentionPolicy;

                        @Everything(
                                number = 42,
                                type = String.class,
                                types = {Integer.class, Long.class},
                                policy = RetentionPolicy.RUNTIME,
                                nested = @Nested("inner"),
                                nesteds = {@Nested("first"), @Nested("second")})
                        class Annotated {}
                        """)))
                .succeeded();
    }

    @Test
    void readsPrimitivesAndStrings() {
        read(everything -> {
            assertThat(everything.value("number", Integer.class)).isEqualTo(42);
            assertThat(everything.string("text")).isEqualTo("default");
            assertThat(everything.strings("texts")).containsExactly("a", "b");
            assertThat(everything.values("numbers", Long.class)).containsExactly(1L, 2L);
        });
    }

    @Test
    void readsClassValuesAsTypes() {
        read(everything -> {
            assertThat(everything.type("type")).hasToString("java.lang.String");
            assertThat(everything.types("types"))
                    .map(TypeMirror::toString)
                    .containsExactly("java.lang.Integer", "java.lang.Long");
        });
    }

    @Test
    void readsEnumValues() {
        read(everything -> {
            assertThat(everything.enumConstant("policy")).isEqualTo("RUNTIME");
            assertThat(everything.enumValue("policy", RetentionPolicy.class)).isEqualTo(RetentionPolicy.RUNTIME);
            assertThat(everything.enumConstants("policies")).containsExactly("SOURCE", "CLASS");
        });
    }

    @Test
    void readsNestedAnnotations() {
        read(everything -> {
            assertThat(everything.annotation("nested").string("value")).isEqualTo("inner");
            assertThat(everything.annotations("nesteds"))
                    .map(nested -> nested.string("value"))
                    .containsExactly("first", "second");
        });
    }

    @Test
    void tellsExplicitValuesFromDefaults() {
        read(everything -> {
            assertThat(everything.qualifiedName()).isEqualTo("example.Everything");
            assertThat(everything.isExplicit("number")).isTrue();
            assertThat(everything.isExplicit("text")).isFalse();
        });
    }

    @Test
    void rejectsUnknownAttributesAndWrongTypes() {
        read(everything -> {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> everything.raw("missing"))
                    .withMessage("@example.Everything has no value for missing");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> everything.value("text", Integer.class))
                    .withMessageContaining("@example.Everything.text is not a Integer");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> everything.strings("text"))
                    .withMessageContaining("is not an array");
        });
    }

    /** Runs assertions on the {@code @Everything} annotation of {@code Annotated}, while javac runs. */
    private static final class Reading extends StepProcessor {

        private final Consumer<AnnotationReader> assertions;

        Reading(Consumer<AnnotationReader> assertions) {
            this.assertions = assertions;
        }

        @Override
        protected List<Step> steps(ProcessingContext context) {
            return List.of(new Step() {
                @Override
                public Set<String> annotations() {
                    return Set.of("example.Everything");
                }

                @Override
                public Set<? extends Element> process(AnnotatedElements elements) {
                    elements.annotatedWith("example.Everything")
                            .forEach(element -> assertions.accept(context.annotation(element, "example.Everything")
                                    .orElseThrow()));
                    return Set.of();
                }
            });
        }
    }
}
