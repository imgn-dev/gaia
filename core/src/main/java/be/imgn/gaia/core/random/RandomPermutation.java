package be.imgn.gaia.core.random;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.checkIndex;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.hash.Feistel;
import java.util.random.RandomGenerator;
import java.util.stream.LongStream;
import org.jspecify.annotations.Nullable;

/**
 * The numbers from zero to a size, in a random order. Past a few thousand numbers, the order is computed one number at
 * a time: nothing is stored or shuffled, however large the size.
 *
 * <pre>{@code
 * var order = RandomPermutation.of(1_000_000_000_000L, random);
 * order.get(0);                         // the first number, such as 613_204_457_390
 * order.stream().limit(100);            // a hundred different numbers
 * order.indexOf(order.get(42));         // 42
 * }</pre>
 *
 * <p>Every number comes up exactly once. It's meant to visit things in an order that looks random, such as the cells of
 * a grid or the rows of a table. A permutation of at most 4,096 numbers is shuffled in memory, and every order is as
 * likely. A larger one is computed: of all the orders, only some may come up, so it isn't a substitute for
 * {@link java.util.Collections#shuffle(java.util.List, RandomGenerator)} where every order must be as likely, and its
 * order isn't hard to guess.
 *
 * <p>A permutation takes its randomness when it's created, and is immutable afterwards: one call to
 * {@link RandomGenerator#nextInt(int)} per number but the last if it's shuffled, and eight calls to
 * {@link RandomGenerator#nextLong()} if it's computed.
 *
 * @since 0.2.0
 */
public final class RandomPermutation {

    private static final int ROUNDS = 8;

    // Up to this size, the halves of a number are too small for the scrambling to hide its structure.
    private static final int MAX_SHUFFLED_SIZE = 4096;
    private static final long MAX_SIZE = 1L << 62;

    private final long size;

    // For a small permutation, the numbers by position and the positions by number. For a large one, a network that
    // scrambles the numbers of as many bits as the size takes: fewer than twice the size, so that a result past the
    // size, which is scrambled again until it falls within, is so less than half the time.
    private final int @Nullable [] numbers;
    private final int @Nullable [] indexes;
    private final @Nullable Feistel network;

    private RandomPermutation(long size, int @Nullable [] numbers, @Nullable Feistel network) {
        this.size = size;
        this.numbers = numbers;
        this.network = network;
        if (numbers == null) {
            this.indexes = null;
        } else {
            this.indexes = new int[numbers.length];
            for (int index = 0; index < numbers.length; index++) {
                indexes[numbers[index]] = index;
            }
        }
    }

    /**
     * Returns a random order of the numbers from zero to below a size.
     *
     * @param size how many numbers there are
     * @param random the source of randomness
     * @return a new permutation
     * @throws IllegalArgumentException if {@code size} is negative or more than 2^62
     */
    public static RandomPermutation of(long size, RandomGenerator random) {
        requireNonNull(random, "random");
        checkArgument(size >= 0 && size <= MAX_SIZE, "Not a size: %d", size);
        if (size <= MAX_SHUFFLED_SIZE) {
            var numbers = new int[(int) size];
            for (int index = 0; index < numbers.length; index++) {
                numbers[index] = index;
            }
            // Each position takes one of the numbers that are left.
            for (int index = 0; index < numbers.length - 1; index++) {
                int taken = index + random.nextInt(numbers.length - index);
                int number = numbers[taken];
                numbers[taken] = numbers[index];
                numbers[index] = number;
            }
            return new RandomPermutation(size, numbers, null);
        }
        var keys = new long[ROUNDS];
        for (int round = 0; round < ROUNDS; round++) {
            keys[round] = random.nextLong();
        }
        int bits = Long.SIZE - Long.numberOfLeadingZeros(size - 1);
        return new RandomPermutation(size, null, Feistel.of(bits, keys));
    }

    /**
     * Returns how many numbers this permutation has.
     *
     * @return the size
     */
    public long size() {
        return size;
    }

    /**
     * Returns the number at a position.
     *
     * @param index the position, from zero
     * @return the number there, from zero to below the size
     * @throws IndexOutOfBoundsException if {@code index} is negative or not less than the size
     */
    public long get(long index) {
        checkIndex(index, size);
        if (numbers != null) {
            return numbers[(int) index];
        }
        long number = index;
        do {
            number = network().apply(number);
        } while (number >= size);
        return number;
    }

    /**
     * Returns the position of a number.
     *
     * @param number the number, from zero to below the size
     * @return the position {@code index} such that {@code get(index) == number}
     * @throws IndexOutOfBoundsException if {@code number} is negative or not less than the size
     */
    public long indexOf(long number) {
        checkIndex(number, size);
        if (indexes != null) {
            return indexes[(int) number];
        }
        long index = number;
        do {
            index = network().invert(index);
        } while (index >= size);
        return index;
    }

    /**
     * Returns the numbers, in the order of this permutation.
     *
     * @return a stream of {@link #size()} different numbers
     */
    public LongStream stream() {
        return LongStream.range(0, size).map(this::get);
    }

    private Feistel network() {
        return requireNonNull(network);
    }
}
