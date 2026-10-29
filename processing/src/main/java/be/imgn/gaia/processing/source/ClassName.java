package be.imgn.gaia.processing.source;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.text.Splitter;
import be.imgn.gaia.processing.MoreElements;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * The name of a class, interface, enum, record or annotation type, possibly nested in others.
 *
 * <p>Names are ordered by package, then by their simple names from the top-level type, then by their type annotations
 * as they are written, so that {@code java.lang.String} comes before {@code java.lang.@Nullable String}.
 *
 * @param packageName the package, empty for the unnamed package
 * @param simpleNames the simple names from the top-level type to this one, such as {@code [Map, Entry]}
 * @param annotations the type annotations
 */
public record ClassName(String packageName, List<String> simpleNames, List<Annotation> annotations)
        implements TypeName, Comparable<ClassName> {

    /** {@code java.lang.Object}. */
    public static final ClassName OBJECT = of("java.lang", "Object");

    // Ends the names of types, packages and methods in generated names.
    private static final String MARKER = "$$";

    /**
     * Creates the name.
     *
     * @param packageName the package, empty for the unnamed package
     * @param simpleNames the simple names from the top-level type to this one
     * @param annotations the type annotations
     * @throws IllegalArgumentException if a name is not a valid identifier, or there is no simple name
     */
    public ClassName {
        requireNonNull(packageName, "packageName");
        simpleNames = Checks.elements(simpleNames, "simpleNames");
        annotations = Checks.elements(annotations, "annotations");
        if (simpleNames.isEmpty()) {
            throw new IllegalArgumentException("A class name needs a simple name");
        }
        if (!packageName.isEmpty() && !SourceVersion.isName(packageName)) {
            throw new IllegalArgumentException("Not a package name: " + packageName);
        }
        for (String simpleName : simpleNames) {
            if (!Checks.isTypeName(simpleName)) {
                throw new IllegalArgumentException("Not a simple name: " + simpleName);
            }
        }
    }

    /**
     * Returns the name of a class.
     *
     * @param packageName the package, empty for the unnamed package
     * @param simpleName the simple name of the top-level type
     * @param nestedNames the simple names of the nested types, from the outermost
     * @return the name
     * @throws IllegalArgumentException if a name is not valid
     */
    public static ClassName of(String packageName, String simpleName, String... nestedNames) {
        requireNonNull(packageName, "packageName");
        requireNonNull(simpleName, "simpleName");
        List<String> simpleNames = new ArrayList<>();
        simpleNames.add(simpleName);
        simpleNames.addAll(Checks.elements(nestedNames, "nestedNames"));
        return new ClassName(packageName, simpleNames, List.of());
    }

    /**
     * Returns the name of a class.
     *
     * @param type the class
     * @return its name
     * @throws IllegalArgumentException if the class is primitive, an array, local or anonymous
     */
    public static ClassName of(Class<?> type) {
        requireNonNull(type, "type");
        if (type.isPrimitive() || type.isArray() || type.getCanonicalName() == null) {
            throw new IllegalArgumentException("Not a named class: " + type);
        }
        List<String> simpleNames = new ArrayList<>();
        for (Class<?> current = type; current != null; current = current.getEnclosingClass()) {
            simpleNames.addFirst(current.getSimpleName());
        }
        return new ClassName(type.getPackageName(), simpleNames, List.of());
    }

    /**
     * Returns the name of a type element.
     *
     * @param type the type
     * @return its name
     * @throws IllegalArgumentException if the type is local or anonymous
     */
    public static ClassName of(TypeElement type) {
        requireNonNull(type, "type");
        List<String> simpleNames = new ArrayList<>();
        Element current = type;
        while (current != null && current.getKind().isDeclaredType()) {
            simpleNames.addFirst(current.getSimpleName().toString());
            current = current.getEnclosingElement();
        }
        if (current == null || current.getKind() != ElementKind.PACKAGE) {
            throw new IllegalArgumentException("Not a named type: " + type + " is local or anonymous");
        }
        return new ClassName(MoreElements.packageOf(type).getQualifiedName().toString(), simpleNames, List.of());
    }

    /**
     * Returns the name of a class from its canonical name, guessing where the package ends: at the first name that
     * starts with an upper-case letter, as conventional names do. The names of nested types must start with one too.
     *
     * @param canonicalName the canonical name, such as {@code java.util.Map.Entry}
     * @return the name
     * @throws IllegalArgumentException if no part of the name starts with an upper-case letter, a part is empty, a part
     *     after the first upper-case one does not start with an upper-case letter, or a part is not a valid identifier
     */
    public static ClassName bestGuess(String canonicalName) {
        requireNonNull(canonicalName, "canonicalName");
        List<String> parts = Splitter.on('.').split(canonicalName);
        int first = 0;
        while (first < parts.size() && !startsWithUpperCase(parts.get(first))) {
            if (parts.get(first).isEmpty()) {
                throw new IllegalArgumentException("Not a canonical name: " + canonicalName);
            }
            first++;
        }
        if (first == parts.size()) {
            throw new IllegalArgumentException("Cannot tell the package from the class in " + canonicalName);
        }
        List<String> simpleNames = parts.subList(first, parts.size());
        for (String simpleName : simpleNames) {
            if (!startsWithUpperCase(simpleName)) {
                throw new IllegalArgumentException(
                        "Cannot tell a nested class from a member in " + canonicalName + ": " + simpleName);
            }
        }
        return new ClassName(String.join(".", parts.subList(0, first)), simpleNames, List.of());
    }

    private static boolean startsWithUpperCase(String part) {
        return !part.isEmpty() && Character.isUpperCase(part.codePointAt(0));
    }

    /**
     * Returns the simple name of this type.
     *
     * @return the last simple name, such as {@code Entry}
     */
    public String simpleName() {
        return simpleNames.getLast();
    }

    /**
     * Returns the canonical name of this type.
     *
     * @return the name, such as {@code java.util.Map.Entry}
     */
    public String canonicalName() {
        String names = String.join(".", simpleNames);
        return packageName.isEmpty() ? names : packageName + "." + names;
    }

    /**
     * Returns the binary name of this type, as {@link Class#forName(String)} expects it.
     *
     * @return the name, such as {@code java.util.Map$Entry}
     */
    public String reflectionName() {
        String names = String.join("$", simpleNames);
        return packageName.isEmpty() ? names : packageName + "." + names;
    }

    /**
     * Returns the top-level type this type is, or is nested in, without annotations.
     *
     * @return the top-level type, such as {@code java.util.Map} for {@code java.util.Map.Entry}
     */
    public ClassName topLevel() {
        return new ClassName(packageName, List.of(simpleNames.getFirst()), List.of());
    }

    /**
     * Returns the type this type is directly nested in, without annotations.
     *
     * @return the enclosing type, or empty for a top-level type
     */
    public Optional<ClassName> enclosing() {
        return simpleNames.size() == 1
                ? Optional.empty()
                : Optional.of(new ClassName(packageName, simpleNames.subList(0, simpleNames.size() - 1), List.of()));
    }

    /**
     * Returns the name of a type nested in this one. The annotations of this name are not kept: to write an annotated
     * enclosing type, as in {@code @A Outer.Inner}, give the inner class an {@link ParameterizedTypeName#ownerType()
     * owner type}.
     *
     * @param simpleName the simple name of the nested type
     * @return its name, without annotations
     * @throws IllegalArgumentException if the simple name is not valid
     */
    public ClassName nested(String simpleName) {
        requireNonNull(simpleName, "simpleName");
        return new ClassName(
                packageName,
                Stream.concat(simpleNames.stream(), Stream.of(simpleName)).toList(),
                List.of());
    }

    /**
     * Returns the name of a type in the same package as this one, and nested in the same type, if any.
     *
     * @param simpleName the simple name of the other type
     * @return its name, without annotations
     * @throws IllegalArgumentException if the simple name is not valid
     */
    public ClassName peer(String simpleName) {
        requireNonNull(simpleName, "simpleName");
        List<String> names = new ArrayList<>(simpleNames);
        names.set(names.size() - 1, simpleName);
        return new ClassName(packageName, names, List.of());
    }

    /**
     * Returns the name of a top-level type generated for this one: in the same package, {@code $}, the simple names
     * from the top-level type to this one joined by {@code $}, then {@code $$} and the kind of the generated type. For
     * {@code com.example.Outer.Inner} and the kind {@code Mapper}, it is {@code com.example.$Outer$Inner$$Mapper}; for
     * the top-level {@code com.example.Outer}, it is {@code com.example.$Outer$$Mapper}.
     *
     * <p>All the generated names of {@code ClassName}, this one, {@link #generatedName(String, String)},
     * {@link #generatedName(ExecutableElement, String)} and {@link #generatedNameIn(String, String)}, follow one
     * scheme, in which each type, package, method and kind gets its own name. {@code $$} ends the names of types,
     * packages and methods, and the kind, which has no dollar sign, comes last. A dollar sign within a name is written
     * {@code $0}: {@code $0} is one of the name's, {@code $} followed by another digit numbers an overloaded method,
     * and any other one joins two names, as no name starts with a digit. {@code A$B.C} gives {@code $A$0B$C$$Mapper}
     * where the nested {@code A.B.C} gives {@code $A$B$C$$Mapper}. A type that is not generated can still have the
     * name: whether a type has it already is for
     * {@link be.imgn.gaia.processing.GeneratedSources#isAvailable(ClassName)} to tell.
     *
     * @param kind what the generated type is for this one, such as {@code Mapper}: a Java identifier without a dollar
     *     sign, which may be a keyword
     * @return the name of the generated type, without annotations
     * @throws IllegalArgumentException if the kind is not an identifier without a dollar sign: if it is empty, starts
     *     with a digit, or has a dollar sign or a character that cannot be in an identifier, such as a dot or a space
     * @since 0.1.2
     */
    public ClassName generatedName(String kind) {
        checkKind(kind);
        return generated(packageName, "$" + join(simpleNames) + MARKER + kind);
    }

    /**
     * Returns the name of a type generated for a whole package: in that package, {@code $$} and the kind of the
     * generated type, such as {@code com.example.$$Mappers} for the package {@code com.example} and the kind
     * {@code Mappers}. It follows the scheme of {@link #generatedName(String)}.
     *
     * @param packageName the package, empty for the unnamed package
     * @param kind what the generated type is for the package, such as {@code Mappers}: a Java identifier without a
     *     dollar sign, which may be a keyword
     * @return the name of the generated type
     * @throws IllegalArgumentException if the package name is not valid, or the kind is not an identifier without a
     *     dollar sign
     * @since 0.1.2
     */
    public static ClassName generatedName(String packageName, String kind) {
        requireNonNull(packageName, "packageName");
        checkKind(kind);
        return generated(packageName, MARKER + kind);
    }

    /**
     * Returns the name of a top-level type generated for a method: in the package of its type, {@code $}, the simple
     * names from the top-level type to the method's joined by {@code $}, then {@code $$}, the name of the method,
     * {@code $$} and the kind of the generated type. For the method {@code create} of {@code com.example.Outer.Inner}
     * and the kind {@code Factory}, it is {@code com.example.$Outer$Inner$$create$$Factory}. It follows the scheme of
     * {@link #generatedName(String)}.
     *
     * <p>An overloaded method, one whose type declares other methods with its name, has its number after its name: its
     * position among them in the order they are declared, from 1, such as {@code $Outer$$create$2$$Factory} for the
     * second {@code create} method. Declaring an overload changes the name of a method that had none.
     *
     * @param method the method
     * @param kind what the generated type is for the method, such as {@code Factory}: a Java identifier without a
     *     dollar sign, which may be a keyword
     * @return the name of the generated type
     * @throws IllegalArgumentException if the element is not a method, such as a constructor, its type is local or
     *     anonymous, or the kind is not an identifier without a dollar sign
     * @since 0.1.2
     */
    public static ClassName generatedName(ExecutableElement method, String kind) {
        requireNonNull(method, "method");
        checkKind(kind);
        if (method.getKind() != ElementKind.METHOD) {
            throw new IllegalArgumentException("Not a method: " + method);
        }
        var type = (TypeElement) requireNonNull(method.getEnclosingElement());
        var name = method.getSimpleName();
        var overloads = type.getEnclosedElements().stream()
                .filter(member -> member.getKind() == ElementKind.METHOD
                        && member.getSimpleName().contentEquals(name))
                .toList();
        int number = overloads.size() == 1 ? 0 : overloads.indexOf(method) + 1;
        return of(type).generatedMethodName(name.toString(), number, kind);
    }

    /**
     * Returns the name of a type generated for this one in another package, such as an implementation of an annotation
     * type that belongs with the code using it: in that package, {@code $$}, the names of this type's package joined by
     * {@code $}, {@code $$}, the simple names from the top-level type to this one joined by {@code $}, then {@code $$}
     * and the kind of the generated type. For {@code com.example.Outer.Named}, the package {@code org.app} and the kind
     * {@code Impl}, it is {@code org.app.$$com$example$$Outer$Named$$Impl}. It follows the scheme of
     * {@link #generatedName(String)}.
     *
     * @param packageName the package of the generated type, empty for the unnamed package
     * @param kind what the generated type is for this one, such as {@code Impl}: a Java identifier without a dollar
     *     sign, which may be a keyword
     * @return the name of the generated type
     * @throws IllegalArgumentException if the package name is not valid, or the kind is not an identifier without a
     *     dollar sign
     * @since 0.1.2
     */
    public ClassName generatedNameIn(String packageName, String kind) {
        requireNonNull(packageName, "packageName");
        checkKind(kind);
        List<String> packageNames =
                this.packageName.isEmpty() ? List.of() : Splitter.on('.').split(this.packageName);
        return generated(packageName, MARKER + join(packageNames) + MARKER + join(simpleNames) + MARKER + kind);
    }

    // The name generated for a method of this type, numbered from 1 if it is overloaded, 0 otherwise.
    ClassName generatedMethodName(String method, int number, String kind) {
        checkKind(kind);
        String overload = number == 0 ? "" : "$" + number;
        return generated(packageName, "$" + join(simpleNames) + MARKER + escape(method) + overload + MARKER + kind);
    }

    /**
     * Returns this type parameterized with type arguments, such as {@code List<String>}.
     *
     * @param typeArguments the type arguments
     * @return the parameterized type, with the annotations of this name
     * @throws IllegalArgumentException if there is no type argument, or one is primitive
     */
    public ParameterizedTypeName parameterized(TypeName... typeArguments) {
        requireNonNull(typeArguments, "typeArguments");
        return ParameterizedTypeName.of(this, typeArguments);
    }

    @Override
    public ClassName annotated(Annotation... more) {
        return annotated(Checks.elements(more, "more"));
    }

    @Override
    public ClassName annotated(List<Annotation> more) {
        return new ClassName(
                packageName,
                simpleNames,
                Stream.concat(annotations.stream(), Checks.elements(more, "more").stream())
                        .toList());
    }

    @Override
    public ClassName withoutAnnotations() {
        return new ClassName(packageName, simpleNames, List.of());
    }

    @Override
    public int compareTo(ClassName other) {
        requireNonNull(other, "other");
        int byPackage = packageName.compareTo(other.packageName);
        if (byPackage != 0) {
            return byPackage;
        }
        int byNames = compare(simpleNames, other.simpleNames);
        return byNames != 0
                ? byNames
                : compare(
                        annotations.stream().map(Annotation::toString).toList(),
                        other.annotations.stream().map(Annotation::toString).toList());
    }

    private static int compare(List<String> left, List<String> right) {
        for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
            int byElement = left.get(i).compareTo(right.get(i));
            if (byElement != 0) {
                return byElement;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    @Override
    public String toString() {
        return CodeWriter.standalone(this);
    }

    private static void checkKind(String kind) {
        requireNonNull(kind, "kind");
        if (!SourceVersion.isIdentifier(kind) || kind.indexOf('$') >= 0) {
            throw new IllegalArgumentException("Not a kind of generated type: \"" + kind + "\"");
        }
    }

    // Joins the names with dollar signs, each dollar sign within a name written $0.
    private static String join(List<String> names) {
        return names.stream().map(ClassName::escape).collect(Collectors.joining("$"));
    }

    private static String escape(String name) {
        return name.replace("$", "$0");
    }

    private static ClassName generated(String packageName, String simpleName) {
        return new ClassName(packageName, List.of(simpleName), List.of());
    }
}
