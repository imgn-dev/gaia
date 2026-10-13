package be.imgn.gaia.processing.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.jupiter.api.Test;

class AnnotationTest {

    @Test
    void writesAMarkerAnnotation() {
        assertThat(Annotation.of(Override.class)).hasToString("@java.lang.Override");
    }

    @Test
    void writesASingleValueWithoutItsName() {
        assertThat(Annotation.newBuilder(Retention.class)
                        .addMember("value", "$T.$L", RetentionPolicy.class, "RUNTIME")
                        .build())
                .hasToString("@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)");
    }

    @Test
    void writesNamedMembersAndArrays() {
        Annotation annotation = Annotation.newBuilder(ClassName.of("org.example", "Mapping"))
                .addMember("source", "$S", "a")
                .addMember("targets", "$S", "b")
                .addMember("targets", "$S", "c")
                .addMember("nested", "$L", Annotation.of(ClassName.of("org.example", "Nested")))
                .build();

        assertThat(annotation)
                .hasToString(
                        "@org.example.Mapping(source = \"a\", targets = {\"b\", \"c\"}, nested = @org.example.Nested)");
        assertThat(annotation.members()).containsOnlyKeys("source", "targets", "nested");
    }

    @Test
    void writesAnArrayForSeveralValues() {
        assertThat(Annotation.newBuilder(SuppressWarnings.class)
                        .addMember("value", "$S", "unchecked")
                        .addMember("value", "$S", "rawtypes")
                        .build())
                .hasToString("@java.lang.SuppressWarnings({\"unchecked\", \"rawtypes\"})");
    }

    @Test
    void comparesAnnotationsByContent() {
        Annotation first = Annotation.newBuilder(SuppressWarnings.class)
                .addMember("value", "$S", "x")
                .build();
        Annotation same = Annotation.newBuilder(SuppressWarnings.class)
                .addMember("value", "$S", "x")
                .build();

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(Annotation.of(SuppressWarnings.class));
        assertThat(first.toBuilder().addMember("value", "$S", "y").build())
                .hasToString("@java.lang.SuppressWarnings({\"x\", \"y\"})");
        assertThat(first).hasToString("@java.lang.SuppressWarnings(\"x\")");
    }

    @Test
    void rejectsInvalidMemberNames() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Annotation.newBuilder(Deprecated.class).addMember("for", "true"));
    }
}
