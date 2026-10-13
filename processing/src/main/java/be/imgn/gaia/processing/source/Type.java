package be.imgn.gaia.processing.source;

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
        return new Builder(Kind.CLASS, checkName(name));
    }

    /**
     * Returns a builder of classes.
     *
     * @param name the name, of which only the simple name is used
     * @return a new builder
     */
    public static Builder newClassBuilder(ClassName name) {
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
        return new Builder(Kind.INTERFACE, checkName(name));
    }

    /**
     * Returns a builder of interfaces.
     *
     * @param name the name, of which only the simple name is used
     * @return a new builder
     */
    public static Builder newInterfaceBuilder(ClassName name) {
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
        return new Builder(Kind.ENUM, checkName(name));
    }

    /**
     * Returns a builder of enums.
     *
     * @param name the name, of which only the simple name is used
     * @return a new builder
     */
    public static Builder newEnumBuilder(ClassName name) {
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
        return new Builder(Kind.RECORD, checkName(name));
    }

    /**
     * Returns a builder of records.
     *
     * @param name the name, of which only the simple name is used
     * @return a new builder
     */
    public static Builder newRecordBuilder(ClassName name) {
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
        return new Builder(Kind.ANNOTATION, checkName(name));
    }

    /**
     * Returns a builder of annotation types.
     *
     * @param name the name, of which only the simple name is used
     * @return a new builder
     */
    public static Builder newAnnotationBuilder(ClassName name) {
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
     * @throws IllegalArgumentException if the format and the arguments do not match
     */
    public static Builder newAnonymousClassBuilder(String format, @Nullable Object... args) {
        Builder builder = new Builder(Kind.CLASS, null);
        builder.anonymousArguments = CodeBlock.of(format, args);
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

        /**
         * Adds modifiers, such as {@code public}, {@code sealed} or {@code non-sealed}.
         *
         * @param more the modifiers
         * @return this builder
         */
        public Builder addModifiers(Modifier... more) {
            modifiers.addAll(List.of(more));
            return this;
        }

        /**
         * Adds modifiers.
         *
         * @param more the modifiers
         * @return this builder
         */
        public Builder addModifiers(Iterable<Modifier> more) {
            more.forEach(modifiers::add);
            return this;
        }

        /**
         * Adds an annotation.
         *
         * @param annotation the annotation
         * @return this builder
         */
        public Builder addAnnotation(Annotation annotation) {
            annotations.add(annotation);
            return this;
        }

        /**
         * Adds an annotation without members.
         *
         * @param annotation the annotation type
         * @return this builder
         */
        public Builder addAnnotation(Class<? extends java.lang.annotation.Annotation> annotation) {
            return addAnnotation(Annotation.of(annotation));
        }

        /**
         * Adds an annotation without members.
         *
         * @param annotation the annotation type
         * @return this builder
         */
        public Builder addAnnotation(ClassName annotation) {
            return addAnnotation(Annotation.of(annotation));
        }

        /**
         * Adds annotations.
         *
         * @param more the annotations
         * @return this builder
         */
        public Builder addAnnotations(Iterable<Annotation> more) {
            more.forEach(annotations::add);
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
            javadoc.add(format, args);
            return this;
        }

        /**
         * Declares a type variable, such as {@code T} in {@code class Box<T>}.
         *
         * @param typeVariable the type variable, with its bounds
         * @return this builder
         */
        public Builder addTypeVariable(TypeVariableName typeVariable) {
            typeVariables.add(typeVariable);
            return this;
        }

        /**
         * Declares type variables.
         *
         * @param more the type variables, with their bounds
         * @return this builder
         */
        public Builder addTypeVariables(Iterable<TypeVariableName> more) {
            more.forEach(typeVariables::add);
            return this;
        }

        /**
         * Sets the superclass of a class. The member types it brings are not known: see
         * {@link #avoidClashesWithNestedClasses(TypeElement)}.
         *
         * @param type the superclass
         * @return this builder
         * @throws IllegalArgumentException if the type is not a class or a parameterized type
         * @throws IllegalStateException if the superclass is already set
         */
        public Builder superclass(TypeName type) {
            if (!(type instanceof ClassName || type instanceof ParameterizedTypeName)) {
                throw new IllegalArgumentException("A superclass is a class or a parameterized type: " + type);
            }
            if (superclass != null) {
                throw new IllegalStateException("The superclass is already set: " + superclass);
            }
            superclass = type;
            return this;
        }

        /**
         * Sets the superclass of a class, and qualifies the names of the member types it brings.
         *
         * @param type the superclass
         * @return this builder
         * @throws IllegalArgumentException if the type is not a class
         * @throws IllegalStateException if the superclass is already set
         */
        public Builder superclass(Class<?> type) {
            superclass(TypeName.of(type));
            nestedNames(type, alwaysQualifiedNames);
            return this;
        }

        /**
         * Sets the superclass of a class, and qualifies the names of the member types it brings.
         *
         * @param type the superclass
         * @return this builder
         * @throws IllegalArgumentException if the type is not a declared type
         * @throws IllegalStateException if the superclass is already set
         */
        public Builder superclass(TypeMirror type) {
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
         */
        public Builder addSuperinterface(TypeName type) {
            superinterfaces.add(type);
            return this;
        }

        /**
         * Adds an interface this type implements, or extends for an interface, and qualifies the names of the member
         * types it brings.
         *
         * @param type the interface
         * @return this builder
         */
        public Builder addSuperinterface(Class<?> type) {
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
         */
        public Builder addSuperinterface(TypeMirror type) {
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
         */
        public Builder addSuperinterfaces(Iterable<? extends TypeName> more) {
            more.forEach(superinterfaces::add);
            return this;
        }

        /**
         * Adds a subclass this sealed type permits.
         *
         * @param type the subclass
         * @return this builder
         */
        public Builder addPermittedSubclass(TypeName type) {
            permittedSubclasses.add(type);
            return this;
        }

        /**
         * Adds subclasses this sealed type permits.
         *
         * @param more the subclasses
         * @return this builder
         */
        public Builder addPermittedSubclasses(Iterable<? extends TypeName> more) {
            more.forEach(permittedSubclasses::add);
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
            nestedNames(type, alwaysQualifiedNames);
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
            nestedNames(type, alwaysQualifiedNames);
            return this;
        }

        /**
         * Writes the types of these simple names qualified, wherever they are referred to in this type.
         *
         * @param simpleNames the simple names
         * @return this builder
         */
        public Builder alwaysQualify(String... simpleNames) {
            alwaysQualifiedNames.addAll(List.of(simpleNames));
            return this;
        }

        /**
         * Adds a constant to an enum.
         *
         * @param constant the name of the constant
         * @return this builder
         * @throws IllegalArgumentException if the name is not a valid identifier
         */
        public Builder addEnumConstant(String constant) {
            return addEnumConstant(constant, newAnonymousClassBuilder("").build());
        }

        /**
         * Adds a constant to an enum, with constructor arguments or a body: the arguments and members of an anonymous
         * class.
         *
         * @param constant the name of the constant
         * @param body an anonymous class: its arguments go to the enum constructor, its members to the constant
         * @return this builder
         * @throws IllegalArgumentException if the name is not a valid identifier, or the body is not an anonymous class
         */
        public Builder addEnumConstant(String constant, Type body) {
            checkName(constant);
            if (!body.isAnonymous()) {
                throw new IllegalArgumentException("The body of an enum constant must be an anonymous class");
            }
            enumConstants.put(constant, body);
            return this;
        }

        /**
         * Adds a component to a record.
         *
         * @param component the component; its annotations apply to the component
         * @return this builder
         */
        public Builder addRecordComponent(Parameter component) {
            recordComponents.add(component);
            return this;
        }

        /**
         * Adds a component to a record.
         *
         * @param type the type
         * @param name the name
         * @return this builder
         * @throws IllegalArgumentException if the name is not a valid identifier
         */
        public Builder addRecordComponent(TypeName type, String name) {
            return addRecordComponent(Parameter.of(type, name));
        }

        /**
         * Adds a component to a record.
         *
         * @param type the type
         * @param name the name
         * @return this builder
         * @throws IllegalArgumentException if the name is not a valid identifier
         */
        public Builder addRecordComponent(Class<?> type, String name) {
            return addRecordComponent(TypeName.of(type), name);
        }

        /**
         * Adds components to a record.
         *
         * @param more the components
         * @return this builder
         */
        public Builder addRecordComponents(Iterable<Parameter> more) {
            more.forEach(recordComponents::add);
            return this;
        }

        /**
         * Makes the last component of a record variable arity, or not. Its type must be an array.
         *
         * @param isVarargs whether the last component is variable arity
         * @return this builder
         */
        public Builder varargs(boolean isVarargs) {
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
            fields.add(field);
            return this;
        }

        /**
         * Adds a field without initializer.
         *
         * @param type the type
         * @param name the name
         * @param modifiers the modifiers
         * @return this builder
         * @throws IllegalArgumentException if the name is not a valid identifier, or a modifier is not allowed on a
         *     field
         */
        public Builder addField(TypeName type, String name, Modifier... modifiers) {
            return addField(Field.of(type, name, modifiers));
        }

        /**
         * Adds a field without initializer.
         *
         * @param type the type
         * @param name the name
         * @param modifiers the modifiers
         * @return this builder
         * @throws IllegalArgumentException if the name is not a valid identifier, or a modifier is not allowed on a
         *     field
         */
        public Builder addField(Class<?> type, String name, Modifier... modifiers) {
            return addField(TypeName.of(type), name, modifiers);
        }

        /**
         * Adds fields.
         *
         * @param more the fields
         * @return this builder
         */
        public Builder addFields(Iterable<Field> more) {
            more.forEach(fields::add);
            return this;
        }

        /**
         * Adds code to the static initializer.
         *
         * @param code the code
         * @return this builder
         */
        public Builder addStaticBlock(CodeBlock code) {
            staticBlock.add(code);
            return this;
        }

        /**
         * Adds code to the instance initializer.
         *
         * @param code the code
         * @return this builder
         */
        public Builder addInitializerBlock(CodeBlock code) {
            initializerBlock.add(code);
            return this;
        }

        /**
         * Adds a method or constructor.
         *
         * @param method the method
         * @return this builder
         */
        public Builder addMethod(Method method) {
            methods.add(method);
            return this;
        }

        /**
         * Adds methods and constructors.
         *
         * @param more the methods
         * @return this builder
         */
        public Builder addMethods(Iterable<Method> more) {
            more.forEach(methods::add);
            return this;
        }

        /**
         * Adds a nested type.
         *
         * @param type the nested type
         * @return this builder
         * @throws IllegalArgumentException if the type is anonymous
         */
        public Builder addType(Type type) {
            if (type.isAnonymous()) {
                throw new IllegalArgumentException("A nested type must have a name");
            }
            types.add(type);
            return this;
        }

        /**
         * Adds nested types.
         *
         * @param more the nested types
         * @return this builder
         * @throws IllegalArgumentException if a type is anonymous
         */
        public Builder addTypes(Iterable<Type> more) {
            more.forEach(this::addType);
            return this;
        }

        /**
         * Builds the type.
         *
         * @return the type
         * @throws IllegalStateException if a member does not fit the kind of type: a superclass outside a class, enum
         *     constants outside an enum, components or varargs outside a record, a compact constructor outside a
         *     record, permitted subclasses on an enum, record or annotation type, an instance initializer in an
         *     interface, a default method outside an interface, a default value outside an annotation type, an abstract
         *     method in a class that is not abstract, or a modifier or type variable on an anonymous class
         */
        public Type build() {
            checkKind();
            checkMethods();
            checkAnonymous();
            return new Type(this);
        }

        private void checkKind() {
            require(superclass == null || kind == Kind.CLASS, "Only a class has a superclass");
            require(enumConstants.isEmpty() || kind == Kind.ENUM, "Only an enum has constants");
            require(recordComponents.isEmpty() || kind == Kind.RECORD, "Only a record has components");
            require(
                    !varargs
                            || (kind == Kind.RECORD
                                    && !recordComponents.isEmpty()
                                    && recordComponents.getLast().type() instanceof ArrayTypeName),
                    "Only a record whose last component is an array is varargs");
            require(
                    permittedSubclasses.isEmpty() || kind == Kind.CLASS || kind == Kind.INTERFACE,
                    "Only a class or an interface permits subclasses");
            require(!kind.isInterface() || initializerBlock.isEmpty(), "An interface has no instance initializer");
            require(kind != Kind.ANNOTATION || superinterfaces.isEmpty(), "An annotation type extends nothing");
        }

        private void checkMethods() {
            require(
                    kind == Kind.RECORD || methods.stream().noneMatch(Method::isCompactConstructor),
                    "Only a record has a compact constructor");
            require(
                    !kind.isInterface() || methods.stream().allMatch(Builder::fitsInterface),
                    "A method of an interface with code must be default, static or private, and a private one cannot"
                            + " be public, abstract or default");
            require(
                    kind.isInterface() || methods.stream().noneMatch(method -> has(method, Modifier.DEFAULT)),
                    "Only a method of an interface is default");
            require(
                    kind == Kind.ANNOTATION
                            || methods.stream()
                                    .allMatch(method -> method.defaultValue().isEmpty()),
                    "Only a member of an annotation type has a default value");
            require(
                    kind != Kind.CLASS
                            || name == null
                            || modifiers.contains(Modifier.ABSTRACT)
                            || methods.stream().noneMatch(method -> has(method, Modifier.ABSTRACT)),
                    "Only an abstract class has abstract methods: " + name);
        }

        private static boolean has(Method method, Modifier modifier) {
            return method.modifiers().contains(modifier);
        }

        private void checkAnonymous() {
            if (name == null) {
                require(
                        modifiers.isEmpty() && typeVariables.isEmpty() && permittedSubclasses.isEmpty(),
                        "An anonymous class has no modifiers, type variables or permitted subclasses");
                require(
                        superclass == null || superinterfaces.isEmpty(),
                        "An anonymous class extends a class or implements an interface, not both");
                require(superinterfaces.size() <= 1, "An anonymous class implements one interface at most");
            }
        }

        private static boolean fitsInterface(Method method) {
            if (has(method, Modifier.PRIVATE)) {
                return !has(method, Modifier.PUBLIC)
                        && !has(method, Modifier.ABSTRACT)
                        && !has(method, Modifier.DEFAULT);
            }
            return method.code().isEmpty() || has(method, Modifier.DEFAULT) || has(method, Modifier.STATIC);
        }

        private static void require(boolean condition, String message) {
            if (!condition) {
                throw new IllegalStateException(message);
            }
        }
    }
}
