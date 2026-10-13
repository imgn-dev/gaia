package be.imgn.gaia.processing.internal;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;
import org.jspecify.annotations.Nullable;

/**
 * Finds the types javac could not resolve in the signature of an element, typically because another processor has not
 * generated them yet.
 *
 * <p>The signature covers the element's annotations and their values, and the types it declares: superclass,
 * interfaces, type parameters and members of a type; return, parameter and thrown types of a method; the type of a
 * field, parameter or record component. Method bodies and field initializers are not covered.
 */
public final class Resolution {

    private final Set<TypeParameterElement> visitedTypeVariables = new HashSet<>();

    private Resolution() {}

    /**
     * Returns the first unresolved type in the signature of {@code element}.
     *
     * @param element the element to check
     * @return the name of the unresolved type, as written in the source but without type annotations or type arguments,
     *     such as {@code Missing} for {@code @Nullable Missing}; or empty if every type is resolved
     */
    public static Optional<String> firstUnresolved(Element element) {
        return new Resolution().element(element);
    }

    private Optional<String> element(Element element) {
        return annotations(element.getAnnotationMirrors()).or(() -> declaration(element));
    }

    private Optional<String> declaration(Element element) {
        return switch (element) {
            case TypeElement type ->
                type(type.getSuperclass())
                        .or(() -> types(type.getInterfaces()))
                        .or(() -> elements(type.getTypeParameters()))
                        .or(() -> elements(type.getEnclosedElements()));
            case ExecutableElement method ->
                type(method.getReturnType())
                        .or(() -> elements(method.getParameters()))
                        .or(() -> types(method.getThrownTypes()))
                        .or(() -> elements(method.getTypeParameters()));
            case TypeParameterElement parameter -> types(parameter.getBounds());
            case VariableElement variable -> type(variable.asType());
            case RecordComponentElement component -> type(component.asType());
            default -> Optional.empty();
        };
    }

    private Optional<String> elements(List<? extends Element> elements) {
        return elements.stream().map(this::element).flatMap(Optional::stream).findFirst();
    }

    private Optional<String> types(List<? extends TypeMirror> types) {
        return types.stream().map(this::type).flatMap(Optional::stream).findFirst();
    }

    private Optional<String> type(@Nullable TypeMirror type) {
        if (type == null) {
            return Optional.empty();
        }
        return switch (type.getKind()) {
            case ERROR -> Optional.of(name(type));
            case DECLARED -> types(((DeclaredType) type).getTypeArguments());
            case ARRAY -> type(((ArrayType) type).getComponentType());
            case WILDCARD ->
                type(((WildcardType) type).getExtendsBound()).or(() -> type(((WildcardType) type).getSuperBound()));
            case TYPEVAR -> typeVariable((TypeVariable) type);
            case INTERSECTION -> types(((IntersectionType) type).getBounds());
            case UNION -> types(((UnionType) type).getAlternatives());
            default -> Optional.empty();
        };
    }

    private Optional<String> typeVariable(TypeVariable variable) {
        if (!visitedTypeVariables.add((TypeParameterElement) variable.asElement())) {
            return Optional.empty();
        }
        return type(variable.getUpperBound()).or(() -> type(variable.getLowerBound()));
    }

    private Optional<String> annotations(List<? extends AnnotationMirror> annotations) {
        return annotations.stream()
                .map(this::annotation)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<String> annotation(AnnotationMirror annotation) {
        return type(annotation.getAnnotationType())
                .or(() -> annotation.getElementValues().entrySet().stream()
                        .map(entry -> value(entry.getKey().getReturnType(), entry.getValue()))
                        .flatMap(Optional::stream)
                        .findFirst());
    }

    private Optional<String> value(TypeMirror expected, AnnotationValue value) {
        return switch (value.getValue()) {
            case TypeMirror type -> type(type);
            case AnnotationMirror annotation -> annotation(annotation);
            case List<?> list ->
                list.stream()
                        .map(item -> value(componentOf(expected), (AnnotationValue) item))
                        .flatMap(Optional::stream)
                        .findFirst();
            // javac gives an unresolved class literal as a string, such as "<error>".
            case String text when isClass(expected) -> Optional.of(text);
            default -> Optional.empty();
        };
    }

    // The name of an unresolved type as written, without the type annotations toString() shows, as ".@example.N
    // Missing".
    private static String name(TypeMirror type) {
        return type instanceof DeclaredType declared && declared.asElement() instanceof TypeElement element
                ? element.getQualifiedName().toString()
                : type.toString();
    }

    private static TypeMirror componentOf(TypeMirror type) {
        return type.getKind() == TypeKind.ARRAY ? ((ArrayType) type).getComponentType() : type;
    }

    private static boolean isClass(TypeMirror type) {
        return type.getKind() == TypeKind.DECLARED
                && ((TypeElement) ((DeclaredType) type).asElement())
                        .getQualifiedName()
                        .contentEquals("java.lang.Class");
    }
}
