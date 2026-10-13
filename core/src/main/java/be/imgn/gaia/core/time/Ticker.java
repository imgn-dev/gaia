package be.imgn.gaia.core.time;

/**
 * A source of nanosecond time, for measuring elapsed time: only the difference between two readings is meaningful.
 *
 * <p>Code that reads time through a {@code Ticker} it is given, rather than through {@link System#nanoTime()}, can be
 * tested with a ticker that tests advance by hand, such as {@code FakeTicker} in {@code gaia-core-testing}.
 */
@FunctionalInterface
public interface Ticker {

    /**
     * Returns the ticker of the running JVM, which reads {@link System#nanoTime()}.
     *
     * @return the system ticker
     */
    static Ticker system() {
        return System::nanoTime;
    }

    /**
     * Returns the current reading, in nanoseconds since an arbitrary origin.
     *
     * @return the current reading
     */
    long read();
}
