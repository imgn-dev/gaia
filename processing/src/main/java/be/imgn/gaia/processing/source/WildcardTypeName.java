package be.imgn.gaia.processing.source;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The name of a wildcard type argument: {@code ?}, {@code ? extends T} or {@code ? super T}.
 *
 * @param upperBound the bound of {@code ? extends}, if any; never {@code Object}, which a wildcard does not need
 * @param lowerBound the bound of {@code ? super}, if any
 * @param annotations the type annotations
 */
public record WildcardTypeName(
        @Nullable TypeName upperBound, @Nullable TypeName lowerBound, List<Annotation> annotations)
        implements TypeName {

    /**
     * Creates the name.
     *
     * @param upperBound the bound of {@code ? extends}, if any; {@code Object} without annotations is dropped, so that
     *     {@code ? extends Object} is {@code ?}
     * @param lowerBound the bound of {@code ? super}, if any
     * @param annotations the type annotations
     * @throws IllegalArgumentException if there are both bounds, or a bound is primitive or a wildcard
     */
    public WildcardTypeName {
        annotations = Checks.elements(annotations, "annotations");
        if (ClassName.OBJECT.equals(upperBound)) {
            upperBound = null;
        }
        if (upperBound != null && lowerBound != null) {
            throw new IllegalArgumentException("A wildcard has at most one bound");
        }
        if (upperBound instanceof PrimitiveTypeName || lowerBound instanceof PrimitiveTypeName) {
            throw new IllegalArgumentException("A wildcard bound cannot be primitive");
        }
        if (upperBound instanceof WildcardTypeName || lowerBound instanceof WildcardTypeName) {
            throw new IllegalArgumentException("A wildcard bound cannot be a wildcard");
        }
    }

    /**
     * Returns {@code ?}.
     *
     * @return the unbounded wildcard
     */
    public static WildcardTypeName unbounded() {
        return new WildcardTypeName(null, null, List.of());
    }

    /**
     * Returns {@code ? extends bound}.
     *
     * @param bound the upper bound
     * @return the wildcard
     * @throws IllegalArgumentException if the bound is primitive or a wildcard
     */
    public static WildcardTypeName subtypeOf(TypeName bound) {
        return new WildcardTypeName(requireNonNull(bound, "bound"), null, List.of());
    }

    /**
     * Returns {@code ? super bound}.
     *
     * @param bound the lower bound
     * @return the wildcard
     * @throws IllegalArgumentException if the bound is primitive or a wildcard
     */
    public static WildcardTypeName supertypeOf(TypeName bound) {
        return new WildcardTypeName(null, requireNonNull(bound, "bound"), List.of());
    }

    @Override
    public WildcardTypeName annotated(Annotation... more) {
        return annotated(Checks.elements(more, "more"));
    }

    @Override
    public WildcardTypeName annotated(List<Annotation> more) {
        return new WildcardTypeName(
                upperBound,
                lowerBound,
                Stream.concat(annotations.stream(), Checks.elements(more, "more").stream())
                        .toList());
    }

    @Override
    public WildcardTypeName withoutAnnotations() {
        return new WildcardTypeName(upperBound, lowerBound, List.of());
    }

    @Override
    public String toString() {
        return CodeWriter.standalone(this);
    }
}
