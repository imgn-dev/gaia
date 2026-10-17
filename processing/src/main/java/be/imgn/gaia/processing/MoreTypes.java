package be.imgn.gaia.processing;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;

/**
 * Checks and conversions on {@link TypeMirror}s that {@code javax.lang.model} does not provide.
 *
 * <p>The {@code as...} methods that return a type, rather than an {@code Optional}, cast a type whose kind the caller
 * knows, such as after a switch on its {@link TypeMirror#getKind() kind}. They go by that kind, as {@link TypeMirror}
 * recommends, and fail with a message naming the type and its kind, where a cast would throw a
 * {@link ClassCastException} naming javac's implementation classes.
 */
public final class MoreTypes {

    private MoreTypes() {}

    /**
     * Returns whether a type is the type of a class, ignoring type arguments: {@code List<String>} is
     * {@code List.class}.
     *
     * @param type the type to check
     * @param clazz the class, which may be primitive or an array
     * @return {@code true} if {@code type} is {@code clazz}
     * @throws IllegalArgumentException if {@code clazz} has no canonical name, as a local, anonymous or hidden class,
     *     or is an array of such a class
     */
    public static boolean isType(TypeMirror type, Class<?> clazz) {
        requireNonNull(type, "type");
        requireNonNull(clazz, "clazz");
        if (clazz.isArray()) {
            return type.getKind() == TypeKind.ARRAY
                    && isType(((ArrayType) type).getComponentType(), clazz.getComponentType());
        }
        if (clazz.isPrimitive()) {
            // TypeKind names match primitive class names, void included: INT and int, VOID and void.
            return type.getKind().name().equalsIgnoreCase(clazz.getName());
        }
        return isType(type, canonicalName(clazz));
    }

    /**
     * Returns whether a type is the declared type of a name, ignoring type arguments.
     *
     * @param type the type to check
     * @param qualifiedName the canonical name of a class, interface, enum, record or annotation type
     * @return {@code true} if {@code type} is a declared type of that name
     */
    public static boolean isType(TypeMirror type, String qualifiedName) {
        requireNonNull(type, "type");
        requireNonNull(qualifiedName, "qualifiedName");
        return asTypeElement(type)
                .map(element -> element.getQualifiedName().contentEquals(qualifiedName))
                .orElse(false);
    }

    /**
     * Returns a type as a declared type, if it is one.
     *
     * @param type the type
     * @return the declared type, or empty for a primitive, array, type variable or any other kind of type
     */
    public static Optional<DeclaredType> asDeclared(TypeMirror type) {
        requireNonNull(type, "type");
        return type.getKind() == TypeKind.DECLARED ? Optional.of((DeclaredType) type) : Optional.empty();
    }

    /**
     * Returns the element of a declared type.
     *
     * @param type the type
     * @return its class, interface, enum, record or annotation type, or empty if it is not a declared type
     */
    public static Optional<TypeElement> asTypeElement(TypeMirror type) {
        requireNonNull(type, "type");
        return asDeclared(type).map(declared -> (TypeElement) declared.asElement());
    }

    /**
     * Returns a type as an array type.
     *
     * @param type the type
     * @return the type, as an array type
     * @throws IllegalArgumentException if the type is not an array type
     */
    public static ArrayType asArray(TypeMirror type) {
        requireNonNull(type, "type");
        return cast(type, type.getKind() == TypeKind.ARRAY, ArrayType.class, "an array type");
    }

    /**
     * Returns a type as the type of a method, a constructor or an initializer, such as the one {@code Types.asMemberOf}
     * gives a method.
     *
     * @param type the type
     * @return the type, as an executable type
     * @throws IllegalArgumentException if the type is not an executable type
     */
    public static ExecutableType asExecutable(TypeMirror type) {
        requireNonNull(type, "type");
        return cast(type, type.getKind() == TypeKind.EXECUTABLE, ExecutableType.class, "an executable type");
    }

    /**
     * Returns a type as a primitive type, {@code void} excluded.
     *
     * @param type the type
     * @return the type, as a primitive type
     * @throws IllegalArgumentException if the type is not a primitive type
     */
    public static PrimitiveType asPrimitive(TypeMirror type) {
        requireNonNull(type, "type");
        return cast(type, type.getKind().isPrimitive(), PrimitiveType.class, "a primitive type");
    }

    /**
     * Returns a type as a type variable.
     *
     * @param type the type
     * @return the type, as a type variable
     * @throws IllegalArgumentException if the type is not a type variable
     */
    public static TypeVariable asTypeVariable(TypeMirror type) {
        requireNonNull(type, "type");
        return cast(type, type.getKind() == TypeKind.TYPEVAR, TypeVariable.class, "a type variable");
    }

    /**
     * Returns a type as a wildcard type argument.
     *
     * @param type the type
     * @return the type, as a wildcard
     * @throws IllegalArgumentException if the type is not a wildcard
     */
    public static WildcardType asWildcard(TypeMirror type) {
        requireNonNull(type, "type");
        return cast(type, type.getKind() == TypeKind.WILDCARD, WildcardType.class, "a wildcard");
    }

    /**
     * Returns a type as an intersection type, such as the bound of {@code T extends Number & Comparable<T>}.
     *
     * @param type the type
     * @return the type, as an intersection type
     * @throws IllegalArgumentException if the type is not an intersection type
     */
    public static IntersectionType asIntersection(TypeMirror type) {
        requireNonNull(type, "type");
        return cast(type, type.getKind() == TypeKind.INTERSECTION, IntersectionType.class, "an intersection type");
    }

    /**
     * Returns a type as a union type, the type of a multi-catch parameter such as {@code IOException | SQLException}.
     *
     * @param type the type
     * @return the type, as a union type
     * @throws IllegalArgumentException if the type is not a union type
     */
    public static UnionType asUnion(TypeMirror type) {
        requireNonNull(type, "type");
        return cast(type, type.getKind() == TypeKind.UNION, UnionType.class, "a union type");
    }

    /**
     * Returns whether casting an {@code Object} to a type is an unchecked cast: one the runtime cannot fully check, so
     * that javac warns about it. Generated code that casts to such a type needs a reason it is safe.
     *
     * <p>A cast is unchecked to a type variable; to a parameterized type with a type argument other than {@code ?} or
     * {@code ? extends Object}, such as {@code List<String>} or {@code List<? super Object>}; to an inner class of such
     * a type, such as {@code Outer<String>.Inner}; to an array of any of these; and to an intersection type with any of
     * these among its bounds. A cast to a raw type, to {@code List<?>} or to a primitive type is checked.
     *
     * @param type the type to cast to
     * @return {@code true} if casting an {@code Object} to {@code type} is unchecked
     */
    public static boolean isCastFromObjectUnchecked(TypeMirror type) {
        requireNonNull(type, "type");
        return switch (type.getKind()) {
            case TYPEVAR -> true;
            case ARRAY -> isCastFromObjectUnchecked(((ArrayType) type).getComponentType());
            case DECLARED -> {
                DeclaredType declared = (DeclaredType) type;
                yield !declared.getTypeArguments().stream().allMatch(MoreTypes::isUnboundedWildcard)
                        || isCastFromObjectUnchecked(declared.getEnclosingType());
            }
            case INTERSECTION ->
                ((IntersectionType) type).getBounds().stream().anyMatch(MoreTypes::isCastFromObjectUnchecked);
            default -> false;
        };
    }

    // ? and ? extends Object stand for any type argument, as the runtime does.
    private static boolean isUnboundedWildcard(TypeMirror type) {
        if (!(type instanceof WildcardType wildcard) || wildcard.getSuperBound() != null) {
            return false;
        }
        TypeMirror bound = wildcard.getExtendsBound();
        return bound == null || isType(bound, "java.lang.Object");
    }

    private static <T extends TypeMirror> T cast(TypeMirror type, boolean matches, Class<T> kind, String expected) {
        if (!matches) {
            throw new IllegalArgumentException(type.getKind() + " " + type + " is not " + expected);
        }
        return kind.cast(type);
    }

    // The name a class is known by in the compilation; a class without one has no type there.
    static String canonicalName(Class<?> clazz) {
        String name = clazz.getCanonicalName();
        if (name == null) {
            throw new IllegalArgumentException(
                    clazz.getName() + " has no canonical name, as a local, anonymous or hidden class");
        }
        return name;
    }
}
