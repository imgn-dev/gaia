package be.imgn.gaia.processing;

import be.imgn.gaia.processing.internal.DebugLog;
import java.util.Set;
import javax.lang.model.element.Element;

/**
 * One unit of work of a {@link StepProcessor}: the processing of the elements annotated with some annotations, or with
 * annotations that some {@linkplain #metaAnnotations() meta-annotations} annotate, and optionally of
 * {@linkplain #processesRootElements() every root element}.
 *
 * <pre>{@code
 * final class MapperStep implements Step {
 *     public Set<String> annotations() {
 *         return Set.of("com.example.Mapper");
 *     }
 *
 *     public Set<Element> process(AnnotatedElements elements) {
 *         for (Element mapper : elements.annotatedWith("com.example.Mapper")) {
 *             // generate code for the mapper
 *         }
 *         return Set.of();
 *     }
 * }
 * }</pre>
 *
 * <p>A step reports an invalid element by throwing a {@link ProcessingException}, which
 * {@link ProcessingContext#forEach(Iterable, java.util.function.Consumer)} catches for each element:
 *
 * <pre>{@code
 * public Set<Element> process(AnnotatedElements elements) {
 *     context.forEach(elements.annotatedWith("com.example.Mapper"), mapper -> {
 *         if (mapper.getKind() != ElementKind.INTERFACE) {
 *             throw new ProcessingException("must be an interface", mapper);
 *         }
 *         // generate code for the mapper
 *     });
 *     return Set.of();
 * }
 * }</pre>
 */
public interface Step {

    /**
     * Returns the annotations this step processes. The annotation classes need not be on the processor's class path.
     *
     * @return the fully qualified names of the annotation types
     */
    Set<String> annotations();

    /**
     * Returns the meta-annotations this step processes: the step also gets the elements annotated with any annotation
     * whose type is annotated with one of them, such as each qualifier for {@code javax.inject.Qualifier}, when the
     * annotations are not known in advance.
     *
     * <pre>{@code
     * public Set<String> annotations() {
     *     return Set.of();
     * }
     *
     * public Set<String> metaAnnotations() {
     *     return Set.of("com.example.Injecting");
     * }
     *
     * public Set<Element> process(AnnotatedElements elements) {
     *     for (Element element : elements.withAnnotationsAnnotatedWith("com.example.Injecting")) {
     *         for (AnnotationReader injecting : context.annotationsAnnotatedWith(element, "com.example.Injecting")) {
     *             ...
     *         }
     *     }
     *     return Set.of();
     * }
     * }</pre>
     *
     * <p>Each element is given under the annotation it has, with {@link AnnotatedElements#annotatedWith(String)}, and
     * under each of the step's meta-annotations that annotate the type of that annotation, with
     * {@link AnnotatedElements#withAnnotationsAnnotatedWith(String)}. An element annotated with a meta-annotation
     * itself, such as an annotation type, is not found by it. Only the meta-annotations of the annotation type itself
     * count, not those of its meta-annotations, as for {@link ProcessingContext#annotationsAnnotatedWith(Element,
     * String)}: list each meta-annotation to follow. The annotations the container of a repeatable annotation wraps
     * count, and a class has the {@code @Inherited} annotations of its superclasses. An annotation type read from a
     * class file only shows the meta-annotations kept in class files, with a retention of {@code CLASS} or
     * {@code RUNTIME}.
     *
     * <p>As with {@link #annotations()}, an element whose signature has a type javac could not resolve yet is held back
     * until a later round resolves it. The annotation that has the meta-annotation must be resolved, though: until its
     * type exists, javac cannot tell what annotates it. Javac only shows a round the elements of the sources and
     * classes new in that round, so an element whose annotation type is generated in a later round than the element
     * itself is never found by its meta-annotations; an annotation type generated with or before the code it annotates
     * is.
     *
     * <p>A {@link StepProcessor} with a step that has meta-annotations supports all annotations, {@code *}, and so
     * takes part in every round. It still claims none, so that other processors see them.
     *
     * @return the fully qualified names of the meta-annotation types; none by default
     */
    default Set<String> metaAnnotations() {
        return Set.of();
    }

    /**
     * Returns whether this step also gets the root elements of each round, annotated or not, with
     * {@link AnnotatedElements#rootElements()}, such as to process the classes that implement an interface.
     *
     * <pre>{@code
     * public Set<String> annotations() {
     *     return Set.of();
     * }
     *
     * public boolean processesRootElements() {
     *     return true;
     * }
     *
     * public Set<Element> process(AnnotatedElements elements) {
     *     for (TypeElement type : ElementFilter.typesIn(elements.rootElements())) {
     *         ...
     *     }
     *     return Set.of();
     * }
     * }</pre>
     *
     * <p>The root elements are those of {@link javax.annotation.processing.RoundEnvironment#getRootElements()}: the
     * top-level types of the sources and classes new in the round, and the packages and modules their
     * {@code package-info} and {@code module-info} files declare. Nested types are among the enclosed elements of their
     * top-level type. The sources a round generates are root elements of the next one, including those this step
     * generates.
     *
     * <p>As with {@link #annotations()}, a root element whose signature, or the signature of one of its members, has a
     * type javac could not resolve yet is held back until a later round resolves it. If none does, the processor does
     * not report it: javac reports the unresolved type itself.
     *
     * <p>A {@link StepProcessor} with a step that processes root elements supports all annotations, {@code *}, and so
     * takes part in every round. It still claims none, so that other processors see them.
     *
     * @return {@code true} to get the root elements; {@code false} by default
     */
    default boolean processesRootElements() {
        return false;
    }

    /**
     * Processes the elements annotated with this step's annotations that are ready: every type in their signature is
     * resolved. The others are kept for a later round, and given to this step once they are ready. The same goes for
     * the root elements, if this step {@linkplain #processesRootElements() processes them}.
     *
     * @param elements the ready elements, in source order, by each of this step's annotations they have, and by each of
     *     its meta-annotations their annotations have; and the ready root elements, if this step processes them
     * @return the elements to give to this step again in the next round, for example because a type they need is not
     *     generated yet: elements with its annotations, or with annotations its meta-annotations annotate, and root
     *     elements if it processes them; any other element is reported as an error, as is an element still deferred
     *     after the last round
     * @throws ProcessingException if an element is invalid: the {@link StepProcessor} reports the error, and gives none
     *     of these elements to this step again
     */
    Set<? extends Element> process(AnnotatedElements elements);

    /**
     * Finishes this step once all rounds are done: to check what they produced together, to report a summary, or to
     * register or {@linkplain ServiceFiles#discard(be.imgn.gaia.processing.source.ClassName) discard} the
     * implementations of a service before its file is written.
     *
     * <pre>{@code
     * public void processingOver() {
     *     if (hasHandWrittenServiceFile()) {
     *         context.services().discard(FACTORY);
     *         context.diagnostics().error("Remove " + FACTORY_FILE + ": the processor writes it");
     *         return;
     *     }
     *     context.diagnostics().note("On the module path, declare: provides " + FACTORY + " with ...;");
     * }
     * }</pre>
     *
     * <p>The {@link StepProcessor} calls it once, for each step in turn, in the last round, the one where
     * {@link javax.annotation.processing.RoundEnvironment#processingOver()} is {@code true}: after it reports the
     * elements still deferred, and before it writes the service files. Javac only runs that round for a processor it
     * ran in an earlier round, as when the compilation has one of the annotations the processor supports. It is too
     * late to generate sources then: no processor sees them. An element of an earlier round is found again by name,
     * with {@link ProcessingContext#elements()}, and errors and notes are reported through
     * {@link ProcessingContext#diagnostics()}.
     *
     * <p>The service files are written whatever errors were reported, except the files of the services whose
     * implementations were discarded.
     *
     * @throws ProcessingException if something is invalid: the {@link StepProcessor} reports the error, and goes on
     *     with the next step; any other exception propagates, and the service files are not written
     */
    default void processingOver() {}

    /**
     * Returns the name of this step in the debug output of its {@link StepProcessor}, which {@code -Agaia.debug} turns
     * on. If several steps of a processor have the same name, each is followed there by {@code #} and its rank among
     * them, as {@code MapperStep#1} and {@code MapperStep#2}.
     *
     * @return the name; by default, the simple name of the class of this step, or for an anonymous class, its binary
     *     name without the package, such as {@code MapperProcessor$1}
     */
    default String name() {
        return DebugLog.name(getClass());
    }
}
