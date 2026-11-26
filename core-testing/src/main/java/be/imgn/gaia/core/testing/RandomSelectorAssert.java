package be.imgn.gaia.core.testing;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.math.BigFraction;
import be.imgn.gaia.core.random.Chances;
import be.imgn.gaia.core.random.RandomSelector;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import org.assertj.core.api.AbstractAssert;
import org.jspecify.annotations.Nullable;

/**
 * AssertJ assertions on how often a {@link RandomSelector} selects each element: the selector is asked for many
 * elements, and the counts are compared with what the expected chances make likely.
 *
 * <pre>{@code
 * assertThat(rarity).selectsInProportion(Map.of("common", 70, "rare", 25, "legendary", 5));
 * assertThat(rarity).selectsWithItsChances();
 * assertThat(loot).usingRandom(new SplittableRandom(42)).inSelections(1_000_000).selectsWith(expected);
 * }</pre>
 *
 * <p>The comparison is a chi-squared test: an assertion passes when counts as far from the expected ones would come up
 * by chance more than about once in a million tests of a correct selector. A selector that is wrong by little passes
 * too, unless enough elements are selected to tell: with the default of 100,000 selections, chances that are off by one
 * percent of the whole are found, and smaller differences mostly aren't.
 *
 * <p>The selections use a {@link SplittableRandom} with a fixed seed unless another source of randomness is given, so
 * that a test passes or fails the same way each time it runs.
 *
 * @param <T> the type of the elements
 * @since 0.2.0
 */
public final class RandomSelectorAssert<T> extends AbstractAssert<RandomSelectorAssert<T>, RandomSelector<T>> {

    private static final int DEFAULT_SELECTIONS = 100_000;

    // An expected count below this makes the chi-squared test unreliable.
    private static final double MIN_EXPECTED_COUNT = 5;

    // How far in the tail of a normal distribution the test fails: about one chance in a million.
    private static final double TAIL = 4.75;

    // The variance of the cube root of a chi-squared statistic, times its degrees of freedom.
    private static final double WILSON_HILFERTY = 2.0 / 9;

    private RandomGenerator random = new SplittableRandom(0);
    private int selections = DEFAULT_SELECTIONS;

    private RandomSelectorAssert(@Nullable RandomSelector<T> actual) {
        super(actual, RandomSelectorAssert.class);
    }

    /**
     * Returns assertions on a selector.
     *
     * @param <T> the type of the elements
     * @param actual the selector; like any AssertJ assertion, every assertion fails if it is {@code null}
     * @return the assertions
     */
    public static <T> RandomSelectorAssert<T> assertThat(@Nullable RandomSelector<T> actual) {
        return new RandomSelectorAssert<>(actual);
    }

    /**
     * Uses a source of randomness for the selections of the next assertions.
     *
     * @param random the source of randomness
     * @return these assertions
     */
    public RandomSelectorAssert<T> usingRandom(RandomGenerator random) {
        this.random = requireNonNull(random, "random");
        return this;
    }

    /**
     * Sets how many elements the next assertions select, which is 100,000 by default. More selections find smaller
     * differences.
     *
     * @param count the number of selections
     * @return these assertions
     * @throws IllegalArgumentException if {@code count} isn't positive
     */
    public RandomSelectorAssert<T> inSelections(int count) {
        checkArgument(count > 0, "count must be positive: %d", count);
        this.selections = count;
        return this;
    }

    /**
     * Verifies that the selector selects each element about as often as given chances make likely, and no other
     * element.
     *
     * @param expected the expected chances
     * @return these assertions
     * @throws IllegalArgumentException if an element is expected fewer than five times in the selections: more
     *     selections are needed for the comparison to mean something
     */
    public RandomSelectorAssert<T> selectsWith(Chances<T> expected) {
        requireNonNull(expected, "expected");
        isNotNull();
        var chances = expected.exactOutcomes();
        for (var entry : chances.entrySet()) {
            double count = entry.getValue().doubleValue() * selections;
            checkArgument(
                    count >= MIN_EXPECTED_COUNT,
                    "%s is expected about %s times in %d selections: select more",
                    entry.getKey(),
                    count,
                    selections);
        }
        var counts = new LinkedHashMap<T, Integer>();
        for (int i = 0; i < selections; i++) {
            counts.merge(actual.next(random), 1, Integer::sum);
        }
        for (var element : counts.keySet()) {
            if (!chances.containsKey(element)) {
                failWithMessage(
                        "Expected only %s to be selected, but %s was selected %d times in %d",
                        chances.keySet(), element, counts.get(element), selections);
            }
        }
        double statistic = 0;
        for (var entry : chances.entrySet()) {
            double count = entry.getValue().doubleValue() * selections;
            double difference = counts.getOrDefault(entry.getKey(), 0) - count;
            statistic += difference * difference / count;
        }
        double limit = limit(chances.size() - 1);
        if (statistic > limit) {
            failWithMessage(
                    "Expected elements to be selected with the chances%n  %s%nbut in %d selections they were selected"
                            + "%n  %s%ntimes, which is too far from them (chi-squared of %.1f, over the limit of %.1f)",
                    expected, selections, counts, statistic, limit);
        }
        return this;
    }

    /**
     * Verifies that the selector selects each element about as often as its weight makes likely, and no other element.
     *
     * @param weights the expected weight of each element: an element whose weight is zero must never be selected
     * @return these assertions
     * @throws IllegalArgumentException if a weight is negative, if no weight is positive, or if an element is expected
     *     fewer than five times in the selections
     */
    public RandomSelectorAssert<T> selectsInProportion(Map<? extends T, ? extends Number> weights) {
        var shares = new LinkedHashMap<T, BigFraction>();
        weights.forEach((element, weight) -> shares.put(element, BigFraction.of(weight.doubleValue())));
        return selectsWith(Chances.ofShares(shares));
    }

    /**
     * Verifies that the selector selects each element about as often as its own {@link RandomSelector#chances()} make
     * likely: that what it says and what it does agree.
     *
     * @return these assertions
     * @throws UnsupportedOperationException if the selector doesn't know its chances
     * @throws IllegalArgumentException if an element is expected fewer than five times in the selections
     */
    public RandomSelectorAssert<T> selectsWithItsChances() {
        isNotNull();
        return selectsWith(actual.chances());
    }

    /**
     * The value that a chi-squared statistic with some degrees of freedom exceeds about once in a million, by the
     * approximation of Wilson and Hilferty.
     */
    private static double limit(int degreesOfFreedom) {
        if (degreesOfFreedom == 0) {
            // One expected element: any other one has already failed.
            return 0;
        }
        double spread = WILSON_HILFERTY / degreesOfFreedom;
        double root = 1 - spread + TAIL * Math.sqrt(spread);
        return degreesOfFreedom * root * root * root;
    }
}
