package be.imgn.gaia.processing.source;

import java.util.List;
import java.util.Locale;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;

/**
 * The name of a type as it is written in source code: a primitive, a class, a parameterized type, an array, a type
 * variable or a wildcard, possibly with type annotations such as {@code @Nullable}.
 *
 * <p>Type names are values: two names of the same type, with the same annotations, are equal.
 */
public sealed interface TypeName
        permits ArrayTypeName, ClassName, ParameterizedTypeName, PrimitiveTypeName, TypeVariableName, WildcardTypeName {

    /**
     * Returns the name of a class, which may be primitive, {@code void} or an array.
     *
     * @param type the class
     * @return its name
     * @throws IllegalArgumentException if the class is local or anonymous, and has no canonical name
     */
    static TypeName of(Class<?> type) {
        if (type.isArray()) {
            return ArrayTypeName.of(of(type.getComponentType()));
        }
        return type.isPrimitive() ? PrimitiveTypeName.of(type.getName()) : ClassName.of(type);
    }

    /**
     * Returns the name of a type, with its type annotations.
     *
     * <p>An inner class whose enclosing type has type arguments or annotations, as in {@code Outer<String>.Inner}, is
     * named with a {@link ParameterizedTypeName#ownerType() owner type}.
     *
     * @param type the type
     * @return its name
     * @throws IllegalArgumentException if the type has no name, such as an executable or a package
     */
    static TypeName of(TypeMirror type) {
        TypeName name = switch (type.getKind()) {
            case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE, VOID ->
                PrimitiveTypeName.of(type.getKind().name().toLowerCase(Locale.ROOT));
            case ARRAY -> ArrayTypeName.of(of(((ArrayType) type).getComponentType()));
            case DECLARED -> declared((DeclaredType) type);
            case ERROR -> ClassName.bestGuess(type.toString().replaceFirst("<.*", ""));
            case TYPEVAR ->
                TypeVariableName.of(
                        ((TypeVariable) type).asElement().getSimpleName().toString());
            case WILDCARD -> wildcard((WildcardType) type);
            default -> throw new IllegalArgumentException("Cannot name a type of kind " + type.getKind() + ": " + type);
        };
        List<Annotation> annotations =
                type.getAnnotationMirrors().stream().map(Annotation::of).toList();
        return annotations.isEmpty() ? name : name.annotated(annotations);
    }

    // The enclosing type of an inner class is kept when it says more than its name: Outer<String>.Inner.
    private static TypeName declared(DeclaredType type) {
        ClassName raw = ClassName.of((TypeElement) type.asElement());
        List<TypeName> arguments =
                type.getTypeArguments().stream().map(TypeName::of).toList();
        TypeMirror enclosing = type.getEnclosingType();
        if (enclosing.getKind() == TypeKind.DECLARED) {
            TypeName owner = of(enclosing);
            if (owner instanceof ParameterizedTypeName || owner.isAnnotated()) {
                return new ParameterizedTypeName(owner, raw, arguments, List.of());
            }
        }
        return arguments.isEmpty() ? raw : ParameterizedTypeName.of(raw, arguments);
    }

    private static TypeName wildcard(WildcardType type) {
        TypeMirror upperBound = type.getExtendsBound();
        TypeMirror lowerBound = type.getSuperBound();
        if (upperBound != null) {
            return WildcardTypeName.subtypeOf(of(upperBound));
        }
        return lowerBound == null ? WildcardTypeName.unbounded() : WildcardTypeName.supertypeOf(of(lowerBound));
    }

    /**
     * Returns the type annotations of this name.
     *
     * @return the annotations, in order
     */
    List<Annotation> annotations();

    /**
     * Returns whether this name has type annotations, at the top level.
     *
     * @return {@code true} if {@link #annotations()} is not empty
     */
    default boolean isAnnotated() {
        return !annotations().isEmpty();
    }

    /**
     * Returns this name with more type annotations.
     *
     * @param annotations the annotations to add after the ones it has
     * @return the annotated name
     */
    TypeName annotated(List<Annotation> annotations);

    /**
     * Returns this name with more type annotations.
     *
     * @param annotations the annotations to add after the ones it has
     * @return the annotated name
     */
    default TypeName annotated(Annotation... annotations) {
        return annotated(List.of(annotations));
    }

    /**
     * Returns this name without type annotations, at the top level: those of type arguments and array components are
     * kept.
     *
     * @return the name without annotations
     */
    TypeName withoutAnnotations();

    /**
     * Returns whether this is a primitive type, {@code void} excluded.
     *
     * @return {@code true} for {@code boolean}, {@code byte}, {@code short}, {@code int}, {@code long}, {@code char},
     *     {@code float} and {@code double}
     */
    default boolean isPrimitive() {
        return this instanceof PrimitiveTypeName primitive
                && !primitive.keyword().equals("void");
    }

    /**
     * Returns the boxed type of a primitive type, such as {@code Integer} for {@code int}, with the same annotations;
     * any other type unchanged.
     *
     * @return the boxed type, or this name
     */
    default TypeName box() {
        return this instanceof PrimitiveTypeName primitive ? primitive.boxed() : this;
    }

    /**
     * Returns whether this is the boxed type of a primitive type, such as {@code java.lang.Integer}; {@code Void}
     * excluded.
     *
     * @return {@code true} for {@code Boolean}, {@code Byte}, {@code Short}, {@code Integer}, {@code Long},
     *     {@code Character}, {@code Float} and {@code Double}
     */
    default boolean isBoxedPrimitive() {
        PrimitiveTypeName primitive = this instanceof ClassName name ? PrimitiveTypeName.unboxed(name) : null;
        return primitive != null && primitive.isPrimitive();
    }

    /**
     * Returns the primitive type of a boxed type, such as {@code int} for {@code Integer}, with the same annotations; a
     * primitive type unchanged. {@code Void} unboxes to {@code void}, as {@code void} boxes to {@code Void}.
     *
     * @return the primitive type
     * @throws UnsupportedOperationException if this is neither a primitive type nor a boxed one
     */
    default TypeName unbox() {
        if (this instanceof PrimitiveTypeName) {
            return this;
        }
        PrimitiveTypeName primitive = this instanceof ClassName name ? PrimitiveTypeName.unboxed(name) : null;
        if (primitive == null) {
            throw new UnsupportedOperationException("Not a boxed primitive type: " + this);
        }
        return primitive;
    }
}
