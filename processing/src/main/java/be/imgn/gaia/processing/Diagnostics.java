package be.imgn.gaia.processing;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import org.jspecify.annotations.Nullable;

/**
 * Reports errors, warnings and notes at a precise place in the source: an element, one of its annotations, or one
 * attribute of that annotation.
 *
 * <pre>{@code
 * diagnostics.on(method).error("must be public");
 * diagnostics.on(type).annotation("com.example.Mapper").attribute("target").error("must be an interface");
 * }</pre>
 *
 * <p>It counts the errors it reports, so a step can report every problem it finds, then skip generating code if there
 * was any.
 */
public final class Diagnostics {

    private final Messager messager;
    private int errors;

    Diagnostics(Messager messager) {
        this.messager = messager;
    }

    /**
     * Starts a report about an element.
     *
     * @param element the element the report is about
     * @return the report, to narrow to an annotation and then report
     */
    public Report on(Element element) {
        return new Report(requireNonNull(element, "element"), null, null);
    }

    /**
     * Reports an error about no element in particular, such as an invalid option. It makes the compilation fail.
     *
     * @param message the message
     */
    public void error(String message) {
        requireNonNull(message, "message");
        errors++;
        messager.printMessage(Diagnostic.Kind.ERROR, message);
    }

    /**
     * Reports the error an exception carries, with its message, at the place it names: its element, one of its
     * annotations, or one attribute of that annotation. It makes the compilation fail. The stack trace of the exception
     * is not reported.
     *
     * <pre>{@code
     * try {
     *     validate(element);
     * } catch (ProcessingException e) {
     *     diagnostics.error(e);
     * }
     * }</pre>
     *
     * @param exception the exception
     */
    public void error(ProcessingException exception) {
        requireNonNull(exception, "exception");
        Report report = on(exception.element());
        Optional<AnnotationMirror> annotation = exception.annotation();
        if (annotation.isPresent()) {
            report = report.annotation(annotation.get());
            Optional<String> attribute = exception.attribute();
            if (attribute.isPresent()) {
                report = report.attribute(attribute.get());
            }
        }
        report.error(requireNonNull(exception.getMessage(), "message"));
    }

    /**
     * Reports a warning about no element in particular.
     *
     * @param message the message
     */
    public void warning(String message) {
        messager.printMessage(Diagnostic.Kind.WARNING, requireNonNull(message, "message"));
    }

    /**
     * Reports a note about no element in particular.
     *
     * @param message the message
     */
    public void note(String message) {
        messager.printMessage(Diagnostic.Kind.NOTE, requireNonNull(message, "message"));
    }

    /**
     * Returns the number of errors reported so far through these diagnostics.
     *
     * @return the number of errors
     */
    public int errorCount() {
        return errors;
    }

    /**
     * Returns whether any error was reported through these diagnostics.
     *
     * @return {@code true} if an error was reported
     */
    public boolean hasErrors() {
        return errors > 0;
    }

    // Finds the annotation of a type on an element, as Report.annotation(String) does.
    static AnnotationMirror annotationOf(Element element, String qualifiedName) {
        return element.getAnnotationMirrors().stream()
                .filter(mirror -> ((TypeElement) mirror.getAnnotationType().asElement())
                        .getQualifiedName()
                        .contentEquals(qualifiedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(element + " has no @" + qualifiedName));
    }

    // Checks that an annotation is on an element, as Report.annotation(AnnotationMirror) does: one of its annotations,
    // or nested in the value of one, as a repeatable annotation is in its container, defaults included, or one that a
    // class inherits from a superclass.
    static AnnotationMirror requireAnnotationOf(Element element, AnnotationMirror annotation) {
        checkArgument(
                isWritten(element, annotation) || isInherited(element, annotation),
                "%s is not an annotation of %s",
                annotation,
                element);
        return annotation;
    }

    // Whether the source of the element has the annotation, which a report can then point at.
    private static boolean isWritten(Element element, AnnotationMirror annotation) {
        return contains(element.getAnnotationMirrors(), annotation);
    }

    private static boolean isInherited(Element element, AnnotationMirror annotation) {
        if (!(element instanceof TypeElement type)) {
            return false;
        }
        TypeMirror superclass = type.getSuperclass();
        while (superclass.getKind() == TypeKind.DECLARED) {
            TypeElement declaring = (TypeElement) ((DeclaredType) superclass).asElement();
            if (isWritten(declaring, annotation)) {
                return true;
            }
            superclass = declaring.getSuperclass();
        }
        return false;
    }

    private static boolean contains(List<? extends AnnotationMirror> mirrors, AnnotationMirror annotation) {
        for (AnnotationMirror mirror : mirrors) {
            if (mirror.equals(annotation) || contains(nested(mirror), annotation)) {
                return true;
            }
        }
        return false;
    }

    // The annotations an annotation holds in its attributes, written or left to their defaults.
    private static List<AnnotationMirror> nested(AnnotationMirror mirror) {
        TypeElement type = (TypeElement) mirror.getAnnotationType().asElement();
        List<AnnotationMirror> nested = new ArrayList<>();
        for (ExecutableElement attribute : ElementFilter.methodsIn(type.getEnclosedElements())) {
            AnnotationValue written = mirror.getElementValues().get(attribute);
            AnnotationValue value = written == null ? attribute.getDefaultValue() : written;
            if (value != null) {
                nested.addAll(annotationsIn(value));
            }
        }
        return nested;
    }

    private static List<AnnotationMirror> annotationsIn(AnnotationValue value) {
        return switch (value.getValue()) {
            case AnnotationMirror mirror -> List.of(mirror);
            case List<?> values ->
                values.stream()
                        .map(AnnotationValue.class::cast)
                        .flatMap(element -> annotationsIn(element).stream())
                        .toList();
            default -> List.of();
        };
    }

    // Checks that the type of an annotation has an attribute, as Report.attribute(String) does; returns its name.
    static String requireAttribute(AnnotationMirror annotation, String name) {
        TypeElement type = (TypeElement) annotation.getAnnotationType().asElement();
        boolean declared = type.getEnclosedElements().stream()
                .anyMatch(attribute -> attribute.getKind() == ElementKind.METHOD
                        && attribute.getSimpleName().contentEquals(name));
        if (!declared) {
            throw new IllegalArgumentException("@" + type.getQualifiedName() + " has no attribute " + name);
        }
        return name;
    }

    /** A report about an element, possibly narrowed to one of its annotations and one attribute of it. */
    public final class Report {

        private final Element element;
        private final @Nullable AnnotationMirror annotation;
        private final @Nullable AnnotationValue value;

        private Report(Element element, @Nullable AnnotationMirror annotation, @Nullable AnnotationValue value) {
            this.element = element;
            this.annotation = annotation;
            this.value = value;
        }

        /**
         * Narrows this report to an annotation of the element.
         *
         * @param annotation the annotation, as found on the element: one of its annotations, or one nested in the value
         *     of an attribute of those, as a repeatable annotation is in its container, or one a class inherits from a
         *     superclass, which is reported on the class, its source not having it
         * @return the narrowed report
         * @throws IllegalArgumentException if the annotation is not on the element
         */
        public Report annotation(AnnotationMirror annotation) {
            requireNonNull(annotation, "annotation");
            return new Report(element, requireAnnotationOf(element, annotation), null);
        }

        /**
         * Narrows this report to the annotation of the element of a type.
         *
         * @param qualifiedName the fully qualified name of the annotation type
         * @return the narrowed report
         * @throws IllegalArgumentException if the element has no such annotation
         */
        public Report annotation(String qualifiedName) {
            requireNonNull(qualifiedName, "qualifiedName");
            return new Report(element, annotationOf(element, qualifiedName), null);
        }

        /**
         * Narrows this report to an attribute of the annotation. If the annotation does not write the attribute,
         * leaving it to its default, the report stays on the annotation.
         *
         * @param name the name of the attribute
         * @return the narrowed report
         * @throws IllegalStateException if the report is not narrowed to an annotation yet
         * @throws IllegalArgumentException if the annotation has no such attribute
         */
        public Report attribute(String name) {
            requireNonNull(name, "name");
            if (annotation == null) {
                throw new IllegalStateException("Narrow the report to an annotation before an attribute");
            }
            AnnotationMirror mirror = annotation;
            requireAttribute(mirror, name);
            AnnotationValue explicit = mirror.getElementValues().entrySet().stream()
                    .filter(entry -> entry.getKey().getSimpleName().contentEquals(name))
                    .map(entry -> (AnnotationValue) entry.getValue())
                    .findFirst()
                    .orElse(null);
            return new Report(element, mirror, explicit);
        }

        /**
         * Reports an error, which makes the compilation fail.
         *
         * @param message the message
         */
        public void error(String message) {
            requireNonNull(message, "message");
            errors++;
            print(Diagnostic.Kind.ERROR, message);
        }

        /**
         * Reports a warning.
         *
         * @param message the message
         */
        public void warning(String message) {
            print(Diagnostic.Kind.WARNING, message);
        }

        /**
         * Reports a note.
         *
         * @param message the message
         */
        public void note(String message) {
            print(Diagnostic.Kind.NOTE, message);
        }

        private void print(Diagnostic.Kind kind, String message) {
            requireNonNull(message, "message");
            AnnotationMirror mirror = annotation;
            if (mirror == null || isWritten(element, mirror)) {
                messager.printMessage(kind, message, element, mirror, value);
            } else {
                // Inherited: the annotation is in the source of a superclass, not in that of the element.
                messager.printMessage(kind, message, element);
            }
        }
    }
}
