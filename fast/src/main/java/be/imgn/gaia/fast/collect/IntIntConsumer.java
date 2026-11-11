package be.imgn.gaia.fast.collect;

/**
 * An operation on an {@code int} key and its {@code int} value, such as an entry of an {@link IntIntMap}.
 *
 * @since 0.2.0
 */
@FunctionalInterface
public interface IntIntConsumer {

    /**
     * Performs this operation on a key and its value.
     *
     * @param key the key
     * @param value the value
     */
    void accept(int key, int value);
}
