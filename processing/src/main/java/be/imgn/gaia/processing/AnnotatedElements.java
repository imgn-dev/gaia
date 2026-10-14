package be.imgn.gaia.processing;

import be.imgn.gaia.core.collect.Sets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.element.Element;

/**
 * The elements a {@link Step} gets in a round, by annotation, and by meta-annotation for the step's
 * {@linkplain Step#metaAnnotations() meta-annotations}.
 *
 * <pre>{@code
 * for (Element mapper : elements.annotatedWith("com.example.Mapper")) {
 *     ...
 * }
 * }</pre>
 *
 * <p>An element found by a meta-annotation is also listed under the annotation it is annotated with, the one whose type
 * has the meta-annotation: with {@code @Qualifier @interface Primary}, an element annotated with {@code @Primary} is in
 * both {@code withAnnotationsAnnotatedWith("javax.inject.Qualifier")} and {@code annotatedWith("com.example.Primary")}.
 */
public final class AnnotatedElements {

    private final Map<String, Set<Element>> byAnnotation;
    private final Map<String, Set<Element>> byMetaAnnotation;

    AnnotatedElements(
            Map<String, ? extends Set<Element>> byAnnotation, Map<String, ? extends Set<Element>> byMetaAnnotation) {
        this.byAnnotation = copy(byAnnotation);
        this.byMetaAnnotation = copy(byMetaAnnotation);
    }

    private static Map<String, Set<Element>> copy(Map<String, ? extends Set<Element>> elements) {
        Map<String, Set<Element>> copy = new LinkedHashMap<>();
        elements.forEach((name, annotated) -> copy.put(name, Sets.setCopy(annotated)));
        return copy;
    }

    /**
     * Returns the elements annotated with an annotation.
     *
     * @param qualifiedName the fully qualified name of the annotation type
     * @return the elements, in source order; empty if there is none
     */
    public Set<Element> annotatedWith(String qualifiedName) {
        Objects.requireNonNull(qualifiedName, "qualifiedName");
        return byAnnotation.getOrDefault(qualifiedName, Set.of());
    }

    /**
     * Returns the elements annotated with an annotation whose type is annotated with a meta-annotation of the step:
     * with {@code javax.inject.Qualifier}, the elements annotated with a qualifier, such as {@code @Named("primary")}.
     * Only the meta-annotations of the annotation type itself count, as for
     * {@link ProcessingContext#annotationsAnnotatedWith(Element, String)}, which reads those annotations.
     *
     * @param metaAnnotation the fully qualified name of the meta-annotation type, one of {@link Step#metaAnnotations()}
     * @return the elements, by annotation and then in source order; empty if there is none, or if the step does not
     *     declare that meta-annotation
     */
    public Set<Element> withAnnotationsAnnotatedWith(String metaAnnotation) {
        Objects.requireNonNull(metaAnnotation, "metaAnnotation");
        return byMetaAnnotation.getOrDefault(metaAnnotation, Set.of());
    }

    /**
     * Returns every element, whatever its annotation, each once.
     *
     * @return the elements, by annotation and then in source order
     */
    public Set<Element> all() {
        return byAnnotation.values().stream().flatMap(Set::stream).collect(Sets.toSet());
    }

    /**
     * Returns the annotations at least one element has: those of the step, and those found by its meta-annotations.
     *
     * @return the fully qualified names of the annotation types
     */
    public Set<String> annotations() {
        return Sets.setCopy(byAnnotation.keySet());
    }

    /**
     * Returns whether there is no element.
     *
     * @return {@code true} if no element is annotated
     */
    public boolean isEmpty() {
        return byAnnotation.isEmpty();
    }

    @Override
    public String toString() {
        return byAnnotation.toString();
    }
}
