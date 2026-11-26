package be.imgn.gaia.core.random;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.math.BigFraction;
import be.imgn.gaia.core.math.IntFraction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;

/**
 * The exact chances of each outcome of something random: of each element that a {@link RandomSelector} selects, or of
 * each multiset that a {@link RandomTable} draws.
 *
 * <pre>{@code
 * var rarity = RandomSelector.weighted(Maps.map("common", 70, "rare", 25, "legendary", 5));
 * var chances = rarity.chances();
 * chances.of("rare");                                      // 1/4
 * chances.where(name -> !name.equals("common"));           // 3/10
 * chances.outcomes();                                      // {common=7/10, rare=1/4, legendary=1/20}
 * }</pre>
 *
 * <p>Chances are computed on fractions of any size. A method that returns fractions throws {@link ArithmeticException}
 * if one of them, in lowest terms, has a numerator or a denominator that doesn't fit in an {@code int}. The chances of
 * weights that are whole numbers are simple fractions; a weight such as {@code 0.1} is the {@code double} nearest to a
 * tenth, which is a fraction of very large numbers.
 *
 * <p>Outcomes are told apart with {@code equals} and {@code hashCode}, and kept in the order they were given. Chances
 * are immutable.
 *
 * @param <T> the type of the outcomes
 * @since 0.2.0
 */
public final class Chances<T> {

    private final Map<T, BigFraction> chances;

    private Chances(Map<T, BigFraction> chances) {
        this.chances = chances;
    }

    /**
     * Returns the chances of outcomes whose chances are known.
     *
     * @param <T> the type of the outcomes
     * @param chances the chance of each outcome: an outcome whose chance is zero is left out
     * @return the chances
     * @throws IllegalArgumentException if a chance is negative, or if the chances don't add up to one
     */
    public static <T> Chances<T> ofChances(Map<? extends T, BigFraction> chances) {
        var copy = new LinkedHashMap<T, BigFraction>();
        var total = BigFraction.ZERO;
        for (var entry : chances.entrySet()) {
            var chance = entry.getValue();
            checkArgument(chance.signum() >= 0, "Negative chance for %s: %s", entry.getKey(), chance);
            if (chance.signum() > 0) {
                copy.put(requireNonNull(entry.getKey(), "chances has a null outcome"), chance);
                total = total.plus(chance);
            }
        }
        checkArgument(total.equals(BigFraction.ONE), "The chances add up to %s, not to 1", total);
        return new Chances<>(Collections.unmodifiableMap(copy));
    }

    /**
     * Returns the chances of outcomes that are each given a share of the whole, such as a weight or a number of cases.
     *
     * @param <T> the type of the outcomes
     * @param shares the share of each outcome: an outcome whose share is zero is left out
     * @return the chances, each one the share of its outcome over the sum of the shares
     * @throws IllegalArgumentException if a share is negative, or if no share is positive
     */
    public static <T> Chances<T> ofShares(Map<? extends T, BigFraction> shares) {
        var total = BigFraction.ZERO;
        for (var entry : shares.entrySet()) {
            var share = entry.getValue();
            checkArgument(share.signum() >= 0, "Negative share for %s: %s", entry.getKey(), share);
            total = total.plus(share);
        }
        checkArgument(total.signum() > 0, "No outcome has a positive share");
        var chances = new LinkedHashMap<T, BigFraction>();
        for (var entry : shares.entrySet()) {
            chances.put(entry.getKey(), entry.getValue().dividedBy(total));
        }
        return ofChances(chances);
    }

    /**
     * Returns the chance of an outcome.
     *
     * @param outcome the outcome
     * @return its chance, which is zero if it never happens
     * @throws ArithmeticException if the chance doesn't fit
     */
    public IntFraction of(T outcome) {
        return chances.getOrDefault(outcome, BigFraction.ZERO).toIntFractionExact();
    }

    /**
     * Returns the chance of the outcomes that satisfy a predicate.
     *
     * @param predicate the outcomes to count
     * @return the sum of their chances
     * @throws ArithmeticException if the chance doesn't fit
     */
    public IntFraction where(Predicate<? super T> predicate) {
        requireNonNull(predicate, "predicate");
        var total = BigFraction.ZERO;
        for (var entry : chances.entrySet()) {
            if (predicate.test(entry.getKey())) {
                total = total.plus(entry.getValue());
            }
        }
        return total.toIntFractionExact();
    }

    /**
     * Returns the chance of each outcome that may happen.
     *
     * @return the outcomes with their chances, none of which is zero, in the order the outcomes were given
     * @throws ArithmeticException if a chance doesn't fit
     */
    public Map<T, IntFraction> outcomes() {
        var outcomes = new LinkedHashMap<T, IntFraction>();
        chances.forEach((outcome, chance) -> outcomes.put(outcome, chance.toIntFractionExact()));
        return Collections.unmodifiableMap(outcomes);
    }

    /**
     * Returns the chance of each outcome that may happen, as fractions of any size.
     *
     * @return the outcomes with their chances, none of which is zero, in the order the outcomes were given
     */
    public Map<T, BigFraction> exactOutcomes() {
        return chances;
    }

    /**
     * Returns the chances of what a function returns for the outcomes. Outcomes for which it returns equal values add
     * up.
     *
     * @param <R> the type of the new outcomes
     * @param mapper the function to apply to each outcome
     * @return the chances of the mapped outcomes
     */
    public <R> Chances<R> map(Function<? super T, ? extends R> mapper) {
        requireNonNull(mapper, "mapper");
        var mapped = new LinkedHashMap<R, BigFraction>();
        chances.forEach((outcome, chance) -> mapped.merge(mapper.apply(outcome), chance, BigFraction::plus));
        return new Chances<>(Collections.unmodifiableMap(mapped));
    }

    /**
     * Returns a selector that selects each outcome with its chance, and whose {@link RandomSelector#chances()} are
     * these ones. It selects in the same time however the chances were computed.
     *
     * <p>The selector rounds each chance to a {@code double}. Each selection calls {@link RandomGenerator#nextLong()}
     * once, or what a uniform selector does if the chances are all equal.
     *
     * @return a selector of the outcomes
     */
    public RandomSelector<T> selector() {
        var outcomes = new ArrayList<>(chances.keySet());
        var weights =
                chances.values().stream().mapToDouble(BigFraction::doubleValue).toArray();
        var selector = Selectors.weighted(outcomes, weights);
        return new RandomSelector<>() {
            @Override
            public T next(RandomGenerator random) {
                return selector.next(random);
            }

            @Override
            public Chances<T> chances() {
                return Chances.this;
            }
        };
    }

    /**
     * Returns whether an object is the chances of the same outcomes, with the same chances.
     *
     * @param object the object to compare
     * @return whether {@code object} is equal chances
     */
    @Override
    public boolean equals(@Nullable Object object) {
        return object instanceof Chances<?> other && chances.equals(other.chances);
    }

    @Override
    public int hashCode() {
        return chances.hashCode();
    }

    /**
     * Returns the outcomes with their chances, such as {@code {common=7/10, rare=1/4, legendary=1/20}}.
     *
     * @return the chances as a text
     */
    @Override
    public String toString() {
        return chances.toString();
    }
}
