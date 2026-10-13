package be.imgn.gaia.processing.tests;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.processing.testing.CompilationExtension;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(CompilationExtension.class)
class CompilationExtensionTest {

    @Test
    void injectsElements(Elements elements) {
        TypeElement string = elements.getTypeElement("java.lang.String");

        assertThat(string.getKind()).isEqualTo(ElementKind.CLASS);
        assertThat(ElementFilter.methodsIn(string.getEnclosedElements()))
                .anySatisfy(method -> assertThat(method.getSimpleName().contentEquals("isBlank"))
                        .isTrue());
    }

    @Test
    void injectsTypes(Elements elements, Types types) {
        TypeMirror string = elements.getTypeElement("java.lang.String").asType();
        TypeMirror charSequence =
                elements.getTypeElement("java.lang.CharSequence").asType();

        assertThat(types.isAssignable(string, charSequence)).isTrue();
        assertThat(types.isAssignable(charSequence, string)).isFalse();
    }

    @Test
    void injectsElementsAndTypesOfTheSameCompilation(Elements elements, Types types) {
        TypeElement list = elements.getTypeElement("java.util.List");
        TypeElement string = elements.getTypeElement("java.lang.String");

        TypeMirror listOfString = types.getDeclaredType(list, string.asType());

        assertThat(listOfString).hasToString("java.util.List<java.lang.String>");
        assertThat(types.isSameType(types.erasure(listOfString), types.erasure(list.asType())))
                .isTrue();
    }

    @Test
    void seesTheClassPath(Elements elements) {
        assertThat(elements.getTypeElement(InfoProcessor.class.getCanonicalName()))
                .isNotNull();
    }
}
