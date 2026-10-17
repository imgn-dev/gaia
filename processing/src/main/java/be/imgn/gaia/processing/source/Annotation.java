package be.imgn.gaia.processing.source;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import org.jspecify.annotations.Nullable;

/**
 * An annotation to write, such as {@code @Override} or {@code @SuppressWarnings({"unchecked", "rawtypes"})}.
 *
 * <p>Each member has one or more values: a member with several values is written as an array.
 */
public final class Annotation {

    private final ClassName type;
    private final Map<String, List<CodeBlock>> members;

    private Annotation(Builder builder) {
        this.type = builder.type;
        Map<String, List<CodeBlock>> copy = new LinkedHashMap<>();
        builder.members.forEach((name, values) -> copy.put(name, List.copyOf(values)));
        this.members = Collections.unmodifiableMap(copy);
    }

    /**
     * Returns an annotation without members.
     *
     * @param type the annotation type
     * @return the annotation
     */
    public static Annotation of(ClassName type) {
        requireNonNull(type, "type");
        return newBuilder(type).build();
    }

    /**
     * Returns an annotation without members.
     *
     * @param type the annotation type
     * @return the annotation
     * @throws IllegalArgumentException if the class is not an annotation type
     */
    public static Annotation of(Class<? extends java.lang.annotation.Annotation> type) {
        requireNonNull(type, "type");
        return newBuilder(type).build();
    }

    /**
     * Returns a copy of an annotation found in the source, with the values written in it; values left to their defaults
     * are left out.
     *
     * @param annotation the annotation
     * @return the copy
     */
    public static Annotation of(AnnotationMirror annotation) {
        requireNonNull(annotation, "annotation");
        Builder builder = newBuilder(
                ClassName.of((TypeElement) annotation.getAnnotationType().asElement()));
        annotation
                .getElementValues()
                .forEach((member, value) ->
                        builder.addValue(member.getSimpleName().toString(), value));
        return builder.build();
    }

    /**
     * Returns a copy of an annotation found in the source, with every value it has: those written in it, and the
     * defaults of the others, in the order the annotation type declares its members.
     *
     * @param annotation the annotation
     * @return the copy
     */
    public static Annotation ofWithDefaults(AnnotationMirror annotation) {
        requireNonNull(annotation, "annotation");
        TypeElement type = (TypeElement) annotation.getAnnotationType().asElement();
        Builder builder = newBuilder(ClassName.of(type));
        Map<? extends ExecutableElement, ? extends AnnotationValue> written = annotation.getElementValues();
        for (ExecutableElement member : ElementFilter.methodsIn(type.getEnclosedElements())) {
            AnnotationValue value = written.containsKey(member) ? written.get(member) : member.getDefaultValue();
            if (value != null) {
                builder.addValue(member.getSimpleName().toString(), value);
            }
        }
        return builder.build();
    }

    /**
     * Returns a builder of annotations of a type.
     *
     * @param type the annotation type
     * @return a new builder
     */
    public static Builder newBuilder(ClassName type) {
        requireNonNull(type, "type");
        return new Builder(type.withoutAnnotations());
    }

    /**
     * Returns a builder of annotations of a type.
     *
     * @param type the annotation type
     * @return a new builder
     * @throws IllegalArgumentException if the class is not an annotation type
     */
    public static Builder newBuilder(Class<? extends java.lang.annotation.Annotation> type) {
        requireNonNull(type, "type");
        checkArgument(type.isAnnotation(), "Not an annotation type: %s", type);
        return newBuilder(ClassName.of(type));
    }

    /**
     * Returns the annotation type.
     *
     * @return the type
     */
    public ClassName type() {
        return type;
    }

    /**
     * Returns the members, in the order they were added.
     *
     * @return the values of each member
     */
    public Map<String, List<CodeBlock>> members() {
        return members;
    }

    /**
     * Returns a builder that starts with this annotation.
     *
     * @return a new builder
     */
    public Builder toBuilder() {
        Builder builder = newBuilder(type);
        members.forEach((name, values) -> builder.members.put(name, new ArrayList<>(values)));
        return builder;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Annotation annotation
                && type.equals(annotation.type)
                && members.equals(annotation.members);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, members);
    }

    @Override
    public String toString() {
        return CodeWriter.standalone(this);
    }

    /** A builder of {@link Annotation}s. */
    public static final class Builder {

        private final ClassName type;
        private final Map<String, List<CodeBlock>> members = new LinkedHashMap<>();

        private Builder(ClassName type) {
            this.type = type;
        }

        private static CodeBlock literal(Object value) {
            return switch (value) {
                case String text -> CodeBlock.of("$S", text);
                case Character character -> CodeBlock.of("$L", CodeWriter.characterLiteral(character));
                case Long number -> CodeBlock.of("$LL", number);
                case Float number -> floating(Float.class, number.doubleValue(), number + "f");
                case Double number -> floating(Double.class, number, number.toString());
                case TypeMirror type -> CodeBlock.of("$T.class", type);
                case VariableElement constant ->
                    CodeBlock.of(
                            "$T.$N", constant.asType(), constant.getSimpleName().toString());
                case AnnotationMirror annotation -> CodeBlock.of("$L", Annotation.of(annotation));
                default -> CodeBlock.of("$L", value);
            };
        }

        // NaN and the infinities have no literal: they are written as the constants of Float or Double.
        private static CodeBlock floating(Class<?> type, double value, String literal) {
            if (Double.isNaN(value)) {
                return CodeBlock.of("$T.NaN", type);
            }
            if (Double.isInfinite(value)) {
                return CodeBlock.of("$T.$L", type, value > 0 ? "POSITIVE_INFINITY" : "NEGATIVE_INFINITY");
            }
            return CodeBlock.of("$L", literal);
        }

        /**
         * Adds a value to a member.
         *
         * @param name the name of the member
         * @param format the value, with placeholders
         * @param args the arguments of the placeholders
         * @return this builder
         * @throws IllegalArgumentException if the name is not a valid identifier, the format and the arguments do not
         *     match, or the value is not {@linkplain CodeBlock balanced}
         */
        public Builder addMember(String name, String format, @Nullable Object... args) {
            requireNonNull(name, "name");
            requireNonNull(format, "format");
            requireNonNull(args, "args");
            return addMember(name, CodeBlock.of(format, args));
        }

        /**
         * Adds a value to a member.
         *
         * @param name the name of the member
         * @param value the value
         * @return this builder
         * @throws IllegalArgumentException if the name is not a valid identifier, or the value is not
         *     {@linkplain CodeBlock balanced}
         */
        public Builder addMember(String name, CodeBlock value) {
            requireNonNull(name, "name");
            requireNonNull(value, "value");
            CodeBlock.requireBalanced(value, "The value of " + name);
            member(name).add(value);
            return this;
        }

        /**
         * Builds the annotation.
         *
         * @return the annotation
         */
        public Annotation build() {
            return new Annotation(this);
        }

        private List<CodeBlock> member(String name) {
            if (!SourceVersion.isIdentifier(name) || SourceVersion.isKeyword(name)) {
                throw new IllegalArgumentException("Not a member name: " + name);
            }
            return members.computeIfAbsent(name, ignored -> new ArrayList<>());
        }

        private void addValue(String name, AnnotationValue value) {
            if (value.getValue() instanceof List<?> values) {
                List<CodeBlock> member = member(name);
                values.forEach(item -> member.add(literal(((AnnotationValue) item).getValue())));
            } else {
                member(name).add(literal(value.getValue()));
            }
        }
    }
}
