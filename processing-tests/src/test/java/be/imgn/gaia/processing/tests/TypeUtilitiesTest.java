package be.imgn.gaia.processing.tests;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.processing.MoreElements;
import be.imgn.gaia.processing.MoreTypes;
import be.imgn.gaia.processing.TypeKey;
import be.imgn.gaia.processing.testing.CompilationExtension;
import be.imgn.gaia.processing.testing.SharedCompilation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(CompilationExtension.class)
@SharedCompilation
class TypeUtilitiesTest {

    private static TypeMirror listOf(Elements elements, Types types, Class<?> argument) {
        return types.getDeclaredType(
                elements.getTypeElement("java.util.List"),
                elements.getTypeElement(argument.getCanonicalName()).asType());
    }

    @Test
    void comparesTypesWithClassesAndNames(Elements elements, Types types) {
        TypeMirror listOfStrings = listOf(elements, types, String.class);
        TypeMirror stringArray =
                types.getArrayType(elements.getTypeElement("java.lang.String").asType());

        assertThat(MoreTypes.isType(listOfStrings, List.class)).isTrue();
        assertThat(MoreTypes.isType(listOfStrings, "java.util.List")).isTrue();
        assertThat(MoreTypes.isType(listOfStrings, Set.class)).isFalse();
        assertThat(MoreTypes.isType(types.getPrimitiveType(TypeKind.INT), int.class))
                .isTrue();
        assertThat(MoreTypes.isType(types.getPrimitiveType(TypeKind.INT), long.class))
                .isFalse();
        assertThat(MoreTypes.isType(types.getNoType(TypeKind.VOID), void.class)).isTrue();
        assertThat(MoreTypes.isType(stringArray, String[].class)).isTrue();
        assertThat(MoreTypes.isType(stringArray, Object[].class)).isFalse();
    }

    @Test
    void convertsTypesToElementsSafely(Elements elements, Types types) {
        assertThat(MoreTypes.asTypeElement(listOf(elements, types, String.class)))
                .contains(elements.getTypeElement("java.util.List"));
        assertThat(MoreTypes.asTypeElement(types.getPrimitiveType(TypeKind.INT)))
                .isEmpty();
        assertThat(MoreTypes.asDeclared(types.getPrimitiveType(TypeKind.INT))).isEmpty();
    }

    @Test
    void keysEqualTypesEqually(Elements elements, Types types) {
        TypeKey strings = TypeKey.of(listOf(elements, types, String.class));
        TypeKey sameStrings = TypeKey.of(listOf(elements, types, String.class));
        TypeKey integers = TypeKey.of(listOf(elements, types, Integer.class));

        assertThat(strings)
                .isEqualTo(sameStrings)
                .hasSameHashCodeAs(sameStrings)
                .isNotEqualTo(integers);
        assertThat(Set.of(strings, integers)).contains(sameStrings);
        assertThat(strings).hasToString("java.util.List<java.lang.String>");
    }

    @Test
    void navigatesToEnclosingTypesAndPackages(Elements elements) {
        TypeElement entry = elements.getTypeElement("java.util.Map.Entry");
        Element getKey = ElementFilter.methodsIn(entry.getEnclosedElements()).stream()
                .filter(method -> method.getSimpleName().contentEquals("getKey"))
                .findFirst()
                .orElseThrow();

        assertThat(MoreElements.packageOf(getKey).getQualifiedName()).hasToString("java.util");
        assertThat(MoreElements.enclosingType(getKey)).contains(entry);
        assertThat(MoreElements.enclosingType(entry)).contains(elements.getTypeElement(Map.class.getCanonicalName()));
        assertThat(MoreElements.topLevelType(getKey)).contains(elements.getTypeElement("java.util.Map"));
        assertThat(MoreElements.topLevelType(elements.getTypeElement("java.util.Map")))
                .contains(elements.getTypeElement("java.util.Map"));
    }

    @Test
    void checksDirectAnnotations(Elements elements) {
        assertThat(MoreElements.isAnnotatedWith(
                        elements.getTypeElement("java.lang.Runnable"), "java.lang.FunctionalInterface"))
                .isTrue();
        assertThat(MoreElements.isAnnotatedWith(
                        elements.getTypeElement("java.lang.Thread"), "java.lang.FunctionalInterface"))
                .isFalse();
    }
}
