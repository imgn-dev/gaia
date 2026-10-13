package be.imgn.gaia.processing.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.Parameterizable;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import org.jspecify.annotations.Nullable;

/**
 * Identifies an element across processing rounds, where {@link Element} instances are not valid: the qualified name of
 * its nearest enclosing type or package, and the path from there to the element.
 *
 * <p>A method is identified by its name and the simple names of its parameter types, which stay the same when an
 * unresolved type becomes resolved.
 *
 * @param root the qualified name of the nearest enclosing type or package
 * @param inPackage whether the root is a package rather than a type
 * @param path the segments from the root to the element; empty for the root itself
 */
public record ElementKey(String root, boolean inPackage, List<String> path) {

    /**
     * Creates a key. The path is copied.
     *
     * @param root the qualified name of the nearest enclosing type or package
     * @param inPackage whether the root is a package rather than a type
     * @param path the segments from the root to the element
     */
    public ElementKey {
        path = List.copyOf(path);
    }

    /**
     * Returns the key of an element.
     *
     * @param element the element
     * @return its key, or empty if it cannot be found again by name, such as a module or a member of a local class
     */
    public static Optional<ElementKey> of(Element element) {
        List<String> path = new ArrayList<>();
        Element current = element;
        while (!(current instanceof TypeElement) && !(current instanceof PackageElement)) {
            Element parent = parentOf(current);
            if (parent == null || current.getKind() == ElementKind.MODULE) {
                return Optional.empty();
            }
            path.addFirst(segment(current));
            current = parent;
        }
        String root = ((QualifiedNameable) current).getQualifiedName().toString();
        if (root.isEmpty() && current instanceof TypeElement) {
            return Optional.empty();
        }
        return Optional.of(new ElementKey(root, current instanceof PackageElement, path));
    }

    private static @Nullable Element parentOf(Element element) {
        return element instanceof TypeParameterElement parameter
                ? parameter.getGenericElement()
                : element.getEnclosingElement();
    }

    private static String segment(Element element) {
        return switch (element) {
            case ExecutableElement method ->
                "method:"
                        + method.getSimpleName()
                        + method.getParameters().stream()
                                .map(parameter -> simpleName(parameter.asType()))
                                .collect(Collectors.joining(",", "(", ")"));
            case TypeParameterElement parameter -> "type-parameter:" + parameter.getSimpleName();
            default ->
                element.getKind() == ElementKind.PARAMETER
                                && element.getEnclosingElement() instanceof ExecutableElement method
                        ? "parameter:" + method.getParameters().indexOf(element)
                        : element.getKind() + ":" + element.getSimpleName();
        };
    }

    private static String simpleName(TypeMirror type) {
        return switch (type.getKind()) {
            case DECLARED, ERROR ->
                lastSegment(((DeclaredType) type).asElement().getSimpleName().toString());
            case ARRAY -> simpleName(((ArrayType) type).getComponentType()) + "[]";
            case TYPEVAR -> ((TypeVariable) type).asElement().getSimpleName().toString();
            default -> type.toString();
        };
    }

    private static String lastSegment(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private static Stream<? extends Element> childrenOf(Element element) {
        Stream<? extends Element> children = element.getEnclosedElements().stream();
        if (element instanceof Parameterizable parameterizable) {
            children = Stream.concat(children, parameterizable.getTypeParameters().stream());
        }
        if (element instanceof ExecutableElement method) {
            children = Stream.concat(children, method.getParameters().stream());
        }
        return children;
    }

    /**
     * Finds the element this key identifies, in the current round.
     *
     * @param elements the elements of the current round
     * @return the element, or {@code null} if it no longer exists
     */
    public @Nullable Element resolve(Elements elements) {
        Element current = inPackage ? elements.getPackageElement(root) : elements.getTypeElement(root);
        for (String segment : path) {
            if (current == null) {
                return null;
            }
            current = childrenOf(current)
                    .filter(child -> segment.equals(segment(child)))
                    .findFirst()
                    .orElse(null);
        }
        return current;
    }
}
