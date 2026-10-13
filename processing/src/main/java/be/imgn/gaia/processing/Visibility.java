package be.imgn.gaia.processing;

/**
 * How widely an element can be accessed, as its access modifier, or the lack of one, says: see
 * {@link MoreElements#visibility(javax.lang.model.element.Element)} and
 * {@link MoreElements#effectiveVisibility(javax.lang.model.element.Element)}.
 *
 * <p>The constants are ordered from the most restrictive to the most open, so that {@code a.compareTo(b) < 0} when
 * {@code a} is more restrictive than {@code b}.
 */
public enum Visibility {

    /**
     * Accessible within the top-level type that declares the element only. An element that can only be named within a
     * block or a declaration, such as a local class or a type parameter, is private too: code outside that top-level
     * type cannot access it either.
     */
    PRIVATE,

    /** Accessible within the package of the element, without an access modifier. */
    PACKAGE,

    /** Accessible within the package of the element, and from the subclasses of the type that declares it. */
    PROTECTED,

    /** Accessible wherever the element's module lets it be. */
    PUBLIC
}
