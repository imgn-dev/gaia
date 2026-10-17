package be.imgn.gaia.core.time;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Measures elapsed time, on a {@link Ticker} rather than on the wall clock.
 *
 * <pre>{@code
 * Stopwatch stopwatch = Stopwatch.started();
 * doSomething();
 * log.info("Done in {}", stopwatch);  // Done in 1.234 s
 * }</pre>
 *
 * <p>A stopwatch is either running or stopped. While stopped, its elapsed time does not change; starting it again
 * resumes counting from there. A stopwatch is mutable and not thread-safe.
 */
public final class Stopwatch {

    private static final List<TimeUnit> UNITS = List.of(
            TimeUnit.DAYS,
            TimeUnit.HOURS,
            TimeUnit.MINUTES,
            TimeUnit.SECONDS,
            TimeUnit.MILLISECONDS,
            TimeUnit.MICROSECONDS);

    private final Ticker ticker;
    private boolean running;
    private long elapsedNanos;
    private long startTick;

    private Stopwatch(Ticker ticker) {
        this.ticker = requireNonNull(ticker, "ticker");
    }

    /**
     * Returns a stopped stopwatch, with no elapsed time, on the system ticker.
     *
     * @return a new, stopped stopwatch
     */
    public static Stopwatch unstarted() {
        return unstarted(Ticker.system());
    }

    /**
     * Returns a stopped stopwatch, with no elapsed time, on {@code ticker}.
     *
     * @param ticker the source of time
     * @return a new, stopped stopwatch
     */
    public static Stopwatch unstarted(Ticker ticker) {
        return new Stopwatch(ticker);
    }

    /**
     * Returns a running stopwatch on the system ticker.
     *
     * @return a new, running stopwatch
     */
    public static Stopwatch started() {
        return started(Ticker.system());
    }

    /**
     * Returns a running stopwatch on {@code ticker}.
     *
     * @param ticker the source of time
     * @return a new, running stopwatch
     */
    public static Stopwatch started(Ticker ticker) {
        return unstarted(ticker).start();
    }

    private static TimeUnit unitOf(long nanos) {
        for (TimeUnit unit : UNITS) {
            if (unit.convert(nanos, TimeUnit.NANOSECONDS) > 0) {
                return unit;
            }
        }
        return TimeUnit.NANOSECONDS;
    }

    private static String abbreviate(TimeUnit unit) {
        return switch (unit) {
            case NANOSECONDS -> "ns";
            case MICROSECONDS -> "μs";
            case MILLISECONDS -> "ms";
            case SECONDS -> "s";
            case MINUTES -> "min";
            case HOURS -> "h";
            case DAYS -> "d";
        };
    }

    /**
     * Starts this stopwatch, resuming from its elapsed time.
     *
     * @return this stopwatch
     * @throws IllegalStateException if this stopwatch is already running
     */
    public Stopwatch start() {
        if (running) {
            throw new IllegalStateException("The stopwatch is already running");
        }
        running = true;
        startTick = ticker.read();
        return this;
    }

    /**
     * Stops this stopwatch, keeping its elapsed time.
     *
     * @return this stopwatch
     * @throws IllegalStateException if this stopwatch is already stopped
     */
    public Stopwatch stop() {
        if (!running) {
            throw new IllegalStateException("The stopwatch is already stopped");
        }
        elapsedNanos += ticker.read() - startTick;
        running = false;
        return this;
    }

    /**
     * Stops this stopwatch, if it is running, and sets its elapsed time to zero.
     *
     * @return this stopwatch
     */
    public Stopwatch reset() {
        running = false;
        elapsedNanos = 0;
        return this;
    }

    /**
     * Returns whether this stopwatch is running.
     *
     * @return {@code true} if it is running, {@code false} if it is stopped
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns the elapsed time: the total time this stopwatch has been running since it was created or last reset.
     *
     * @return the elapsed time
     */
    public Duration elapsed() {
        return Duration.ofNanos(elapsedNanos());
    }

    /**
     * Returns the elapsed time in the largest unit in which it is at least 1, with four significant digits, as
     * {@code String.format(Locale.ROOT, "%.4g", value)} prints them, followed by the unit's abbreviation: {@code ns},
     * {@code μs}, {@code ms}, {@code s}, {@code min}, {@code h} or {@code d}. For example {@code "1.234 s"} or
     * {@code "38.35 ms"}.
     *
     * @return the elapsed time, for people to read
     */
    @Override
    public String toString() {
        long nanos = elapsedNanos();
        TimeUnit unit = unitOf(nanos);
        double value = (double) nanos / TimeUnit.NANOSECONDS.convert(1, unit);
        return String.format(Locale.ROOT, "%.4g %s", value, abbreviate(unit));
    }

    private long elapsedNanos() {
        return running ? elapsedNanos + (ticker.read() - startTick) : elapsedNanos;
    }
}
