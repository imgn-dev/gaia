package be.imgn.gaia.processing.source;

import java.util.List;
import java.util.stream.Stream;

/**
 * The name of an array type, such as {@code String[]}.
 *
 * @param componentType the type of the elements
 * @param annotations the type annotations of the array type itself, written before its brackets, as in
 *     {@code String @Nullable []}
 */
public record ArrayTypeName(TypeName componentType, List<Annotation> annotations) implements TypeName {

    /**
     * Creates the name.
     *
     * @param componentType the type of the elements
     * @param annotations the type annotations of the array type itself
     * @throws IllegalArgumentException if the component type is {@code void}
     */
    public ArrayTypeName {
        annotations = List.copyOf(annotations);
        if (componentType.equals(PrimitiveTypeName.VOID)) {
            throw new IllegalArgumentException("An array cannot have void elements");
        }
    }

    /**
     * Returns the name of an array type, without annotations.
     *
     * @param componentType the type of the elements
     * @return the name
     * @throws IllegalArgumentException if the component type is {@code void}
     */
    public static ArrayTypeName of(TypeName componentType) {
        return new ArrayTypeName(componentType, List.of());
    }

    @Override
    public ArrayTypeName annotated(Annotation... more) {
        return annotated(List.of(more));
    }

    @Override
    public ArrayTypeName annotated(List<Annotation> more) {
        return new ArrayTypeName(
                componentType,
                Stream.concat(annotations.stream(), more.stream()).toList());
    }

    @Override
    public ArrayTypeName withoutAnnotations() {
        return of(componentType);
    }

    @Override
    public String toString() {
        return CodeWriter.standalone(this);
    }
}
