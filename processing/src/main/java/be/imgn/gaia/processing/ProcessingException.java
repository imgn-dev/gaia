package be.imgn.gaia.processing;

import static java.util.Objects.requireNonNull;

import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import org.jspecify.annotations.Nullable;

/**
 * An error in the source, thrown to stop processing an element and report the error at a precise place: the element,
 * one of its annotations, or one attribute of that annotation, as {@link Diagnostics} reports it.
 *
 * <pre>{@code
 * context.forEach(elements.annotatedWith("com.example.Mapper"), mapper -> {
 *     if (mapper.getKind() != ElementKind.INTERFACE) {
 *         throw new ProcessingException("must be an interface", mapper, "com.example.Mapper");
 *     }
 *     // generate the mapper
 * });
 * }</pre>
 *
 * <p>{@link ProcessingContext#forEach(Iterable, java.util.function.Consumer)} reports it and goes on with the next
 * element. A {@link StepProcessor} reports one that escapes {@link Step#process(AnnotatedElements)}, and goes on with
 * the next step. Any processor reports it with {@link Diagnostics#error(ProcessingException)}. The report is an error,
 * which makes the compilation fail, with the message of the exception; its stack trace is not reported.
 *
 * <p>The element and the annotation belong to javac's model of one compilation, which is not serializable: the
 * exception cannot be serialized either.
 */
public final class ProcessingException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Element element;
    private final transient @Nullable AnnotationMirror annotation;
    private final @Nullable String attribute;

    /**
     * Creates an exception reporting an error on an element.
     *
     * @param message the message of the error
     * @param element the element the error is about
     */
    public ProcessingException(String message, Element element) {
        this(element, null, null, message);
    }

    /**
     * Creates an exception reporting an error on an annotation of an element.
     *
     * @param message the message of the error
     * @param element the element the error is about
     * @param annotation the annotation the error is about, as found on the element: one of its annotations, or one
     *     nested in the value of an attribute of those, as a repeatable annotation is in its container
     * @throws IllegalArgumentException if the annotation is not on the element
     */
    public ProcessingException(String message, Element element, AnnotationMirror annotation) {
        this(element, checkedAnnotation(message, element, annotation), null, message);
    }

    /**
     * Creates an exception reporting an error on the annotation of a type of an element.
     *
     * @param message the message of the error
     * @param element the element the error is about
     * @param annotation the fully qualified name of the type of the annotation the error is about
     * @throws IllegalArgumentException if the element has no such annotation
     */
    public ProcessingException(String message, Element element, String annotation) {
        this(element, checkedAnnotation(message, element, annotation), null, message);
    }

    /**
     * Creates an exception reporting an error on an attribute of an annotation of an element. If the annotation does
     * not write the attribute, leaving it to its default, the error is reported on the annotation.
     *
     * @param message the message of the error
     * @param element the element the error is about
     * @param annotation the annotation, as found on the element: one of its annotations, or one nested in the value of
     *     an attribute of those, as a repeatable annotation is in its container
     * @param attribute the name of the attribute the error is about
     * @throws IllegalArgumentException if the annotation is not on the element, or has no such attribute
     */
    public ProcessingException(String message, Element element, AnnotationMirror annotation, String attribute) {
        this(element, annotation, checkedAttribute(message, element, annotation, attribute), message);
    }

    /**
     * Creates an exception reporting an error on an attribute of the annotation of a type of an element. If the
     * annotation does not write the attribute, leaving it to its default, the error is reported on the annotation.
     *
     * @param message the message of the error
     * @param element the element the error is about
     * @param annotation the fully qualified name of the type of the annotation
     * @param attribute the name of the attribute the error is about
     * @throws IllegalArgumentException if the element has no such annotation, or the annotation no such attribute
     */
    public ProcessingException(String message, Element element, String annotation, String attribute) {
        this(message, element, checkedAnnotation(message, element, annotation, attribute), attribute);
    }

    // The annotation and the attribute are checked already; the order of the parameters sets it apart.
    private ProcessingException(
            Element element, @Nullable AnnotationMirror annotation, @Nullable String attribute, String message) {
        super(requireNonNull(message, "message"));
        this.element = requireNonNull(element, "element");
        this.annotation = annotation;
        this.attribute = attribute;
    }

    /**
     * Returns the element the error is about.
     *
     * @return the element
     */
    public Element element() {
        return element;
    }

    /**
     * Returns the annotation of the element the error is about.
     *
     * @return the annotation; empty if the error is about the element as a whole
     */
    public Optional<AnnotationMirror> annotation() {
        return Optional.ofNullable(annotation);
    }

    /**
     * Returns the attribute of the annotation the error is about.
     *
     * @return the name of the attribute; empty if the error is about the element or the whole annotation
     */
    public Optional<String> attribute() {
        return Optional.ofNullable(attribute);
    }

    // The checks of the constructors, which come before they read the element; each returns the annotation.
    private static AnnotationMirror checkedAnnotation(String message, Element element, AnnotationMirror annotation) {
        requireNonNull(message, "message");
        requireNonNull(element, "element");
        requireNonNull(annotation, "annotation");
        return Diagnostics.requireAnnotationOf(element, annotation);
    }

    private static AnnotationMirror checkedAnnotation(String message, Element element, String annotation) {
        requireNonNull(message, "message");
        requireNonNull(element, "element");
        requireNonNull(annotation, "annotation");
        return Diagnostics.annotationOf(element, annotation);
    }

    private static AnnotationMirror checkedAnnotation(
            String message, Element element, String annotation, String attribute) {
        requireNonNull(attribute, "attribute");
        return checkedAnnotation(message, element, annotation);
    }

    private static String checkedAttribute(
            String message, Element element, AnnotationMirror annotation, String attribute) {
        requireNonNull(attribute, "attribute");
        return Diagnostics.requireAttribute(checkedAnnotation(message, element, annotation), attribute);
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws NotSerializableException {
        throw new NotSerializableException(ProcessingException.class.getName());
    }
}
