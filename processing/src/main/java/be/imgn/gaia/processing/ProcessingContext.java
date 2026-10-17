package be.imgn.gaia.processing;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.processing.internal.Inheritance;
import be.imgn.gaia.processing.internal.PresentAnnotations;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;

/**
 * Everything a processor works with: javac's processing environment, and gaia's helpers on top of it.
 *
 * <p>A {@link StepProcessor} creates one and passes it to its steps. Any processor can create one with
 * {@link #of(ProcessingEnvironment, Class)}.
 */
public final class ProcessingContext {

    // Reference types and wildcards, as Types.getDeclaredType accepts, but not the null type.
    private static final Set<TypeKind> TYPE_ARGUMENT_KINDS =
            EnumSet.of(TypeKind.DECLARED, TypeKind.ARRAY, TypeKind.TYPEVAR, TypeKind.WILDCARD, TypeKind.ERROR);

    // The kinds of types that are no subtypes of anything, which Types.isSubtype rejects.
    private static final Set<TypeKind> NOT_SUBTYPE_KINDS =
            EnumSet.of(TypeKind.EXECUTABLE, TypeKind.PACKAGE, TypeKind.MODULE);

    private final ProcessingEnvironment environment;
    private final Diagnostics diagnostics;
    private final GeneratedSources sources;
    private final ServiceFiles services;

    private ProcessingContext(ProcessingEnvironment environment, Class<? extends Processor> processor) {
        this.environment = environment;
        this.diagnostics = new Diagnostics(environment.getMessager());
        this.sources = new GeneratedSources(environment.getElementUtils(), environment.getFiler(), processor.getName());
        this.services = new ServiceFiles(
                environment.getElementUtils(), environment.getTypeUtils(), environment.getFiler(), diagnostics);
    }

    /**
     * Returns a context over a processing environment.
     *
     * @param environment the environment javac gave the processor
     * @param processor the class of the processor, named in the {@code @Generated} annotation of its sources
     * @return the context
     */
    public static ProcessingContext of(ProcessingEnvironment environment, Class<? extends Processor> processor) {
        requireNonNull(environment, "environment");
        requireNonNull(processor, "processor");
        return new ProcessingContext(environment, processor);
    }

    /**
     * Returns javac's processing environment.
     *
     * @return the environment
     */
    public ProcessingEnvironment environment() {
        return environment;
    }

    /**
     * Returns the utilities for elements.
     *
     * @return the elements
     */
    public Elements elements() {
        return environment.getElementUtils();
    }

    /**
     * Returns the utilities for types.
     *
     * @return the types
     */
    public Types types() {
        return environment.getTypeUtils();
    }

    /**
     * Returns the filer, to create source, class and resource files.
     *
     * @return the filer
     */
    public Filer filer() {
        return environment.getFiler();
    }

    /**
     * Returns the options passed to the processors, with {@code -Akey=value}.
     *
     * @return the options, by key
     */
    public Map<String, String> options() {
        return environment.getOptions();
    }

    /**
     * Returns the value of an option of the processor. An invalid value is reported as an error, and the default value
     * returned instead.
     *
     * @param <T> the type of the value
     * @param option the option
     * @return its value, or its default value if it is not given or invalid
     */
    public <T> T option(ProcessorOption<T> option) {
        requireNonNull(option, "option");
        return option.read(environment.getOptions(), diagnostics);
    }

    /**
     * Returns the writer of generated source files.
     *
     * @return the generated sources
     */
    public GeneratedSources sources() {
        return sources;
    }

    /**
     * Returns the writer of service files, {@code META-INF/services/<service>}, which a {@link StepProcessor} writes
     * after the last round.
     *
     * @return the service files
     */
    public ServiceFiles services() {
        return services;
    }

    /**
     * Returns the type of a class, as the compilation sees it: a primitive type for {@code int.class}, the
     * {@linkplain TypeKind#VOID void} type for {@code void.class}, an array type for {@code String[][].class}, and a
     * declared type for a class, interface, enum, record or annotation type, nested ones included, such as
     * {@code Map.Entry.class}.
     *
     * <p>A generic class gives its raw type: {@code List.class} gives {@code List}, neither {@code List<E>}, the type
     * of its declaration, which {@link TypeElement#asType()} gives, nor any parameterization of it, which
     * {@link #type(Class, Class...)} and {@link #type(Class, TypeMirror...)} build. So does an inner class of a generic
     * class: {@code Outer.Inner.class} gives the raw {@code Outer.Inner}.
     *
     * <p>The compilation finds the class by its canonical name, on its own class path or module path, which need not be
     * those the processor runs with.
     *
     * @param clazz the class, which may be primitive, {@code void} or an array
     * @return its type
     * @throws IllegalArgumentException if the class has no canonical name, as a local, anonymous or hidden class, or if
     *     the compilation cannot see it
     */
    public TypeMirror type(Class<?> clazz) {
        requireNonNull(clazz, "clazz");
        if (clazz.isArray()) {
            return types().getArrayType(type(clazz.getComponentType()));
        }
        if (clazz.isPrimitive()) {
            // TypeKind names match primitive class names, void included: INT and int, VOID and void.
            TypeKind kind = TypeKind.valueOf(clazz.getName().toUpperCase(Locale.ROOT));
            return kind == TypeKind.VOID ? types().getNoType(kind) : types().getPrimitiveType(kind);
        }
        return types().getDeclaredType(typeElement(clazz));
    }

    /**
     * Returns a parameterized type of a generic class, with classes as type arguments: {@code type(Map.class,
     * String.class, Integer[].class)} gives {@code Map<String, Integer[]>}. To pass other types, such as a
     * parameterized type, a type variable or a wildcard, as type arguments, use {@link #type(Class, TypeMirror...)}.
     *
     * @param clazz the generic class or interface
     * @param typeArguments as many type arguments as {@code clazz} has type parameters, each a class or an array class;
     *     none for the raw type
     * @return the parameterized type, or the raw type for no type arguments
     * @throws IllegalArgumentException if {@code clazz} or a type argument has no canonical name or is not visible to
     *     the compilation, if {@code clazz} is primitive or an array, if a type argument is primitive, if the number of
     *     type arguments is not that of the type parameters of {@code clazz}, or if {@code clazz} is an inner class of
     *     a generic class: see {@link #type(Class, TypeMirror...)}
     */
    public DeclaredType type(Class<?> clazz, Class<?>... typeArguments) {
        requireNonNull(clazz, "clazz");
        requireNonNullElements(typeArguments);
        TypeMirror[] mirrors = new TypeMirror[typeArguments.length];
        for (int i = 0; i < typeArguments.length; i++) {
            mirrors[i] = type(typeArguments[i]);
        }
        return type(clazz, mirrors);
    }

    /**
     * Returns a parameterized type of a generic class, with any reference types or wildcards as type arguments:
     *
     * <pre>{@code
     * // Map<String, List<? extends Number>>
     * WildcardType numbers = context.types().getWildcardType(context.type(Number.class), null);
     * DeclaredType map = context.type(Map.class, context.type(String.class), context.type(List.class, numbers));
     * }</pre>
     *
     * <p>No type arguments give the raw type, as {@link #type(Class)} does. Type arguments are not checked against the
     * bounds of the type parameters: {@code type(Enum.class, String.class)} gives {@code Enum<String>}.
     *
     * <p>The type arguments of a generic inner class of a generic class, such as {@code Outer<O>.Inner<I>}, go with
     * those of the class it is in: build it with {@link Types#getDeclaredType(DeclaredType, TypeElement,
     * TypeMirror...)}, from the parameterized enclosing type, as {@code Outer<String>}. A generic static nested class,
     * or a generic inner class of a class that is not generic, has no such constraint.
     *
     * @param clazz the generic class or interface
     * @param typeArguments as many type arguments as {@code clazz} has type parameters: declared types, array types,
     *     type variables, wildcards, or types javac could not resolve; none for the raw type
     * @return the parameterized type, or the raw type for no type arguments
     * @throws IllegalArgumentException if {@code clazz} has no canonical name or is not visible to the compilation, if
     *     it is primitive or an array, if a type argument is another kind of type, such as a primitive type, if the
     *     number of type arguments is not that of the type parameters of {@code clazz}, or if {@code clazz} is an inner
     *     class of a generic class
     */
    public DeclaredType type(Class<?> clazz, TypeMirror... typeArguments) {
        requireNonNull(clazz, "clazz");
        requireNonNullElements(typeArguments);
        if (clazz.isPrimitive() || clazz.isArray()) {
            throw new IllegalArgumentException(clazz.getTypeName() + " is not a class or an interface");
        }
        return declaredType(typeElement(clazz), typeArguments);
    }

    /**
     * Returns a type known by name, raw or parameterized, as {@link #type(Class, TypeMirror...)} does for a class: for
     * a type the processor cannot load, or that does not exist yet when it is compiled, such as a type of the code it
     * processes.
     *
     * @param qualifiedName the canonical name of a class, interface, enum, record or annotation type
     * @param typeArguments as many type arguments as the type has type parameters, as for {@link #type(Class,
     *     TypeMirror...)}; none for the raw type, or the type itself if it is not generic
     * @return the parameterized type, or the raw type for no type arguments
     * @throws IllegalArgumentException if the compilation sees no type of that name, or if the type arguments are
     *     invalid, as for {@link #type(Class, TypeMirror...)}
     */
    public DeclaredType type(String qualifiedName, TypeMirror... typeArguments) {
        requireNonNull(qualifiedName, "qualifiedName");
        requireNonNullElements(typeArguments);
        return declaredType(typeElement(qualifiedName), typeArguments);
    }

    /**
     * Returns whether two types are the same type, ignoring type annotations: {@code @Nullable String} is the same type
     * as {@code String}, and so are {@code List<@Nullable String>} and {@code List<String>}, {@code @Nullable T} and
     * {@code T}, or {@code String @Nullable []} and {@code String[]}, at any depth, wildcard bounds included.
     *
     * <p>This is {@link Types#isSameType(TypeMirror, TypeMirror)}, which javac implements ignoring type annotations,
     * except for wildcards. {@code Types.isSameType} says that a wildcard is never the same type as any type, itself
     * included, while javac compares the wildcards of type arguments by their bounds. This compares two wildcards the
     * same way: {@code ? extends Number} is the same as {@code ? extends @Nullable Number}, {@code ? super String} as
     * {@code ? super String}, and {@code ?} as {@code ? extends Object}, as {@code List<?>} is the same type as
     * {@code List<? extends Object>}; a wildcard is never the same type as a type that is not a wildcard.
     *
     * <p>Otherwise, types are the same as {@code Types.isSameType} says: a raw type, such as {@code List}, is neither
     * the same type as a parameterization of it nor as the type of its declaration, {@code List<E>}; a type variable is
     * only the same type as itself, not as another type variable of the same name; and a type javac could not resolve,
     * of kind {@link TypeKind#ERROR}, is the same type as any type, so check for those first.
     *
     * @param left a type
     * @param right another type
     * @return {@code true} if {@code left} and {@code right} are the same type
     */
    public boolean isSameType(TypeMirror left, TypeMirror right) {
        requireNonNull(left, "left");
        requireNonNull(right, "right");
        if (left.getKind() == TypeKind.WILDCARD && right.getKind() == TypeKind.WILDCARD) {
            return isSameWildcard((WildcardType) left, (WildcardType) right);
        }
        return types().isSameType(left, right);
    }

    /**
     * Returns whether a type is a subtype of a class, ignoring type arguments: {@code ArrayList<String>} is a subtype
     * of {@code List.class}. A type is a subtype of itself.
     *
     * @param type the type to check
     * @param supertype the class
     * @return {@code true} if {@code type} is {@code supertype} or one of its subtypes; {@code false} if the
     *     compilation sees no such class
     * @throws IllegalArgumentException if {@code type} is an executable, package or module type, which is no subtype of
     *     anything, or if {@code supertype} has no canonical name, as a local, anonymous or hidden class
     */
    public boolean isSubtype(TypeMirror type, Class<?> supertype) {
        requireNonNull(type, "type");
        requireNonNull(supertype, "supertype");
        checkSubtypeKind(type);
        return (supertype.isPrimitive() || supertype.isArray())
                ? MoreTypes.isType(type, supertype)
                : isSubtype(type, MoreTypes.canonicalName(supertype));
    }

    /**
     * Returns whether a type is a subtype of a type known by name, ignoring type arguments.
     *
     * @param type the type to check
     * @param qualifiedName the canonical name of the supertype
     * @return {@code true} if {@code type} is that type or one of its subtypes; {@code false} if no such type exists
     * @throws IllegalArgumentException if {@code type} is an executable, package or module type, which is no subtype of
     *     anything
     */
    public boolean isSubtype(TypeMirror type, String qualifiedName) {
        requireNonNull(type, "type");
        requireNonNull(qualifiedName, "qualifiedName");
        checkSubtypeKind(type);
        TypeElement supertype = elements().getTypeElement(qualifiedName);
        return supertype != null && types().isSubtype(types().erasure(type), types().erasure(supertype.asType()));
    }

    /**
     * Returns a supertype of a type as that type sees it, with the type arguments its hierarchy gives it. With
     * {@code class StringConverter extends AbstractConverter<String>} and {@code class AbstractConverter<X> implements
     * Converter<X>}, the {@code Converter} supertype of {@code StringConverter} is {@code Converter<String>}; that of a
     * field of type {@code ArrayList<Integer>}, as an {@code Iterable}, is {@code Iterable<Integer>}. A type is its own
     * supertype.
     *
     * <p>Type arguments are substituted as they are, without capture: a type variable or a wildcard that the type
     * leaves open stays in the result, as {@code Converter<X>} for {@code AbstractConverter<X>} itself, or
     * {@code Iterable<? extends Number>} for {@code List<? extends Number>}. A raw type, or a hierarchy that goes
     * through a raw supertype, as {@code class Legacy implements Comparable}, gives a raw supertype, without type
     * arguments. A type variable or an intersection type has the supertypes of its bounds. A type javac could not
     * resolve has no supertypes, and the supertypes it could not resolve contribute nothing.
     *
     * <p>The type arguments of the supertype are the answer to "what is {@code T} of {@code Converter<T>} for this
     * type?":
     *
     * <pre>{@code
     * Optional<TypeMirror> converted = context.asSupertype(element.asType(), converter)
     *         .map(supertype -> supertype.getTypeArguments().getFirst());
     * }</pre>
     *
     * @param type the type: a declared type, a type variable or an intersection type
     * @param supertype the class or interface to find among the supertypes of {@code type}
     * @return the parameterization of {@code supertype} that {@code type} is a subtype of; empty if it is not a subtype
     *     of it, or if it is another kind of type, such as a primitive, which is not boxed, or an array
     */
    public Optional<DeclaredType> asSupertype(TypeMirror type, TypeElement supertype) {
        requireNonNull(type, "type");
        requireNonNull(supertype, "supertype");
        Deque<TypeMirror> pending = new ArrayDeque<>();
        pending.push(type);
        Set<Element> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            TypeMirror current = pending.pop();
            Element element = types().asElement(current);
            if (current.getKind() == TypeKind.DECLARED && supertype.equals(element)) {
                return Optional.of((DeclaredType) current);
            }
            // A type several paths reach is explored once; the superclass first, then each superinterface in turn.
            if (element == null || visited.add(element)) {
                directSupertypes(current).reversed().forEach(pending::push);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns a supertype of a type, known by name, as that type sees it: see {@link #asSupertype(TypeMirror,
     * TypeElement)}.
     *
     * @param type the type: a declared type, a type variable or an intersection type
     * @param qualifiedName the canonical name of the class or interface to find among the supertypes of {@code type}
     * @return the parameterization of that class or interface that {@code type} is a subtype of; empty if it is not a
     *     subtype of it, if it is another kind of type, or if no type has that name
     */
    public Optional<DeclaredType> asSupertype(TypeMirror type, String qualifiedName) {
        requireNonNull(type, "type");
        requireNonNull(qualifiedName, "qualifiedName");
        TypeElement supertype = elements().getTypeElement(qualifiedName);
        return supertype == null ? Optional.empty() : asSupertype(type, supertype);
    }

    /**
     * Returns a supertype of a type, as a class, as that type sees it: the {@code List.class} supertype of
     * {@code ArrayList<String>} is {@code List<String>}. See {@link #asSupertype(TypeMirror, TypeElement)}.
     *
     * @param type the type: a declared type, a type variable or an intersection type
     * @param supertype the class or interface to find among the supertypes of {@code type}
     * @return the parameterization of {@code supertype} that {@code type} is a subtype of; empty if it is not a subtype
     *     of it, if it is another kind of type, if {@code supertype} is a primitive or an array class, or if the
     *     compilation sees no such class
     * @throws IllegalArgumentException if {@code supertype} has no canonical name, as a local, anonymous or hidden
     *     class
     */
    public Optional<DeclaredType> asSupertype(TypeMirror type, Class<?> supertype) {
        requireNonNull(type, "type");
        requireNonNull(supertype, "supertype");
        return (supertype.isPrimitive() || supertype.isArray())
                ? Optional.empty()
                : asSupertype(type, MoreTypes.canonicalName(supertype));
    }

    /**
     * Returns the direct superclass of a class as that class sees it, with the type arguments its declaration gives the
     * superclass: {@code AbstractList<String>} for {@code ArrayList<String>}, as {@link #asSupertype(TypeMirror,
     * TypeElement)} gives it. A raw type has a raw superclass.
     *
     * <p>{@code Object} is left out, so that walking up the hierarchy stops by itself:
     *
     * <pre>{@code
     * for (var current = context.superclass(type); current.isPresent(); current = context.superclass(current.get())) {
     *     ...
     * }
     * }</pre>
     *
     * @param type the class, enum or record
     * @return its superclass; empty if the superclass is {@code Object} or javac could not resolve it, or if
     *     {@code type} is {@code Object} itself, an interface or an annotation type
     */
    public Optional<DeclaredType> superclass(DeclaredType type) {
        requireNonNull(type, "type");
        TypeMirror superclass = ((TypeElement) type.asElement()).getSuperclass();
        if (superclass.getKind() != TypeKind.DECLARED) {
            return Optional.empty();
        }
        TypeElement element = (TypeElement) ((DeclaredType) superclass).asElement();
        return element.getQualifiedName().contentEquals("java.lang.Object")
                ? Optional.empty()
                : asSupertype(type, element);
    }

    /**
     * Returns the methods of a type as the compiler sees them: those it declares, and those it inherits from its
     * supertypes, as JLS 8.4.8 and 9.4.1 define inheritance. A method that the type, or another inherited method,
     * overrides is left out, as is a static method it hides. Private methods of supertypes, static methods of
     * interfaces, and package-private methods of another package are not inherited. An interface also has the public
     * methods of {@code Object} it does not override, which JLS 9.2 makes its members.
     *
     * <p>A type can inherit several methods with override-equivalent signatures, such as an abstract method of its
     * superclass and one of an interface: they are all returned. {@link #abstractMethods(TypeElement)} keeps one.
     *
     * <p>The methods are those of the type's declaration, with its type variables: as a member of a parameterized type,
     * a method has the types {@code types().asMemberOf(type, method)} gives, which is what
     * {@link be.imgn.gaia.processing.source.Method#overriding(ExecutableElement, DeclaredType, Types)} uses. Supertypes
     * javac could not resolve contribute nothing.
     *
     * <p>Whether a method overrides another depends on the type both are members of, which
     * {@code elements().overrides(overrider, overridden, type)} takes: {@link Elements#overrides(ExecutableElement,
     * ExecutableElement, TypeElement)}. A concrete method of a superclass implements, as seen from a subclass, an
     * interface method that the subclass implements and the superclass does not: with {@code class Base { public void
     * run() {} }} and {@code class Task extends Base implements Runnable {}}, {@code Base.run()} overrides
     * {@code Runnable.run()} from {@code Task}, but not from {@code Base}, which is why {@code Runnable.run()} is not
     * among the methods of {@code Task}. A method does not override itself; a static method does not override, but
     * hides; and a package-private method is not overridden from another package.
     *
     * @param type the class, interface, enum, record or annotation type
     * @return its methods, without constructors or initializers: those it declares in the order of
     *     {@link TypeElement#getEnclosedElements()}, then those it inherits from its superclass, then from each of its
     *     direct superinterfaces in turn, each in the order this method returns for that supertype; each method once;
     *     for an interface, the methods of {@code Object} last
     */
    public List<ExecutableElement> methods(TypeElement type) {
        requireNonNull(type, "type");
        return new Inheritance(elements(), types()).methods(type);
    }

    /**
     * Returns the abstract methods a type leaves to its implementations: the abstract ones among its
     * {@linkplain #methods(TypeElement) methods}. A default method is not abstract, unless a subinterface declares it
     * again as abstract; an abstract method that a superclass implements, or that a public method of {@code Object}
     * implements for an interface, such as {@code Comparator.equals}, is not returned.
     *
     * <p>Of several abstract methods with override-equivalent signatures, which one method implements all at once, only
     * one is returned: the first whose return type can be returned for all of them and whose thrown exceptions all of
     * them allow; failing that, the first whose return type can; failing that, the first.
     *
     * <p>To implement them, as a class implementing an interface or extending an abstract class:
     *
     * <pre>{@code
     * DeclaredType supertype = (DeclaredType) element.asType();
     * for (ExecutableElement method : context.abstractMethods(element)) {
     *     implementation.addMethod(Method.overriding(method, supertype, context.types())
     *             .addStatement("throw new $T()", UnsupportedOperationException.class)
     *             .build());
     * }
     * }</pre>
     *
     * @param type the class, interface, enum, record or annotation type
     * @return its abstract methods, in the order of {@link #methods(TypeElement)}; empty if it has none, as for a
     *     concrete class
     */
    public List<ExecutableElement> abstractMethods(TypeElement type) {
        requireNonNull(type, "type");
        return new Inheritance(elements(), types()).abstractMethods(type);
    }

    /**
     * Returns the type of a field or of a parameter as a member of a type: the parameter of {@code add(E)} has the type
     * {@code String} in {@code List<String>}. This is {@link Types#asMemberOf(DeclaredType, Element)}, which javac
     * rejects for a parameter, since its enclosing element is a method rather than a type: a parameter has the type
     * that its method, as a member of {@code container}, gives it. The field of a record component gives the type of
     * the component.
     *
     * @param container the type the variable is a member of, or a subtype of the type declaring it
     * @param variable a field, an enum constant, or a parameter of a method or constructor
     * @return the type of {@code variable} in {@code container}
     * @throws IllegalArgumentException if {@code variable} is another kind of variable, such as a local variable, or is
     *     not a member of {@code container}, nor a parameter of one
     */
    public TypeMirror asMemberOf(DeclaredType container, VariableElement variable) {
        requireNonNull(container, "container");
        requireNonNull(variable, "variable");
        if (variable.getKind() != ElementKind.PARAMETER) {
            return types().asMemberOf(container, variable);
        }
        if (!(variable.getEnclosingElement() instanceof ExecutableElement method)
                || !method.getParameters().contains(variable)) {
            throw new IllegalArgumentException("Not a parameter of a method or constructor: " + variable);
        }
        var type = (ExecutableType) types().asMemberOf(container, method);
        return type.getParameterTypes().get(method.getParameters().indexOf(variable));
    }

    /**
     * Returns whether an element comes from a source file of this compilation, and so is compiled with the code a
     * processor generates, rather than read from a class file compiled before. The sources are those given to javac,
     * those it finds on its source path, and those a processor generated, in any round. A type of a library, on the
     * class path or the module path, of the platform, or compiled by an earlier build and found again in the class
     * output, does not; nor does a class file a processor generated.
     *
     * <pre>{@code
     * if (context.isFromSource(dependency)) {
     *     // compiled now: generate its factory too
     * } else {
     *     // compiled before: read what its class file declares
     * }
     * }</pre>
     *
     * <p>The answer is the same in every round, the last one included, unlike the root elements of a round, which are
     * only the types new in that round: an element held back to a later round, or the types it refers to, are found
     * this way whatever round they come from.
     *
     * <p>An element declared in a type, such as a nested or local type, a method, a field, a parameter or a type
     * parameter, comes from the file of its top-level type. A package comes from its {@code package-info.java} and a
     * module from its {@code module-info.java}: a package without one, or the unnamed module, comes from no file.
     *
     * @param element the element
     * @return {@code true} if the element comes from a source file of this compilation; {@code false} if it comes from
     *     a class file, or from no file javac knows of, as a type javac could not resolve
     * @throws UnsupportedOperationException if the compiler does not implement
     *     {@link Elements#getFileObjectOf(Element)}, as javac does
     */
    public boolean isFromSource(Element element) {
        requireNonNull(element, "element");
        Element declaration =
                MoreElements.topLevelType(element).<Element>map(type -> type).orElse(element);
        // A type javac could not resolve has no file, and javac fails to look for one.
        if (declaration.asType().getKind() == TypeKind.ERROR) {
            return false;
        }
        JavaFileObject file = elements().getFileObjectOf(declaration);
        return file != null && file.getKind() == JavaFileObject.Kind.SOURCE;
    }

    /**
     * Returns the diagnostics, to report errors, warnings and notes at a precise place.
     *
     * @return the diagnostics
     */
    public Diagnostics diagnostics() {
        return diagnostics;
    }

    /**
     * Runs an action on each item in turn, typically an element, reporting a {@link ProcessingException} the action
     * throws for one item as an error, with {@link Diagnostics#error(ProcessingException)}, and going on with the next
     * item. Validation can then throw at the first problem it finds with an element without stopping the others:
     *
     * <pre>{@code
     * context.forEach(elements.annotatedWith("com.example.Mapper"), mapper -> {
     *     if (!mapper.getModifiers().contains(Modifier.PUBLIC)) {
     *         throw new ProcessingException("must be public", mapper);
     *     }
     *     // generate the mapper
     * });
     * }</pre>
     *
     * <p>Any other exception stops the iteration and propagates.
     *
     * @param <T> the type of the items
     * @param items the items, such as the elements a step gets
     * @param action what to do with each item
     */
    public <T> void forEach(Iterable<? extends T> items, Consumer<? super T> action) {
        requireNonNull(items, "items");
        requireNonNull(action, "action");
        for (T item : items) {
            try {
                action.accept(item);
            } catch (ProcessingException e) {
                diagnostics.error(e);
            }
        }
    }

    /**
     * Returns a reader of the annotation of a type written on an element. A repeatable annotation written several times
     * is only found through its container: see {@link #annotations(Element, String)}.
     *
     * <p>An annotation of a type javac could not resolve, such as {@code @Missing} or {@code @com.example.Missing} when
     * no such type exists, is never found, not even by the name it is written with: as for the other lookups, its type
     * is unknown, and so are its elements. Annotations are not inherited: see {@link #inheritedAnnotation(TypeElement,
     * String)}.
     *
     * @param element the annotated element
     * @param qualifiedName the fully qualified name of the annotation type
     * @return a reader of the annotation, or empty if the element does not have it
     */
    public Optional<AnnotationReader> annotation(Element element, String qualifiedName) {
        requireNonNull(element, "element");
        requireNonNull(qualifiedName, "qualifiedName");
        return element.getAnnotationMirrors().stream()
                .filter(mirror -> MoreTypes.isType(mirror.getAnnotationType(), qualifiedName))
                .findFirst()
                .map(this::reader);
    }

    /**
     * Returns readers of every annotation of a type on an element, whether it is written once, several times, or within
     * its container. Javac wraps the annotations of a {@linkplain java.lang.annotation.Repeatable repeatable} type
     * written several times in their container, the type {@code @Repeatable} names, so that the element only has the
     * container, which {@link #annotation(Element, String)} finds by the container's name only. This looks into the
     * container, as {@link java.lang.reflect.AnnotatedElement#getDeclaredAnnotationsByType(Class)} does: with
     * {@code @Repeatable(Tags.class) @interface Tag}, the {@code Tag} annotations of {@code @Tag("a") @Tag("b")} and of
     * {@code @Tags({@Tag("a"), @Tag("b")})} are {@code @Tag("a")} and {@code @Tag("b")}.
     *
     * <p>The language forbids an annotation written several times next to its container, but allows it written once
     * next to its container, as in {@code @Tag("a") @Tags({@Tag("b"), @Tag("c")})}: the annotation and those the
     * container wraps are all returned, in the order they are written, here {@code a}, {@code b}, {@code c}. A
     * container that is itself repeated, in the container of the container, is not looked into. Annotations of a type
     * javac could not resolve are not returned. Annotations are not inherited: see
     * {@link #inheritedAnnotation(TypeElement, String)}.
     *
     * @param element the annotated element
     * @param qualifiedName the fully qualified name of the annotation type
     * @return readers of the annotations of that type on the element, in the order they are written; for an element
     *     read from a class file, javac reads the annotations kept at run time before the others, so the order is that
     *     of the source among annotations of the same retention; empty if the element has none
     */
    public List<AnnotationReader> annotations(Element element, String qualifiedName) {
        requireNonNull(element, "element");
        requireNonNull(qualifiedName, "qualifiedName");
        return readers(PresentAnnotations.ofType(element, qualifiedName));
    }

    /**
     * Returns readers of the annotations of an element whose type is annotated with a meta-annotation: with
     * {@code javax.inject.Qualifier}, the JSR-330 qualifiers of the element, such as {@code @Named("primary")}. Only
     * the meta-annotations of the annotation type itself count, as for qualifiers and scopes;
     * {@link #annotationsMetaAnnotatedWith(Element, String)} also follows the meta-annotations of meta-annotations.
     *
     * <p>The annotations considered are those {@link #annotations(Element, String)} sees: a container of a repeatable
     * annotation, and each annotation it wraps, so that the repeated annotations of a meta-annotated type are found
     * although only their container is written on the element. The meta-annotation may itself be repeated on the
     * annotation type. The meta-annotation written on the element is not returned, unless its type is annotated with
     * itself. Annotations of a type javac could not resolve are neither returned nor followed.
     *
     * @param element the annotated element
     * @param metaAnnotation the fully qualified name of the meta-annotation type
     * @return readers of the annotations whose type has the meta-annotation, in the order they are written, each
     *     container followed by the annotations it wraps; empty if there is none
     */
    public List<AnnotationReader> annotationsAnnotatedWith(Element element, String metaAnnotation) {
        requireNonNull(element, "element");
        requireNonNull(metaAnnotation, "metaAnnotation");
        return readers(PresentAnnotations.annotatedWith(element, metaAnnotation, false));
    }

    /**
     * Returns readers of the annotations of an element whose type is annotated with a meta-annotation, directly or
     * through other annotations, at any depth: with {@code @Component @interface Controller} and
     * {@code @Controller @interface RestController}, both {@code @Controller} and {@code @RestController} are
     * meta-annotated with {@code @Component}. {@link #annotationsAnnotatedWith(Element, String)} only looks at the
     * annotation type itself.
     *
     * <p>Each annotation type is examined once for an annotation, so that cycles end: {@code @Documented} annotates
     * itself, and {@code @Retention} and {@code @Documented} annotate each other. The annotations considered, on the
     * element and on the annotation types, are those of {@link #annotationsAnnotatedWith(Element, String)}. The
     * meta-annotation written on the element is not returned, unless its type is meta-annotated with itself, as through
     * a cycle.
     *
     * @param element the annotated element
     * @param metaAnnotation the fully qualified name of the meta-annotation type
     * @return readers of the annotations whose type has the meta-annotation at some depth, in the order they are
     *     written, each container followed by the annotations it wraps; empty if there is none
     */
    public List<AnnotationReader> annotationsMetaAnnotatedWith(Element element, String metaAnnotation) {
        requireNonNull(element, "element");
        requireNonNull(metaAnnotation, "metaAnnotation");
        return readers(PresentAnnotations.annotatedWith(element, metaAnnotation, true));
    }

    /**
     * Returns a reader of the annotation of a type that a class has, declared on it or inherited from a superclass, as
     * {@link java.lang.annotation.Inherited @Inherited} makes it present: if the class does not declare one, and the
     * annotation type is {@code @Inherited}, the nearest superclass that declares one gives it. Only classes inherit
     * annotations, and only from their superclasses: never from the interfaces they implement, and an interface
     * inherits nothing. Methods and other elements do not inherit annotations, not even from the methods they override:
     * {@link #annotation(Element, String)} reads theirs.
     *
     * <p>This builds on {@link Elements#getAllAnnotationMirrors(Element)}, which gives every annotation present on an
     * element, inherited ones included. A superclass javac could not resolve gives nothing, nor do its superclasses. A
     * repeatable annotation written several times is stored in its container, which the language requires to be
     * {@code @Inherited} too: the class declares or inherits the container, so look it up by the container's name.
     *
     * @param type the class, or another type, which only has the annotations it declares
     * @param qualifiedName the fully qualified name of the annotation type
     * @return a reader of the annotation the type declares, or else the one it inherits; empty if it has none
     */
    public Optional<AnnotationReader> inheritedAnnotation(TypeElement type, String qualifiedName) {
        requireNonNull(type, "type");
        requireNonNull(qualifiedName, "qualifiedName");
        return elements().getAllAnnotationMirrors(type).stream()
                .filter(mirror -> MoreTypes.isType(mirror.getAnnotationType(), qualifiedName))
                .findFirst()
                .map(this::reader);
    }

    /**
     * Returns a reader of an annotation.
     *
     * @param annotation the annotation
     * @return a reader of it
     */
    public AnnotationReader reader(AnnotationMirror annotation) {
        requireNonNull(annotation, "annotation");
        return new AnnotationReader(annotation, elements());
    }

    private static void requireNonNullElements(Object[] typeArguments) {
        requireNonNull(typeArguments, "typeArguments");
        for (Object argument : typeArguments) {
            requireNonNull(argument, "typeArguments");
        }
    }

    private static void checkSubtypeKind(TypeMirror type) {
        checkArgument(
                !NOT_SUBTYPE_KINDS.contains(type.getKind()),
                "%s is a type of kind %s, which is no subtype of anything",
                type,
                type.getKind());
    }

    private TypeElement typeElement(Class<?> clazz) {
        String name = clazz.getCanonicalName();
        if (name == null) {
            throw new IllegalArgumentException(clazz.getName()
                    + " has no canonical name: a local, anonymous or hidden class has no type to build");
        }
        return typeElement(name);
    }

    private TypeElement typeElement(String qualifiedName) {
        TypeElement element = elements().getTypeElement(qualifiedName);
        if (element == null) {
            throw new IllegalArgumentException("The compilation sees no type named " + qualifiedName
                    + ": it is not on its class path or module path, or several modules have one");
        }
        return element;
    }

    private DeclaredType declaredType(TypeElement element, TypeMirror... typeArguments) {
        if (typeArguments.length == 0) {
            return types().getDeclaredType(element);
        }
        int parameters = element.getTypeParameters().size();
        if (typeArguments.length != parameters) {
            throw new IllegalArgumentException("Wrong number of type arguments for " + element.asType() + ": expected "
                    + parameters + ", got " + typeArguments.length + ": " + List.of(typeArguments));
        }
        for (TypeMirror argument : typeArguments) {
            if (!TYPE_ARGUMENT_KINDS.contains(argument.getKind())) {
                throw new IllegalArgumentException(argument + " cannot be a type argument of " + element.asType()
                        + ": only a class, interface, array, type variable or wildcard can");
            }
        }
        for (TypeMirror enclosing = ((DeclaredType) element.asType()).getEnclosingType();
                enclosing.getKind() == TypeKind.DECLARED;
                enclosing = ((DeclaredType) enclosing).getEnclosingType()) {
            if (!((DeclaredType) enclosing).getTypeArguments().isEmpty()) {
                throw new IllegalArgumentException(element.getQualifiedName() + " is an inner class of the generic "
                        + enclosing + ": use types().getDeclaredType(DeclaredType, TypeElement, TypeMirror...) with a"
                        + " parameterization of "
                        + MoreTypes.asTypeElement(enclosing).orElseThrow().getQualifiedName());
            }
        }
        return types().getDeclaredType(element, typeArguments);
    }

    // As javac compares the wildcards of type arguments: an unbounded wildcard is bounded by Object.
    private boolean isSameWildcard(WildcardType left, WildcardType right) {
        TypeMirror leftSuper = left.getSuperBound();
        TypeMirror rightSuper = right.getSuperBound();
        if (leftSuper != null || rightSuper != null) {
            return leftSuper != null && rightSuper != null && types().isSameType(leftSuper, rightSuper);
        }
        return types().isSameType(upperBound(left), upperBound(right));
    }

    private TypeMirror upperBound(WildcardType wildcard) {
        TypeMirror bound = wildcard.getExtendsBound();
        return bound == null ? typeElement("java.lang.Object").asType() : bound;
    }

    private List<AnnotationReader> readers(List<AnnotationMirror> annotations) {
        return annotations.stream().map(this::reader).toList();
    }

    // The types a walk up the hierarchy goes to next: the bounds of a type variable or intersection type stand for it.
    private List<? extends TypeMirror> directSupertypes(TypeMirror type) {
        return switch (type.getKind()) {
            case DECLARED -> types().directSupertypes(type);
            case TYPEVAR -> List.of(((TypeVariable) type).getUpperBound());
            case INTERSECTION -> ((IntersectionType) type).getBounds();
            default -> List.of();
        };
    }
}
