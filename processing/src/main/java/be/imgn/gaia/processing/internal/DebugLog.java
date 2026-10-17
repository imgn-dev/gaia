package be.imgn.gaia.processing.internal;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.time.Stopwatch;
import be.imgn.gaia.core.time.Ticker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.lang.model.element.Element;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;

/**
 * The debug output of a step processor: one note per step that had work in a round, one per round, and a summary of
 * each step and of the processor after the last round.
 *
 * <pre>{@code
 * MapperProcessor round 1, MapperStep: 2 elements [Car, Car.wheels()], 1 given back, 0 held back, 0 errors, 1.234 ms
 * MapperProcessor round 1: 2 elements, 1 given back, 0 held back, 0 errors, 1.456 ms
 * MapperProcessor round 3 (last): 0 elements still deferred, 0 errors, 12.34 μs
 * MapperProcessor total, MapperStep: 3 elements in 2 rounds, 0 errors, 2.345 ms
 * MapperProcessor total: 3 rounds, 3 elements, 0 errors, 2.678 ms
 * }</pre>
 *
 * <p>The time of a step includes finding its elements and checking that their types are resolved. In the last round, a
 * step is started and ended again around its {@code processingOver}, which counts in its time and its errors. The time
 * of a round includes that of its steps; the time of the last round, reporting the elements still deferred and writing
 * the service files.
 */
public final class DebugLog {

    private static final int SHOWN_ELEMENTS = 3;

    private final String processor;
    private final List<StepTotals> steps;
    private final Consumer<String> notes;
    private final Ticker ticker;
    private final Stopwatch total;
    private int rounds;
    private int elements;
    private int errors;
    private Stopwatch round;
    private int roundStartErrors;
    private int roundElements;
    private int roundGivenBack;
    private int roundHeldBack;
    private Stopwatch step;
    private int stepStartErrors;

    /**
     * Creates the log of a processor.
     *
     * @param processor the name of the processor, which starts every note
     * @param steps the names of the steps, by index
     * @param notes where the notes go
     * @param ticker the source of time
     */
    public DebugLog(String processor, List<String> steps, Consumer<String> notes, Ticker ticker) {
        this.processor = requireNonNull(processor, "processor");
        this.steps = steps.stream().map(name -> new StepTotals(name, ticker)).toList();
        this.notes = requireNonNull(notes, "notes");
        this.ticker = ticker;
        this.total = Stopwatch.unstarted(ticker);
        this.round = Stopwatch.unstarted(ticker);
        this.step = Stopwatch.unstarted(ticker);
    }

    /**
     * Returns the name of a class in notes: its simple name; for an anonymous class, its binary name without the
     * package; for a lambda, the binary name of the class that defines it, followed by {@code $$Lambda}.
     *
     * @param type the class
     * @return its name
     */
    public static String name(Class<?> type) {
        String simpleName = type.getSimpleName();
        if (!simpleName.isEmpty() && !type.isHidden()) {
            return simpleName;
        }
        String binaryName = type.getName();
        int hidden = binaryName.indexOf('/');
        String name = hidden < 0 ? binaryName : binaryName.substring(0, hidden);
        return name.substring(name.lastIndexOf('.') + 1);
    }

    /**
     * Returns the name of an element in notes: the simple name of a type, preceded by those of its enclosing types; the
     * qualified name of a package or module; and for any other element, the name of its enclosing element, a dot, and
     * the element as javac prints it, such as {@code Car.wheels(int)}.
     *
     * @param element the element
     * @return its name
     */
    public static String name(Element element) {
        Element enclosing = element.getEnclosingElement();
        return switch (element) {
            case TypeElement type
            when enclosing instanceof TypeElement outer -> name(outer) + "." + type.getSimpleName();
            case TypeElement type -> type.getSimpleName().toString();
            case QualifiedNameable named -> named.getQualifiedName().toString();
            default -> enclosing == null ? element.toString() : name(enclosing) + "." + element;
        };
    }

    /**
     * Returns names made distinct: each name that several have is followed by {@code #} and its rank among them.
     *
     * @param names the names, such as those of the steps
     * @return the distinct names, in order: {@code [A#1, B, A#2]} for {@code [A, B, A]}
     */
    public static List<String> numbered(List<String> names) {
        Map<String, Integer> ranks = new HashMap<>();
        List<String> unique = new ArrayList<>();
        for (String name : names) {
            unique.add(Collections.frequency(names, name) > 1 ? name + "#" + ranks.merge(name, 1, Integer::sum) : name);
        }
        return List.copyOf(unique);
    }

    private static String count(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    private static String elements(List<String> names) {
        if (names.isEmpty()) {
            return count(0, "element");
        }
        List<String> shown = new ArrayList<>(names.subList(0, Math.min(names.size(), SHOWN_ELEMENTS)));
        if (names.size() > SHOWN_ELEMENTS) {
            shown.add("+" + (names.size() - SHOWN_ELEMENTS) + " more");
        }
        return count(names.size(), "element") + " " + shown;
    }

    /**
     * Starts a round.
     *
     * @param errorCount the number of errors reported so far
     */
    public void roundStarted(int errorCount) {
        rounds++;
        roundStartErrors = errorCount;
        roundElements = 0;
        roundGivenBack = 0;
        roundHeldBack = 0;
        round = Stopwatch.started(ticker);
        total.start();
    }

    /**
     * Starts a step in the current round.
     *
     * @param index the index of the step
     * @param errorCount the number of errors reported so far
     */
    public void stepStarted(int index, int errorCount) {
        stepStartErrors = errorCount;
        step = Stopwatch.started(ticker);
        steps.get(index).time.start();
    }

    /**
     * Ends a step in the current round, and notes what it did, unless it had nothing to do.
     *
     * @param index the index of the step
     * @param processed the names of the elements the step got
     * @param heldBack the number of elements held back because a type in their signature is not resolved
     * @param givenBack the number of elements the step gave back
     * @param errorCount the number of errors reported so far
     */
    public void stepEnded(int index, List<String> processed, int heldBack, int givenBack, int errorCount) {
        step.stop();
        StepTotals totals = steps.get(index);
        totals.time.stop();
        int stepErrors = errorCount - stepStartErrors;
        totals.add(processed.size(), stepErrors);
        roundElements += processed.size();
        roundGivenBack += givenBack;
        roundHeldBack += heldBack;
        if (!processed.isEmpty() || heldBack > 0 || stepErrors > 0) {
            notes.accept(processor + " round " + rounds + ", " + totals.name + ": " + elements(processed) + ", "
                    + givenBack + " given back, " + heldBack + " held back, " + count(stepErrors, "error") + ", "
                    + step);
        }
    }

    /**
     * Ends a round other than the last, and notes what its steps did.
     *
     * @param errorCount the number of errors reported so far
     */
    public void roundEnded(int errorCount) {
        int roundErrors = endRound(errorCount);
        elements += roundElements;
        notes.accept(processor + " round " + rounds + ": " + count(roundElements, "element") + ", " + roundGivenBack
                + " given back, " + roundHeldBack + " held back, " + count(roundErrors, "error") + ", " + round);
    }

    /**
     * Ends the last round, and notes it, each step's totals, and the processor's.
     *
     * @param stillDeferred the number of elements still deferred, reported as errors
     * @param errorCount the number of errors reported so far
     */
    public void lastRoundEnded(int stillDeferred, int errorCount) {
        int roundErrors = endRound(errorCount);
        notes.accept(processor + " round " + rounds + " (last): " + count(stillDeferred, "element")
                + " still deferred, " + count(roundErrors, "error") + ", " + round);
        for (StepTotals totals : steps) {
            notes.accept(processor + " total, " + totals.name + ": " + count(totals.elements, "element") + " in "
                    + count(totals.rounds, "round") + ", " + count(totals.errors, "error") + ", " + totals.time);
        }
        notes.accept(processor + " total: " + count(rounds, "round") + ", " + count(elements, "element") + ", "
                + count(errors, "error") + ", " + total);
    }

    private int endRound(int errorCount) {
        round.stop();
        total.stop();
        int roundErrors = errorCount - roundStartErrors;
        errors += roundErrors;
        return roundErrors;
    }

    /** What a step did over all rounds. */
    private static final class StepTotals {

        private final String name;
        private final Stopwatch time;
        private int elements;
        private int rounds;
        private int errors;

        StepTotals(String name, Ticker ticker) {
            this.name = requireNonNull(name, "name");
            this.time = Stopwatch.unstarted(ticker);
        }

        void add(int processed, int errorCount) {
            elements += processed;
            errors += errorCount;
            if (processed > 0) {
                rounds++;
            }
        }
    }
}
