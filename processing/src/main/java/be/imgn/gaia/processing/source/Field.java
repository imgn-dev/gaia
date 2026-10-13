package be.imgn.gaia.processing.source;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import org.jspecify.annotations.Nullable;

/**
 * A field, such as {@code private static final int LIMIT = 10;}.
 *
 * <p>Fields are values: two fields written the same are equal.
 */
public final class Field {

    private static final Set<Modifier> ALLOWED = EnumSet.of(
            Modifier.PUBLIC,
            Modifier.PROTECTED,
            Modifier.PRIVATE,
            Modifier.STATIC,
            Modifier.FINAL,
            Modifier.TRANSIENT,
            Modifier.VOLATILE);

    private final TypeName type;
    private final String name;
    private final Set<Modifier> modifiers;
    private final List<Annotation> annotations;
    private final CodeBlock javadoc;
    private final CodeBlock initializer;

    private Field(Builder builder) {
        this.type = builder.type;
        this.name = builder.name;
        this.modifiers = Modifiers.copy(builder.modifiers);
        this.annotations = List.copyOf(builder.annotations);
        this.javadoc = builder.javadoc.build();
        this.initializer = builder.initializer;
    }

    /**
     * Returns a field without initializer.
     *
     * @param type the type
     * @param name the name
     * @param modifiers the modifiers
     * @return the field
     * @throws IllegalArgumentException if the name is not a valid identifier, or a modifier is not allowed on a field
     */
    public static Field of(TypeName type, String name, Modifier... modifiers) {
        return newBuilder(type, name, modifiers).build();
    }

    /**
     * Returns a builder of fields.
     *
     * @param type the type
     * @param name the name
     * @param modifiers the modifiers
     * @return a new builder
     * @throws IllegalArgumentException if the name is not a valid identifier, or a modifier is not allowed on a field
     */
    public static Builder newBuilder(TypeName type, String name, Modifier... modifiers) {
        return new Builder(type, name).addModifiers(modifiers);
    }

    /**
     * Returns a builder of fields.
     *
     * @param type the type
     * @param name the name
     * @param modifiers the modifiers
     * @return a new builder
     * @throws IllegalArgumentException if the name is not a valid identifier, or a modifier is not allowed on a field
     */
    public static Builder newBuilder(Class<?> type, String name, Modifier... modifiers) {
        return newBuilder(TypeName.of(type), name, modifiers);
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
     * Returns the Javadoc.
     *
     * @return the Javadoc text, empty if there is none
     */
    public CodeBlock javadoc() {
        return javadoc;
    }

    /**
     * Returns the initializer.
     *
     * @return the expression after {@code =}, empty if there is none
     */
    public CodeBlock initializer() {
        return initializer;
    }

    /**
     * Returns a builder that starts with this field.
     *
     * @return a new builder
     */
    public Builder toBuilder() {
        Builder builder = new Builder(type, name);
        builder.modifiers.addAll(modifiers);
        builder.annotations.addAll(annotations);
        builder.javadoc.add(javadoc);
        builder.initializer = initializer;
        return builder;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Field field && toString().equals(field.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        return CodeWriter.standalone(this);
    }

    /** A builder of {@link Field}s. */
    public static final class Builder {

        private final TypeName type;
        private final String name;
        private final Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
        private final List<Annotation> annotations = new ArrayList<>();
        private final CodeBlock.Builder javadoc = CodeBlock.newBuilder();
        private CodeBlock initializer = CodeBlock.empty();

        private Builder(TypeName type, String name) {
            if (!SourceVersion.isIdentifier(name) || SourceVersion.isKeyword(name)) {
                throw new IllegalArgumentException("Not a field name: " + name);
            }
            if (type.equals(PrimitiveTypeName.VOID)) {
                throw new IllegalArgumentException("A field cannot be void: " + name);
            }
            this.type = type;
            this.name = name;
        }

        /**
         * Adds modifiers.
         *
         * @param more the modifiers
         * @return this builder
         * @throws IllegalArgumentException if a modifier is not allowed on a field
         */
        public Builder addModifiers(Modifier... more) {
            Set<Modifier> added = Set.of(more);
            Modifiers.requireOnly(added, ALLOWED, "A field");
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
            annotations.add(annotation);
            return this;
        }

        /**
         * Adds a declaration annotation without members.
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
         * Sets the initializer.
         *
         * @param format the expression after {@code =}, with placeholders
         * @param args the arguments of the placeholders
         * @return this builder
         * @throws IllegalArgumentException if the format and the arguments do not match
         * @throws IllegalStateException if the initializer is already set
         */
        public Builder initializer(String format, @Nullable Object... args) {
            return initializer(CodeBlock.of(format, args));
        }

        /**
         * Sets the initializer.
         *
         * @param expression the expression after {@code =}
         * @return this builder
         * @throws IllegalStateException if the initializer is already set
         */
        public Builder initializer(CodeBlock expression) {
            if (!initializer.isEmpty()) {
                throw new IllegalStateException("The initializer of " + name + " is already set: " + initializer);
            }
            initializer = expression;
            return this;
        }

        /**
         * Builds the field.
         *
         * @return the field
         */
        public Field build() {
            return new Field(this);
        }
    }
}
