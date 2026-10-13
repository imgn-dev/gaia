package be.imgn.gaia.processing.source;

import be.imgn.gaia.core.text.Splitter;
import be.imgn.gaia.processing.MoreElements;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

/**
 * The name of a class, interface, enum, record or annotation type, possibly nested in others.
 *
 * @param packageName the package, empty for the unnamed package
 * @param simpleNames the simple names from the top-level type to this one, such as {@code [Map, Entry]}
 * @param annotations the type annotations
 */
public record ClassName(String packageName, List<String> simpleNames, List<Annotation> annotations)
        implements TypeName {

    /** {@code java.lang.Object}. */
    public static final ClassName OBJECT = of("java.lang", "Object");

    /**
     * Creates the name.
     *
     * @param packageName the package, empty for the unnamed package
     * @param simpleNames the simple names from the top-level type to this one
     * @param annotations the type annotations
     * @throws IllegalArgumentException if a name is not a valid identifier, or there is no simple name
     */
    public ClassName {
        simpleNames = List.copyOf(simpleNames);
        annotations = List.copyOf(annotations);
        if (simpleNames.isEmpty()) {
            throw new IllegalArgumentException("A class name needs a simple name");
        }
        if (!packageName.isEmpty() && !SourceVersion.isName(packageName)) {
            throw new IllegalArgumentException("Not a package name: " + packageName);
        }
        for (String simpleName : simpleNames) {
            if (!SourceVersion.isIdentifier(simpleName) || SourceVersion.isKeyword(simpleName)) {
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
        List<String> simpleNames = new ArrayList<>();
        simpleNames.add(simpleName);
        simpleNames.addAll(List.of(nestedNames));
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
        List<String> simpleNames = new ArrayList<>();
        Element current = type;
        while (current instanceof TypeElement nested) {
            simpleNames.addFirst(nested.getSimpleName().toString());
            current = nested.getEnclosingElement();
        }
        if (!(current instanceof PackageElement)) {
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
     * @throws IllegalArgumentException if no part of the name starts with an upper-case letter, a part is empty, or a
     *     part after the first upper-case one does not start with an upper-case letter
     */
    public static ClassName bestGuess(String canonicalName) {
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
        List<String> names = new ArrayList<>(simpleNames);
        names.set(names.size() - 1, simpleName);
        return new ClassName(packageName, names, List.of());
    }

    /**
     * Returns the name of a top-level type generated for this one, as processors name the code they generate for a
     * type: in the same package, named after the simple names from the top-level type to this one, then a suffix, all
     * joined by underscores. For {@code com.example.Outer.Inner} and the suffix {@code Mapper}, it is
     * {@code com.example.Outer_Inner_Mapper}; for the top-level {@code com.example.Outer}, it is
     * {@code com.example.Outer_Mapper}. {@link #generatedNameWithPrefix(String)} puts the name of what the type is for
     * first instead.
     *
     * <p>Each type and suffix gets its own name. An underscore within a simple name or the suffix is written
     * {@code _0}: an underscore followed by {@code 0} is one of the type's, any other one joins two names, as no name
     * starts with a digit. {@code A_B.C} gives {@code A_0B_C_Mapper} where {@code A.B_C} gives {@code A_B_0C_Mapper},
     * and {@code A} with the suffix {@code B_Mapper} gives {@code A_B_0Mapper}. Other characters, such as a dollar
     * sign, are kept as they are. A type that is not generated can still have the name: whether a type has it already
     * is for {@link be.imgn.gaia.processing.GeneratedSources#isAvailable(ClassName)} to tell.
     *
     * @param suffix what the generated type is for this one, such as {@code Mapper}: a Java identifier, which may be a
     *     keyword
     * @return the name of the generated type, without annotations
     * @throws IllegalArgumentException if the suffix is not an identifier: if it is empty, starts with a digit, or has
     *     a character that cannot be in an identifier, such as a dot or a space
     */
    public ClassName generatedNameWithSuffix(String suffix) {
        if (!SourceVersion.isIdentifier(suffix)) {
            throw new IllegalArgumentException("Not a suffix for a generated type name: \"" + suffix + "\"");
        }
        return generatedName(Stream.concat(simpleNames.stream(), Stream.of(suffix)));
    }

    /**
     * Returns the name of a top-level type generated for this one, with a prefix: in the same package, named after a
     * prefix, then the simple names from the top-level type to this one, all joined by underscores. For
     * {@code com.example.Outer.Inner} and the prefix {@code Generated}, it is
     * {@code com.example.Generated_Outer_Inner}; for the top-level {@code com.example.Outer}, it is
     * {@code com.example.Generated_Outer}.
     *
     * <p>Underscores are written as {@link #generatedNameWithSuffix(String)} writes them, so that each type and prefix
     * gets its own name: {@code A_B.C} with the prefix {@code P} gives {@code P_A_0B_C} where {@code A.B_C} gives
     * {@code P_A_B_0C}, and {@code A} with the prefix {@code P_B} gives {@code P_0B_A}.
     *
     * <p>Both methods join the same way, so a name with a prefix is also the name of another type with a suffix when
     * the prefix followed by the simple names of this type are the simple names of the other type followed by the
     * suffix, and only then: {@code Outer.Inner} with the prefix {@code Generated} and {@code Generated.Outer} with the
     * suffix {@code Inner} both give {@code Generated_Outer_Inner}, and so do {@code Inner} with the prefix
     * {@code Outer} and {@code Outer} with the suffix {@code Inner}. Such a clash needs the prefix to be the simple
     * name of a top-level type of the package, and the suffix that of a type of the package.
     *
     * @param prefix what the generated type is for this one, such as {@code Generated} or {@code AutoValue}: a Java
     *     identifier, which may be a keyword
     * @return the name of the generated type, without annotations
     * @throws IllegalArgumentException if the prefix is not an identifier: if it is empty, starts with a digit, or has
     *     a character that cannot be in an identifier, such as a dot or a space
     */
    public ClassName generatedNameWithPrefix(String prefix) {
        if (!SourceVersion.isIdentifier(prefix)) {
            throw new IllegalArgumentException("Not a prefix for a generated type name: \"" + prefix + "\"");
        }
        return generatedName(Stream.concat(Stream.of(prefix), simpleNames.stream()));
    }

    /**
     * Returns this type parameterized with type arguments, such as {@code List<String>}.
     *
     * @param typeArguments the type arguments
     * @return the parameterized type, with the annotations of this name
     * @throws IllegalArgumentException if there is no type argument, or one is primitive
     */
    public ParameterizedTypeName parameterized(TypeName... typeArguments) {
        return ParameterizedTypeName.of(this, typeArguments);
    }

    @Override
    public ClassName annotated(Annotation... more) {
        return annotated(List.of(more));
    }

    @Override
    public ClassName annotated(List<Annotation> more) {
        return new ClassName(
                packageName,
                simpleNames,
                Stream.concat(annotations.stream(), more.stream()).toList());
    }

    @Override
    public ClassName withoutAnnotations() {
        return new ClassName(packageName, simpleNames, List.of());
    }

    @Override
    public String toString() {
        return CodeWriter.standalone(this);
    }

    // Joins the names with underscores, each underscore within a name written _0.
    private ClassName generatedName(Stream<String> names) {
        String simpleName = names.map(name -> name.replace("_", "_0")).collect(Collectors.joining("_"));
        return new ClassName(packageName, List.of(simpleName), List.of());
    }
}
