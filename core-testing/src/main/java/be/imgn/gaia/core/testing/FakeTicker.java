package be.imgn.gaia.core.testing;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.time.Ticker;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link Ticker} that only moves when told to, so that tests of timing code are exact and instant.
 *
 * <pre>{@code
 * FakeTicker ticker = new FakeTicker();
 * Stopwatch stopwatch = Stopwatch.started(ticker);
 * ticker.advance(Duration.ofSeconds(2));
 * assertThat(stopwatch.elapsed()).isEqualTo(Duration.ofSeconds(2));
 * }</pre>
 *
 * <p>It is thread-safe.
 */
public final class FakeTicker implements Ticker {

    private final AtomicLong nanos = new AtomicLong();

    /** Creates a ticker that reads zero until advanced. */
    public FakeTicker() {}

    /**
     * Moves this ticker forward.
     *
     * @param duration how far to move it
     * @return this ticker
     * @throws IllegalArgumentException if {@code duration} is negative: a ticker never goes back
     * @throws ArithmeticException if {@code duration} does not fit in a {@code long} of nanoseconds
     */
    public FakeTicker advance(Duration duration) {
        requireNonNull(duration, "duration");
        checkArgument(!duration.isNegative(), "A ticker never goes back: %s", duration);
        nanos.addAndGet(duration.toNanos());
        return this;
    }

    @Override
    public long read() {
        return nanos.get();
    }
}
