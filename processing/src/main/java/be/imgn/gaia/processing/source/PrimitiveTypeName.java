package be.imgn.gaia.processing.source;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The name of a primitive type, or {@code void}.
 *
 * @param keyword the keyword, such as {@code int} or {@code void}
 * @param annotations the type annotations
 */
public record PrimitiveTypeName(String keyword, List<Annotation> annotations) implements TypeName {

    /** {@code void}. */
    public static final PrimitiveTypeName VOID = of("void");

    /** {@code boolean}. */
    public static final PrimitiveTypeName BOOLEAN = of("boolean");

    /** {@code byte}. */
    public static final PrimitiveTypeName BYTE = of("byte");

    /** {@code short}. */
    public static final PrimitiveTypeName SHORT = of("short");

    /** {@code int}. */
    public static final PrimitiveTypeName INT = of("int");

    /** {@code long}. */
    public static final PrimitiveTypeName LONG = of("long");

    /** {@code char}. */
    public static final PrimitiveTypeName CHAR = of("char");

    /** {@code float}. */
    public static final PrimitiveTypeName FLOAT = of("float");

    /** {@code double}. */
    public static final PrimitiveTypeName DOUBLE = of("double");

    private static final List<String> KEYWORDS =
            List.of("boolean", "byte", "short", "int", "long", "char", "float", "double", "void");

    /**
     * Creates the name.
     *
     * @param keyword the keyword
     * @param annotations the type annotations
     * @throws IllegalArgumentException if the keyword is not a primitive type or {@code void}
     */
    public PrimitiveTypeName {
        if (boxName(keyword) == null) {
            throw new IllegalArgumentException("Not a primitive type: " + keyword);
        }
        annotations = List.copyOf(annotations);
    }

    /**
     * Returns the name of a primitive type, without annotations.
     *
     * @param keyword the keyword, such as {@code int}
     * @return the name
     * @throws IllegalArgumentException if the keyword is not a primitive type or {@code void}
     */
    public static PrimitiveTypeName of(String keyword) {
        return new PrimitiveTypeName(keyword, List.of());
    }

    private static @Nullable String boxName(String keyword) {
        return switch (keyword) {
            case "boolean" -> "Boolean";
            case "byte" -> "Byte";
            case "short" -> "Short";
            case "int" -> "Integer";
            case "long" -> "Long";
            case "char" -> "Character";
            case "float" -> "Float";
            case "double" -> "Double";
            case "void" -> "Void";
            default -> null;
        };
    }

    static @Nullable PrimitiveTypeName unboxed(ClassName name) {
        if (!name.packageName().equals("java.lang") || name.simpleNames().size() != 1) {
            return null;
        }
        for (String keyword : KEYWORDS) {
            if (name.simpleName().equals(boxName(keyword))) {
                return new PrimitiveTypeName(keyword, name.annotations());
            }
        }
        return null;
    }

    @Override
    public PrimitiveTypeName annotated(Annotation... more) {
        return annotated(List.of(more));
    }

    @Override
    public PrimitiveTypeName annotated(List<Annotation> more) {
        return new PrimitiveTypeName(
                keyword, Stream.concat(annotations.stream(), more.stream()).toList());
    }

    @Override
    public PrimitiveTypeName withoutAnnotations() {
        return of(keyword);
    }

    ClassName boxed() {
        return ClassName.of("java.lang", Objects.requireNonNull(boxName(keyword)))
                .annotated(annotations);
    }

    @Override
    public String toString() {
        return CodeWriter.standalone(this);
    }
}
