package be.imgn.gaia.processing.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import be.imgn.gaia.processing.MoreElements;
import be.imgn.gaia.processing.MoreTypes;
import be.imgn.gaia.processing.TypeKey;
import be.imgn.gaia.processing.source.ClassName;
import be.imgn.gaia.processing.source.Type;
import be.imgn.gaia.processing.testing.CompilationExtension;
import be.imgn.gaia.processing.testing.SharedCompilation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
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
    void rejectsClassesWithoutACanonicalName(Elements elements, Types types) {
        class Local {}
        TypeMirror list = listOf(elements, types, String.class);
        TypeMirror objects =
                types.getArrayType(elements.getTypeElement("java.lang.Object").asType());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> MoreTypes.isType(list, Local.class))
                .withMessage(Local.class.getName() + " has no canonical name, as a local, anonymous or hidden class");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MoreTypes.isType(objects, Local[].class))
                .withMessageStartingWith(Local.class.getName() + " has no canonical name");
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
    void castsTypesOfAKnownKind(Elements elements, Types types) {
        TypeMirror ints = types.getArrayType(types.getPrimitiveType(TypeKind.INT));
        TypeElement collections = elements.getTypeElement("java.util.Collections");
        ExecutableElement max = ElementFilter.methodsIn(collections.getEnclosedElements()).stream()
                .filter(method -> method.getSimpleName().contentEquals("max")
                        && method.getParameters().size() == 1)
                .findFirst()
                .orElseThrow();
        TypeMirror element = elements.getTypeElement("java.util.List")
                .getTypeParameters()
                .getFirst()
                .asType();
        TypeMirror wildcard = types.getWildcardType(null, null);

        assertThat(MoreTypes.asArray(ints)).isSameAs(ints);
        assertThat(MoreTypes.asPrimitive(types.getPrimitiveType(TypeKind.INT)).getKind())
                .isEqualTo(TypeKind.INT);
        assertThat(MoreTypes.asExecutable(max.asType()).getParameterTypes()).hasSize(1);
        assertThat(MoreTypes.asTypeVariable(element)).isSameAs(element);
        assertThat(MoreTypes.asWildcard(wildcard)).isSameAs(wildcard);
        // <T extends Object & Comparable<? super T>> T max(Collection<? extends T> coll)
        assertThat(MoreTypes.asIntersection(
                                MoreTypes.asTypeVariable(max.getReturnType()).getUpperBound())
                        .getBounds())
                .hasSize(2);
    }

    @Test
    void rejectsTypesOfAnotherKind(Elements elements, Types types) {
        TypeMirror string = elements.getTypeElement("java.lang.String").asType();
        TypeMirror voidType = types.getNoType(TypeKind.VOID);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> MoreTypes.asArray(string))
                .withMessage("DECLARED java.lang.String is not an array type");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MoreTypes.asPrimitive(voidType))
                .withMessage("VOID void is not a primitive type");
        assertThatIllegalArgumentException().isThrownBy(() -> MoreTypes.asExecutable(string));
        assertThatIllegalArgumentException().isThrownBy(() -> MoreTypes.asTypeVariable(string));
        assertThatIllegalArgumentException().isThrownBy(() -> MoreTypes.asWildcard(string));
        assertThatIllegalArgumentException().isThrownBy(() -> MoreTypes.asIntersection(string));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MoreTypes.asUnion(string))
                .withMessage("DECLARED java.lang.String is not a union type");
    }

    @Test
    void permitsSubclassesGivenAsTypeMirrors(Elements elements, Types types) {
        Type shape = Type.newInterfaceBuilder("Shape")
                .addModifiers(Modifier.SEALED)
                .addPermittedSubclass(listOf(elements, types, String.class))
                .build();

        assertThat(shape.permittedSubclasses()).containsExactly(ClassName.of(List.class));
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        Type.newInterfaceBuilder("Shape").addPermittedSubclass(types.getPrimitiveType(TypeKind.INT)));
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
    void castsElementsToTypesIfTheyAreTypes(Elements elements) {
        TypeElement map = elements.getTypeElement("java.util.Map");
        Element size = ElementFilter.methodsIn(map.getEnclosedElements()).stream()
                .filter(method -> method.getSimpleName().contentEquals("size"))
                .findFirst()
                .orElseThrow();

        assertThat(MoreElements.asTypeElement(map)).contains(map);
        assertThat(MoreElements.asTypeElement(elements.getTypeElement("java.lang.annotation.Retention")))
                .isPresent();
        assertThat(MoreElements.asTypeElement(size)).isEmpty();
        assertThat(MoreElements.asTypeElement(elements.getPackageElement("java.util")))
                .isEmpty();
        assertThat(MoreElements.asTypeElement(map.getTypeParameters().getFirst()))
                .isEmpty();
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

    @Test
    void checksDirectAnnotationsByClass(Elements elements) {
        assertThat(MoreElements.isAnnotatedWith(
                        elements.getTypeElement("java.lang.Runnable"), FunctionalInterface.class))
                .isTrue();
        assertThat(MoreElements.isAnnotatedWith(elements.getTypeElement("java.lang.Thread"), FunctionalInterface.class))
                .isFalse();
        assertThat(MoreElements.isAnnotatedWith(elements.getTypeElement("java.lang.SecurityManager"), Deprecated.class))
                .isTrue();
    }
}
