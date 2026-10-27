package be.imgn.gaia.processing;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.collect.Sets;
import be.imgn.gaia.core.time.Ticker;
import be.imgn.gaia.processing.internal.DebugLog;
import be.imgn.gaia.processing.internal.ElementKey;
import be.imgn.gaia.processing.internal.MetaAnnotated;
import be.imgn.gaia.processing.internal.Resolution;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import org.jspecify.annotations.Nullable;

/**
 * An annotation processor made of {@link Step}s, which takes care of processing rounds.
 *
 * <pre>{@code
 * public final class MapperProcessor extends StepProcessor {
 *     protected List<Step> steps(ProcessingContext context) {
 *         return List.of(new MapperStep(context));
 *     }
 * }
 * }</pre>
 *
 * <p>Each round, each step gets the elements annotated with its annotations, or with annotations that its
 * {@linkplain Step#metaAnnotations() meta-annotations} annotate, grouped by annotation, and the root elements of the
 * round if it {@linkplain Step#processesRootElements() processes them}. An element whose signature still has a type
 * javac could not resolve, typically one another processor has yet to generate, is held back until a later round
 * resolves it. So is an element a step gives back. An element still held back after the last round is reported as an
 * error, with the type that blocked it. Then each step {@linkplain Step#processingOver() finishes}, and the processor
 * writes the service files its steps registered with {@link ProcessingContext#services()}, whatever errors were
 * reported.
 *
 * <p>A {@link ProcessingException} that escapes a step is reported as an error, at the place it names, and the
 * processor goes on with the next step: the elements of that step in that round are done, none is given back to it. To
 * go on with the next element instead, a step processes each with {@link ProcessingContext#forEach(Iterable,
 * java.util.function.Consumer)}. The same goes for {@link Step#processingOver()}. Any other exception propagates: javac
 * stops and reports it, with its stack trace, as a crash of the processor.
 *
 * <p>The processor supports the annotations of its steps, or all annotations if a step has meta-annotations or
 * processes root elements, and the latest source version. It never claims the annotations, so other processors see them
 * too.
 *
 * <p>Every step processor accepts the boolean option {@code gaia.debug}, as {@code -Agaia.debug} or
 * {@code -Agaia.debug=true}, which turns on its debug output: javac notes, each starting with the simple name of the
 * processor. Each round, one note per step that had work tells which elements it got, how many it gave back, how many
 * were held back with an unresolved type, how many errors it reported through the {@linkplain Diagnostics diagnostics}
 * of the context, and how long it took. One note per round sums its steps up. In the last round, a step only has a note
 * if its {@link Step#processingOver()} reported errors; its time counts in the totals either way. After the last round,
 * one note per step and one for the processor give the totals over all rounds. A step is named by its
 * {@link Step#name() name}, by default the simple name of its class, numbered with {@code #} if several steps have the
 * same. The rounds are those the processor takes part in.
 */
public abstract class StepProcessor extends AbstractProcessor {

    private static final ProcessorOption<Boolean> DEBUG = ProcessorOption.ofBoolean("gaia.debug", false);

    private final Map<Step, Set<ElementKey>> deferred = new LinkedHashMap<>();
    private @Nullable ProcessingContext context;
    private List<Step> steps = List.of();
    private @Nullable DebugLog debug;

    /** Creates the processor; javac does, through the service loader. */
    protected StepProcessor() {}

    /**
     * Returns the steps of this processor. Called once, when javac initializes the processor.
     *
     * @param context the context of the processor, for the steps to work with
     * @return the steps, in the order they run each round
     */
    protected abstract Iterable<? extends Step> steps(ProcessingContext context);

    /**
     * Initializes the processor with javac's processing environment, and creates its steps with
     * {@link #steps(ProcessingContext)}.
     *
     * @param processingEnv the environment
     * @throws IllegalStateException if the processor is already initialized
     */
    @Override
    public final synchronized void init(ProcessingEnvironment processingEnv) {
        requireNonNull(processingEnv, "processingEnv");
        super.init(processingEnv);
        ProcessingContext created = ProcessingContext.of(processingEnv, getClass());
        List<Step> all = new ArrayList<>();
        requireNonNull(steps(created), "steps(ProcessingContext) returned null")
                .forEach(step -> all.add(requireNonNull(step, "steps(ProcessingContext) returned a null step")));
        context = created;
        steps = List.copyOf(all);
        if (created.option(DEBUG)) {
            debug = new DebugLog(
                    DebugLog.name(getClass()),
                    DebugLog.numbered(steps.stream().map(Step::name).toList()),
                    created.diagnostics()::note,
                    Ticker.system());
        }
    }

    /**
     * Returns the options this processor accepts, given to javac as {@code -Aname=value}. Javac warns about any other
     * option no processor accepts. The processor also accepts {@code gaia.debug}, without declaring it here.
     *
     * @return the options; none by default
     */
    protected Set<ProcessorOption<?>> options() {
        return Set.of();
    }

    @Override
    public final Set<String> getSupportedOptions() {
        return Stream.of(
                        super.getSupportedOptions().stream(),
                        options().stream().map(ProcessorOption::name),
                        Stream.of(DEBUG.name()))
                .flatMap(names -> names)
                .collect(Sets.toSet());
    }

    /**
     * Returns the annotations of the steps; or {@code *}, all annotations, if a step has
     * {@linkplain Step#metaAnnotations() meta-annotations}, since the annotations they annotate are not known in
     * advance, or if a step {@linkplain Step#processesRootElements() processes root elements}, which javac only gives
     * to the processors that support {@code *} when none is annotated. The processor never claims annotations either
     * way.
     *
     * @return the fully qualified names of the annotation types of the steps, or {@code *} alone
     */
    @Override
    public final Set<String> getSupportedAnnotationTypes() {
        if (!metaAnnotations().isEmpty() || steps.stream().anyMatch(Step::processesRootElements)) {
            return Set.of("*");
        }
        return steps.stream().flatMap(step -> step.annotations().stream()).collect(Sets.toSet());
    }

    /**
     * Returns the latest source version, so that the processor never warns about newer sources.
     *
     * @return {@link SourceVersion#latestSupported()}
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * Processes a round: gives each step the elements it gets in this round, or finishes the steps and writes the
     * service files in the last round.
     *
     * @param annotations the annotations of the round that the processor supports
     * @param roundEnv the environment of the round
     * @return {@code false}: the processor never claims the annotations
     * @throws IllegalStateException if the processor is not initialized
     */
    @Override
    public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        requireNonNull(annotations, "annotations");
        requireNonNull(roundEnv, "roundEnv");
        ProcessingContext current = context();
        DebugLog log = debug;
        if (log != null) {
            log.roundStarted(current.diagnostics().errorCount());
        }
        if (roundEnv.processingOver()) {
            int stillDeferred = reportDeferred(current);
            for (int index = 0; index < steps.size(); index++) {
                finishStep(current, index);
            }
            current.services().write();
            if (log != null) {
                log.lastRoundEnded(stillDeferred, current.diagnostics().errorCount());
            }
        } else {
            Set<String> metaAnnotations = metaAnnotations();
            MetaAnnotated metaAnnotated = metaAnnotations.isEmpty()
                    ? null
                    : new MetaAnnotated(current.elements(), metaAnnotations, roundEnv.getRootElements());
            for (int index = 0; index < steps.size(); index++) {
                processStep(current, index, roundEnv, metaAnnotated);
            }
            if (log != null) {
                log.roundEnded(current.diagnostics().errorCount());
            }
        }
        return false;
    }

    private Set<String> metaAnnotations() {
        return steps.stream().flatMap(step -> step.metaAnnotations().stream()).collect(Sets.toSet());
    }

    private ProcessingContext context() {
        ProcessingContext current = context;
        if (current == null) {
            throw new IllegalStateException("The processor is not initialized");
        }
        return current;
    }

    private void processStep(
            ProcessingContext current, int index, RoundEnvironment roundEnv, @Nullable MetaAnnotated metaAnnotated) {
        Step step = steps.get(index);
        DebugLog log = debug;
        if (log != null) {
            log.stepStarted(index, current.diagnostics().errorCount());
        }
        Batch batch = new Batch(step, metaAnnotated);
        if (step.processesRootElements()) {
            roundEnv.getRootElements().forEach(batch::offerRoot);
        }
        for (ElementKey key : deferred.getOrDefault(step, Set.of())) {
            Element element = key.resolve(current.elements());
            if (element != null) {
                batch.offerAgain(current, element);
            }
        }
        for (String name : step.annotations()) {
            TypeElement annotation = current.elements().getTypeElement(name);
            if (annotation != null) {
                roundEnv.getElementsAnnotatedWith(annotation).forEach(element -> batch.offer(element, name, Set.of()));
            }
        }
        batch.offerMetaAnnotated();
        Set<ElementKey> next = new LinkedHashSet<>(batch.heldBack);
        int heldBack = next.size();
        if (!batch.byAnnotation.isEmpty() || !batch.roots.isEmpty()) {
            processBatch(current, step, new AnnotatedElements(batch.byAnnotation, batch.byMetaAnnotation, batch.roots))
                    .stream()
                    .map(ElementKey::of)
                    .flatMap(Optional::stream)
                    .forEach(next::add);
        }
        deferred.put(step, next);
        if (log != null) {
            List<String> processed = Stream.concat(
                            batch.roots.stream(),
                            batch.byAnnotation.values().stream().flatMap(Set::stream))
                    .distinct()
                    .map(DebugLog::name)
                    .toList();
            log.stepEnded(
                    index,
                    processed,
                    heldBack,
                    next.size() - heldBack,
                    current.diagnostics().errorCount());
        }
    }

    // A ProcessingException that escapes the step is reported; the step gave back no element then.
    private static Set<? extends Element> processBatch(
            ProcessingContext current, Step step, AnnotatedElements elements) {
        try {
            return step.process(elements);
        } catch (ProcessingException e) {
            current.diagnostics().error(e);
            return Set.of();
        }
    }

    // In the debug output, the step's processingOver counts in its time and its errors, as part of the last round.
    private void finishStep(ProcessingContext current, int index) {
        DebugLog log = debug;
        if (log != null) {
            log.stepStarted(index, current.diagnostics().errorCount());
        }
        try {
            steps.get(index).processingOver();
        } catch (ProcessingException e) {
            current.diagnostics().error(e);
        }
        if (log != null) {
            log.stepEnded(index, List.of(), 0, 0, current.diagnostics().errorCount());
        }
    }

    // Returns the number of elements reported.
    private int reportDeferred(ProcessingContext current) {
        int reported = 0;
        for (Set<ElementKey> keys : deferred.values()) {
            for (ElementKey key : keys) {
                Element element = key.resolve(current.elements());
                if (element != null) {
                    current.diagnostics()
                            .on(element)
                            .error(Resolution.firstUnresolved(element)
                                    .map(type -> "Could not process " + element + ": type " + type
                                            + " could not be resolved")
                                    .orElse("Could not process " + element + ": it was still deferred after the"
                                            + " last round"));
                    reported++;
                }
            }
        }
        deferred.clear();
        return reported;
    }

    /**
     * The elements a step gets in a round, by annotation and by meta-annotation, its root elements, and those held back
     * because a type in their signature is not resolved.
     */
    private static final class Batch {

        private final Map<String, Set<Element>> byAnnotation = new LinkedHashMap<>();
        private final Map<String, Set<Element>> byMetaAnnotation = new LinkedHashMap<>();
        private final Set<Element> roots = new LinkedHashSet<>();
        private final Set<ElementKey> heldBack = new LinkedHashSet<>();
        private final Map<Element, Boolean> resolved = new HashMap<>();
        private final Step step;
        private final Set<String> metaAnnotations;
        private final @Nullable MetaAnnotated metaAnnotated;

        Batch(Step step, @Nullable MetaAnnotated metaAnnotated) {
            this.step = step;
            this.metaAnnotations = step.metaAnnotations();
            this.metaAnnotated = metaAnnotated;
        }

        // An element of an earlier round, with the annotations of the step it still has, and as a root element again if
        // it is one and the step processes them.
        void offerAgain(ProcessingContext current, Element element) {
            if (step.processesRootElements() && isRoot(element)) {
                offerRoot(element);
            }
            Set<String> annotations = step.annotations();
            current.elements().getAllAnnotationMirrors(element).stream()
                    .map(MetaAnnotated::qualifiedName)
                    .filter(annotations::contains)
                    .forEach(name -> offer(element, name, Set.of()));
            MetaAnnotated scan = metaAnnotated;
            if (scan != null && !metaAnnotations.isEmpty()) {
                for (AnnotationMirror annotation : scan.annotationsOf(element)) {
                    TypeElement type = MetaAnnotated.typeOf(annotation);
                    offerMetaAnnotated(element, type, scan.of(type));
                }
            }
        }

        // The elements of the round annotated with an annotation that one of the step's meta-annotations annotates.
        void offerMetaAnnotated() {
            MetaAnnotated scan = metaAnnotated;
            if (scan != null && !metaAnnotations.isEmpty()) {
                scan.elementsByType().forEach((type, elements) -> {
                    Set<String> found = scan.of(type);
                    elements.forEach(element -> offerMetaAnnotated(element, type, found));
                });
            }
        }

        private void offerMetaAnnotated(Element element, TypeElement type, Set<String> found) {
            Set<String> matching =
                    found.stream().filter(metaAnnotations::contains).collect(Sets.toSet());
            if (!matching.isEmpty()) {
                offer(element, type.getQualifiedName().toString(), matching);
            }
        }

        void offerRoot(Element element) {
            if (isResolved(element)) {
                roots.add(element);
            }
        }

        void offer(Element element, String annotation, Set<String> annotationMetaAnnotations) {
            if (!isResolved(element)) {
                return;
            }
            byAnnotation
                    .computeIfAbsent(annotation, ignored -> new LinkedHashSet<>())
                    .add(element);
            annotationMetaAnnotations.forEach(meta -> byMetaAnnotation
                    .computeIfAbsent(meta, ignored -> new LinkedHashSet<>())
                    .add(element));
        }

        // Holds an element back if it is not resolved.
        private boolean isResolved(Element element) {
            boolean isResolved = resolved.computeIfAbsent(
                    element, candidate -> Resolution.firstUnresolved(candidate).isEmpty());
            if (!isResolved) {
                ElementKey.of(element).ifPresent(heldBack::add);
            }
            return isResolved;
        }

        // A top-level type, a package or a module.
        private static boolean isRoot(Element element) {
            ElementKind kind = element.getKind();
            if (kind == ElementKind.PACKAGE || kind == ElementKind.MODULE) {
                return true;
            }
            Element enclosing = element.getEnclosingElement();
            return kind.isDeclaredType() && enclosing != null && enclosing.getKind() == ElementKind.PACKAGE;
        }
    }
}
