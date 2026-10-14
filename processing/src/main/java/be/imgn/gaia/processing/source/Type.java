package be.imgn.gaia.processing.source;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static be.imgn.gaia.core.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import org.jspecify.annotations.Nullable;

/**
 * A class, interface, enum, record or annotation type, or an anonymous class.
 *
 * <pre>{@code
 * Type point = Type.newRecordBuilder("Point")
 *         .addModifiers(Modifier.PUBLIC)
 *         .addRecordComponent(int.class, "x")
 *         .addRecordComponent(int.class, "y")
 *         .addMethod(Method.newCompactConstructorBuilder()
 *                 .addModifiers(Modifier.PUBLIC)
 *                 .beginControlFlow("if (x < 0 || y < 0)")
 *                 .addStatement("throw new $T($S)", IllegalArgumentException.class, "negative")
 *                 .endControlFlow()
 *                 .build())
 *         .build();
 * }</pre>
 *
 * <p>The member types a type inherits hide the types of the same simple name: a class implementing {@code Map} cannot
 * name another {@code Entry} type by its simple name. Supertypes given as a {@link TypeMirror} or a {@link Class} bring
 * the simple names of their member types along, so that such types are written qualified; for supertypes given as a
 * {@link TypeName}, use {@link Builder#avoidClashesWithNestedClasses(TypeElement)} or
 * {@link Builder#alwaysQualify(String...)}.
 *
 * <p>Types are values: two types written the same, which qualify the same names, are equal.
 */
public final class Type {

    private final Kind kind;
    private final @Nullable String name;
    private final CodeBlock anonymousArguments;
    private final Set<Modifier> modifiers;
    private final List<Annotation> annotations;
    private final CodeBlock javadoc;
    private final List<TypeVariableName> typeVariables;
    private final @Nullable TypeName superclass;
    private final List<TypeName> superinterfaces;
    private final List<TypeName> permittedSubclasses;
    private final Map<String, Type> enumConstants;
    private final List<Parameter> recordComponents;
    private final boolean varargs;
    private final List<Field> fields;
    private final CodeBlock staticBlock;
    private final CodeBlock initializerBlock;
    private final List<Method> methods;
    private final List<Type> types;
    private final Set<String> alwaysQualifiedNames;

    private Type(Builder builder) {
        this.kind = builder.kind;
        this.name = builder.name;
        this.anonymousArguments = builder.anonymousArguments;
        this.modifiers = Modifiers.copy(builder.modifiers);
        this.annotations = List.copyOf(builder.annotations);
        this.javadoc = builder.javadoc.build();
        this.typeVariables = List.copyOf(builder.typeVariables);
        this.superclass = builder.superclass;
        this.superinterfaces = List.copyOf(builder.superinterfaces);
        this.permittedSubclasses = List.copyOf(builder.permittedSubclasses);
        this.enumConstants = Collections.unmodifiableMap(new LinkedHashMap<>(builder.enumConstants));
        this.recordComponents = List.copyOf(builder.recordComponents);
        this.varargs = builder.varargs;
        this.fields = List.copyOf(builder.fields);
        this.staticBlock = builder.staticBlock.build();
        this.initializerBlock = builder.initializerBlock.build();
        this.methods = List.copyOf(builder.methods);
        this.types = List.copyOf(builder.types);
        this.alwaysQualifiedNames = Collections.unmodifiableSet(new TreeSet<>(builder.alwaysQualifiedNames));
    }

    /**
     * Returns a builder of classes.
     *
     * @param name the simple name
     * @return a new builder
     * @throws IllegalArgumentException if the name is not a valid identifier
     */
    public static Builder newClassBuilder(String name) {
        Objects.requireNonNull(name, "name");
        return new Builder(Kind.CLASS, checkName(name));
    }

    /**
     * Returns a builder of classes.
     *
     * @param name the name, of which only the simple name is used
     * @return a new builder
     */
    public static Builder newClassBuilder(ClassName name) {
        Objects.requireNonNull(name, "name");
        return newClassBuilder(name.simpleName());
    }

    /**
     * Returns a builder of interfaces.
     *
     * @param name the simple name
     * @return a new builder
     * @throws IllegalArgumentException if the name is not a valid identifier
     */
    public static Builder newInterfaceBuilder(String name) {
        Objects.requireNonNull(name, "name");
        return new Builder(Kind.INTERFACE, checkName(name));
    }

    /**
     * Returns a builder of interfaces.
     *
     * @param name the name, of which only the simple name is used
     * @return a new builder
     */
    public static Builder newInterfaceBuilder(ClassName name) {
        Objects.requireNonNull(name, "name");
        return newInterfaceBuilder(name.simpleName());
    }

    /**
     * Returns a builder of enums.
     *
     * @param name the simple name
     * @return a new builder
     * @throws IllegalArgumentException if the name is not a valid identifier
     */
    public static Builder newEnumBuilder(String name) {
        Objects.requireNonNull(name, "name");
        return new Builder(Kind.ENUM, checkName(name));
    }

    /**
     * Returns a builder of enums.
     *
     * @param name the name, of which only the simple name is used
     * @return a new builder
     */
    public static Builder newEnumBuilder(ClassName name) {
        Objects.requireNonNull(name, "name");
        return newEnumBuilder(name.simpleName());
    }

    /**
     * Returns a builder of records.
     *
     * @param name the simple name
     * @return a new builder
     * @throws IllegalArgumentException if the name is not a valid identifier
     */
    public static Builder newRecordBuilder(String name) {
        Objects.requireNonNull(name, "name");
        return new Builder(Kind.RECORD, checkName(name));
    }

    /**
     * Returns a builder of records.
     *
     * @param name the name, of which only the simple name is used
     * @return a new builder
     */
    public static Builder newRecordBuilder(ClassName name) {
        Objects.requireNonNull(name, "name");
        return newRecordBuilder(name.simpleName());
    }

    /**
     * Returns a builder of annotation types.
     *
     * @param name the simple name
     * @return a new builder
     * @throws IllegalArgumentException if the name is not a valid identifier
     */
    public static Builder newAnnotationBuilder(String name) {
        Objects.requireNonNull(name, "name");
        return new Builder(Kind.ANNOTATION, checkName(name));
    }

    /**
     * Returns a builder of annotation types.
     *
     * @param name the name, of which only the simple name is used
     * @return a new builder
     */
    public static Builder newAnnotationBuilder(ClassName name) {
        Objects.requireNonNull(name, "name");
        return newAnnotationBuilder(name.simpleName());
    }

    /**
     * Returns a builder of anonymous classes, written with {@code $L} in code, as in {@code new Runnable() { ... }}. It
     * extends the superclass, or implements the one interface, it is given; {@code Object} if neither. The body of an
     * enum constant is an anonymous class too.
     *
     * @param format the arguments of the superclass constructor, with placeholders; empty for none
     * @param args the arguments of the placeholders
     * @return a new builder
     * @throws IllegalArgumentException if the format and the arguments do not match, or the arguments are not
     *     {@linkplain CodeBlock balanced}
     */
    public static Builder newAnonymousClassBuilder(String format, @Nullable Object... args) {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(args, "args");
        CodeBlock arguments =
                CodeBlock.requireBalanced(CodeBlock.of(format, args), "The arguments of an anonymous class");
        Builder builder = new Builder(Kind.CLASS, null);
        builder.anonymousArguments = arguments;
        return builder;
    }

    private static String checkName(String name) {
        if (!SourceVersion.isIdentifier(name) || SourceVersion.isKeyword(name)) {
            throw new IllegalArgumentException("Not a type name: " + name);
        }
        return name;
    }

    /**
     * Returns the kind of type.
     *
     * @return the kind
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Returns the simple name.
     *
     * @return the name, or {@code null} for an anonymous class
     */
    public @Nullable String name() {
        return name;
    }

    /**
     * Returns whether this is an anonymous class.
     *
     * @return {@code true} for an anonymous class
     */
    public boolean isAnonymous() {
        return name == null;
    }

    /**
     * Returns the arguments an anonymous class passes to its superclass constructor.
     *
     * @return the arguments, empty for none or for a named type
     */
    public CodeBlock anonymousArguments() {
        return anonymousArguments;
    }

    /**
     * Returns the modifiers.
     *
     * @return the modifiers
     */
    public Set<Modifier> modifiers() {
        return modifiers;
    }

    /**
     * Returns the annotations.
     *
     * @return the annotations, in order
     */
    public List<Annotation> annotations() {
        return annotations;
    }

    /**
     * Returns the Javadoc.
     *
     * @return the Javadoc text, empty if there is none
     */
    public CodeBlock javadoc() {
        return javadoc;
    }

    /**
     * Returns the declared type variables.
     *
     * @return the type variables, in order
     */
    public List<TypeVariableName> typeVariables() {
        return typeVariables;
    }

    /**
     * Returns the superclass.
     *
     * @return the superclass, or {@code null} for none written
     */
    public @Nullable TypeName superclass() {
        return superclass;
    }

    /**
     * Returns the interfaces this type implements, or extends for an interface.
     *
     * @return the interfaces, in order
     */
    public List<TypeName> superinterfaces() {
        return superinterfaces;
    }

    /**
     * Returns the subclasses a sealed type permits.
     *
     * @return the permitted subclasses, in order
     */
    public List<TypeName> permittedSubclasses() {
        return permittedSubclasses;
    }

    /**
     * Returns the constants of an enum, with their arguments and bodies as anonymous classes.
     *
     * @return the constants by name, in order
     */
    public Map<String, Type> enumConstants() {
        return enumConstants;
    }

    /**
     * Returns the components of a record.
     *
     * @return the components, in order
     */
    public List<Parameter> recordComponents() {
        return recordComponents;
    }

    /**
     * Returns whether the last component of a record is variable arity, written {@code String... names}.
     *
     * @return {@code true} for a varargs record
     */
    public boolean isVarargs() {
        return varargs;
    }

    /**
     * Returns the fields.
     *
     * @return the fields, in order
     */
    public List<Field> fields() {
        return fields;
    }

    /**
     * Returns the static initializer.
     *
     * @return its code, empty if there is none
     */
    public CodeBlock staticBlock() {
        return staticBlock;
    }

    /**
     * Returns the instance initializer.
     *
     * @return its code, empty if there is none
     */
    public CodeBlock initializerBlock() {
        return initializerBlock;
    }

    /**
     * Returns the methods and constructors.
     *
     * @return the methods, in order
     */
    public List<Method> methods() {
        return methods;
    }

    /**
     * Returns the nested types.
     *
     * @return the nested types, in order
     */
    public List<Type> types() {
        return types;
    }

    /**
     * Returns the simple names that are written qualified wherever they would refer to another type than the one meant,
     * such as the names of the member types this type inherits.
     *
     * @return the names, sorted
     */
    public Set<String> alwaysQualifiedNames() {
        return alwaysQualifiedNames;
    }

    /**
     * Returns a builder that starts with this type.
     *
     * @return a new builder
     */
    public Builder toBuilder() {
        Builder builder = new Builder(kind, name);
        builder.anonymousArguments = anonymousArguments;
        builder.modifiers.addAll(modifiers);
        builder.annotations.addAll(annotations);
        builder.javadoc.add(javadoc);
        builder.typeVariables.addAll(typeVariables);
        builder.superclass = superclass;
        builder.superinterfaces.addAll(superinterfaces);
        builder.permittedSubclasses.addAll(permittedSubclasses);
        builder.enumConstants.putAll(enumConstants);
        builder.recordComponents.addAll(recordComponents);
        builder.varargs = varargs;
        builder.fields.addAll(fields);
        builder.staticBlock.add(staticBlock);
        builder.initializerBlock.add(initializerBlock);
        builder.methods.addAll(methods);
        builder.types.addAll(types);
        builder.alwaysQualifiedNames.addAll(alwaysQualifiedNames);
        return builder;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Type type
                && toString().equals(type.toString())
                && alwaysQualifiedNames.equals(type.alwaysQualifiedNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toString(), alwaysQualifiedNames);
    }

    @Override
    public String toString() {
        return CodeWriter.standalone(this);
    }

    /** The kinds of type. */
    public enum Kind {
        /** A class, anonymous or not. */
        CLASS("class"),
        /** An interface. */
        INTERFACE("interface"),
        /** An enum. */
        ENUM("enum"),
        /** A record. */
        RECORD("record"),
        /** An annotation type. */
        ANNOTATION("@interface");

        private final String keyword;

        Kind(String keyword) {
            this.keyword = keyword;
        }

        String keyword() {
            return keyword;
        }

        boolean isInterface() {
            return this == INTERFACE || this == ANNOTATION;
        }
    }

    /** A builder of {@link Type}s. */
    public static final class Builder {

        private static final Set<Modifier> CLASS_MODIFIERS = EnumSet.of(
                Modifier.PUBLIC,
                Modifier.PROTECTED,
                Modifier.PRIVATE,
                Modifier.ABSTRACT,
                Modifier.STATIC,
                Modifier.FINAL,
                Modifier.SEALED,
                Modifier.NON_SEALED,
                Modifier.STRICTFP);
        private static final Set<Modifier> INTERFACE_METHOD_FORBIDDEN =
                EnumSet.of(Modifier.PROTECTED, Modifier.FINAL, Modifier.SYNCHRONIZED, Modifier.NATIVE);

        private final Kind kind;
        private final @Nullable String name;
        private CodeBlock anonymousArguments = CodeBlock.empty();
        private final Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
        private final List<Annotation> annotations = new ArrayList<>();
        private final CodeBlock.Builder javadoc = CodeBlock.newBuilder();
        private final List<TypeVariableName> typeVariables = new ArrayList<>();
        private @Nullable TypeName superclass;
        private final List<TypeName> superinterfaces = new ArrayList<>();
        private final List<TypeName> permittedSubclasses = new ArrayList<>();
        private final Map<String, Type> enumConstants = new LinkedHashMap<>();
        private final List<Parameter> recordComponents = new ArrayList<>();
        private boolean varargs;
        private final List<Field> fields = new ArrayList<>();
        private final CodeBlock.Builder staticBlock = CodeBlock.newBuilder();
        private final CodeBlock.Builder initializerBlock = CodeBlock.newBuilder();
        private final List<Method> methods = new ArrayList<>();
        private final List<Type> types = new ArrayList<>();
        private final Set<String> alwaysQualifiedNames = new TreeSet<>();

        private Builder(Kind kind, @Nullable String name) {
            this.kind = kind;
            this.name = name;
        }

        private static void nestedNames(TypeElement type, Set<String> names) {
            ElementFilter.typesIn(type.getEnclosedElements())
                    .forEach(nested -> names.add(nested.getSimpleName().toString()));
            List<TypeMirror> supertypes = new ArrayList<>(type.getInterfaces());
            supertypes.add(type.getSuperclass());
            for (TypeMirror supertype : supertypes) {
                if (supertype.getKind() == TypeKind.DECLARED) {
                    nestedNames((TypeElement) ((DeclaredType) supertype).asElement(), names);
                }
            }
        }

        private static void nestedNames(Class<?> type, Set<String> names) {
            for (Class<?> nested : type.getDeclaredClasses()) {
                names.add(nested.getSimpleName());
            }
            List<Class<?>> supertypes = new ArrayList<>(List.of(type.getInterfaces()));
            if (type.getSuperclass() != null) {
                supertypes.add(type.getSuperclass());
            }
            supertypes.forEach(supertype -> nestedNames(supertype, names));
        }

        // The modifiers the JLS allows on a declaration of the kind, wherever it is declared.
        private static Set<Modifier> allowedModifiers(Kind kind) {
            Set<Modifier> excluded = switch (kind) {
                case CLASS -> Set.of();
                case INTERFACE -> Set.of(Modifier.FINAL);
                // An enum may be given final, which it is anyway: final is left out where it is written.
                case ENUM -> Set.of(Modifier.ABSTRACT, Modifier.SEALED, Modifier.NON_SEALED);
                case RECORD -> Set.of(Modifier.ABSTRACT, Modifier.SEALED, Modifier.NON_SEALED);
                case ANNOTATION -> Set.of(Modifier.FINAL, Modifier.SEALED, Modifier.NON_SEALED);
            };
            Set<Modifier> allowed = EnumSet.copyOf(CLASS_MODIFIERS);
            allowed.removeAll(excluded);
            return allowed;
        }

        private String what() {
            return switch (kind) {
                case CLASS -> "A class";
                case INTERFACE -> "An interface";
                case ENUM -> "An enum";
                case RECORD -> "A record";
                case ANNOTATION -> "An annotation type";
            };
        }

        private String describe() {
            return name == null ? "an anonymous class" : name;
        }

        /**
         * Adds modifiers, such as {@code public}, {@code sealed} or {@code non-sealed}.
         *
         * @param more the modifiers
         * @return this builder
         * @throws IllegalArgumentException if a modifier is not allowed on this kind of type, such as {@code final} on
         *     an interface; is repeated; or conflicts with another one, such as {@code public} with {@code private} or
         *     {@code sealed} with {@code final}
         * @throws IllegalStateException if this is an anonymous class and there are modifiers
         */
        public Builder addModifiers(Modifier... more) {
            return addCheckedModifiers(Modifiers.added(more, "more", what()));
        }

        /**
         * Adds modifiers.
         *
         * @param more the modifiers
         * @return this builder
         * @throws IllegalArgumentException if a modifier is not allowed on this kind of type, such as {@code final} on
         *     an interface; is repeated; or conflicts with another one, such as {@code public} with {@code private} or
         *     {@code sealed} with {@code final}
         * @throws IllegalStateException if this is an anonymous class and there are modifiers
         */
        public Builder addModifiers(Iterable<Modifier> more) {
            return addCheckedModifiers(Modifiers.added(more, "more", what()));
        }

        private Builder addCheckedModifiers(Set<Modifier> added) {
            checkState(added.isEmpty() || name != null, "An anonymous class has no modifiers");
            Modifiers.requireOnly(added, allowedModifiers(kind), what());
            Set<Modifier> all = Modifiers.union(modifiers, added);
            Modifiers.requireAtMostOne(all, Modifiers.ACCESS, what());
            Modifiers.requireAtMostOne(all, EnumSet.of(Modifier.SEALED, Modifier.NON_SEALED, Modifier.FINAL), what());
            Modifiers.requireNoneWith(all, Modifier.ABSTRACT, Set.of(Modifier.FINAL), what());
            modifiers.addAll(added);
            return this;
        }

        /**
         * Adds an annotation.
         *
         * @param annotation the annotation
         * @return this builder
         */
        public Builder addAnnotation(Annotation annotation) {
            annotations.add(Objects.requireNonNull(annotation, "annotation"));
            return this;
        }

        /**
         * Adds an annotation without members.
         *
         * @param annotation the annotation type
         * @return this builder
         * @throws IllegalArgumentException if the class is not an annotation type
         */
        public Builder addAnnotation(Class<? extends java.lang.annotation.Annotation> annotation) {
            Objects.requireNonNull(annotation, "annotation");
            return addAnnotation(Annotation.of(annotation));
        }

        /**
         * Adds an annotation without members.
         *
         * @param annotation the annotation type
         * @return this builder
         */
        public Builder addAnnotation(ClassName annotation) {
            Objects.requireNonNull(annotation, "annotation");
            return addAnnotation(Annotation.of(annotation));
        }

        /**
         * Adds annotations.
         *
         * @param more the annotations
         * @return this builder
         */
        public Builder addAnnotations(Iterable<Annotation> more) {
            annotations.addAll(Checks.elements(more, "more"));
            return this;
        }

        /**
         * Adds Javadoc text. End each line with {@code \n}.
         *
         * @param format the text, with placeholders
         * @param args the arguments of the placeholders
         * @return this builder
         * @throws IllegalArgumentException if the format and the arguments do not match
         */
        public Builder addJavadoc(String format, @Nullable Object... args) {
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(args, "args");
            javadoc.add(format, args);
            return this;
        }

        /**
         * Declares a type variable, such as {@code T} in {@code class Box<T>}.
         *
         * @param typeVariable the type variable, with its bounds
         * @return this builder
         * @throws IllegalStateException if this is an enum, an annotation type or an anonymous class
         */
        public Builder addTypeVariable(TypeVariableName typeVariable) {
            Objects.requireNonNull(typeVariable, "typeVariable");
            return addTypeVariables(List.of(typeVariable));
        }

        /**
         * Declares type variables.
         *
         * @param more the type variables, with their bounds
         * @return this builder
         * @throws IllegalStateException if this is an enum, an annotation type or an anonymous class, and there are
         *     type variables
         */
        public Builder addTypeVariables(Iterable<TypeVariableName> more) {
            List<TypeVariableName> added = Checks.elements(more, "more");
            if (!added.isEmpty()) {
                checkState(name != null, "An anonymous class has no type variables");
                checkState(kind != Kind.ENUM && kind != Kind.ANNOTATION, "%s has no type variables", what());
            }
            typeVariables.addAll(added);
            return this;
        }

        /**
         * Sets the superclass of a class. The member types it brings are not known: see
         * {@link #avoidClashesWithNestedClasses(TypeElement)}.
         *
         * @param type the superclass
         * @return this builder
         * @throws IllegalArgumentException if the type is not a class or a parameterized type
         * @throws IllegalStateException if this is not a class, the superclass is already set, or this is an anonymous
         *     class that implements an interface
         */
        public Builder superclass(TypeName type) {
            Objects.requireNonNull(type, "type");
            checkArgument(
                    type instanceof ClassName || type instanceof ParameterizedTypeName,
                    "A superclass is a class or a parameterized type: %s",
                    type);
            checkState(kind == Kind.CLASS, "Only a class has a superclass");
            checkState(superclass == null, "The superclass is already set: %s", superclass);
            checkState(
                    name != null || superinterfaces.isEmpty(),
                    "An anonymous class extends a class or implements an interface, not both");
            superclass = type;
            return this;
        }

        /**
         * Sets the superclass of a class, and qualifies the names of the member types it brings.
         *
         * @param type the superclass
         * @return this builder
         * @throws IllegalArgumentException if the class cannot be extended: if it is an interface, an enum, a record,
         *     final, primitive, an array, {@code Enum} or {@code Record}; or it is local or anonymous
         * @throws IllegalStateException if this is not a class, the superclass is already set, or this is an anonymous
         *     class that implements an interface
         */
        public Builder superclass(Class<?> type) {
            Objects.requireNonNull(type, "type");
            checkArgument(
                    !type.isInterface()
                            && !type.isEnum()
                            && !type.isRecord()
                            && !type.isPrimitive()
                            && !type.isArray()
                            && !java.lang.reflect.Modifier.isFinal(type.getModifiers())
                            && type != Enum.class
                            && type != Record.class,
                    "Not a class that can be extended: %s",
                    type);
            superclass(TypeName.of(type));
            nestedNames(type, alwaysQualifiedNames);
            return this;
        }

        /**
         * Sets the superclass of a class, and qualifies the names of the member types it brings.
         *
         * @param type the superclass
         * @return this builder
         * @throws IllegalArgumentException if the type is not a declared type, or is one that cannot be extended: an
         *     interface, an enum, a record, a final class, {@code Enum} or {@code Record}; or it has no name, as
         *     {@link TypeName#of(TypeMirror)} names it
         * @throws IllegalStateException if this is not a class, the superclass is already set, or this is an anonymous
         *     class that implements an interface
         */
        public Builder superclass(TypeMirror type) {
            Objects.requireNonNull(type, "type");
            if (type.getKind() == TypeKind.DECLARED) {
                TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
                checkArgument(
                        element.getKind() == ElementKind.CLASS
                                && !element.getModifiers().contains(Modifier.FINAL)
                                && !element.getQualifiedName().contentEquals("java.lang.Enum")
                                && !element.getQualifiedName().contentEquals("java.lang.Record"),
                        "Not a class that can be extended: %s",
                        type);
            }
            superclass(TypeName.of(type));
            if (type.getKind() == TypeKind.DECLARED) {
                nestedNames((TypeElement) ((DeclaredType) type).asElement(), alwaysQualifiedNames);
            }
            return this;
        }

        /**
         * Adds an interface this type implements, or extends for an interface. The member types it brings are not
         * known: see {@link #avoidClashesWithNestedClasses(TypeElement)}.
         *
         * @param type the interface
         * @return this builder
         * @throws IllegalArgumentException if the type is not a class or a parameterized type
         * @throws IllegalStateException if this is an annotation type, or an anonymous class that extends a class or
         *     implements an interface already
         */
        public Builder addSuperinterface(TypeName type) {
            Objects.requireNonNull(type, "type");
            return addSuperinterfaces(List.of(type));
        }

        /**
         * Adds an interface this type implements, or extends for an interface, and qualifies the names of the member
         * types it brings.
         *
         * @param type the interface
         * @return this builder
         * @throws IllegalArgumentException if the class is not an interface, or is local or anonymous
         * @throws IllegalStateException if this is an annotation type, or an anonymous class that extends a class or
         *     implements an interface already
         */
        public Builder addSuperinterface(Class<?> type) {
            Objects.requireNonNull(type, "type");
            checkArgument(type.isInterface(), "Not an interface: %s", type);
            addSuperinterface(TypeName.of(type));
            nestedNames(type, alwaysQualifiedNames);
            return this;
        }

        /**
         * Adds an interface this type implements, or extends for an interface, and qualifies the names of the member
         * types it brings.
         *
         * @param type the interface
         * @return this builder
         * @throws IllegalArgumentException if the type is not a declared type, or is one that is not an interface; or
         *     it has no name, as {@link TypeName#of(TypeMirror)} names it
         * @throws IllegalStateException if this is an annotation type, or an anonymous class that extends a class or
         *     implements an interface already
         */
        public Builder addSuperinterface(TypeMirror type) {
            Objects.requireNonNull(type, "type");
            if (type.getKind() == TypeKind.DECLARED) {
                ElementKind elementKind = ((DeclaredType) type).asElement().getKind();
                checkArgument(
                        elementKind == ElementKind.INTERFACE || elementKind == ElementKind.ANNOTATION_TYPE,
                        "Not an interface: %s",
                        type);
            }
            addSuperinterface(TypeName.of(type));
            if (type.getKind() == TypeKind.DECLARED) {
                nestedNames((TypeElement) ((DeclaredType) type).asElement(), alwaysQualifiedNames);
            }
            return this;
        }

        /**
         * Adds interfaces this type implements, or extends for an interface.
         *
         * @param more the interfaces
         * @return this builder
         * @throws IllegalArgumentException if a type is not a class or a parameterized type
         * @throws IllegalStateException if there are interfaces and this is an annotation type, or an anonymous class
         *     that would extend a class and implement an interface, or implement more than one
         */
        public Builder addSuperinterfaces(Iterable<? extends TypeName> more) {
            List<TypeName> added = Checks.elements(more, "more");
            for (TypeName type : added) {
                checkArgument(
                        type instanceof ClassName || type instanceof ParameterizedTypeName,
                        "A superinterface is a class or a parameterized type: %s",
                        type);
            }
            if (!added.isEmpty()) {
                checkState(kind != Kind.ANNOTATION, "An annotation type extends nothing");
                if (name == null) {
                    checkState(
                            superclass == null,
                            "An anonymous class extends a class or implements an interface, not both");
                    checkState(
                            superinterfaces.size() + added.size() <= 1,
                            "An anonymous class implements one interface at most");
                }
            }
            superinterfaces.addAll(added);
            return this;
        }

        /**
         * Adds a subclass this sealed type permits.
         *
         * @param type the subclass
         * @return this builder
         * @throws IllegalArgumentException if the type is not a class, or has type annotations
         * @throws IllegalStateException if this is not a class or an interface, or is an anonymous class
         */
        public Builder addPermittedSubclass(TypeName type) {
            Objects.requireNonNull(type, "type");
            return addPermittedSubclasses(List.of(type));
        }

        /**
         * Adds subclasses this sealed type permits.
         *
         * @param more the subclasses
         * @return this builder
         * @throws IllegalArgumentException if a type is not a class, or has type annotations
         * @throws IllegalStateException if there are subclasses and this is not a class or an interface, or is an
         *     anonymous class
         */
        public Builder addPermittedSubclasses(Iterable<? extends TypeName> more) {
            List<TypeName> added = Checks.elements(more, "more");
            for (TypeName type : added) {
                checkArgument(
                        type instanceof ClassName && !type.isAnnotated(),
                        "A permitted subclass is a class, without type annotations: %s",
                        type);
            }
            if (!added.isEmpty()) {
                checkState(name != null, "An anonymous class has no permitted subclasses");
                checkState(
                        kind == Kind.CLASS || kind == Kind.INTERFACE,
                        "Only a class or an interface permits subclasses");
            }
            permittedSubclasses.addAll(added);
            return this;
        }

        /**
         * Qualifies the names of the member types of a type, and of those it inherits, wherever a type of the same
         * simple name is referred to: for a supertype given as a {@link TypeName}, or a type whose members are in scope
         * some other way.
         *
         * @param type the type
         * @return this builder
         */
        public Builder avoidClashesWithNestedClasses(TypeElement type) {
            nestedNames(Objects.requireNonNull(type, "type"), alwaysQualifiedNames);
            return this;
        }

        /**
         * Qualifies the names of the member types of a class, and of those it inherits, wherever a type of the same
         * simple name is referred to.
         *
         * @param type the class
         * @return this builder
         */
        public Builder avoidClashesWithNestedClasses(Class<?> type) {
            nestedNames(Objects.requireNonNull(type, "type"), alwaysQualifiedNames);
            return this;
        }

        /**
         * Writes the types of these simple names qualified, wherever they are referred to in this type.
         *
         * @param simpleNames the simple names
         * @return this builder
         */
        public Builder alwaysQualify(String... simpleNames) {
            alwaysQualifiedNames.addAll(Checks.elements(simpleNames, "simpleNames"));
            return this;
        }

        /**
         * Adds a constant to an enum.
         *
         * @param constant the name of the constant
         * @return this builder
         * @throws IllegalArgumentException if the name is not a valid identifier, or the enum has a constant of that
         *     name already
         * @throws IllegalStateException if this is not an enum
         */
        public Builder addEnumConstant(String constant) {
            Objects.requireNonNull(constant, "constant");
            return addEnumConstant(constant, newAnonymousClassBuilder("").build());
        }

        /**
         * Adds a constant to an enum, with constructor arguments or a body: the arguments and members of an anonymous
         * class.
         *
         * @param constant the name of the constant
         * @param body an anonymous class without supertypes: its arguments go to the enum constructor, its members to
         *     the constant
         * @return this builder
         * @throws IllegalArgumentException if the name is not a valid identifier, the enum has a constant of that name
         *     already, or the body is not an anonymous class or has a superclass or an interface
         * @throws IllegalStateException if this is not an enum
         */
        public Builder addEnumConstant(String constant, Type body) {
            Objects.requireNonNull(constant, "constant");
            Objects.requireNonNull(body, "body");
            checkArgument(
                    SourceVersion.isIdentifier(constant) && !SourceVersion.isKeyword(constant),
                    "Not an enum constant name: %s",
                    constant);
            checkArgument(body.isAnonymous(), "The body of an enum constant must be an anonymous class");
            checkArgument(
                    body.superclass() == null && body.superinterfaces().isEmpty(),
                    "The body of an enum constant has no superclass or interface: %s",
                    constant);
            checkState(kind == Kind.ENUM, "Only an enum has constants");
            checkArgument(!enumConstants.containsKey(constant), "Duplicate enum constant: %s", constant);
            enumConstants.put(constant, body);
            return this;
        }

        /**
         * Adds a component to a record.
         *
         * @param component the component, without modifiers; its annotations apply to the component
         * @return this builder
         * @throws IllegalArgumentException if the component has modifiers or is a receiver parameter
         * @throws IllegalStateException if this is not a record
         */
        public Builder addRecordComponent(Parameter component) {
            Objects.requireNonNull(component, "component");
            return addRecordComponents(List.of(component));
        }

        /**
         * Adds a component to a record.
         *
         * @param type the type
         * @param name the name
         * @return this builder
         * @throws IllegalArgumentException if the name is not a valid identifier, or the type is {@code void} or a
         *     wildcard
         * @throws IllegalStateException if this is not a record
         */
        public Builder addRecordComponent(TypeName type, String name) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(name, "name");
            return addRecordComponent(Parameter.of(type, name));
        }

        /**
         * Adds a component to a record.
         *
         * @param type the type
         * @param name the name
         * @return this builder
         * @throws IllegalArgumentException if the class is {@code void}, local or anonymous, or the name is not a valid
         *     identifier
         * @throws IllegalStateException if this is not a record
         */
        public Builder addRecordComponent(Class<?> type, String name) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(name, "name");
            return addRecordComponent(TypeName.of(type), name);
        }

        /**
         * Adds components to a record.
         *
         * @param more the components, without modifiers
         * @return this builder
         * @throws IllegalArgumentException if a component has modifiers or is a receiver parameter
         * @throws IllegalStateException if there are components and this is not a record
         */
        public Builder addRecordComponents(Iterable<Parameter> more) {
            List<Parameter> added = Checks.elements(more, "more");
            for (Parameter component : added) {
                checkArgument(!component.isReceiver(), "A record component is not a receiver parameter: %s", component);
                checkArgument(component.modifiers().isEmpty(), "A record component has no modifiers: %s", component);
            }
            checkState(added.isEmpty() || kind == Kind.RECORD, "Only a record has components");
            recordComponents.addAll(added);
            return this;
        }

        /**
         * Makes the last component of a record variable arity, or not. Its type must be an array.
         *
         * @param isVarargs whether the last component is variable arity
         * @return this builder
         * @throws IllegalStateException if this is not a record and {@code isVarargs} is {@code true}
         */
        public Builder varargs(boolean isVarargs) {
            checkState(!isVarargs || kind == Kind.RECORD, "Only a record is varargs");
            varargs = isVarargs;
            return this;
        }

        /**
         * Adds a field.
         *
         * @param field the field
         * @return this builder
         */
        public Builder addField(Field field) {
            fields.add(Objects.requireNonNull(field, "field"));
            return this;
        }

        /**
         * Adds a field without initializer.
         *
         * @param type the type
         * @param name the name
         * @param modifiers the modifiers
         * @return this builder
         * @throws IllegalArgumentException if the name is not a valid identifier, the type is {@code void} or a
         *     wildcard, or a modifier is not allowed on a field, is repeated, or conflicts with another one
         */
        public Builder addField(TypeName type, String name, Modifier... modifiers) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(modifiers, "modifiers");
            return addField(Field.of(type, name, modifiers));
        }

        /**
         * Adds a field without initializer.
         *
         * @param type the type
         * @param name the name
         * @param modifiers the modifiers
         * @return this builder
         * @throws IllegalArgumentException if the class is {@code void}, local or anonymous; the name is not a valid
         *     identifier; or a modifier is not allowed on a field, is repeated, or conflicts with another one
         */
        public Builder addField(Class<?> type, String name, Modifier... modifiers) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(modifiers, "modifiers");
            return addField(TypeName.of(type), name, modifiers);
        }

        /**
         * Adds fields.
         *
         * @param more the fields
         * @return this builder
         */
        public Builder addFields(Iterable<Field> more) {
            fields.addAll(Checks.elements(more, "more"));
            return this;
        }

        /**
         * Adds code to the static initializer.
         *
         * @param code the code
         * @return this builder
         * @throws IllegalStateException if this is an interface or an annotation type
         */
        public Builder addStaticBlock(CodeBlock code) {
            Objects.requireNonNull(code, "code");
            checkState(!kind.isInterface(), "An interface has no static initializer");
            staticBlock.add(code);
            return this;
        }

        /**
         * Adds code to the instance initializer.
         *
         * @param code the code
         * @return this builder
         * @throws IllegalStateException if this is an interface, an annotation type or a record
         */
        public Builder addInitializerBlock(CodeBlock code) {
            Objects.requireNonNull(code, "code");
            checkState(!kind.isInterface(), "An interface has no instance initializer");
            checkState(kind != Kind.RECORD, "A record has no instance initializer");
            initializerBlock.add(code);
            return this;
        }

        /**
         * Adds a method or constructor.
         *
         * @param method the method
         * @return this builder
         * @throws IllegalStateException if the method does not fit the kind of type: a constructor in an interface, an
         *     annotation type or an anonymous class; a compact constructor outside a record; a default method outside
         *     an interface; a default value outside an annotation type; or, in an interface, a method with code that is
         *     not default, static or private, or a method that is protected, final, synchronized or native
         */
        public Builder addMethod(Method method) {
            Objects.requireNonNull(method, "method");
            return addMethods(List.of(method));
        }

        /**
         * Adds methods and constructors.
         *
         * @param more the methods
         * @return this builder
         * @throws IllegalStateException if a method does not fit the kind of type, as {@link #addMethod(Method)} tells
         */
        public Builder addMethods(Iterable<Method> more) {
            List<Method> added = Checks.elements(more, "more");
            added.forEach(this::checkMethod);
            methods.addAll(added);
            return this;
        }

        private void checkMethod(Method method) {
            checkState(
                    kind == Kind.RECORD || !method.isCompactConstructor(), "Only a record has a compact constructor");
            checkState(!method.isConstructor() || !kind.isInterface(), "%s has no constructor", what());
            checkState(!method.isConstructor() || name != null, "An anonymous class has no constructor");
            if (kind.isInterface()) {
                checkState(
                        fitsInterface(method),
                        "A method of an interface with code must be default, static or private: %s",
                        method.name());
                checkState(
                        method.modifiers().stream().noneMatch(INTERFACE_METHOD_FORBIDDEN::contains),
                        "A method of an interface cannot be protected, final, synchronized or native: %s",
                        method.name());
            } else {
                checkState(!has(method, Modifier.DEFAULT), "Only a method of an interface is default");
            }
            checkState(
                    kind == Kind.ANNOTATION || method.defaultValue().isEmpty(),
                    "Only a member of an annotation type has a default value");
        }

        /**
         * Adds a nested type.
         *
         * @param type the nested type
         * @return this builder
         * @throws IllegalArgumentException if the type is anonymous
         */
        public Builder addType(Type type) {
            Objects.requireNonNull(type, "type");
            return addTypes(List.of(type));
        }

        /**
         * Adds nested types.
         *
         * @param more the nested types
         * @return this builder
         * @throws IllegalArgumentException if a type is anonymous
         */
        public Builder addTypes(Iterable<Type> more) {
            List<Type> added = Checks.elements(more, "more");
            for (Type type : added) {
                checkArgument(!type.isAnonymous(), "A nested type must have a name");
            }
            types.addAll(added);
            return this;
        }

        /**
         * Builds the type.
         *
         * @return the type
         * @throws IllegalStateException if it is a varargs record whose last component is not an array; it permits
         *     subclasses but is not sealed; it is a named class that is not abstract but has an abstract method; or its
         *     Javadoc, static initializer or instance initializer is not {@linkplain CodeBlock balanced}. The message
         *     names the type.
         */
        public Type build() {
            checkState(
                    !varargs
                            || (!recordComponents.isEmpty()
                                    && recordComponents.getLast().type() instanceof ArrayTypeName),
                    "The record %s is varargs but its last component is not an array",
                    describe());
            checkState(
                    permittedSubclasses.isEmpty() || modifiers.contains(Modifier.SEALED),
                    "%s permits subclasses but is not sealed",
                    describe());
            checkState(
                    kind != Kind.CLASS
                            || name == null
                            || modifiers.contains(Modifier.ABSTRACT)
                            || methods.stream().noneMatch(method -> has(method, Modifier.ABSTRACT)),
                    "Only an abstract class has abstract methods: %s",
                    describe());
            CodeBlock.checkBalanced(javadoc.build(), "The Javadoc of " + describe());
            CodeBlock.checkBalanced(staticBlock.build(), "The static initializer of " + describe());
            CodeBlock.checkBalanced(initializerBlock.build(), "The instance initializer of " + describe());
            return new Type(this);
        }

        private static boolean has(Method method, Modifier modifier) {
            return method.modifiers().contains(modifier);
        }

        private static boolean fitsInterface(Method method) {
            return method.code().isEmpty()
                    || has(method, Modifier.DEFAULT)
                    || has(method, Modifier.STATIC)
                    || has(method, Modifier.PRIVATE);
        }
    }
}
