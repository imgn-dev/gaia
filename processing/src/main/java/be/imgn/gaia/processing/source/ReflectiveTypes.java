package be.imgn.gaia.processing.source;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;

/**
 * Names reflective types for {@link TypeName#of(Type)}, in a class of its own so that the {@code java.lang.reflect}
 * types can be imported: their names clash with {@link be.imgn.gaia.processing.source.Type} and with those of
 * {@code javax.lang.model}.
 */
final class ReflectiveTypes {

    private ReflectiveTypes() {}

    static TypeName name(Type type) {
        return switch (type) {
            case Class<?> raw -> TypeName.of(raw);
            case ParameterizedType parameterized -> parameterized(parameterized);
            case GenericArrayType array -> ArrayTypeName.of(name(array.getGenericComponentType()));
            case TypeVariable<?> variable -> TypeVariableName.of(variable.getName());
            case WildcardType wildcard -> wildcard(wildcard);
            default ->
                throw new IllegalArgumentException(
                        "Cannot name a type of " + type.getClass().getName() + ": " + type);
        };
    }

    // The enclosing type of an inner class is kept when it says more than its name: Outer<String>.Inner.
    private static TypeName parameterized(ParameterizedType type) {
        ClassName raw = ClassName.of((Class<?>) type.getRawType());
        List<TypeName> arguments = Arrays.stream(type.getActualTypeArguments())
                .map(ReflectiveTypes::name)
                .toList();
        Type enclosing = type.getOwnerType();
        if (enclosing != null && name(enclosing) instanceof ParameterizedTypeName owner) {
            return new ParameterizedTypeName(owner, raw, arguments, List.of());
        }
        return arguments.isEmpty() ? raw : ParameterizedTypeName.of(raw, arguments);
    }

    private static TypeName wildcard(WildcardType type) {
        Type[] lowerBounds = type.getLowerBounds();
        return lowerBounds.length == 0
                ? WildcardTypeName.subtypeOf(name(type.getUpperBounds()[0]))
                : WildcardTypeName.supertypeOf(name(lowerBounds[0]));
    }
}
