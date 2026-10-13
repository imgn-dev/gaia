package be.imgn.gaia.processing.source;

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
     *     method
     */
    public static Builder overriding(ExecutableElement method) {
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
     *     method
     */
    public static Builder overriding(ExecutableElement method, DeclaredType enclosing, Types types) {
        return overriding(method, (ExecutableType) types.asMemberOf(enclosing, method));
    }

    private static Builder overriding(ExecutableElement method, ExecutableType type) {
        checkOverridable(method);
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
         */
        public Builder addModifiers(Iterable<Modifier> more) {
            more.forEach(modifiers::add);
            return this;
        }

        /**
         * Adds modifiers.
         *
         * @param more the modifiers
         * @return this builder
         */
        public Builder addModifiers(Modifier... more) {
            modifiers.addAll(List.of(more));
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
         * Declares a type variable, such as {@code T} in {@code <T> T first(List<T> list)}.
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
         * Sets the return type.
         *
         * @param type the return type
         * @return this builder
         * @throws IllegalStateException if this is a constructor
         */
        public Builder returns(TypeName type) {
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
         * @throws IllegalStateException if this is a constructor
         */
        public Builder returns(Class<?> type) {
            return returns(TypeName.of(type));
        }

        /**
         * Adds a parameter.
         *
         * @param parameter the parameter
         * @return this builder
         */
        public Builder addParameter(Parameter parameter) {
            parameters.add(parameter);
            return this;
        }

        /**
         * Adds a parameter.
         *
         * @param type the type
         * @param name the name
         * @param modifiers the modifiers: none, or {@code final}
         * @return this builder
         * @throws IllegalArgumentException if the name is not a valid identifier, or a modifier is not {@code final}
         */
        public Builder addParameter(TypeName type, String name, Modifier... modifiers) {
            return addParameter(Parameter.of(type, name, modifiers));
        }

        /**
         * Adds a parameter.
         *
         * @param type the type
         * @param name the name
         * @param modifiers the modifiers: none, or {@code final}
         * @return this builder
         * @throws IllegalArgumentException if the name is not a valid identifier, or a modifier is not {@code final}
         */
        public Builder addParameter(Class<?> type, String name, Modifier... modifiers) {
            return addParameter(TypeName.of(type), name, modifiers);
        }

        /**
         * Adds parameters.
         *
         * @param more the parameters
         * @return this builder
         */
        public Builder addParameters(Iterable<Parameter> more) {
            more.forEach(parameters::add);
            return this;
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
         * Adds an exception to the {@code throws} clause, unless it is there already.
         *
         * @param exception the exception type
         * @return this builder
         */
        public Builder addException(TypeName exception) {
            if (!exceptions.contains(exception)) {
                exceptions.add(exception);
            }
            return this;
        }

        /**
         * Adds an exception to the {@code throws} clause.
         *
         * @param exception the exception type
         * @return this builder
         */
        public Builder addException(Class<? extends Throwable> exception) {
            return addException(TypeName.of(exception));
        }

        /**
         * Adds exceptions to the {@code throws} clause, except those there already.
         *
         * @param more the exception types
         * @return this builder
         */
        public Builder addExceptions(Iterable<? extends TypeName> more) {
            more.forEach(this::addException);
            return this;
        }

        /**
         * Adds code to the body.
         *
         * @param format the code, with placeholders
         * @param args the arguments of the placeholders
         * @return this builder
         * @throws IllegalArgumentException if the format and the arguments do not match
         */
        public Builder addCode(String format, @Nullable Object... args) {
            code.add(format, args);
            return this;
        }

        /**
         * Adds code to the body.
         *
         * @param block the code
         * @return this builder
         */
        public Builder addCode(CodeBlock block) {
            code.add(block);
            return this;
        }

        /**
         * Adds code whose placeholders name their arguments; see {@link CodeBlock.Builder#addNamed(String, Map)}.
         *
         * @param format the code, with named placeholders
         * @param arguments the arguments, by name
         * @return this builder
         * @throws IllegalArgumentException if a name is not valid, or a placeholder has no argument
         */
        public Builder addNamedCode(String format, Map<String, ?> arguments) {
            code.addNamed(format, arguments);
            return this;
        }

        /**
         * Adds a statement to the body: the code, a semicolon and a new line.
         *
         * @param format the statement, without its semicolon, with placeholders
         * @param args the arguments of the placeholders
         * @return this builder
         * @throws IllegalArgumentException if the format and the arguments do not match
         */
        public Builder addStatement(String format, @Nullable Object... args) {
            code.addStatement(format, args);
            return this;
        }

        /**
         * Adds a statement to the body: the code, a semicolon and a new line.
         *
         * @param statement the statement, without its semicolon
         * @return this builder
         */
        public Builder addStatement(CodeBlock statement) {
            code.addStatement(statement);
            return this;
        }

        /**
         * Adds a line comment to the body.
         *
         * @param comment the comment, without {@code //}
         * @return this builder
         */
        public Builder addComment(String comment) {
            code.addComment(comment);
            return this;
        }

        /**
         * Opens a block in the body; see {@link CodeBlock.Builder#beginControlFlow(String, Object...)}.
         *
         * @param controlFlow the code before the opening brace, with placeholders
         * @param args the arguments of the placeholders
         * @return this builder
         * @throws IllegalArgumentException if the format and the arguments do not match
         */
        public Builder beginControlFlow(String controlFlow, @Nullable Object... args) {
            code.beginControlFlow(controlFlow, args);
            return this;
        }

        /**
         * Opens a block in the body; see {@link CodeBlock.Builder#beginControlFlow(CodeBlock)}.
         *
         * @param controlFlow the code before the opening brace
         * @return this builder
         */
        public Builder beginControlFlow(CodeBlock controlFlow) {
            code.beginControlFlow(controlFlow);
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
         */
        public Builder nextControlFlow(String controlFlow, @Nullable Object... args) {
            code.nextControlFlow(controlFlow, args);
            return this;
        }

        /**
         * Closes a block in the body and opens the next one; see {@link CodeBlock.Builder#nextControlFlow(CodeBlock)}.
         *
         * @param controlFlow the code between the braces
         * @return this builder
         */
        public Builder nextControlFlow(CodeBlock controlFlow) {
            code.nextControlFlow(controlFlow);
            return this;
        }

        /**
         * Closes a block in the body.
         *
         * @return this builder
         */
        public Builder endControlFlow() {
            code.endControlFlow();
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
         */
        public Builder endControlFlow(String controlFlow, @Nullable Object... args) {
            code.endControlFlow(controlFlow, args);
            return this;
        }

        /**
         * Closes a block in the body with code after its closing brace; see
         * {@link CodeBlock.Builder#endControlFlow(CodeBlock)}.
         *
         * @param controlFlow the code after the closing brace, without its semicolon
         * @return this builder
         */
        public Builder endControlFlow(CodeBlock controlFlow) {
            code.endControlFlow(controlFlow);
            return this;
        }

        /**
         * Sets the default value of an annotation type member.
         *
         * @param format the value after {@code default}, with placeholders
         * @param args the arguments of the placeholders
         * @return this builder
         * @throws IllegalArgumentException if the format and the arguments do not match
         */
        public Builder defaultValue(String format, @Nullable Object... args) {
            defaultValue = CodeBlock.of(format, args);
            return this;
        }

        /**
         * Builds the method.
         *
         * @return the method
         * @throws IllegalStateException if it is varargs but its last parameter is not an array, it is abstract or
         *     native but has code, it is a compact constructor with parameters, or a receiver parameter is not first or
         *     is on a static method
         */
        public Method build() {
            if (varargs && (parameters.isEmpty() || !(parameters.getLast().type() instanceof ArrayTypeName))) {
                throw new IllegalStateException("The last parameter of a varargs method must be an array: " + name);
            }
            for (Modifier modifier : List.of(Modifier.ABSTRACT, Modifier.NATIVE)) {
                if (modifiers.contains(modifier) && !code.isEmpty()) {
                    throw new IllegalStateException("A " + modifier + " method has no code: " + name);
                }
            }
            if (name.equals(COMPACT_CONSTRUCTOR) && !parameters.isEmpty()) {
                throw new IllegalStateException("A compact constructor has no parameter list");
            }
            checkReceiver();
            return new Method(this);
        }

        private void checkReceiver() {
            for (int i = 0; i < parameters.size(); i++) {
                if (parameters.get(i).isReceiver() && (i > 0 || modifiers.contains(Modifier.STATIC))) {
                    throw new IllegalStateException(
                            "A receiver parameter comes first, in an instance method or an inner class constructor: "
                                    + name);
                }
            }
        }

        private boolean isConstructor() {
            return name.equals(CONSTRUCTOR) || name.equals(COMPACT_CONSTRUCTOR);
        }
    }
}
