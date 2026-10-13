package be.imgn.gaia.processing.internal;

import be.imgn.gaia.processing.MoreTypes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

/**
 * The annotations present on an element, directly or indirectly: those written on it, and those that the container of a
 * repeatable annotation wraps, as {@link java.lang.reflect.AnnotatedElement} defines them. Annotations of a type javac
 * could not resolve are left out.
 */
public final class PresentAnnotations {

    private static final String REPEATABLE = "java.lang.annotation.Repeatable";
    private static final String VALUE = "value";

    private PresentAnnotations() {}

    /**
     * Returns the annotations directly or indirectly present on an element.
     *
     * @param element the annotated element
     * @return each annotation written on it, followed by those it wraps if it is the container of a repeatable
     *     annotation, in the order of {@link Element#getAnnotationMirrors()}
     */
    public static List<AnnotationMirror> of(Element element) {
        return of(element.getAnnotationMirrors());
    }

    /**
     * Returns annotations with those they wrap: each container of a repeatable annotation is followed by the
     * annotations it wraps. Annotations of a type javac could not resolve are left out.
     *
     * @param annotations the annotations, such as those {@code Elements.getAllAnnotationMirrors} gives, inherited ones
     *     included
     * @return each annotation, followed by those it wraps if it is the container of a repeatable annotation, in order
     */
    public static List<AnnotationMirror> of(List<? extends AnnotationMirror> annotations) {
        List<AnnotationMirror> present = new ArrayList<>();
        for (AnnotationMirror annotation : annotations) {
            if (annotation.getAnnotationType().getKind() == TypeKind.DECLARED) {
                present.add(annotation);
                present.addAll(contained(annotation));
            }
        }
        return present;
    }

    /**
     * Returns the annotations of a type directly or indirectly present on an element.
     *
     * @param element the annotated element
     * @param qualifiedName the fully qualified name of the annotation type
     * @return the annotations of that type, in the order of {@link #of(Element)}
     */
    public static List<AnnotationMirror> ofType(Element element, String qualifiedName) {
        return of(element).stream()
                .filter(annotation -> MoreTypes.isType(annotation.getAnnotationType(), qualifiedName))
                .toList();
    }

    /**
     * Returns the annotations directly or indirectly present on an element whose type is annotated with a
     * meta-annotation.
     *
     * @param element the annotated element
     * @param metaAnnotation the fully qualified name of the meta-annotation type
     * @param transitively whether the meta-annotation may also be on an annotation type that annotates the type, and so
     *     on, rather than on the type itself only
     * @return the annotations, in the order of {@link #of(Element)}
     */
    public static List<AnnotationMirror> annotatedWith(Element element, String metaAnnotation, boolean transitively) {
        return of(element).stream()
                .filter(annotation -> isAnnotatedWith(annotation, metaAnnotation, transitively))
                .toList();
    }

    private static boolean isAnnotatedWith(AnnotationMirror annotation, String metaAnnotation, boolean transitively) {
        TypeElement type = (TypeElement) annotation.getAnnotationType().asElement();
        Deque<TypeElement> pending = new ArrayDeque<>();
        pending.add(type);
        // Each annotation type is examined once: meta-annotations are often cyclic, as @Documented on itself.
        Set<TypeElement> visited = new HashSet<>();
        visited.add(type);
        while (!pending.isEmpty()) {
            for (AnnotationMirror meta : of(pending.remove())) {
                TypeElement metaType = (TypeElement) meta.getAnnotationType().asElement();
                if (metaType.getQualifiedName().contentEquals(metaAnnotation)) {
                    return true;
                }
                if (transitively && visited.add(metaType)) {
                    pending.add(metaType);
                }
            }
        }
        return false;
    }

    // The annotations a container wraps, if the annotation is the container of a repeatable annotation type.
    private static List<AnnotationMirror> contained(AnnotationMirror annotation) {
        TypeElement container = (TypeElement) annotation.getAnnotationType().asElement();
        Optional<ExecutableElement> value = ElementFilter.methodsIn(container.getEnclosedElements()).stream()
                .filter(method -> method.getSimpleName().contentEquals(VALUE))
                .findFirst();
        if (value.isEmpty() || !isContainerOf(container, value.get().getReturnType())) {
            return List.of();
        }
        AnnotationValue values = annotation.getElementValues().get(value.get());
        if (values == null) {
            values = value.get().getDefaultValue();
        }
        return values != null && values.getValue() instanceof List<?> list
                ? list.stream()
                        .map(item -> ((AnnotationValue) item).getValue())
                        .filter(AnnotationMirror.class::isInstance)
                        .map(AnnotationMirror.class::cast)
                        .toList()
                : List.of();
    }

    // Whether a value() of that type makes the type the container: it is an array of a type whose @Repeatable names it.
    private static boolean isContainerOf(TypeElement container, TypeMirror valueType) {
        if (valueType.getKind() != TypeKind.ARRAY) {
            return false;
        }
        return MoreTypes.asTypeElement(((ArrayType) valueType).getComponentType())
                .filter(repeatable -> repeatable.getKind() == ElementKind.ANNOTATION_TYPE)
                .stream()
                .flatMap(repeatable -> repeatable.getAnnotationMirrors().stream())
                .filter(meta -> MoreTypes.isType(meta.getAnnotationType(), REPEATABLE))
                .flatMap(meta -> meta.getElementValues().entrySet().stream()
                        .filter(entry -> entry.getKey().getSimpleName().contentEquals(VALUE))
                        .map(entry -> entry.getValue().getValue()))
                .anyMatch(named -> named instanceof TypeMirror type
                        && MoreTypes.asTypeElement(type)
                                .filter(container::equals)
                                .isPresent());
    }
}
