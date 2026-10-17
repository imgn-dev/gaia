package be.imgn.gaia.processing.source;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The name of a parameterized type, such as {@code Map<String, List<Integer>>}, or of an inner class whose enclosing
 * type has type arguments or annotations, such as {@code Outer<String>.Inner}.
 *
 * @param ownerType the enclosing type of an inner class, written before it as in {@code Outer<String>.Inner}; or
 *     {@code null}, and the raw type is written from its top-level type
 * @param rawType the type being parameterized, without annotations
 * @param typeArguments the type arguments; empty only for an inner class with an owner type
 * @param annotations the type annotations of the parameterized type
 */
public record ParameterizedTypeName(
        @Nullable TypeName ownerType, ClassName rawType, List<TypeName> typeArguments, List<Annotation> annotations)
        implements TypeName {

    /**
     * Creates the name.
     *
     * @param ownerType the enclosing type of an inner class, if it is written: a class or parameterized type
     * @param rawType the type being parameterized, without annotations
     * @param typeArguments the type arguments
     * @param annotations the type annotations of the parameterized type
     * @throws IllegalArgumentException if the raw type has annotations or is not nested in the owner type, there is no
     *     type argument and no owner type, or a type argument is primitive
     */
    public ParameterizedTypeName {
        requireNonNull(rawType, "rawType");
        typeArguments = Checks.elements(typeArguments, "typeArguments");
        annotations = Checks.elements(annotations, "annotations");
        if (!rawType.annotations().isEmpty()) {
            throw new IllegalArgumentException("Annotate the parameterized type rather than its raw type: " + rawType);
        }
        if (ownerType != null) {
            checkOwner(ownerType, rawType);
        } else if (typeArguments.isEmpty()) {
            throw new IllegalArgumentException("A parameterized type needs type arguments: " + rawType);
        }
        for (TypeName argument : typeArguments) {
            if (argument instanceof PrimitiveTypeName) {
                throw new IllegalArgumentException("A type argument cannot be primitive: " + argument);
            }
        }
    }

    /**
     * Returns the name of a parameterized type. The annotations of the raw type go to the parameterized type, where
     * they are written, as in {@code @Nullable List<String>}.
     *
     * @param rawType the type being parameterized
     * @param typeArguments the type arguments
     * @return the name
     * @throws IllegalArgumentException if there is no type argument, or one is primitive
     */
    public static ParameterizedTypeName of(ClassName rawType, TypeName... typeArguments) {
        requireNonNull(rawType, "rawType");
        return of(rawType, Checks.elements(typeArguments, "typeArguments"));
    }

    /**
     * Returns the name of a parameterized type. The annotations of the raw type go to the parameterized type, where
     * they are written, as in {@code @Nullable List<String>}.
     *
     * @param rawType the type being parameterized
     * @param typeArguments the type arguments
     * @return the name
     * @throws IllegalArgumentException if there is no type argument, or one is primitive
     */
    public static ParameterizedTypeName of(ClassName rawType, List<? extends TypeName> typeArguments) {
        requireNonNull(rawType, "rawType");
        return new ParameterizedTypeName(
                null,
                rawType.withoutAnnotations(),
                Checks.elements(typeArguments, "typeArguments"),
                rawType.annotations());
    }

    /**
     * Returns the name of a parameterized type, such as {@code Map<String, List<Integer>>} for {@code Map.class} and
     * the generic types of its arguments.
     *
     * @param rawType the class being parameterized
     * @param typeArguments the type arguments: classes, or generic types
     * @return the name
     * @throws IllegalArgumentException if there is no type argument, or one is primitive; the class is primitive or an
     *     array; or a class is local or anonymous
     */
    public static ParameterizedTypeName of(Class<?> rawType, java.lang.reflect.Type... typeArguments) {
        requireNonNull(rawType, "rawType");
        return of(
                ClassName.of(rawType),
                Checks.elements(typeArguments, "typeArguments").stream()
                        .map(TypeName::of)
                        .toList());
    }

    private static void checkOwner(TypeName ownerType, ClassName rawType) {
        ClassName ownerRaw = switch (ownerType) {
            case ClassName name -> name.withoutAnnotations();
            case ParameterizedTypeName parameterized -> parameterized.rawType();
            default ->
                throw new IllegalArgumentException("An owner type is a class or a parameterized type: " + ownerType);
        };
        if (!rawType.enclosing().map(ownerRaw::equals).orElse(false)) {
            throw new IllegalArgumentException(rawType + " is not directly nested in " + ownerRaw);
        }
    }

    /**
     * Returns the name of an inner class of this type, with this type as its owner, such as {@code Outer<String>.Inner}
     * or {@code Outer<String>.Inner<Integer>}.
     *
     * @param simpleName the simple name of the inner class
     * @param typeArguments its type arguments, if any
     * @return the name
     * @throws IllegalArgumentException if the simple name is not valid, or a type argument is primitive
     */
    public ParameterizedTypeName nested(String simpleName, TypeName... typeArguments) {
        requireNonNull(simpleName, "simpleName");
        List<TypeName> arguments = Checks.elements(typeArguments, "typeArguments");
        return new ParameterizedTypeName(this, rawType.nested(simpleName), arguments, List.of());
    }

    @Override
    public ParameterizedTypeName annotated(Annotation... more) {
        return annotated(Checks.elements(more, "more"));
    }

    @Override
    public ParameterizedTypeName annotated(List<Annotation> more) {
        return new ParameterizedTypeName(
                ownerType,
                rawType,
                typeArguments,
                Stream.concat(annotations.stream(), Checks.elements(more, "more").stream())
                        .toList());
    }

    @Override
    public ParameterizedTypeName withoutAnnotations() {
        return new ParameterizedTypeName(ownerType, rawType, typeArguments, List.of());
    }

    @Override
    public String toString() {
        return CodeWriter.standalone(this);
    }
}
