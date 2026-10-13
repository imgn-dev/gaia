package be.imgn.gaia.processing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.core.time.Ticker;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class DebugLogTest {

    private final List<String> notes = new ArrayList<>();
    private long now;
    private final Ticker ticker = () -> now;

    private void advance(long duration, TimeUnit unit) {
        now += unit.toNanos(duration);
    }

    @Test
    void notesEachRoundAndTheTotalsWithTheirDurations() {
        DebugLog log = new DebugLog("MapperProcessor", List.of("MapperStep", "OtherStep"), notes::add, ticker);

        log.roundStarted(0);
        advance(1, TimeUnit.MILLISECONDS);
        log.stepStarted(0, 0);
        advance(2, TimeUnit.MILLISECONDS);
        log.stepEnded(0, List.of("Car", "Car.wheels()"), 1, 1, 1);
        log.stepStarted(1, 1);
        advance(500, TimeUnit.MICROSECONDS);
        log.stepEnded(1, List.of(), 0, 0, 1);
        log.roundEnded(1);
        log.roundStarted(1);
        log.stepStarted(0, 1);
        advance(3, TimeUnit.MILLISECONDS);
        log.stepEnded(0, List.of("Truck"), 0, 0, 1);
        log.stepStarted(1, 1);
        log.stepEnded(1, List.of(), 0, 0, 1);
        log.roundEnded(1);
        log.roundStarted(1);
        advance(994_500, TimeUnit.MICROSECONDS);
        log.lastRoundEnded(1, 2);

        assertThat(notes)
                .containsExactly(
                        "MapperProcessor round 1, MapperStep: 2 elements [Car, Car.wheels()], 1 given back,"
                                + " 1 held back, 1 error, 2.000 ms",
                        "MapperProcessor round 1: 2 elements, 1 given back, 1 held back, 1 error, 3.500 ms",
                        "MapperProcessor round 2, MapperStep: 1 element [Truck], 0 given back, 0 held back, 0 errors,"
                                + " 3.000 ms",
                        "MapperProcessor round 2: 1 element, 0 given back, 0 held back, 0 errors, 3.000 ms",
                        "MapperProcessor round 3 (last): 1 element still deferred, 1 error, 994.5 ms",
                        "MapperProcessor total, MapperStep: 3 elements in 2 rounds, 1 error, 5.000 ms",
                        "MapperProcessor total, OtherStep: 0 elements in 0 rounds, 0 errors, 500.0 μs",
                        "MapperProcessor total: 3 rounds, 3 elements, 2 errors, 1.001 s");
    }

    @Test
    void notesAStepThatOnlyHeldElementsBack() {
        DebugLog log = new DebugLog("Processor", List.of("Step"), notes::add, ticker);

        log.roundStarted(0);
        log.stepStarted(0, 0);
        log.stepEnded(0, List.of(), 2, 0, 0);

        assertThat(notes)
                .containsExactly("Processor round 1, Step: 0 elements, 0 given back, 2 held back, 0 errors, 0.000 ns");
    }

    @Test
    void notesAStepThatOnlyReportedErrors() {
        DebugLog log = new DebugLog("Processor", List.of("Step"), notes::add, ticker);

        log.roundStarted(0);
        log.stepStarted(0, 3);
        log.stepEnded(0, List.of(), 0, 0, 4);

        assertThat(notes)
                .containsExactly("Processor round 1, Step: 0 elements, 0 given back, 0 held back, 1 error, 0.000 ns");
    }

    @Test
    void showsThreeElementsAndCountsTheOthers() {
        DebugLog log = new DebugLog("Processor", List.of("Step"), notes::add, ticker);

        log.roundStarted(0);
        log.stepStarted(0, 0);
        log.stepEnded(0, List.of("A", "B", "C"), 0, 0, 0);
        log.stepStarted(0, 0);
        log.stepEnded(0, List.of("A", "B", "C", "D"), 0, 0, 0);
        log.stepStarted(0, 0);
        log.stepEnded(0, List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"), 0, 0, 0);

        assertThat(notes)
                .extracting(note -> note.replaceAll(", 0 given back.*", ""))
                .containsExactly(
                        "Processor round 1, Step: 3 elements [A, B, C]",
                        "Processor round 1, Step: 4 elements [A, B, C, +1 more]",
                        "Processor round 1, Step: 12 elements [A, B, C, +9 more]");
    }

    @Test
    void namesAClassBySimpleName() {
        assertThat(DebugLog.name(DebugLogTest.class)).isEqualTo("DebugLogTest");
        assertThat(DebugLog.name(Nested.class)).isEqualTo("Nested");
        assertThat(DebugLog.name(String.class)).isEqualTo("String");
    }

    @Test
    void namesALocalClassBySimpleName() {
        class Local {}

        assertThat(DebugLog.name(Local.class)).isEqualTo("Local");
    }

    @Test
    void namesAnAnonymousClassByBinaryNameWithoutThePackage() {
        Object anonymous = new Object() {};

        assertThat(DebugLog.name(anonymous.getClass())).matches("DebugLogTest\\$\\d+");
    }

    @Test
    void namesALambdaAfterTheClassDefiningIt() {
        Supplier<String> lambda = () -> "";

        assertThat(DebugLog.name(lambda.getClass())).isEqualTo("DebugLogTest$$Lambda");
    }

    @Test
    void numbersTheNamesSeveralHave() {
        assertThat(DebugLog.numbered(List.of("Nested", "String", "Nested", "Nested", "Integer")))
                .containsExactly("Nested#1", "String", "Nested#2", "Nested#3", "Integer");
        assertThat(DebugLog.numbered(List.of("A", "B"))).containsExactly("A", "B");
        assertThat(DebugLog.numbered(List.of())).isEmpty();
    }

    /** A nested class to name. */
    private static final class Nested {}
}
