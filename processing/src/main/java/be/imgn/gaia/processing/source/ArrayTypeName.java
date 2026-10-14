package be.imgn.gaia.processing.source;

import java.util.List;
import java.util.Objects;
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
     * @throws IllegalArgumentException if the component type is {@code void} or a wildcard
     */
    public ArrayTypeName {
        Objects.requireNonNull(componentType, "componentType");
        annotations = Checks.elements(annotations, "annotations");
        if (componentType.equals(PrimitiveTypeName.VOID)) {
            throw new IllegalArgumentException("An array cannot have void elements");
        }
        if (componentType instanceof WildcardTypeName) {
            throw new IllegalArgumentException("An array cannot have wildcard elements: " + componentType);
        }
    }

    /**
     * Returns the name of an array type, without annotations.
     *
     * @param componentType the type of the elements
     * @return the name
     * @throws IllegalArgumentException if the component type is {@code void} or a wildcard
     */
    public static ArrayTypeName of(TypeName componentType) {
        Objects.requireNonNull(componentType, "componentType");
        return new ArrayTypeName(componentType, List.of());
    }

    @Override
    public ArrayTypeName annotated(Annotation... more) {
        return annotated(Checks.elements(more, "more"));
    }

    @Override
    public ArrayTypeName annotated(List<Annotation> more) {
        return new ArrayTypeName(
                componentType,
                Stream.concat(annotations.stream(), Checks.elements(more, "more").stream())
                        .toList());
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
