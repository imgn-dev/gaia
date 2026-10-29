package be.imgn.gaia.processing.internal;

import be.imgn.gaia.processing.MoreElements;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.QualifiedNameable;
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
 * unresolved type becomes resolved: an unresolved type has no package yet. Two overloads whose parameter types differ
 * only by their packages, as {@code m(java.util.Date)} and {@code m(java.sql.Date)}, therefore share a key, which finds
 * the first of them.
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
        while (!current.getKind().isDeclaredType() && current.getKind() != ElementKind.PACKAGE) {
            Element parent = parentOf(current);
            if (parent == null || current.getKind() == ElementKind.MODULE) {
                return Optional.empty();
            }
            path.addFirst(segment(current));
            current = parent;
        }
        String root = ((QualifiedNameable) current).getQualifiedName().toString();
        if (root.isEmpty() && current.getKind().isDeclaredType()) {
            return Optional.empty();
        }
        return Optional.of(new ElementKey(root, current.getKind() == ElementKind.PACKAGE, path));
    }

    private static @Nullable Element parentOf(Element element) {
        return element.getKind() == ElementKind.TYPE_PARAMETER
                ? MoreElements.asTypeParameter(element).getGenericElement()
                : element.getEnclosingElement();
    }

    private static String segment(Element element) {
        ElementKind kind = element.getKind();
        if (kind.isExecutable()) {
            return "method:"
                    + element.getSimpleName()
                    + MoreElements.asExecutable(element).getParameters().stream()
                            .map(parameter -> simpleName(parameter.asType()))
                            .collect(Collectors.joining(",", "(", ")"));
        }
        if (kind == ElementKind.TYPE_PARAMETER) {
            return "type-parameter:" + element.getSimpleName();
        }
        Element enclosing = element.getEnclosingElement();
        return kind == ElementKind.PARAMETER
                        && enclosing != null
                        && enclosing.getKind().isExecutable()
                ? "parameter:"
                        + MoreElements.asExecutable(enclosing).getParameters().indexOf(element)
                : kind + ":" + element.getSimpleName();
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
        if (element.getKind().isDeclaredType()) {
            children = Stream.concat(children, MoreElements.asType(element).getTypeParameters().stream());
        }
        if (element.getKind().isExecutable()) {
            ExecutableElement method = MoreElements.asExecutable(element);
            children = Stream.concat(children, method.getTypeParameters().stream());
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
