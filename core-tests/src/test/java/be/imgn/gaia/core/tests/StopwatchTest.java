package be.imgn.gaia.core.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import be.imgn.gaia.core.testing.FakeTicker;
import be.imgn.gaia.core.time.Stopwatch;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StopwatchTest {

    private final FakeTicker ticker = new FakeTicker();

    @Test
    void measuresTheTimeWhileRunning() {
        Stopwatch stopwatch = Stopwatch.started(ticker);

        ticker.advance(Duration.ofMillis(1500));

        assertThat(stopwatch.isRunning()).isTrue();
        assertThat(stopwatch.elapsed()).isEqualTo(Duration.ofMillis(1500));
    }

    @Test
    void startsUnstartedWithNoElapsedTime() {
        Stopwatch stopwatch = Stopwatch.unstarted(ticker);

        ticker.advance(Duration.ofSeconds(1));

        assertThat(stopwatch.isRunning()).isFalse();
        assertThat(stopwatch.elapsed()).isZero();
    }

    @Test
    void freezesWhileStoppedAndAccumulatesWhenRestarted() {
        Stopwatch stopwatch = Stopwatch.started(ticker);
        ticker.advance(Duration.ofSeconds(1));

        stopwatch.stop();
        ticker.advance(Duration.ofSeconds(10));

        assertThat(stopwatch.elapsed()).isEqualTo(Duration.ofSeconds(1));

        stopwatch.start();
        ticker.advance(Duration.ofSeconds(2));

        assertThat(stopwatch.elapsed()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void resetsToZeroAndStops() {
        Stopwatch stopwatch = Stopwatch.started(ticker);
        ticker.advance(Duration.ofSeconds(1));

        stopwatch.reset();
        ticker.advance(Duration.ofSeconds(1));

        assertThat(stopwatch.isRunning()).isFalse();
        assertThat(stopwatch.elapsed()).isZero();
    }

    @Test
    void rejectsStartingWhileRunningAndStoppingWhileStopped() {
        Stopwatch running = Stopwatch.started(ticker);
        Stopwatch stopped = Stopwatch.unstarted(ticker);

        assertThatIllegalStateException().isThrownBy(running::start);
        assertThatIllegalStateException().isThrownBy(stopped::stop);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            0                | 0.000 ns
            999              | 999.0 ns
            1234             | 1.234 μs
            38349999         | 38.35 ms
            1234000000       | 1.234 s
            90000000000      | 1.500 min
            123456789012     | 2.058 min
            7200000000000    | 2.000 h
            259200000000000  | 3.000 d
            """)
    void printsFourSignificantDigitsInTheLargestUnit(long nanos, String expected) {
        Stopwatch stopwatch = Stopwatch.started(ticker);

        ticker.advance(Duration.ofNanos(nanos));

        assertThat(stopwatch).hasToString(expected);
    }

    @Test
    void measuresTheSystemTicker() {
        assertThat(Stopwatch.started().elapsed()).isGreaterThanOrEqualTo(Duration.ZERO);
    }

    @Test
    void fakeTickerNeverGoesBack() {
        assertThatIllegalArgumentException().isThrownBy(() -> ticker.advance(Duration.ofNanos(-1)));
    }
}
