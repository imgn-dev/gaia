package be.imgn.gaia.fast.collect;

/**
 * A condition on an {@code int} key and its {@code int} value, such as an entry of an {@link IntIntMap}.
 *
 * @since 0.2.0
 */
@FunctionalInterface
public interface IntIntPredicate {

    /**
     * Tests a key and its value.
     *
     * @param key the key
     * @param value the value
     * @return whether they meet the condition
     */
    boolean test(int key, int value);
}
