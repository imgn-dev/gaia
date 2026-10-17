package be.imgn.gaia.processing.source;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static be.imgn.gaia.core.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Types;
import org.jspecify.annotations.Nullable;

/**
 * A method, a constructor, or the compact constructor of a record.
 *
 * <pre>{@code
 * Method.newBuilder("max")
 *         .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
 *         .returns(int.class)
 *         .addParameter(int.class, "a")
 *         .addParameter(int.class, "b")
 *         .addStatement("return a > b ? a : b")
 *         .build();
 * }</pre>
 *
 * <p>Methods are values: two methods written the same are equal.
 */
public final class Method {

    private static final String CONSTRUCTOR = "<init>";
    private static final String COMPACT_CONSTRUCTOR = "<compact>";
    private static final Set<Modifier> ALLOWED = EnumSet.of(
            Modifier.PUBLIC,
            Modifier.PROTECTED,
            Modifier.PRIVATE,
            Modifier.ABSTRACT,
            Modifier.STATIC,
            Modifier.FINAL,
            Modifier.SYNCHRONIZED,
            Modifier.NATIVE,
            Modifier.STRICTFP,
            Modifier.DEFAULT);
    private static final Set<Modifier> NOT_ABSTRACT = EnumSet.of(
            Modifier.PRIVATE,
            Modifier.STATIC,
            Modifier.FINAL,
            Modifier.SYNCHRONIZED,
            Modifier.NATIVE,
            Modifier.STRICTFP,
            Modifier.DEFAULT);
    // Only an interface has default methods, and its methods cannot be any of these.
    private static final Set<Modifier> NOT_DEFAULT = EnumSet.of(
            Modifier.PROTECTED,
            Modifier.PRIVATE,
            Modifier.STATIC,
            Modifier.FINAL,
            Modifier.SYNCHRONIZED,
            Modifier.NATIVE);

    private final String name;
    private final Set<Modifier> modifiers;
    private final List<Annotation> annotations;
    private final CodeBlock javadoc;
    private final List<TypeVariableName> typeVariables;
    private final @Nullable TypeName returnType;
    private final List<Parameter> parameters;
    private final boolean varargs;
    private final List<TypeName> exceptions;
    private final CodeBlock code;
    private final CodeBlock defaultValue;

    private Method(Builder builder) {
        this.name = builder.name;
        this.modifiers = Modifiers.copy(builder.modifiers);
        this.annotations = List.copyOf(builder.annotations);
        this.javadoc = builder.javadoc.build();
        this.typeVariables = List.copyOf(builder.typeVariables);
        this.returnType = builder.returnType;
        this.parameters = List.copyOf(builder.parameters);
        this.varargs = builder.varargs;
        this.exceptions = List.copyOf(builder.exceptions);
        this.code = builder.code.build();
        this.defaultValue = builder.defaultValue;
    }

    /**
     * Returns a builder of methods, which return {@code void} unless told otherwise.
     *
     * @param name the name of the method
     * @return a new builder
     * @throws IllegalArgumentException if the name is not a valid identifier
     */
    public static Builder newBuilder(String name) {
        requireNonNull(name, "name");
        return new Builder(checkName(name)).returns(PrimitiveTypeName.VOID);
    }

    private static String checkName(String name) {
        if (!SourceVersion.isIdentifier(name) || SourceVersion.isKeyword(name)) {
            throw new IllegalArgumentException("Not a method name: " + name);
        }
        return name;
    }

    /**
     * Returns a builder of constructors.
     *
     * @return a new builder
     */
    public static Builder newConstructorBuilder() {
        return new Builder(CONSTRUCTOR);
    }

    /**
     * Returns a builder of compact constructors, for records: a constructor without parameter list, whose code runs
     * before the components are assigned.
     *
     * @return a new builder
     */
    public static Builder newCompactConstructorBuilder() {
        return new Builder(COMPACT_CONSTRUCTOR);
    }

    /**
     * Returns a builder of a method that overrides another one: the same name, type variables, return type, parameters
     * and exceptions, annotated with {@code @Override}, and without {@code abstract} or {@code default}.
     *
     * @param method the method to override
     * @return a new builder, to add the code to
     * @throws IllegalArgumentException if the method is private, static or final, a member of a final type, or not a
     *     method; or a type in its signature has no name, as {@link TypeName#of(TypeMirror)} names it
     */
    public static Builder overriding(ExecutableElement method) {
        requireNonNull(method, "method");
        checkOverridable(method);
        return overriding(method, (ExecutableType) method.asType());
    }

    /**
     * Returns a builder of a method that overrides another one, as a member of a type: type variables of the type are
     * replaced by its type arguments. For example, overriding {@code Comparable.compareTo(T)} in
     * {@code Comparable<String>} gives {@code compareTo(String)}.
     *
     * @param method the method to override
     * @param enclosing the type the method is a member of, such as {@code Comparable<String>}
     * @param types the type utilities of the processing environment
     * @return a new builder, to add the code to
     * @throws IllegalArgumentException if the method is private, static or final, a member of a final type, or not a
     *     method; it is not a member of the type; or a type in its signature has no name, as
     *     {@link TypeName#of(TypeMirror)} names it
     */
    public static Builder overriding(ExecutableElement method, DeclaredType enclosing, Types types) {
        requireNonNull(method, "method");
        requireNonNull(enclosing, "enclosing");
        requireNonNull(types, "types");
        checkOverridable(method);
        return overriding(method, (ExecutableType) types.asMemberOf(enclosing, method));
    }

    private static Builder overriding(ExecutableElement method, ExecutableType type) {
        Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
        modifiers.addAll(method.getModifiers());
        modifiers.removeAll(Set.of(Modifier.ABSTRACT, Modifier.DEFAULT));
        Builder builder = newBuilder(method.getSimpleName().toString())
                .addAnnotation(Override.class)
                .addModifiers(modifiers.toArray(Modifier[]::new))
                .returns(TypeName.of(type.getReturnType()));
        for (TypeVariable variable : type.getTypeVariables()) {
            builder.addTypeVariable(TypeVariableName.of(variable));
        }
        List<? extends VariableElement> parameters = method.getParameters();
        List<? extends TypeMirror> parameterTypes = type.getParameterTypes();
        for (int i = 0; i < parameters.size(); i++) {
            builder.addParameter(Parameter.of(
                    TypeName.of(parameterTypes.get(i)),
                    parameters.get(i).getSimpleName().toString()));
        }
        builder.varargs(method.isVarArgs());
        type.getThrownTypes().forEach(thrown -> builder.addException(TypeName.of(thrown)));
        return builder;
    }

    private static void checkOverridable(ExecutableElement method) {
        if (method.getKind() != ElementKind.METHOD) {
            throw new IllegalArgumentException(
                    "Only a method can be overridden, not a " + method.getKind() + ": " + method);
        }
        for (Modifier modifier : List.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)) {
            if (method.getModifiers().contains(modifier)) {
                throw new IllegalArgumentException("Cannot override a " + modifier + " method: " + method);
            }
        }
        Element enclosing = method.getEnclosingElement();
        if (enclosing != null && enclosing.getModifiers().contains(Modifier.FINAL)) {
            throw new IllegalArgumentException(
                    "Cannot override a method of the final type " + enclosing + ": " + method);
        }
    }

    /**
     * Returns the name.
     *
     * @return the name of the method; {@code <init>} for a constructor, {@code <compact>} for a compact constructor
     */
    public String name() {
        return name;
    }

    /**
     * Returns whether this is a constructor, compact or not.
     *
     * @return {@code true} for a constructor
     */
    public boolean isConstructor() {
        return name.equals(CONSTRUCTOR) || isCompactConstructor();
    }

    /**
     * Returns whether this is the compact constructor of a record.
     *
     * @return {@code true} for a compact constructor
     */
    public boolean isCompactConstructor() {
        return name.equals(COMPACT_CONSTRUCTOR);
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
     * Returns the return type.
     *
     * @return the return type, or {@code null} for a constructor
     */
    public @Nullable TypeName returnType() {
        return returnType;
    }

    /**
     * Returns the parameters.
     *
     * @return the parameters, in order
     */
    public List<Parameter> parameters() {
        return parameters;
    }

    /**
     * Returns whether the last parameter is variable arity, written {@code String... names}.
     *
     * @return {@code true} for a varargs method
     */
    public boolean isVarargs() {
        return varargs;
    }

    /**
     * Returns the exceptions in the {@code throws} clause.
     *
     * @return the exceptions, in order
     */
    public List<TypeName> exceptions() {
        return exceptions;
    }

    /**
     * Returns the code of the body.
     *
     * @return the code, empty for no body or an empty one
     */
    public CodeBlock code() {
        return code;
    }

    /**
     * Returns the default value of an annotation type member.
     *
     * @return the value after {@code default}, empty if there is none
     */
    public CodeBlock defaultValue() {
        return defaultValue;
    }

    /**
     * Returns a builder that starts with this method.
     *
     * @return a new builder
     */
    public Builder toBuilder() {
        Builder builder = new Builder(name);
        builder.modifiers.addAll(modifiers);
        builder.annotations.addAll(annotations);
        builder.javadoc.add(javadoc);
        builder.typeVariables.addAll(typeVariables);
        builder.returnType = returnType;
        builder.parameters.addAll(parameters);
        builder.varargs = varargs;
        builder.exceptions.addAll(exceptions);
        builder.code.add(code);
        builder.defaultValue = defaultValue;
        return builder;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Method method && toString().equals(method.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        return CodeWriter.standalone(this);
    }

    /** A builder of {@link Method}s. */
    public static final class Builder {

        private String name;
        private final Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
        private final List<Annotation> annotations = new ArrayList<>();
        private final CodeBlock.Builder javadoc = CodeBlock.newBuilder();
        private final List<TypeVariableName> typeVariables = new ArrayList<>();
        private @Nullable TypeName returnType;
        private final List<Parameter> parameters = new ArrayList<>();
        private boolean varargs;
        private final List<TypeName> exceptions = new ArrayList<>();
        private final CodeBlock.Builder code = CodeBlock.newBuilder();
        private CodeBlock defaultValue = CodeBlock.empty();

        private Builder(String name) {
            this.name = name;
        }

        /**
         * Renames the method, such as a copy made with {@link Method#toBuilder()}.
         *
         * @param newName the new name
         * @return this builder
         * @throws IllegalArgumentException if the name is not a valid identifier
         * @throws IllegalStateException if this is a constructor
         */
        public Builder name(String newName) {
            requireNonNull(newName, "newName");
            if (isConstructor()) {
                throw new IllegalStateException("A constructor has no name");
            }
            name = checkName(newName);
            return this;
        }

        /**
         * Adds modifiers.
         *
         * @param more the modifiers
         * @return this builder
         * @throws IllegalArgumentException if a modifier is not allowed on a method, or on a constructor if this is
         *     one; is repeated; or conflicts with another one, such as {@code public} with {@code private} or
         *     {@code abstract} with {@code final}
         * @throws IllegalStateException if a modifier is {@code abstract} or {@code native} and the method has code, or
         *     it is {@code static} and the method has a receiver parameter
         */
        public Builder addModifiers(Iterable<Modifier> more) {
            String what = isConstructor() ? "A constructor" : "A method";
            return addCheckedModifiers(Modifiers.added(more, "more", what));
        }

        /**
         * Adds modifiers.
         *
         * @param more the modifiers
         * @return this builder
         * @throws IllegalArgumentException if a modifier is not allowed on a method, or on a constructor if this is
         *     one; is repeated; or conflicts with another one, such as {@code public} with {@code private} or
         *     {@code abstract} with {@code final}
         * @throws IllegalStateException if a modifier is {@code abstract} or {@code native} and the method has code, or
         *     it is {@code static} and the method has a receiver parameter
         */
        public Builder addModifiers(Modifier... more) {
            String what = isConstructor() ? "A constructor" : "A method";
            return addCheckedModifiers(Modifiers.added(more, "more", what));
        }

        private Builder addCheckedModifiers(Set<Modifier> added) {
            String what = isConstructor() ? "A constructor" : "A method";
            Modifiers.requireOnly(added, isConstructor() ? Modifiers.ACCESS : ALLOWED, what);
            Set<Modifier> all = Modifiers.union(modifiers, added);
            Modifiers.requireAtMostOne(all, Modifiers.ACCESS, what);
            Modifiers.requireNoneWith(all, Modifier.ABSTRACT, NOT_ABSTRACT, what);
            Modifiers.requireNoneWith(all, Modifier.NATIVE, Set.of(Modifier.STRICTFP), what);
            Modifiers.requireNoneWith(all, Modifier.DEFAULT, NOT_DEFAULT, what);
            for (Modifier modifier : List.of(Modifier.ABSTRACT, Modifier.NATIVE)) {
                checkState(
                        !added.contains(modifier) || code.isEmpty(),
                        "A method that is %s has no code: %s",
                        modifier,
                        name);
            }
            if (added.contains(Modifier.STATIC)) {
                checkReceivers(parameters, true);
            }
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
            annotations.add(requireNonNull(annotation, "annotation"));
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
            requireNonNull(annotation, "annotation");
            return addAnnotation(Annotation.of(annotation));
        }

        /**
         * Adds an annotation without members.
         *
         * @param annotation the annotation type
         * @return this builder
         */
        public Builder addAnnotation(ClassName annotation) {
            requireNonNull(annotation, "annotation");
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
            requireNonNull(format, "format");
            requireNonNull(args, "args");
            javadoc.add(format, args);
            return this;
        }

        /**
         * Adds Javadoc text. End each line with {@code \n}.
         *
         * @param text the text
         * @return this builder
         */
        public Builder addJavadoc(CodeBlock text) {
            requireNonNull(text, "text");
            javadoc.add(text);
            return this;
        }

        /**
         * Declares a type variable, such as {@code T} in {@code <T> T first(List<T> list)}.
         *
         * @param typeVariable the type variable, with its bounds
         * @return this builder
         * @throws IllegalStateException if this is a compact constructor
         */
        public Builder addTypeVariable(TypeVariableName typeVariable) {
            requireNonNull(typeVariable, "typeVariable");
            return addTypeVariables(List.of(typeVariable));
        }

        /**
         * Declares type variables.
         *
         * @param more the type variables, with their bounds
         * @return this builder
         * @throws IllegalStateException if this is a compact constructor and there are type variables
         */
        public Builder addTypeVariables(Iterable<TypeVariableName> more) {
            List<TypeVariableName> added = Checks.elements(more, "more");
            checkState(
                    added.isEmpty() || !name.equals(COMPACT_CONSTRUCTOR),
                    "A compact constructor has no type variables");
            typeVariables.addAll(added);
            return this;
        }

        /**
         * Sets the return type.
         *
         * @param type the return type
         * @return this builder
         * @throws IllegalArgumentException if the type is a wildcard
         * @throws IllegalStateException if this is a constructor
         */
        public Builder returns(TypeName type) {
            requireNonNull(type, "type");
            checkArgument(!(type instanceof WildcardTypeName), "A return type cannot be a wildcard: %s", type);
            if (isConstructor()) {
                throw new IllegalStateException("A constructor has no return type");
            }
            returnType = type;
            return this;
        }

        /**
         * Sets the return type.
         *
         * @param type the return type
         * @return this builder
         * @throws IllegalArgumentException if the class is local or anonymous
         * @throws IllegalStateException if this is a constructor
         */
        public Builder returns(Class<?> type) {
            requireNonNull(type, "type");
            return returns(TypeName.of(type));
        }

        /**
         * Adds a parameter.
         *
         * @param parameter the parameter
         * @return this builder
         * @throws IllegalStateException if this is a compact constructor, or the parameter is a receiver parameter but
         *     is not first or the method is static
         */
        public Builder addParameter(Parameter parameter) {
            requireNonNull(parameter, "parameter");
            return addParameters(List.of(parameter));
        }

        /**
         * Adds a parameter.
         *
         * @param type the type
         * @param name the name
         * @param modifiers the modifiers: none, or {@code final}
         * @return this builder
         * @throws IllegalArgumentException if the name is not a valid identifier, the type is {@code void} or a
         *     wildcard, or a modifier is not {@code final}, is repeated, or is given to a receiver parameter
         * @throws IllegalStateException if this is a compact constructor, or the parameter is a receiver parameter but
         *     is not first or the method is static
         */
        public Builder addParameter(TypeName type, String name, Modifier... modifiers) {
            requireNonNull(type, "type");
            requireNonNull(name, "name");
            requireNonNull(modifiers, "modifiers");
            return addParameter(Parameter.of(type, name, modifiers));
        }

        /**
         * Adds a parameter.
         *
         * @param type the type
         * @param name the name
         * @param modifiers the modifiers: none, or {@code final}
         * @return this builder
         * @throws IllegalArgumentException if the class is {@code void}, local or anonymous; the name is not a valid
         *     identifier; or a modifier is not {@code final}, is repeated, or is given to a receiver parameter
         * @throws IllegalStateException if this is a compact constructor, or the parameter is a receiver parameter but
         *     is not first or the method is static
         */
        public Builder addParameter(Class<?> type, String name, Modifier... modifiers) {
            requireNonNull(type, "type");
            requireNonNull(name, "name");
            requireNonNull(modifiers, "modifiers");
            return addParameter(TypeName.of(type), name, modifiers);
        }

        /**
         * Adds parameters.
         *
         * @param more the parameters
         * @return this builder
         * @throws IllegalStateException if this is a compact constructor and there are parameters, or a receiver
         *     parameter is not first or the method is static
         */
        public Builder addParameters(Iterable<Parameter> more) {
            List<Parameter> added = Checks.elements(more, "more");
            checkState(
                    added.isEmpty() || !name.equals(COMPACT_CONSTRUCTOR),
                    "A compact constructor has no parameter list");
            List<Parameter> all = new ArrayList<>(parameters);
            all.addAll(added);
            checkReceivers(all, modifiers.contains(Modifier.STATIC));
            parameters.addAll(added);
            return this;
        }

        private void checkReceivers(List<Parameter> all, boolean isStatic) {
            for (int i = 0; i < all.size(); i++) {
                checkState(
                        !all.get(i).isReceiver() || (i == 0 && !isStatic),
                        "A receiver parameter comes first, in an instance method or an inner class constructor: %s",
                        name);
            }
        }

        /**
         * Makes the last parameter variable arity, or not. Its type must be an array.
         *
         * @param isVarargs whether the last parameter is variable arity
         * @return this builder
         */
        public Builder varargs(boolean isVarargs) {
            varargs = isVarargs;
            return this;
        }

        /**
         * Makes the last parameter variable arity. Its type must be an array.
         *
         * @return this builder
         */
        public Builder varargs() {
            return varargs(true);
        }

        /**
         * Adds an exception to the {@code throws} clause, unless it is there already.
         *
         * @param exception the exception type: a class or a type variable
         * @return this builder
         * @throws IllegalArgumentException if the exception type is not a class or a type variable
         * @throws IllegalStateException if this is a compact constructor
         */
        public Builder addException(TypeName exception) {
            requireNonNull(exception, "exception");
            return addExceptions(List.of(exception));
        }

        /**
         * Adds an exception to the {@code throws} clause.
         *
         * @param exception the exception type
         * @return this builder
         * @throws IllegalArgumentException if the class is local or anonymous
         * @throws IllegalStateException if this is a compact constructor
         */
        public Builder addException(Class<? extends Throwable> exception) {
            requireNonNull(exception, "exception");
            return addException(TypeName.of(exception));
        }

        /**
         * Adds exceptions to the {@code throws} clause, except those there already.
         *
         * @param more the exception types: classes or type variables
         * @return this builder
         * @throws IllegalArgumentException if an exception type is not a class or a type variable
         * @throws IllegalStateException if this is a compact constructor and there are exceptions
         */
        public Builder addExceptions(Iterable<? extends TypeName> more) {
            List<TypeName> added = Checks.elements(more, "more");
            for (TypeName exception : added) {
                checkArgument(
                        exception instanceof ClassName || exception instanceof TypeVariableName,
                        "An exception type is a class or a type variable: %s",
                        exception);
            }
            checkState(
                    added.isEmpty() || !name.equals(COMPACT_CONSTRUCTOR), "A compact constructor has no throws clause");
            for (TypeName exception : added) {
                if (!exceptions.contains(exception)) {
                    exceptions.add(exception);
                }
            }
            return this;
        }

        /**
         * Adds code to the body.
         *
         * @param format the code, with placeholders
         * @param args the arguments of the placeholders
         * @return this builder
         * @throws IllegalArgumentException if the format and the arguments do not match
         * @throws IllegalStateException if the method is abstract or native
         */
        public Builder addCode(String format, @Nullable Object... args) {
            requireNonNull(format, "format");
            requireNonNull(args, "args");
            code().add(format, args);
            return this;
        }

        /**
         * Adds code to the body.
         *
         * @param block the code
         * @return this builder
         * @throws IllegalStateException if the method is abstract or native
         */
        public Builder addCode(CodeBlock block) {
            requireNonNull(block, "block");
            code().add(block);
            return this;
        }

        /**
         * Adds code whose placeholders name their arguments; see {@link CodeBlock.Builder#addNamed(String, Map)}.
         *
         * @param format the code, with named placeholders
         * @param arguments the arguments, by name
         * @return this builder
         * @throws IllegalArgumentException if a name is not valid, or a placeholder has no argument
         * @throws IllegalStateException if the method is abstract or native
         */
        public Builder addNamedCode(String format, Map<String, ?> arguments) {
            requireNonNull(format, "format");
            requireNonNull(arguments, "arguments");
            code().addNamed(format, arguments);
            return this;
        }

        /**
         * Adds a statement to the body: the code, a semicolon and a new line.
         *
         * @param format the statement, without its semicolon, with placeholders
         * @param args the arguments of the placeholders
         * @return this builder
         * @throws IllegalArgumentException if the format and the arguments do not match
         * @throws IllegalStateException if the method is abstract or native
         */
        public Builder addStatement(String format, @Nullable Object... args) {
            requireNonNull(format, "format");
            requireNonNull(args, "args");
            code().addStatement(format, args);
            return this;
        }

        /**
         * Adds a statement to the body: the code, a semicolon and a new line.
         *
         * @param statement the statement, without its semicolon
         * @return this builder
         * @throws IllegalStateException if the method is abstract or native
         */
        public Builder addStatement(CodeBlock statement) {
            requireNonNull(statement, "statement");
            code().addStatement(statement);
            return this;
        }

        /**
         * Adds a line comment to the body; see {@link CodeBlock.Builder#addComment(String, Object...)}.
         *
         * @param format the comment, without {@code //}, with placeholders
         * @param args the arguments of the placeholders
         * @return this builder
         * @throws IllegalArgumentException if the format and the arguments do not match, or the comment has a line
         *     break
         * @throws IllegalStateException if the method is abstract or native
         */
        public Builder addComment(String format, @Nullable Object... args) {
            requireNonNull(format, "format");
            code().addComment(format, args);
            return this;
        }

        /**
         * Opens a block in the body; see {@link CodeBlock.Builder#beginControlFlow(String, Object...)}.
         *
         * @param controlFlow the code before the opening brace, with placeholders
         * @param args the arguments of the placeholders
         * @return this builder
         * @throws IllegalArgumentException if the format and the arguments do not match
         * @throws IllegalStateException if the method is abstract or native
         */
        public Builder beginControlFlow(String controlFlow, @Nullable Object... args) {
            requireNonNull(controlFlow, "controlFlow");
            requireNonNull(args, "args");
            code().beginControlFlow(controlFlow, args);
            return this;
        }

        /**
         * Opens a block in the body; see {@link CodeBlock.Builder#beginControlFlow(CodeBlock)}.
         *
         * @param controlFlow the code before the opening brace
         * @return this builder
         * @throws IllegalStateException if the method is abstract or native
         */
        public Builder beginControlFlow(CodeBlock controlFlow) {
            requireNonNull(controlFlow, "controlFlow");
            code().beginControlFlow(controlFlow);
            return this;
        }

        /**
         * Closes a block in the body and opens the next one; see {@link CodeBlock.Builder#nextControlFlow(String,
         * Object...)}.
         *
         * @param controlFlow the code between the braces, with placeholders
         * @param args the arguments of the placeholders
         * @return this builder
         * @throws IllegalArgumentException if the format and the arguments do not match
         * @throws IllegalStateException if the method is abstract or native
         */
        public Builder nextControlFlow(String controlFlow, @Nullable Object... args) {
            requireNonNull(controlFlow, "controlFlow");
            requireNonNull(args, "args");
            code().nextControlFlow(controlFlow, args);
            return this;
        }

        /**
         * Closes a block in the body and opens the next one; see {@link CodeBlock.Builder#nextControlFlow(CodeBlock)}.
         *
         * @param controlFlow the code between the braces
         * @return this builder
         * @throws IllegalStateException if the method is abstract or native
         */
        public Builder nextControlFlow(CodeBlock controlFlow) {
            requireNonNull(controlFlow, "controlFlow");
            code().nextControlFlow(controlFlow);
            return this;
        }

        /**
         * Closes a block in the body.
         *
         * @return this builder
         * @throws IllegalStateException if the method is abstract or native
         */
        public Builder endControlFlow() {
            code().endControlFlow();
            return this;
        }

        /**
         * Closes a block in the body with code after its closing brace; see
         * {@link CodeBlock.Builder#endControlFlow(String, Object...)}.
         *
         * @param controlFlow the code after the closing brace, without its semicolon, with placeholders
         * @param args the arguments of the placeholders
         * @return this builder
         * @throws IllegalArgumentException if the format and the arguments do not match
         * @throws IllegalStateException if the method is abstract or native
         */
        public Builder endControlFlow(String controlFlow, @Nullable Object... args) {
            requireNonNull(controlFlow, "controlFlow");
            requireNonNull(args, "args");
            code().endControlFlow(controlFlow, args);
            return this;
        }

        /**
         * Closes a block in the body with code after its closing brace; see
         * {@link CodeBlock.Builder#endControlFlow(CodeBlock)}.
         *
         * @param controlFlow the code after the closing brace, without its semicolon
         * @return this builder
         * @throws IllegalStateException if the method is abstract or native
         */
        public Builder endControlFlow(CodeBlock controlFlow) {
            requireNonNull(controlFlow, "controlFlow");
            code().endControlFlow(controlFlow);
            return this;
        }

        // The body, to add code to: an abstract or native method has none.
        private CodeBlock.Builder code() {
            for (Modifier modifier : List.of(Modifier.ABSTRACT, Modifier.NATIVE)) {
                checkState(!modifiers.contains(modifier), "A method that is %s has no code: %s", modifier, name);
            }
            return code;
        }

        /**
         * Sets the default value of an annotation type member.
         *
         * @param format the value after {@code default}, with placeholders
         * @param args the arguments of the placeholders
         * @return this builder
         * @throws IllegalArgumentException if the format and the arguments do not match, or the value is not
         *     {@linkplain CodeBlock balanced}
         * @throws IllegalStateException if this is a constructor
         */
        public Builder defaultValue(String format, @Nullable Object... args) {
            requireNonNull(format, "format");
            requireNonNull(args, "args");
            CodeBlock value = CodeBlock.requireBalanced(CodeBlock.of(format, args), "The default value of " + name);
            checkState(!isConstructor(), "A constructor has no default value");
            defaultValue = value;
            return this;
        }

        /**
         * Builds the method.
         *
         * @return the method
         * @throws IllegalStateException if it is varargs but its last parameter is not an array, or its Javadoc or code
         *     is not {@linkplain CodeBlock balanced}
         */
        public Method build() {
            if (varargs && (parameters.isEmpty() || !(parameters.getLast().type() instanceof ArrayTypeName))) {
                throw new IllegalStateException("The last parameter of a varargs method must be an array: " + name);
            }
            CodeBlock.checkBalanced(javadoc.build(), "The Javadoc of " + name);
            CodeBlock.checkBalanced(code.build(), "The code of " + name);
            return new Method(this);
        }

        private boolean isConstructor() {
            return name.equals(CONSTRUCTOR) || name.equals(COMPACT_CONSTRUCTOR);
        }
    }
}
