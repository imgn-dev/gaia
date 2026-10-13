package be.imgn.gaia.processing;

import java.util.Optional;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/** Checks and conversions on {@link TypeMirror}s that {@code javax.lang.model} does not provide. */
public final class MoreTypes {

    private MoreTypes() {}

    /**
     * Returns whether a type is the type of a class, ignoring type arguments: {@code List<String>} is
     * {@code List.class}.
     *
     * @param type the type to check
     * @param clazz the class, which may be primitive or an array
     * @return {@code true} if {@code type} is {@code clazz}
     */
    public static boolean isType(TypeMirror type, Class<?> clazz) {
        if (clazz.isArray()) {
            return type.getKind() == TypeKind.ARRAY
                    && isType(((ArrayType) type).getComponentType(), clazz.getComponentType());
        }
        if (clazz.isPrimitive()) {
            // TypeKind names match primitive class names, void included: INT and int, VOID and void.
            return type.getKind().name().equalsIgnoreCase(clazz.getName());
        }
        return isType(type, clazz.getCanonicalName());
    }

    /**
     * Returns whether a type is the declared type of a name, ignoring type arguments.
     *
     * @param type the type to check
     * @param qualifiedName the canonical name of a class, interface, enum, record or annotation type
     * @return {@code true} if {@code type} is a declared type of that name
     */
    public static boolean isType(TypeMirror type, String qualifiedName) {
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
        return type.getKind() == TypeKind.DECLARED ? Optional.of((DeclaredType) type) : Optional.empty();
    }

    /**
     * Returns the element of a declared type.
     *
     * @param type the type
     * @return its class, interface, enum, record or annotation type, or empty if it is not a declared type
     */
    public static Optional<TypeElement> asTypeElement(TypeMirror type) {
        return asDeclared(type).map(declared -> (TypeElement) declared.asElement());
    }
}
