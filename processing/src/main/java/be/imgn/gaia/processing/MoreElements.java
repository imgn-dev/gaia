package be.imgn.gaia.processing;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.processing.internal.Resolution;
import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.ModuleElement.RequiresDirective;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import org.jspecify.annotations.Nullable;

/**
 * Navigation, casts and checks on {@link Element}s that {@code javax.lang.model} does not provide.
 *
 * <p>The {@code as...} methods cast an element whose kind the caller knows, such as one annotated with an annotation
 * that only targets types. They go by its {@link Element#getKind() kind}, as {@link Element} recommends, and fail with
 * a message naming the element and its kind, where a cast would throw a {@link ClassCastException} naming javac's
 * implementation classes. To test an element whose kind is not known, check its kind instead.
 */
public final class MoreElements {

    private MoreElements() {}

    /**
     * Returns the package of an element.
     *
     * @param element the element
     * @return the package it is in, or itself if it is a package
     * @throws IllegalArgumentException if the element is not in a package, such as a module
     */
    public static PackageElement packageOf(Element element) {
        requireNonNull(element, "element");
        for (Element current = element; current != null; current = current.getEnclosingElement()) {
            if (current.getKind() == ElementKind.PACKAGE) {
                return asPackage(current);
            }
        }
        throw new IllegalArgumentException(element + " is not in a package");
    }

    /**
     * Returns the type an element is declared in.
     *
     * @param element the element
     * @return the nearest enclosing type, not counting the element itself, or empty for a top-level type, a package or
     *     a module
     */
    public static Optional<TypeElement> enclosingType(Element element) {
        requireNonNull(element, "element");
        for (Element current = element.getEnclosingElement();
                current != null;
                current = current.getEnclosingElement()) {
            Optional<TypeElement> found = asTypeElement(current);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the top-level type an element is declared in.
     *
     * @param element the element
     * @return the outermost enclosing type, or the element itself if it is a top-level type; empty for a package or a
     *     module
     */
    public static Optional<TypeElement> topLevelType(Element element) {
        requireNonNull(element, "element");
        Optional<TypeElement> top = asTypeElement(element);
        for (Optional<TypeElement> enclosing = enclosingType(element);
                enclosing.isPresent();
                enclosing = enclosingType(enclosing.get())) {
            top = enclosing;
        }
        return top;
    }

    /**
     * Returns whether an element is annotated directly with an annotation.
     *
     * @param element the element
     * @param qualifiedName the fully qualified name of the annotation type
     * @return {@code true} if the element has the annotation
     */
    public static boolean isAnnotatedWith(Element element, String qualifiedName) {
        requireNonNull(element, "element");
        requireNonNull(qualifiedName, "qualifiedName");
        return element.getAnnotationMirrors().stream()
                .anyMatch(mirror -> MoreTypes.isType(mirror.getAnnotationType(), qualifiedName));
    }

    /**
     * Returns whether an element is annotated directly with an annotation, known by its class. The class only names the
     * annotation: the processor needs it on its class path, but the annotation does not need to be retained at run
     * time.
     *
     * @param element the element
     * @param annotation the annotation type
     * @return {@code true} if the element has the annotation
     * @throws IllegalArgumentException if {@code annotation} has no canonical name, as a local or hidden annotation
     *     type
     */
    public static boolean isAnnotatedWith(Element element, Class<? extends Annotation> annotation) {
        requireNonNull(element, "element");
        requireNonNull(annotation, "annotation");
        return isAnnotatedWith(element, MoreTypes.canonicalName(annotation));
    }

    /**
     * Returns the first type javac could not resolve in the signature of an element, typically because another
     * processor has not generated it yet. A processor that needs the element's types to be complete can wait for a
     * later round while there is one, as {@link StepProcessor} does for the elements its steps defer.
     *
     * <p>The signature covers the element's annotations and their values, class literals included, and the types it
     * declares: the superclass, interfaces, type parameters and members of a type; the return, parameter and thrown
     * types and the type parameters of a method; the type of a field, parameter or record component; the bounds of a
     * type parameter. Method bodies and field initializers are not covered.
     *
     * @param element the element
     * @return the name of the unresolved type as written in the source, without type annotations or type arguments,
     *     such as {@code Missing} for {@code List<@Nullable Missing>} or {@code com.example.Missing} when qualified,
     *     but only the name javac gives an unresolved class literal in an annotation, such as {@code <error>}; or empty
     *     if every type is resolved
     */
    public static Optional<String> firstUnresolvedType(Element element) {
        requireNonNull(element, "element");
        return Resolution.firstUnresolved(element);
    }

    /**
     * Returns an element as a class, interface, enum, record or annotation type.
     *
     * @param element the element
     * @return the element, as a type
     * @throws IllegalArgumentException if the element is not a class, an interface, an enum, a record or an annotation
     *     type
     */
    public static TypeElement asType(Element element) {
        requireNonNull(element, "element");
        return cast(element, element.getKind().isDeclaredType(), TypeElement.class, "a type");
    }

    /**
     * Returns an element as a class, interface, enum, record or annotation type, if it is one: the kind-checked
     * counterpart of {@code element instanceof TypeElement type}.
     *
     * @param element the element
     * @return the element, as a type; empty if it is another kind of element
     */
    public static Optional<TypeElement> asTypeElement(Element element) {
        requireNonNull(element, "element");
        return element.getKind().isDeclaredType() ? Optional.of((TypeElement) element) : Optional.empty();
    }

    /**
     * Returns an element as a method, a constructor, or a static or instance initializer.
     *
     * @param element the element
     * @return the element, as an executable
     * @throws IllegalArgumentException if the element is not a method, a constructor or an initializer
     */
    public static ExecutableElement asExecutable(Element element) {
        requireNonNull(element, "element");
        return cast(element, element.getKind().isExecutable(), ExecutableElement.class, "an executable");
    }

    /**
     * Returns an element as a variable: a field, an enum constant, a parameter, a local variable, an exception
     * parameter, a resource variable or a binding variable. A record component is not a variable: see
     * {@link #asRecordComponent(Element)}.
     *
     * @param element the element
     * @return the element, as a variable
     * @throws IllegalArgumentException if the element is not a variable
     */
    public static VariableElement asVariable(Element element) {
        requireNonNull(element, "element");
        return cast(element, element.getKind().isVariable(), VariableElement.class, "a variable");
    }

    /**
     * Returns an element as a package.
     *
     * @param element the element
     * @return the element, as a package
     * @throws IllegalArgumentException if the element is not a package
     */
    public static PackageElement asPackage(Element element) {
        requireNonNull(element, "element");
        return cast(element, element.getKind() == ElementKind.PACKAGE, PackageElement.class, "a package");
    }

    /**
     * Returns an element as a type parameter, of a type, a method or a constructor.
     *
     * @param element the element
     * @return the element, as a type parameter
     * @throws IllegalArgumentException if the element is not a type parameter
     */
    public static TypeParameterElement asTypeParameter(Element element) {
        requireNonNull(element, "element");
        return cast(
                element,
                element.getKind() == ElementKind.TYPE_PARAMETER,
                TypeParameterElement.class,
                "a type parameter");
    }

    /**
     * Returns an element as a record component.
     *
     * @param element the element
     * @return the element, as a record component
     * @throws IllegalArgumentException if the element is not a record component
     */
    public static RecordComponentElement asRecordComponent(Element element) {
        requireNonNull(element, "element");
        return cast(
                element,
                element.getKind() == ElementKind.RECORD_COMPONENT,
                RecordComponentElement.class,
                "a record component");
    }

    /**
     * Returns an element as a module, named or unnamed.
     *
     * @param element the element
     * @return the element, as a module
     * @throws IllegalArgumentException if the element is not a module
     */
    public static ModuleElement asModule(Element element) {
        requireNonNull(element, "element");
        return cast(element, element.getKind() == ElementKind.MODULE, ModuleElement.class, "a module");
    }

    /**
     * Returns whether an element is a record.
     *
     * @param element the element
     * @return {@code true} if the element is a record class
     */
    public static boolean isRecord(Element element) {
        requireNonNull(element, "element");
        return element.getKind() == ElementKind.RECORD;
    }

    /**
     * Returns whether an element is a top-level class, interface, enum, record or annotation type: one declared in a
     * compilation unit, not in another type or in a block. Each type is either top-level, an
     * {@linkplain #isInnerClass(Element) inner class}, or {@linkplain #isStaticNested(Element) static and nested}.
     *
     * @param element the element
     * @return {@code true} if the element is a top-level type; {@code false} if it is not a type
     */
    public static boolean isTopLevel(Element element) {
        requireNonNull(element, "element");
        return element.getKind().isDeclaredType() && ((TypeElement) element).getNestingKind() == NestingKind.TOP_LEVEL;
    }

    /**
     * Returns whether an element is an inner class: a nested class that is neither explicitly nor implicitly static, as
     * JLS 8.1.3 defines it. An inner member class needs an instance of the class enclosing it to be created.
     *
     * <p>A member class is inner unless it is declared {@code static}, or is a member of an interface or an annotation
     * type, which makes it implicitly static. An enum, a record, an interface or an annotation type is never inner: it
     * is implicitly static wherever it is nested.
     *
     * <p>A local class is inner, and so is an anonymous class, the body of an enum constant included, as the language
     * says: even in a static context, such as a static method or a static field initializer, where it has no enclosing
     * instance. A local enum, record or interface is static.
     *
     * <p>This goes by the {@code static} modifier, which {@link Element#getModifiers()} includes when it is implicit,
     * for types read from class files too.
     *
     * @param element the element
     * @return {@code true} if the element is an inner class; {@code false} if it is not a type
     */
    public static boolean isInnerClass(Element element) {
        requireNonNull(element, "element");
        return element.getKind().isDeclaredType() && isInner((TypeElement) element);
    }

    /**
     * Returns whether an element is a nested class, interface, enum, record or annotation type that is static,
     * explicitly or implicitly: a nested type that is not an {@linkplain #isInnerClass(Element) inner class}. A member
     * type created without an instance of the class enclosing it is one, and so is a local enum, record or interface.
     *
     * @param element the element
     * @return {@code true} if the element is a static nested type; {@code false} if it is not a type, or is top-level
     */
    public static boolean isStaticNested(Element element) {
        requireNonNull(element, "element");
        return element.getKind().isDeclaredType()
                && ((TypeElement) element).getNestingKind() != NestingKind.TOP_LEVEL
                && !isInner((TypeElement) element);
    }

    /**
     * Returns the visibility an element is declared with: its access modifier, or the access the language gives it
     * without one. It does not account for the types that enclose the element: {@link #effectiveVisibility(Element)}
     * does.
     *
     * <p>Without an access modifier, a member of an interface or an annotation type is {@link Visibility#PUBLIC}, a
     * constructor of an enum is {@link Visibility#PRIVATE}, and anything else is {@link Visibility#PACKAGE}. An enum
     * constant is public, and so is a record component, which is read through its accessor, the private field that
     * holds it being an element of its own. The constructor javac adds to a class that declares none, and the canonical
     * constructor it adds to a record, have the visibility of their class, as the language says.
     *
     * <p>An element that can only be named within a block or a declaration is {@link Visibility#PRIVATE}: a local or
     * anonymous class, a type parameter, a parameter, a local variable, or an initializer. A package or a module is
     * {@link Visibility#PUBLIC}: the language does not restrict access to them, and modules restrict access to their
     * packages, as {@link #isExportedTo(Element, ModuleElement)} checks.
     *
     * @param element the element
     * @return its own visibility
     */
    public static Visibility visibility(Element element) {
        requireNonNull(element, "element");
        return switch (element.getKind()) {
            case PACKAGE, MODULE, ENUM_CONSTANT, RECORD_COMPONENT -> Visibility.PUBLIC;
            case TYPE_PARAMETER,
                    PARAMETER,
                    LOCAL_VARIABLE,
                    EXCEPTION_PARAMETER,
                    RESOURCE_VARIABLE,
                    BINDING_VARIABLE,
                    INSTANCE_INIT,
                    STATIC_INIT -> Visibility.PRIVATE;
            default -> declaredVisibility(element);
        };
    }

    /**
     * Returns the visibility an element has, given the types it is declared in: the most restrictive of its own
     * {@linkplain #visibility(Element) visibility} and that of each type, method or other element that encloses it, up
     * to its package. A public method of a private nested class is private, and one of a package-private class is
     * package-private. Anything declared in a local or anonymous class is private.
     *
     * <p>This is the visibility of the element where it is declared. A member can be accessed through a subtype of the
     * type that declares it, which may be more visible: a public method declared in a package-private class, and
     * inherited by a public class, is package-private here, yet accessible from any package through that public class.
     *
     * @param element the element
     * @return its effective visibility
     */
    public static Visibility effectiveVisibility(Element element) {
        requireNonNull(element, "element");
        Visibility effective = visibility(element);
        for (Element current = element.getEnclosingElement();
                current != null && isBelowPackage(current);
                current = current.getEnclosingElement()) {
            Visibility enclosing = visibility(current);
            if (enclosing.compareTo(effective) < 0) {
                effective = enclosing;
            }
        }
        return effective;
    }

    /**
     * Returns whether code in a package can access an element, by the rules of JLS 6.6, from a class that does not
     * extend the type that declares the element: generated code, unless it is such a subclass, which
     * {@link #isAccessible(Element, String, TypeElement)} checks.
     *
     * <p>From the element's own package, anything but a {@linkplain Visibility#PRIVATE private} element is accessible,
     * given its {@linkplain #effectiveVisibility(Element) effective visibility}. From another package, the element and
     * every type that encloses it must be public: a protected member is not accessible. A private element is never
     * accessible: the language allows it only within the top-level type that declares it, and generated code is in a
     * top-level type of its own.
     *
     * <p>This judges the element where it is declared, and leaves modules out. A member inherited by a more visible
     * type, such as a public method of a package-private class inherited by a public class, is accessible through that
     * type although this returns {@code false} for it: see {@link #effectiveVisibility(Element)}. Whether the element's
     * module lets the code's module access it is {@link #isExportedTo(Element, ModuleElement)}.
     *
     * @param element the element to access, such as a type, a constructor, a method or a field
     * @param packageName the qualified name of the package of the code, empty for the unnamed package
     * @return {@code true} if code in that package can access the element; {@code true} for a package or a module
     */
    public static boolean isAccessible(Element element, String packageName) {
        requireNonNull(element, "element");
        requireNonNull(packageName, "packageName");
        return accessible(element, packageName, null);
    }

    /**
     * Returns whether a class in a package that extends another class can access an element, as JLS 6.6 says: the
     * generated subclass of a processor, for instance. Beyond what {@link #isAccessible(Element, String)} allows, it
     * can access from another package a protected member of its superclass, or of any class its superclass extends,
     * provided the types that enclose that member are accessible too.
     *
     * <p>Being accessible, a protected element can still only be used in some ways from another package, which this
     * does not check (JLS 6.6.2). A protected instance field or method must be accessed through {@code this},
     * {@code super}, or an expression whose type is the subclass or one of its own subclasses, not through any instance
     * of the superclass. A protected constructor can be invoked as {@code super(...)} from a constructor of the
     * subclass, or to create an anonymous subclass, but not with {@code new} otherwise.
     *
     * @param element the element to access, such as a type, a constructor, a method or a field
     * @param packageName the qualified name of the package of the subclass, empty for the unnamed package
     * @param superclass the class the subclass extends, {@code Object} if it extends no other
     * @return {@code true} if that subclass can access the element; {@code true} for a package or a module
     * @throws IllegalArgumentException if {@code superclass} is an interface or an annotation type
     */
    public static boolean isAccessible(Element element, String packageName, TypeElement superclass) {
        requireNonNull(element, "element");
        requireNonNull(packageName, "packageName");
        requireNonNull(superclass, "superclass");
        if (superclass.getKind().isInterface()) {
            throw new IllegalArgumentException(superclass + " is an interface, not a class");
        }
        return accessible(element, packageName, superclass);
    }

    /**
     * Returns whether the module of an element lets code in another module access it: the code's module reads the
     * element's module, and the element's module exports the element's package to it. Within a module, everything is
     * exported.
     *
     * <p>This goes by the module declarations, as JLS 7.7 says. A named module reads the modules it requires, and those
     * they require transitively, {@code java.base} included; {@code requires static} counts, as it does at compile
     * time. The unnamed module reads every module, but no named module reads it; it exports all its packages, as an
     * automatic module does. What command-line options such as {@code --add-reads} and {@code --add-exports} change is
     * not seen. Without modules, as when compiling for Java 8, everything is accessible.
     *
     * <p>Generated code usually lands in the module of the elements it is generated for, which
     * {@code context.elements().getModuleOf(element)} gives. The language rules within the module, such as access to a
     * package-private type, are {@link #isAccessible(Element, String)}.
     *
     * @param element the element to access: a package, or an element in a package
     * @param module the module of the code that accesses it
     * @return {@code true} if {@code module} can access the package of {@code element}
     * @throws IllegalArgumentException if the element is a module
     */
    public static boolean isExportedTo(Element element, ModuleElement module) {
        requireNonNull(element, "element");
        requireNonNull(module, "module");
        PackageElement found = packageOf(element);
        Element owner = found.getEnclosingElement();
        if (owner == null || owner.getKind() != ElementKind.MODULE || owner.equals(module)) {
            return true;
        }
        return reads(module, asModule(owner)) && exports(asModule(owner), found, module);
    }

    private static <T extends Element> T cast(Element element, boolean matches, Class<T> type, String expected) {
        if (!matches) {
            throw new IllegalArgumentException(describe(element) + " is not " + expected);
        }
        return type.cast(element);
    }

    // As "METHOD run() in example.Task": the kind, the element, and what declares it, unless its qualified name says.
    private static String describe(Element element) {
        ElementKind kind = element.getKind();
        Element enclosing = element.getEnclosingElement();
        boolean qualified = kind == ElementKind.PACKAGE
                || kind == ElementKind.MODULE
                || (kind.isDeclaredType() && !isLocal(element));
        if (qualified || enclosing == null) {
            return kind + " " + element;
        }
        // javac names an initializer after its class: it is named by its kind alone.
        String described = isInitializer(element) ? kind.toString() : kind + " " + element;
        return described + " in " + (isInitializer(enclosing) ? describe(enclosing) : enclosing);
    }

    private static boolean isInitializer(Element element) {
        return element.getKind() == ElementKind.STATIC_INIT || element.getKind() == ElementKind.INSTANCE_INIT;
    }

    private static boolean isInner(TypeElement type) {
        return switch (type.getNestingKind()) {
            case TOP_LEVEL -> false;
            case ANONYMOUS -> true;
            case LOCAL, MEMBER -> !type.getModifiers().contains(Modifier.STATIC);
        };
    }

    private static Visibility declaredVisibility(Element element) {
        Set<Modifier> modifiers = element.getModifiers();
        if (modifiers.contains(Modifier.PRIVATE) || isLocal(element)) {
            return Visibility.PRIVATE;
        }
        Element enclosing = element.getEnclosingElement();
        if (modifiers.contains(Modifier.PUBLIC)
                || (enclosing != null && enclosing.getKind().isInterface())) {
            return Visibility.PUBLIC;
        }
        if (modifiers.contains(Modifier.PROTECTED)) {
            return Visibility.PROTECTED;
        }
        return element.getKind() == ElementKind.CONSTRUCTOR
                        && enclosing != null
                        && enclosing.getKind() == ElementKind.ENUM
                ? Visibility.PRIVATE
                : Visibility.PACKAGE;
    }

    private static boolean isLocal(Element element) {
        return asTypeElement(element)
                .map(type ->
                        type.getNestingKind() == NestingKind.LOCAL || type.getNestingKind() == NestingKind.ANONYMOUS)
                .orElse(false);
    }

    // Whether an element is below its package: a type, a member, a type parameter, a local declaration.
    private static boolean isBelowPackage(Element element) {
        return element.getKind() != ElementKind.PACKAGE && element.getKind() != ElementKind.MODULE;
    }

    private static boolean accessible(Element element, String packageName, @Nullable TypeElement superclass) {
        if (isIn(element, packageName)) {
            return effectiveVisibility(element) != Visibility.PRIVATE;
        }
        // From another package, each element up to the top-level type must be public, or protected and inherited.
        for (Element current = element;
                current != null && isBelowPackage(current);
                current = current.getEnclosingElement()) {
            Visibility visibility = visibility(current);
            Optional<TypeElement> declaring = directlyIn(current);
            boolean permitted = visibility == Visibility.PUBLIC
                    || (visibility == Visibility.PROTECTED
                            && superclass != null
                            && declaring.isPresent()
                            && isSubclass(superclass, declaring.get()));
            if (!permitted) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIn(Element element, String packageName) {
        for (Element current = element; current != null; current = current.getEnclosingElement()) {
            if (current.getKind() == ElementKind.PACKAGE) {
                return asPackage(current).getQualifiedName().contentEquals(packageName);
            }
        }
        return false;
    }

    // The type an element is declared in directly, if it is declared in a type.
    private static Optional<TypeElement> directlyIn(Element element) {
        Element enclosing = element.getEnclosingElement();
        return enclosing == null ? Optional.empty() : asTypeElement(enclosing);
    }

    private static boolean isSubclass(TypeElement type, TypeElement superclass) {
        for (TypeElement current = type;
                current != null;
                current = MoreTypes.asTypeElement(current.getSuperclass()).orElse(null)) {
            if (current.equals(superclass)) {
                return true;
            }
        }
        return false;
    }

    private static boolean reads(ModuleElement reader, ModuleElement read) {
        if (reader.isUnnamed()) {
            return true;
        }
        // A module reads what it requires, and what those modules require transitively, and so on.
        Deque<ModuleElement> pending = new ArrayDeque<>();
        ElementFilter.requiresIn(reader.getDirectives()).forEach(requires -> pending.push(requires.getDependency()));
        Set<ModuleElement> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            ModuleElement current = pending.pop();
            if (current.equals(read)) {
                return true;
            }
            if (visited.add(current)) {
                ElementFilter.requiresIn(current.getDirectives()).stream()
                        .filter(RequiresDirective::isTransitive)
                        .forEach(requires -> pending.push(requires.getDependency()));
            }
        }
        return false;
    }

    private static boolean exports(ModuleElement module, PackageElement exported, ModuleElement to) {
        return module.isUnnamed()
                || ElementFilter.exportsIn(module.getDirectives()).stream()
                        .anyMatch(exports -> exports.getPackage().equals(exported)
                                && (exports.getTargetModules() == null
                                        || exports.getTargetModules().contains(to)));
    }
}
