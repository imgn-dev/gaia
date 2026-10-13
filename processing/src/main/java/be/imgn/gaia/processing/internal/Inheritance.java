package be.imgn.gaia.processing.internal;

import be.imgn.gaia.processing.MoreElements;
import be.imgn.gaia.processing.MoreTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Works out the methods of a type as the compiler sees them: those it declares and those it inherits, following JLS
 * 8.4.8 for classes and 9.4.1 for interfaces.
 *
 * <p>Results are cached by type for the life of the instance, which should not outlive a processing round.
 */
public final class Inheritance {

    private final Elements elements;
    private final Types types;
    private final Map<TypeElement, List<ExecutableElement>> members = new HashMap<>();

    /**
     * Creates an instance over the utilities of a processing environment.
     *
     * @param elements the element utilities
     * @param types the type utilities
     */
    public Inheritance(Elements elements, Types types) {
        this.elements = elements;
        this.types = types;
    }

    /**
     * Returns the methods of a type, declared and inherited, without the ones overridden or hidden. An interface also
     * has the public methods of {@code Object} it does not override, last.
     *
     * @param type the type
     * @return its methods: declared ones in declaration order, then the inherited ones of the superclass and of each
     *     direct superinterface in turn, each in this order
     */
    public List<ExecutableElement> methods(TypeElement type) {
        List<ExecutableElement> methods = new ArrayList<>(members(type));
        if (type.getKind().isInterface()) {
            for (ExecutableElement method : publicObjectMethods()) {
                if (methods.stream().noneMatch(member -> isSubsignature(type, member, method))) {
                    methods.add(method);
                }
            }
        }
        return List.copyOf(methods);
    }

    /**
     * Returns the abstract methods of a type that an implementation must implement: of an interface, those a public
     * method of {@code Object} does not implement; of several with override-equivalent signatures, one only, at the
     * place of the first.
     *
     * @param type the type
     * @return the abstract methods, in the order of {@link #methods(TypeElement)}
     */
    public List<ExecutableElement> abstractMethods(TypeElement type) {
        List<ExecutableElement> candidates = members(type).stream()
                .filter(method -> method.getModifiers().contains(Modifier.ABSTRACT))
                .filter(method -> !type.getKind().isInterface() || !isImplementedByObject(type, method))
                .toList();
        List<ExecutableElement> abstracts = new ArrayList<>();
        Set<ExecutableElement> grouped = new HashSet<>();
        for (ExecutableElement method : candidates) {
            if (grouped.contains(method)) {
                continue;
            }
            List<ExecutableElement> group = candidates.stream()
                    .filter(other -> isOverrideEquivalent(type, method, other))
                    .toList();
            grouped.addAll(group);
            abstracts.add(implementable(type, group));
        }
        return List.copyOf(abstracts);
    }

    // The members of a type, as JLS 8.4.8 and 9.4.1 define them, without the methods of Object for an interface.
    private List<ExecutableElement> members(TypeElement type) {
        List<ExecutableElement> cached = members.get(type);
        if (cached != null) {
            return cached;
        }
        List<ExecutableElement> declared = ElementFilter.methodsIn(type.getEnclosedElements());
        Optional<TypeElement> superclass = MoreTypes.asTypeElement(type.getSuperclass());
        List<TypeElement> supertypes = new ArrayList<>();
        superclass.ifPresent(supertypes::add);
        type.getInterfaces().stream()
                .map(MoreTypes::asTypeElement)
                .flatMap(Optional::stream)
                .forEach(supertypes::add);
        Set<ExecutableElement> concrete = new HashSet<>();
        superclass.ifPresent(parent -> members(parent).stream()
                .filter(method -> isConcrete(method) && isInherited(type, declared, parent, method))
                .forEach(concrete::add));
        Set<ExecutableElement> result = new LinkedHashSet<>(declared);
        for (TypeElement supertype : supertypes) {
            for (ExecutableElement method : members(supertype)) {
                if (concrete.contains(method)
                        || (!isConcrete(method)
                                && isInherited(type, declared, supertype, method)
                                && !isImplementedBy(type, concrete, method)
                                && !isOverriddenInAnother(supertypes, supertype, method))) {
                    result.add(method);
                }
            }
        }
        List<ExecutableElement> computed = List.copyOf(result);
        members.put(type, computed);
        return computed;
    }

    // Whether a type inherits a member of a direct supertype, as far as access and its own declarations go.
    private boolean isInherited(
            TypeElement type, List<ExecutableElement> declared, TypeElement supertype, ExecutableElement method) {
        Set<Modifier> modifiers = method.getModifiers();
        if (modifiers.contains(Modifier.PRIVATE)
                || (supertype.getKind().isInterface() && modifiers.contains(Modifier.STATIC))) {
            return false;
        }
        boolean packageAccess = !modifiers.contains(Modifier.PUBLIC)
                && !modifiers.contains(Modifier.PROTECTED)
                && !isInInterface(method);
        if (packageAccess && !MoreElements.packageOf(method).equals(MoreElements.packageOf(type))) {
            return false;
        }
        return declared.stream().noneMatch(own -> isSubsignature(type, own, method));
    }

    private boolean isImplementedBy(TypeElement type, Set<ExecutableElement> concrete, ExecutableElement method) {
        return concrete.stream().anyMatch(implementation -> isSubsignature(type, implementation, method));
    }

    // Whether a member of another direct supertype overrides the method, from that supertype.
    private boolean isOverriddenInAnother(
            List<TypeElement> supertypes, TypeElement supertype, ExecutableElement method) {
        for (TypeElement other : supertypes) {
            if (!other.equals(supertype)) {
                for (ExecutableElement overrider : members(other)) {
                    if (!overrider.equals(method)
                            && overrider.getSimpleName().equals(method.getSimpleName())
                            && elements.overrides(overrider, method, other)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isImplementedByObject(TypeElement type, ExecutableElement method) {
        return publicObjectMethods().stream().anyMatch(objectMethod -> isSubsignature(type, method, objectMethod));
    }

    private List<ExecutableElement> publicObjectMethods() {
        TypeElement object = elements.getTypeElement("java.lang.Object");
        return ElementFilter.methodsIn(object.getEnclosedElements()).stream()
                .filter(method -> method.getModifiers().contains(Modifier.PUBLIC)
                        && !method.getModifiers().contains(Modifier.STATIC))
                .toList();
    }

    private boolean isOverrideEquivalent(TypeElement type, ExecutableElement left, ExecutableElement right) {
        return isSubsignature(type, left, right) || isSubsignature(type, right, left);
    }

    // Whether the signature of one method is a subsignature of another's, both as members of the type.
    private boolean isSubsignature(TypeElement type, ExecutableElement method, ExecutableElement other) {
        return method.getSimpleName().equals(other.getSimpleName())
                && types.isSubsignature(memberType(type, method), memberType(type, other));
    }

    // Of override-equivalent abstract methods, the first whose return and thrown types suit all the others, or failing
    // that whose return type does, or failing that the first.
    private ExecutableElement implementable(TypeElement type, List<ExecutableElement> group) {
        return group.stream()
                .filter(method -> group.stream()
                        .allMatch(other ->
                                isReturnSubstitutable(type, method, other) && isThrowsCompatible(type, method, other)))
                .findFirst()
                .or(() -> group.stream()
                        .filter(method -> group.stream().allMatch(other -> isReturnSubstitutable(type, method, other)))
                        .findFirst())
                .orElse(group.getFirst());
    }

    private boolean isReturnSubstitutable(TypeElement type, ExecutableElement method, ExecutableElement other) {
        TypeMirror returned = memberType(type, method).getReturnType();
        TypeMirror otherReturned = memberType(type, other).getReturnType();
        if (returned.getKind().isPrimitive() || returned.getKind() == TypeKind.VOID) {
            return returned.getKind() == otherReturned.getKind();
        }
        return !otherReturned.getKind().isPrimitive() && types.isAssignable(returned, otherReturned);
    }

    // Whether each exception the method throws is unchecked or allowed by the other method.
    private boolean isThrowsCompatible(TypeElement type, ExecutableElement method, ExecutableElement other) {
        List<? extends TypeMirror> allowed = memberType(type, other).getThrownTypes();
        return memberType(type, method).getThrownTypes().stream()
                .allMatch(thrown -> isUnchecked(thrown)
                        || allowed.stream().anyMatch(declared -> types.isSubtype(thrown, declared)));
    }

    private boolean isUnchecked(TypeMirror thrown) {
        return types.isSubtype(
                        thrown,
                        elements.getTypeElement("java.lang.RuntimeException").asType())
                || types.isSubtype(
                        thrown, elements.getTypeElement("java.lang.Error").asType());
    }

    private ExecutableType memberType(TypeElement type, ExecutableElement method) {
        return (ExecutableType) types.asMemberOf((DeclaredType) type.asType(), method);
    }

    private static boolean isConcrete(ExecutableElement method) {
        return !method.getModifiers().contains(Modifier.ABSTRACT)
                && !method.getModifiers().contains(Modifier.DEFAULT);
    }

    private static boolean isInInterface(ExecutableElement method) {
        return MoreElements.enclosingType(method)
                .map(type -> type.getKind().isInterface())
                .orElse(false);
    }
}
