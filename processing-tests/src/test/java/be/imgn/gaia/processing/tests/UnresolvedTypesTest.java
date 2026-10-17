package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.processing.MoreElements;
import be.imgn.gaia.processing.ProcessingContext;
import java.util.List;
import java.util.function.Consumer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

/** The types javac could not resolve in the signature of an element: {@code MoreElements.firstUnresolvedType}. */
class UnresolvedTypesTest {

    private static final JavaFileObject REF = source("example.Ref", """
            package example;

            public @interface Ref {
                Class<?>[] value();
            }
            """);

    private static final JavaFileObject N = source("example.N", """
            package example;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;

            @Target(ElementType.TYPE_USE)
            public @interface N {}
            """);

    private static final JavaFileObject SIGNATURES = source("example.Signatures", """
            package example;

            import java.io.Serializable;
            import java.util.List;

            class Resolved<T extends Number> implements Serializable {
                List<? extends T> values;

                <E extends Exception> T first(List<T> list) throws E {
                    Missing body = null;
                    return list.get(0);
                }
            }

            class Superclass extends Missing {}

            class Interface implements Serializable, Comparable<Missing> {}

            class Bound<T extends Missing> {}

            class Field {
                List<@N Missing> missing;
            }

            class Qualified {
                com.example.Missing missing;
            }

            class Methods {
                void parameter(String first, Missing[] second) {}

                Missing returned() {
                    return null;
                }

                void thrown() throws MissingException {}

                <T extends Comparable<? super Missing>> void typeParameter() {}

                void resolved() {}
            }

            record Component(String name, Missing missing) {}

            @MissingAnnotation
            class Annotated {}

            @Ref({String.class, Missing.class})
            class ClassLiteral {}
            """);

    private static void check(Consumer<ProcessingContext> body) {
        assertThat(FirstRound.run(List.of(REF, N, SIGNATURES), List.of(), body)).failed();
    }

    private static TypeElement type(ProcessingContext context, String simpleName) {
        return context.elements().getTypeElement("example." + simpleName);
    }

    private static ExecutableElement method(ProcessingContext context, String name) {
        return ElementFilter.methodsIn(type(context, "Methods").getEnclosedElements()).stream()
                .filter(method -> method.getSimpleName().contentEquals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void findsNothingInAResolvedSignature() {
        check(context -> {
            TypeElement resolved = type(context, "Resolved");

            assertThat(MoreElements.firstUnresolvedType(resolved)).isEmpty();
            for (Element member : resolved.getEnclosedElements()) {
                assertThat(MoreElements.firstUnresolvedType(member))
                        .as(member.toString())
                        .isEmpty();
            }
        });
    }

    @Test
    void findsUnresolvedSupertypesBoundsAndFields() {
        check(context -> {
            assertThat(MoreElements.firstUnresolvedType(type(context, "Superclass")))
                    .contains("Missing");
            assertThat(MoreElements.firstUnresolvedType(type(context, "Interface")))
                    .contains("Missing");
            assertThat(MoreElements.firstUnresolvedType(type(context, "Bound"))).contains("Missing");
            assertThat(MoreElements.firstUnresolvedType(type(context, "Field"))).contains("Missing");
            assertThat(MoreElements.firstUnresolvedType(type(context, "Qualified")))
                    .contains("com.example.Missing");
            assertThat(MoreElements.firstUnresolvedType(type(context, "Component")))
                    .contains("Missing");
        });
    }

    @Test
    void findsUnresolvedTypesInTheSignatureOfAMethod() {
        check(context -> {
            assertThat(MoreElements.firstUnresolvedType(method(context, "parameter")))
                    .contains("Missing");
            assertThat(MoreElements.firstUnresolvedType(method(context, "returned")))
                    .contains("Missing");
            assertThat(MoreElements.firstUnresolvedType(method(context, "thrown")))
                    .contains("MissingException");
            assertThat(MoreElements.firstUnresolvedType(method(context, "typeParameter")))
                    .contains("Missing");
            assertThat(MoreElements.firstUnresolvedType(method(context, "resolved")))
                    .isEmpty();
        });
    }

    @Test
    void findsUnresolvedAnnotationsAndClassLiterals() {
        check(context -> {
            assertThat(MoreElements.firstUnresolvedType(type(context, "Annotated")))
                    .contains("MissingAnnotation");
            // javac names an unresolved class literal only as "<error>".
            assertThat(MoreElements.firstUnresolvedType(type(context, "ClassLiteral")))
                    .isPresent();
        });
    }
}
