package be.imgn.gaia.processing;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.processing.source.Annotation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/**
 * Reads the values of an annotation, defaults included, with the types they are declared with.
 *
 * <pre>{@code
 * AnnotationReader mapper = context.annotation(element, "com.example.Mapper").orElseThrow();
 * TypeMirror target = mapper.type("target");          // a Class value, without MirroredTypeException
 * List<String> names = mapper.strings("names");
 * Policy policy = mapper.enumValue("policy", Policy.class);
 * }</pre>
 *
 * <p>Each method fails with {@link IllegalArgumentException} if the annotation has no such attribute, or if its value
 * is not of the requested type.
 */
public final class AnnotationReader {

    private final AnnotationMirror mirror;
    private final Elements elements;
    private final Map<String, AnnotationValue> values = new LinkedHashMap<>();

    AnnotationReader(AnnotationMirror mirror, Elements elements) {
        this.mirror = mirror;
        this.elements = elements;
        elements.getElementValuesWithDefaults(mirror)
                .forEach((attribute, value) ->
                        values.put(attribute.getSimpleName().toString(), value));
    }

    /**
     * Returns the annotation this reader reads.
     *
     * @return the annotation, such as for {@link Diagnostics.Report#annotation(AnnotationMirror)}
     */
    public AnnotationMirror mirror() {
        return mirror;
    }

    /**
     * Returns the annotation as a model of source code, to copy it onto generated code: with the values written in the
     * source, not the defaults.
     *
     * @return the annotation
     */
    public Annotation toAnnotation() {
        return Annotation.of(mirror);
    }

    /**
     * Returns the fully qualified name of the annotation type.
     *
     * @return the name, such as {@code com.example.Mapper}
     */
    public String qualifiedName() {
        return ((TypeElement) mirror.getAnnotationType().asElement())
                .getQualifiedName()
                .toString();
    }

    /**
     * Returns whether the annotation gives an attribute a value explicitly, rather than through its default.
     *
     * @param name the name of the attribute
     * @return {@code true} if the value is written in the source
     * @throws IllegalArgumentException if the annotation has no such attribute
     */
    public boolean isExplicit(String name) {
        requireNonNull(name, "name");
        raw(name);
        return mirror.getElementValues().keySet().stream()
                .map(ExecutableElement::getSimpleName)
                .anyMatch(attribute -> attribute.contentEquals(name));
    }

    /**
     * Returns the value of an attribute as javac represents it.
     *
     * @param name the name of the attribute
     * @return the value
     * @throws IllegalArgumentException if the annotation has no such attribute, or no value for it
     */
    public AnnotationValue raw(String name) {
        requireNonNull(name, "name");
        AnnotationValue value = values.get(name);
        if (value == null) {
            throw new IllegalArgumentException("@" + qualifiedName() + " has no value for " + name);
        }
        return value;
    }

    /**
     * Returns the value of an attribute of a primitive type or {@code String}.
     *
     * @param <T> the type of the value
     * @param name the name of the attribute
     * @param type the type of the value: {@code String.class}, or a wrapper class such as {@code Integer.class}
     * @return the value
     * @throws IllegalArgumentException if the annotation has no such attribute, or its value is not a {@code type}
     */
    public <T> T value(String name, Class<T> type) {
        requireNonNull(name, "name");
        requireNonNull(type, "type");
        return as(name, raw(name).getValue(), type);
    }

    /**
     * Returns the value of a {@code String} attribute.
     *
     * @param name the name of the attribute
     * @return the value
     * @throws IllegalArgumentException if the annotation has no such attribute, or it is not a {@code String}
     */
    public String string(String name) {
        requireNonNull(name, "name");
        return value(name, String.class);
    }

    /**
     * Returns the value of a {@code Class} attribute, as a type: reading it never throws {@code MirroredTypeException}.
     *
     * @param name the name of the attribute
     * @return the type
     * @throws IllegalArgumentException if the annotation has no such attribute, or it is not a {@code Class}
     */
    public TypeMirror type(String name) {
        requireNonNull(name, "name");
        return value(name, TypeMirror.class);
    }

    /**
     * Returns the name of the constant of an enum attribute. Unlike {@link #enumValue(String, Class)}, it does not need
     * the enum class on the processor's class path.
     *
     * @param name the name of the attribute
     * @return the simple name of the constant, such as {@code RUNTIME}
     * @throws IllegalArgumentException if the annotation has no such attribute, or it is not an enum
     */
    public String enumConstant(String name) {
        requireNonNull(name, "name");
        return value(name, VariableElement.class).getSimpleName().toString();
    }

    /**
     * Returns the constant of an enum attribute.
     *
     * @param <E> the enum type
     * @param name the name of the attribute
     * @param type the enum class
     * @return the constant
     * @throws IllegalArgumentException if the annotation has no such attribute, it is not an enum, or {@code type} has
     *     no constant of that name
     */
    public <E extends Enum<E>> E enumValue(String name, Class<E> type) {
        requireNonNull(name, "name");
        requireNonNull(type, "type");
        return Enum.valueOf(type, enumConstant(name));
    }

    /**
     * Returns the value of an annotation attribute, to read in turn.
     *
     * @param name the name of the attribute
     * @return a reader of the nested annotation
     * @throws IllegalArgumentException if the annotation has no such attribute, or it is not an annotation
     */
    public AnnotationReader annotation(String name) {
        requireNonNull(name, "name");
        return new AnnotationReader(value(name, AnnotationMirror.class), elements);
    }

    /**
     * Returns the values of an array attribute of a primitive type or {@code String}.
     *
     * @param <T> the type of the elements
     * @param name the name of the attribute
     * @param type the type of the elements: {@code String.class}, or a wrapper class such as {@code Integer.class}
     * @return the values, in order
     * @throws IllegalArgumentException if the annotation has no such attribute, or it is not an array of {@code type}
     */
    public <T> List<T> values(String name, Class<T> type) {
        requireNonNull(name, "name");
        requireNonNull(type, "type");
        return list(name).stream()
                .map(value -> as(name, value.getValue(), type))
                .toList();
    }

    /**
     * Returns the values of a {@code String[]} attribute.
     *
     * @param name the name of the attribute
     * @return the values, in order
     * @throws IllegalArgumentException if the annotation has no such attribute, or it is not a {@code String[]}
     */
    public List<String> strings(String name) {
        requireNonNull(name, "name");
        return values(name, String.class);
    }

    /**
     * Returns the values of a {@code Class[]} attribute, as types.
     *
     * @param name the name of the attribute
     * @return the types, in order
     * @throws IllegalArgumentException if the annotation has no such attribute, or it is not a {@code Class[]}
     */
    public List<TypeMirror> types(String name) {
        requireNonNull(name, "name");
        return values(name, TypeMirror.class);
    }

    /**
     * Returns the names of the constants of an enum array attribute.
     *
     * @param name the name of the attribute
     * @return the simple names of the constants, in order
     * @throws IllegalArgumentException if the annotation has no such attribute, or it is not an enum array
     */
    public List<String> enumConstants(String name) {
        requireNonNull(name, "name");
        return values(name, VariableElement.class).stream()
                .map(constant -> constant.getSimpleName().toString())
                .toList();
    }

    /**
     * Returns the values of an annotation array attribute, to read in turn.
     *
     * @param name the name of the attribute
     * @return readers of the nested annotations, in order
     * @throws IllegalArgumentException if the annotation has no such attribute, or it is not an annotation array
     */
    public List<AnnotationReader> annotations(String name) {
        requireNonNull(name, "name");
        return values(name, AnnotationMirror.class).stream()
                .map(nested -> new AnnotationReader(nested, elements))
                .toList();
    }

    @Override
    public String toString() {
        return mirror.toString();
    }

    private List<? extends AnnotationValue> list(String name) {
        Object value = raw(name).getValue();
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(
                    "@" + qualifiedName() + "." + name + " is not an array: " + Objects.toString(value));
        }
        return list.stream().map(AnnotationValue.class::cast).toList();
    }

    private <T> T as(String name, Object value, Class<T> type) {
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException(
                    "@" + qualifiedName() + "." + name + " is not a " + type.getSimpleName() + ": " + value);
        }
        return type.cast(value);
    }
}
