package be.imgn.gaia.processing.internal;

import be.imgn.gaia.core.collect.Sets;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementScanner14;
import javax.lang.model.util.Elements;

/**
 * The elements of a processing round annotated with an annotation whose type is annotated with one of some
 * meta-annotations. Only the meta-annotations of the annotation type itself count, not those of its meta-annotations.
 *
 * <p>The elements are those {@link javax.annotation.processing.RoundEnvironment#getElementsAnnotatedWith(TypeElement)}
 * looks at: the root elements of the round and the elements they declare, at any depth, with the annotations
 * {@link Elements#getAllAnnotationMirrors(Element)} gives, so that a class has the {@code @Inherited} annotations of
 * its superclasses. The annotations the container of a repeatable annotation wraps count too, as do the
 * meta-annotations such a container wraps on an annotation type.
 */
public final class MetaAnnotated {

    private final Elements elements;
    private final Set<String> metaAnnotations;
    private final Map<TypeElement, Set<String>> metaAnnotationsByType = new HashMap<>();
    private final Map<TypeElement, Set<Element>> elementsByType = new LinkedHashMap<>();

    /**
     * Finds the elements of a round annotated with an annotation that one of the meta-annotations annotates.
     *
     * @param elements the elements of the round
     * @param metaAnnotations the fully qualified names of the meta-annotation types
     * @param rootElements the root elements of the round
     */
    public MetaAnnotated(Elements elements, Set<String> metaAnnotations, Collection<? extends Element> rootElements) {
        this.elements = elements;
        this.metaAnnotations = Sets.setCopy(metaAnnotations);
        Scanner scanner = new Scanner();
        rootElements.forEach(root -> scanner.scan(root, elementsByType));
    }

    /**
     * Returns the elements found, by the type of the annotation that has a meta-annotation.
     *
     * @return the annotation types, in the order they are first met, each with the elements annotated with it, in the
     *     order of the scan: an element before the elements it declares
     */
    public Map<TypeElement, Set<Element>> elementsByType() {
        return elementsByType;
    }

    /**
     * Returns the meta-annotations that annotate an annotation type, among those looked for.
     *
     * @param annotationType the annotation type
     * @return the fully qualified names of the meta-annotation types; empty if it has none of them
     */
    public Set<String> of(TypeElement annotationType) {
        return metaAnnotationsByType.computeIfAbsent(
                annotationType,
                type -> PresentAnnotations.of(type).stream()
                        .map(MetaAnnotated::qualifiedName)
                        .filter(metaAnnotations::contains)
                        .collect(Sets.toSet()));
    }

    /**
     * Returns the annotations of an element that this scan looks at, of resolved types only.
     *
     * @param element the element, which may come from an earlier round
     * @return the annotations, inherited ones included, each container followed by the annotations it wraps
     */
    public List<AnnotationMirror> annotationsOf(Element element) {
        return PresentAnnotations.of(elements.getAllAnnotationMirrors(element));
    }

    /**
     * Returns the qualified name of the type of an annotation.
     *
     * @param annotation the annotation
     * @return the fully qualified name of its type
     */
    public static String qualifiedName(AnnotationMirror annotation) {
        return typeOf(annotation).getQualifiedName().toString();
    }

    /**
     * Returns the type of an annotation.
     *
     * @param annotation the annotation
     * @return the annotation type
     */
    public static TypeElement typeOf(AnnotationMirror annotation) {
        return (TypeElement) annotation.getAnnotationType().asElement();
    }

    /** Records each element with an annotation that has a meta-annotation, as javac scans a round's elements. */
    private final class Scanner
            extends ElementScanner14<Map<TypeElement, Set<Element>>, Map<TypeElement, Set<Element>>> {

        Scanner() {
            super(elementsByType);
        }

        @Override
        public Map<TypeElement, Set<Element>> scan(Element element, Map<TypeElement, Set<Element>> found) {
            for (AnnotationMirror annotation : annotationsOf(element)) {
                TypeElement type = typeOf(annotation);
                if (!of(type).isEmpty()) {
                    found.computeIfAbsent(type, ignored -> new LinkedHashSet<>())
                            .add(element);
                }
            }
            return super.scan(element, found);
        }

        // A package or a module is scanned for its own annotations only, not for the elements it encloses.
        @Override
        public Map<TypeElement, Set<Element>> visitPackage(
                PackageElement element, Map<TypeElement, Set<Element>> found) {
            return found;
        }

        @Override
        public Map<TypeElement, Set<Element>> visitModule(ModuleElement element, Map<TypeElement, Set<Element>> found) {
            return found;
        }
    }
}
