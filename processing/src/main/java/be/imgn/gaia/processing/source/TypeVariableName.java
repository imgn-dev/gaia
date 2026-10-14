package be.imgn.gaia.processing.source;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

/**
 * The name of a type variable, such as {@code T}. Its bounds are written where it is declared, as in {@code <T extends
 * Comparable<T>>}, and ignored where it is used.
 *
 * @param name the name of the variable
 * @param bounds the upper bounds, empty for {@code Object}
 * @param annotations the type annotations
 */
public record TypeVariableName(String name, List<TypeName> bounds, List<Annotation> annotations) implements TypeName {

    /**
     * Creates the name.
     *
     * @param name the name of the variable
     * @param bounds the upper bounds, empty for {@code Object}
     * @param annotations the type annotations
     * @throws IllegalArgumentException if the name is not a valid identifier; a bound is primitive, an array or a
     *     wildcard; or a type variable is a bound with other bounds
     */
    public TypeVariableName {
        Objects.requireNonNull(name, "name");
        bounds = Checks.elements(bounds, "bounds");
        annotations = Checks.elements(annotations, "annotations");
        if (!SourceVersion.isIdentifier(name) || SourceVersion.isKeyword(name)) {
            throw new IllegalArgumentException("Not a type variable name: " + name);
        }
        for (TypeName bound : bounds) {
            if (bound instanceof PrimitiveTypeName
                    || bound instanceof ArrayTypeName
                    || bound instanceof WildcardTypeName) {
                throw new IllegalArgumentException("A bound cannot be primitive, an array or a wildcard: " + bound);
            }
            if (bound instanceof TypeVariableName && bounds.size() > 1) {
                throw new IllegalArgumentException("A type variable bound cannot have other bounds: " + bounds);
            }
        }
    }

    /**
     * Returns the name of a type variable, without annotations.
     *
     * @param name the name of the variable
     * @param bounds the upper bounds
     * @return the name
     * @throws IllegalArgumentException if the name is not a valid identifier; a bound is primitive, an array or a
     *     wildcard; or a type variable is a bound with other bounds
     */
    public static TypeVariableName of(String name, TypeName... bounds) {
        Objects.requireNonNull(name, "name");
        return new TypeVariableName(name, Checks.elements(bounds, "bounds"), List.of());
    }

    /**
     * Returns the name of a declared type parameter, with its bounds.
     *
     * @param parameter the type parameter
     * @return the name
     * @throws IllegalArgumentException if a bound has no name, as {@link TypeName#of(TypeMirror)} names it
     */
    public static TypeVariableName of(TypeParameterElement parameter) {
        Objects.requireNonNull(parameter, "parameter");
        List<TypeName> bounds = parameter.getBounds().stream()
                .filter(bound -> bound.getKind() != TypeKind.DECLARED
                        || !TypeName.of(bound).equals(ClassName.OBJECT))
                .map(TypeName::of)
                .toList();
        return new TypeVariableName(parameter.getSimpleName().toString(), bounds, List.of());
    }

    /**
     * Returns the name of a type variable, with its upper bounds, such as the type variables of a method seen as a
     * member of a parameterized type.
     *
     * @param variable the type variable
     * @return the name
     * @throws IllegalArgumentException if a bound has no name, as {@link TypeName#of(TypeMirror)} names it, or cannot
     *     be the bound of a declared type variable, such as the array bound of a captured wildcard
     */
    public static TypeVariableName of(TypeVariable variable) {
        Objects.requireNonNull(variable, "variable");
        TypeMirror upperBound = variable.getUpperBound();
        List<TypeName> bounds = upperBound.getKind() == TypeKind.INTERSECTION
                ? ((IntersectionType) upperBound)
                        .getBounds().stream().map(TypeName::of).toList()
                : List.of(TypeName.of(upperBound));
        return new TypeVariableName(
                variable.asElement().getSimpleName().toString(),
                bounds.stream().filter(bound -> !bound.equals(ClassName.OBJECT)).toList(),
                List.of());
    }

    @Override
    public TypeVariableName annotated(Annotation... more) {
        return annotated(Checks.elements(more, "more"));
    }

    @Override
    public TypeVariableName annotated(List<Annotation> more) {
        return new TypeVariableName(
                name,
                bounds,
                Stream.concat(annotations.stream(), Checks.elements(more, "more").stream())
                        .toList());
    }

    @Override
    public TypeVariableName withoutAnnotations() {
        return new TypeVariableName(name, bounds, List.of());
    }

    @Override
    public String toString() {
        return CodeWriter.standalone(this);
    }
}
