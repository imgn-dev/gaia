package be.imgn.gaia.core.random;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.hash.Mixers;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * A seed from which other seeds are derived by name or by number, so that each part of a program has a random sequence
 * of its own that only depends on the seed and on what the part is, not on what was generated before it.
 *
 * <pre>{@code
 * var world = Seed.of("my world");
 * var terrain = world.derive("terrain");
 * var chunk = terrain.derive(x).derive(y).generator();   // the same chunk, whenever it's generated
 * var loot = world.derive("loot").derive(chestId).generator();
 * }</pre>
 *
 * <p>Deriving is deterministic and its result is part of this class's contract: a seed derives the same seeds in every
 * version and on every JVM. Seeds derived with different keys, or with the same keys in another order, are unrelated.
 *
 * @param value the 64 bits of the seed
 * @since 0.2.0
 */
public record Seed(long value) {

    /**
     * Returns the seed of a number.
     *
     * @param value the 64 bits of the seed
     * @return the seed
     */
    public static Seed of(long value) {
        return new Seed(value);
    }

    /**
     * Returns the seed of a text, such as a name that a user typed. Every character counts, and so does their order.
     *
     * @param text the text
     * @return the seed, which is the one that the seed zero {@linkplain #derive(CharSequence) derives} with the text
     */
    public static Seed of(CharSequence text) {
        return new Seed(0).derive(text);
    }

    /**
     * Returns a seed that differs each time, to start from when no sequence has to be reproduced. Keep its
     * {@link #value()} to reproduce it after all.
     *
     * @return a new seed
     */
    public static Seed random() {
        return new Seed(new SplittableRandom().nextLong());
    }

    /**
     * Returns the seed that this one derives with a number.
     *
     * @param key the number, such as an identifier or a coordinate
     * @return the derived seed
     */
    public Seed derive(long key) {
        return new Seed(Mixers.mix64(Mixers.mix64(value + Mixers.GOLDEN_GAMMA) + key * Mixers.GOLDEN_GAMMA));
    }

    /**
     * Returns the seed that this one derives with a text. Every character counts, and so does their order.
     *
     * @param key the text, such as the name of what the seed is for
     * @return the derived seed
     */
    public Seed derive(CharSequence key) {
        requireNonNull(key, "key");
        var seed = this;
        for (int i = 0; i < key.length(); i++) {
            seed = seed.derive(key.charAt(i));
        }
        // So that deriving with "ab" differs from deriving with "a" then "b".
        return seed.derive(~(long) key.length());
    }

    /**
     * Returns a new source of randomness that starts from this seed. Two sources of the same seed give the same
     * numbers.
     *
     * @return a new {@link SplittableRandom} seeded with {@link #value()}
     */
    public SplittableRandom generator() {
        return new SplittableRandom(value);
    }

    /**
     * Returns a new source of randomness of a given algorithm that starts from this seed.
     *
     * @param algorithm the name of the algorithm, such as {@code "L64X128MixRandom"} or {@code "Xoshiro256PlusPlus"}
     * @return a new source of randomness seeded with {@link #value()}
     * @throws IllegalArgumentException if no such algorithm is available
     */
    public RandomGenerator generator(String algorithm) {
        return RandomGeneratorFactory.of(algorithm).create(value);
    }
}
