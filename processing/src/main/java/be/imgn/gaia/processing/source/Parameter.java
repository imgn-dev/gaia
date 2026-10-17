package be.imgn.gaia.processing.source;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import org.jspecify.annotations.Nullable;

/**
 * A parameter of a method or constructor, or a component of a record, such as {@code final String name}. A receiver
 * parameter, named {@code this} or {@code Outer.this}, gives a place to the type annotations of the receiver, as in
 * {@code void close(@Owned Resource this)}.
 *
 * <p>Parameters are values: two parameters with the same type, name, modifiers, annotations and Javadoc are equal.
 */
public final class Parameter {

    private final TypeName type;
    private final String name;
    private final Set<Modifier> modifiers;
    private final List<Annotation> annotations;
    private final CodeBlock javadoc;

    private Parameter(Builder builder) {
        this.type = builder.type;
        this.name = builder.name;
        this.modifiers = Modifiers.copy(builder.modifiers);
        this.annotations = List.copyOf(builder.annotations);
        this.javadoc = builder.javadoc.build();
    }

    /**
     * Returns a parameter.
     *
     * @param type the type
     * @param name the name
     * @param modifiers the modifiers: none, or {@code final}
     * @return the parameter
     * @throws IllegalArgumentException if the name is not a valid identifier, the type is {@code void} or a wildcard,
     *     or a modifier is not {@code final}, is repeated, or is given to a receiver parameter
     */
    public static Parameter of(TypeName type, String name, Modifier... modifiers) {
        requireNonNull(type, "type");
        requireNonNull(name, "name");
        Checks.elements(modifiers, "modifiers");
        return newBuilder(type, name).addModifiers(modifiers).build();
    }

    /**
     * Returns a parameter.
     *
     * @param type the type: a class, or a generic type
     * @param name the name
     * @param modifiers the modifiers: none, or {@code final}
     * @return the parameter
     * @throws IllegalArgumentException if the type is {@code void}, or names a local or anonymous class, as
     *     {@link TypeName#of(java.lang.reflect.Type)} names it; the name is not a valid identifier; or a modifier is
     *     not {@code final}, is repeated, or is given to a receiver parameter
     */
    public static Parameter of(java.lang.reflect.Type type, String name, Modifier... modifiers) {
        requireNonNull(type, "type");
        requireNonNull(name, "name");
        requireNonNull(modifiers, "modifiers");
        return of(TypeName.of(type), name, modifiers);
    }

    /**
     * Returns a copy of a parameter found in the source: its type, with type annotations such as {@code @Nullable}, its
     * name, and {@code final} if it has it. Declaration annotations are not copied.
     *
     * @param parameter the parameter
     * @return the copy
     * @throws IllegalArgumentException if the element is not a parameter, such as a field or a local variable, or its
     *     type has no name, as {@link TypeName#of(javax.lang.model.type.TypeMirror)} names it
     */
    public static Parameter of(VariableElement parameter) {
        requireNonNull(parameter, "parameter");
        if (parameter.getKind() != ElementKind.PARAMETER) {
            throw new IllegalArgumentException("Not a parameter but a " + parameter.getKind() + ": " + parameter);
        }
        Builder builder = newBuilder(
                TypeName.of(parameter.asType()), parameter.getSimpleName().toString());
        if (parameter.getModifiers().contains(Modifier.FINAL)) {
            builder.addModifiers(Modifier.FINAL);
        }
        return builder.build();
    }

    /**
     * Returns a builder of parameters.
     *
     * @param type the type
     * @param name the name
     * @return a new builder
     * @throws IllegalArgumentException if the name is not a valid identifier, or the type is {@code void} or a wildcard
     */
    public static Builder newBuilder(TypeName type, String name) {
        requireNonNull(type, "type");
        requireNonNull(name, "name");
        return new Builder(type, name);
    }

    /**
     * Returns a builder of parameters.
     *
     * @param type the type: a class, or a generic type
     * @param name the name
     * @return a new builder
     * @throws IllegalArgumentException if the type is {@code void}, or names a local or anonymous class, as
     *     {@link TypeName#of(java.lang.reflect.Type)} names it; or the name is not a valid identifier
     */
    public static Builder newBuilder(java.lang.reflect.Type type, String name) {
        requireNonNull(type, "type");
        requireNonNull(name, "name");
        return newBuilder(TypeName.of(type), name);
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    public TypeName type() {
        return type;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String name() {
        return name;
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
     * Returns the declaration annotations.
     *
     * @return the annotations, in order
     */
    public List<Annotation> annotations() {
        return annotations;
    }

    /**
     * Returns the Javadoc, written as an {@code @param} tag in the Javadoc of the method or record.
     *
     * @return the Javadoc text, empty if there is none
     */
    public CodeBlock javadoc() {
        return javadoc;
    }

    /**
     * Returns whether this is a receiver parameter, named {@code this} or {@code Outer.this}.
     *
     * @return {@code true} for a receiver parameter
     */
    public boolean isReceiver() {
        return isReceiver(name);
    }

    private static boolean isReceiver(String name) {
        if ("this".equals(name)) {
            return true;
        }
        String enclosing = name.endsWith(".this") ? name.substring(0, name.length() - ".this".length()) : "";
        return SourceVersion.isIdentifier(enclosing) && !SourceVersion.isKeyword(enclosing);
    }

    /**
     * Returns a builder that starts with this parameter.
     *
     * @return a new builder
     */
    public Builder toBuilder() {
        Builder builder = newBuilder(type, name);
        builder.modifiers.addAll(modifiers);
        builder.annotations.addAll(annotations);
        builder.javadoc.add(javadoc);
        return builder;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Parameter parameter
                && type.equals(parameter.type)
                && name.equals(parameter.name)
                && modifiers.equals(parameter.modifiers)
                && annotations.equals(parameter.annotations)
                && javadoc.equals(parameter.javadoc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name, modifiers, annotations, javadoc);
    }

    @Override
    public String toString() {
        return CodeWriter.standalone(this);
    }

    /** A builder of {@link Parameter}s. */
    public static final class Builder {

        private final TypeName type;
        private final String name;
        private final Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
        private final List<Annotation> annotations = new ArrayList<>();
        private final CodeBlock.Builder javadoc = CodeBlock.newBuilder();

        private Builder(TypeName type, String name) {
            requireNonNull(type, "type");
            requireNonNull(name, "name");
            if ((!SourceVersion.isIdentifier(name) || SourceVersion.isKeyword(name)) && !isReceiver(name)) {
                throw new IllegalArgumentException("Not a parameter name: " + name);
            }
            if (type.equals(PrimitiveTypeName.VOID)) {
                throw new IllegalArgumentException("A parameter cannot be void: " + name);
            }
            if (type instanceof WildcardTypeName) {
                throw new IllegalArgumentException("A parameter cannot be a wildcard: " + name);
            }
            this.type = type;
            this.name = name;
        }

        /**
         * Adds modifiers.
         *
         * @param more the modifiers: only {@code final} is allowed, and none on a receiver parameter
         * @return this builder
         * @throws IllegalArgumentException if a modifier is not {@code final} or is repeated, or this is a receiver
         *     parameter
         */
        public Builder addModifiers(Modifier... more) {
            Set<Modifier> added = Modifiers.added(more, "more", "A parameter");
            Modifiers.requireOnly(added, isReceiver(name) ? Set.of() : Set.of(Modifier.FINAL), "A parameter");
            modifiers.addAll(added);
            return this;
        }

        /**
         * Adds a declaration annotation. A type annotation, such as {@code @Nullable}, goes on the type instead.
         *
         * @param annotation the annotation
         * @return this builder
         */
        public Builder addAnnotation(Annotation annotation) {
            annotations.add(requireNonNull(annotation, "annotation"));
            return this;
        }

        /**
         * Adds a declaration annotation without members.
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
         * Adds a declaration annotation without members.
         *
         * @param annotation the annotation type
         * @return this builder
         */
        public Builder addAnnotation(ClassName annotation) {
            requireNonNull(annotation, "annotation");
            return addAnnotation(Annotation.of(annotation));
        }

        /**
         * Adds declaration annotations.
         *
         * @param more the annotations
         * @return this builder
         */
        public Builder addAnnotations(Iterable<Annotation> more) {
            annotations.addAll(Checks.elements(more, "more"));
            return this;
        }

        /**
         * Adds Javadoc text, written as an {@code @param} tag in the Javadoc of the method or record. End each line
         * with {@code \n}.
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
         * Adds Javadoc text, written as an {@code @param} tag in the Javadoc of the method or record. End each line
         * with {@code \n}.
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
         * Builds the parameter.
         *
         * @return the parameter
         * @throws IllegalStateException if the Javadoc is not {@linkplain CodeBlock balanced}
         */
        public Parameter build() {
            CodeBlock.checkBalanced(javadoc.build(), "The Javadoc of " + name);
            return new Parameter(this);
        }
    }
}
