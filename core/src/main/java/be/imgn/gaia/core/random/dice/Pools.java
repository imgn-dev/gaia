package be.imgn.gaia.core.random.dice;

import be.imgn.gaia.core.random.dice.DiceNode.Explode;
import be.imgn.gaia.core.random.dice.DiceNode.Keep;
import be.imgn.gaia.core.random.dice.DiceNode.Reroll;
import java.math.BigInteger;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * Counts the ways for a pool of identical dice to reach each sum, without enumerating the rolls.
 *
 * <p>When some dice are dropped, the faces are taken one at a time, from the end that is kept, and for each one the
 * number of dice that show it is chosen among those that remain: there are a binomial coefficient of ways to do so.
 * What is left to remember is how many dice remain and the sum of those kept so far, so rolls that agree on both are
 * counted together. Once enough dice are kept, the remaining ones may show any of the remaining faces.
 *
 * <p>A die that is rerolled is a die whose faces aren't equally likely: each face has a weight, the number of ways for
 * the die to end on it. A face that is always rerolled has none, and a face that is rerolled once has those of the
 * second roll only.
 */
final class Pools {

    /** The most sides that the dice of one node have in total. */
    static final int MAX_TOTAL_SIDES = 1_000_000;

    private Pools() {}

    static Pool pool(int count, int sides, Reroll reroll, Explode explode, Keep keep) {
        Rolls.checkDice(count, sides);
        if ((long) count * sides > MAX_TOTAL_SIDES) {
            throw new ArithmeticException("Too many outcomes: " + count + "d" + sides);
        }
        Rolls.checkRules(reroll, explode, sides);
        var weights = weights(sides, reroll);
        var weight = Arrays.stream(weights).reduce(BigInteger.ZERO, BigInteger::add);
        if (!explode.equals(Explode.NONE)) {
            if (!keep.equals(Keep.ALL)) {
                throw new UnsupportedOperationException(
                        "The chances of dice that explode and of which some are kept or dropped aren't supported");
            }
            return new Pool(
                    power(chain(weights, weight, explode), count), weight.pow((Explode.MAX_EXPLOSIONS + 1) * count));
        }
        int kept = keep.kept(count);
        if (kept == 0) {
            return new Pool(new BigInteger[] {BigInteger.ONE}, BigInteger.ONE);
        }
        @Nullable BigInteger[] ways;
        if (kept < count) {
            ways = keptSums(count, weights, kept, keep.highest());
        } else if (reroll.equals(Reroll.NONE)) {
            ways = sums(count, sides);
        } else {
            // Index zero is the sum of no die.
            var faces = new @Nullable BigInteger[sides + 1];
            System.arraycopy(weights, 0, faces, 1, sides);
            ways = power(faces, count);
        }
        return new Pool(ways, weight.pow(count));
    }

    /** The number of ways for one die to end on each face, from the face one. */
    private static BigInteger[] weights(int sides, Reroll reroll) {
        var matching = BigInteger.valueOf(Rolls.matching(reroll.relation(), reroll.value(), sides));
        var all = BigInteger.valueOf(sides);
        var weights = new BigInteger[sides];
        for (int face = 1; face <= sides; face++) {
            boolean rerolled = reroll.matches(face);
            weights[face - 1] = switch (reroll.times()) {
                case NEVER -> BigInteger.ONE;
                // Among the sides squared rolls of two dice: the first one if it stays, and any rerolled one.
                case ONCE -> rerolled ? matching : matching.add(all);
                case ALWAYS -> rerolled ? BigInteger.ZERO : BigInteger.ONE;
            };
        }
        return weights;
    }

    /**
     * The ways for one die and the dice it adds to reach each sum, among the weight of a die to the power of the most
     * dice there may be.
     */
    private static @Nullable BigInteger[] chain(BigInteger[] weights, BigInteger weight, Explode explode) {
        int sides = weights.length;
        // The last die that may be added doesn't explode.
        var chain = new @Nullable BigInteger[sides + 1];
        System.arraycopy(weights, 0, chain, 1, sides);
        var scale = BigInteger.ONE;
        for (int added = 1; added <= Explode.MAX_EXPLOSIONS; added++) {
            scale = scale.multiply(weight);
            var longer = new @Nullable BigInteger[chain.length + sides];
            for (int face = 1; face <= sides; face++) {
                var faceWeight = weights[face - 1];
                if (faceWeight.signum() == 0) {
                    continue;
                }
                if (explode.matches(face, sides)) {
                    for (int sum = 0; sum < chain.length; sum++) {
                        if (chain[sum] != null) {
                            add(longer, sum + face, chain[sum].multiply(faceWeight));
                        }
                    }
                } else {
                    // A die that stops there is as many cases as the longer chains have.
                    add(longer, face, faceWeight.multiply(scale));
                }
            }
            chain = longer;
        }
        return chain;
    }

    /** The ways for a number of independent copies of something to reach each sum. */
    private static @Nullable BigInteger[] power(@Nullable BigInteger[] ways, int count) {
        @Nullable BigInteger[] result = {BigInteger.ONE};
        for (int copy = 0; copy < count; copy++) {
            var next = new @Nullable BigInteger[result.length + ways.length - 1];
            for (int left = 0; left < result.length; left++) {
                if (result[left] == null) {
                    continue;
                }
                for (int right = 0; right < ways.length; right++) {
                    if (ways[right] != null && ways[right].signum() != 0) {
                        add(next, left + right, result[left].multiply(ways[right]));
                    }
                }
            }
            result = next;
        }
        return result;
    }

    /** The ways to reach each sum with all the dice: each die turns the counts into their sums over a window. */
    private static @Nullable BigInteger[] sums(int count, int sides) {
        @Nullable BigInteger[] current = {BigInteger.ONE};
        for (int dice = 1; dice <= count; dice++) {
            var next = new @Nullable BigInteger[dice * sides + 1];
            // The ways to reach the sums from sum - sides to sum - 1 with one die less.
            var window = BigInteger.ZERO;
            for (int sum = 0; sum < next.length; sum++) {
                if (window.signum() != 0) {
                    next[sum] = window;
                }
                if (sum < current.length && current[sum] != null) {
                    window = window.add(current[sum]);
                }
                int leaving = sum - sides;
                if (leaving >= 0 && leaving < current.length && current[leaving] != null) {
                    window = window.subtract(current[leaving]);
                }
            }
            current = next;
        }
        return current;
    }

    /** The ways to reach each sum of the {@code kept} highest or lowest dice, where {@code 0 < kept < count}. */
    private static @Nullable BigInteger[] keptSums(int count, BigInteger[] weights, int kept, boolean highest) {
        int sides = weights.length;
        var binomials = binomials(count);
        var result = new @Nullable BigInteger[kept * sides + 1];
        // By number of remaining dice, then by sum of the dice kept so far. Dice remain to be kept in every state.
        var current = new @Nullable BigInteger[count + 1][];
        current[count] = new BigInteger[] {BigInteger.ONE};
        var weightLeft = Arrays.stream(weights).reduce(BigInteger.ZERO, BigInteger::add);
        for (int face = 0; face < sides; face++) {
            int value = highest ? sides - face : face + 1;
            var weight = weights[value - 1];
            weightLeft = weightLeft.subtract(weight);
            // The ways for some dice to show this face, and to show any of the faces that are left, by number of dice.
            var showingThis = powers(weight, count);
            var showingLeft = powers(weightLeft, count);
            var next = new @Nullable BigInteger[count + 1][];
            for (int remaining = count - kept + 1; remaining <= count; remaining++) {
                var bySum = current[remaining];
                if (bySum == null) {
                    continue;
                }
                int toKeep = kept - (count - remaining);
                for (int sum = 0; sum < bySum.length; sum++) {
                    var ways = bySum[sum];
                    if (ways == null) {
                        continue;
                    }
                    // Every remaining die shows this face if no other one is left; none does if it has no weight.
                    int least = weightLeft.signum() == 0 ? remaining : 0;
                    int most = weight.signum() == 0 ? 0 : remaining;
                    for (int showing = least; showing <= most; showing++) {
                        var newWays =
                                ways.multiply(binomials[remaining][showing]).multiply(showingThis[showing]);
                        int taken = Math.min(showing, toKeep);
                        int newSum = sum + taken * value;
                        int newRemaining = remaining - showing;
                        if (taken == toKeep) {
                            add(result, newSum, newWays.multiply(showingLeft[newRemaining]));
                        } else {
                            if (next[newRemaining] == null) {
                                next[newRemaining] = new BigInteger[(count - newRemaining) * sides + 1];
                            }
                            add(next[newRemaining], newSum, newWays);
                        }
                    }
                }
            }
            current = next;
        }
        return result;
    }

    private static BigInteger[] powers(BigInteger base, int most) {
        var powers = new BigInteger[most + 1];
        powers[0] = BigInteger.ONE;
        for (int exponent = 1; exponent <= most; exponent++) {
            powers[exponent] = powers[exponent - 1].multiply(base);
        }
        return powers;
    }

    private static void add(@Nullable BigInteger[] ways, int sum, BigInteger added) {
        var existing = ways[sum];
        ways[sum] = existing == null ? added : existing.add(added);
    }

    private static BigInteger[][] binomials(int n) {
        var rows = new BigInteger[n + 1][];
        for (int row = 0; row <= n; row++) {
            rows[row] = new BigInteger[row + 1];
            rows[row][0] = BigInteger.ONE;
            rows[row][row] = BigInteger.ONE;
            for (int column = 1; column < row; column++) {
                rows[row][column] = rows[row - 1][column - 1].add(rows[row - 1][column]);
            }
        }
        return rows;
    }

    /** The number of rolls, among {@code total} equally likely ones, that reach each sum. */
    static final class Pool {

        private final @Nullable BigInteger[] ways;
        private final BigInteger total;

        Pool(@Nullable BigInteger[] ways, BigInteger total) {
            this.ways = ways;
            this.total = total;
        }

        /** The number of rolls by sum, where null stands for none. */
        @Nullable
        BigInteger[] ways() {
            return ways;
        }

        /** The number of rolls. */
        BigInteger total() {
            return total;
        }
    }
}
