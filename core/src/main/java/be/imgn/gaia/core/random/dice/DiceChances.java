package be.imgn.gaia.core.random.dice;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.math.BigFraction;
import be.imgn.gaia.core.math.IntFraction;
import be.imgn.gaia.core.random.Chances;
import be.imgn.gaia.core.random.RandomSelector;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * The exact chances of each outcome of a {@link DiceExpression}: the value it evaluates to, together with the tags that
 * apply.
 *
 * <pre>{@code
 * var chances = DiceExpression.parse("(2d20kh1:critical>=20)+5").chances();
 * chances.of(25);                  // 39/400
 * chances.atLeast(15);             // 319/400
 * chances.tagged("critical");      // 39/400
 * chances.mean();                  // 753/40
 * chances.where(outcome -> outcome.value() >= 15 && !outcome.tags().contains("critical"));    // 7/10
 * }</pre>
 *
 * <p>DiceChances are counted with whole numbers of any size, and only the fractions that are asked for are reduced. A
 * method that returns fractions throws {@link ArithmeticException} if one of them, in lowest terms, has a numerator or
 * a denominator that doesn't fit in an {@code int}.
 *
 * @since 0.2.0
 * @see DiceExpression#chances()
 */
public final class DiceChances {

    private final Distribution distribution;
    private final TreeMap<Integer, BigInteger> casesByValue = new TreeMap<>();

    DiceChances(Distribution distribution) {
        this.distribution = distribution;
        distribution.cases().forEach((outcome, count) -> casesByValue.merge(outcome.value(), count, BigInteger::add));
    }

    /**
     * Returns the chance that the expression evaluates to a value.
     *
     * @param value the value
     * @return the chance that the expression evaluates to a value
     */
    public IntFraction of(int value) {
        return fraction(casesByValue.getOrDefault(value, BigInteger.ZERO));
    }

    /**
     * Returns the chance that the expression evaluates to a value or to a greater one.
     *
     * @param value the least value
     * @return the chance that the expression evaluates to a value or to a greater one
     */
    public IntFraction atLeast(int value) {
        return fraction(sum(casesByValue.tailMap(value, true)));
    }

    /**
     * Returns the chance that the expression evaluates to a value or to a lesser one.
     *
     * @param value the greatest value
     * @return the chance that the expression evaluates to a value or to a lesser one
     */
    public IntFraction atMost(int value) {
        return fraction(sum(casesByValue.headMap(value, true)));
    }

    /**
     * Returns the chance that a tag applies.
     *
     * @param tag the name of the tag
     * @return the chance that a tag applies
     */
    public IntFraction tagged(String tag) {
        requireNonNull(tag, "tag");
        return where(outcome -> outcome.tags().contains(tag));
    }

    /**
     * Returns the chance of the outcomes that satisfy a predicate.
     *
     * @param predicate the outcomes to count
     * @return the chance of the outcomes that satisfy a predicate
     */
    public IntFraction where(Predicate<? super Outcome> predicate) {
        requireNonNull(predicate, "predicate");
        var cases = BigInteger.ZERO;
        for (var entry : distribution.cases().entrySet()) {
            if (predicate.test(entry.getKey())) {
                cases = cases.add(entry.getValue());
            }
        }
        return fraction(cases);
    }

    /**
     * Returns the least value that the expression may evaluate to.
     *
     * @return the least value that the expression may evaluate to
     */
    public int min() {
        return casesByValue.firstKey();
    }

    /**
     * Returns the greatest value that the expression may evaluate to.
     *
     * @return the greatest value that the expression may evaluate to
     */
    public int max() {
        return casesByValue.lastKey();
    }

    /**
     * Returns the average of the values that the expression evaluates to, weighted by their chances.
     *
     * @return the average of the values that the expression evaluates to, weighted by their chances
     */
    public IntFraction mean() {
        var weighted = BigInteger.ZERO;
        for (var entry : casesByValue.entrySet()) {
            weighted = weighted.add(entry.getValue().multiply(BigInteger.valueOf(entry.getKey())));
        }
        return fraction(weighted);
    }

    /**
     * Returns the chance of each value that the expression may evaluate to, from the least value to the greatest.
     *
     * @return the chance of each value that the expression may evaluate to, from the least value to the greatest
     */
    public SortedMap<Integer, IntFraction> outcomes() {
        var outcomes = new TreeMap<Integer, IntFraction>();
        casesByValue.forEach((value, cases) -> outcomes.put(value, fraction(cases)));
        return Collections.unmodifiableSortedMap(outcomes);
    }

    /**
     * Returns the chance of each value, as chances that the rest of the {@code random} package works with.
     *
     * @return the chances of the values, from the least value to the greatest
     */
    public Chances<Integer> values() {
        var shares = new LinkedHashMap<Integer, BigFraction>();
        casesByValue.forEach((value, cases) -> shares.put(value, BigFraction.of(cases)));
        return Chances.ofShares(shares);
    }

    /**
     * Returns the chance of each outcome, values and tags together.
     *
     * @return the chances of the outcomes, in no particular order
     */
    public Chances<Outcome> all() {
        var shares = new LinkedHashMap<Outcome, BigFraction>();
        distribution.cases().forEach((outcome, cases) -> shares.put(outcome, BigFraction.of(cases)));
        return Chances.ofShares(shares);
    }

    /**
     * Returns a selector that selects each value with its chance, without rolling any die: one selection takes the same
     * time whatever the expression.
     *
     * @return a selector of the values, whose chances are those of {@link #values()}
     */
    public RandomSelector<Integer> selector() {
        return values().selector();
    }

    private IntFraction fraction(BigInteger cases) {
        return IntFraction.of(cases, distribution.total());
    }

    private static BigInteger sum(SortedMap<Integer, BigInteger> cases) {
        return cases.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
    }

    /**
     * Returns the chance of each value, as {@link #outcomes()} writes them.
     *
     * @return the chance of each value, as {@link #outcomes()} writes them
     */
    @Override
    public String toString() {
        return outcomes().toString();
    }

    /**
     * One way for an expression to turn out.
     *
     * @param value what the expression evaluates to
     * @param tags the names of the tags that apply anywhere in the expression
     */
    public record Outcome(int value, Set<String> tags) {

        /**
         * Creates an outcome.
         *
         * @param value what the expression evaluates to
         * @param tags the names of the tags that apply anywhere in the expression
         */
        public Outcome {
            tags = Set.copyOf(tags);
        }
    }
}
