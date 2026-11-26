package be.imgn.gaia.core.random.dice;

import be.imgn.gaia.core.random.dice.DiceNode.Explode;
import be.imgn.gaia.core.random.dice.DiceNode.Keep;
import be.imgn.gaia.core.random.dice.DiceNode.Reroll;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            var ways = keep.equals(Keep.ALL)
                    ? power(chain(weights, weight, explode), count)
                    : new ExplodingPool(count, weights, explode, keep).ways();
            return new Pool(ways, weight.pow((Explode.MAX_EXPLOSIONS + 1) * count));
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

    /**
     * Counts the sums of the kept dice of a pool whose dice explode.
     *
     * <p>Whatever the order they were rolled in, the dice of such a pool are of two kinds: those that show a face that
     * explodes, and those that show another face. Each die that was rolled at first brings from none to ten dice of the
     * first kind and one of the second, or eleven of the first kind if its last added die shows a face that would have
     * exploded. So the pool is counted for each number of dice of both kinds, as dice that only have the faces of their
     * kind, times the number of ways for the dice rolled at first to bring those numbers.
     */
    private static final class ExplodingPool {

        private final int count;
        private final BigInteger[] weights;
        private final Explode explode;
        private final Keep keep;
        private final int sides;
        private final BigInteger[][] binomials;

        // The faces in the order they are taken, from the end that is kept, and the weight of the faces of each
        // kind from each position on.
        private final int[] faces;
        private final BigInteger[] explodingLeft;
        private final BigInteger[] othersLeft;
        private final Map<List<Integer>, @Nullable BigInteger[]> counted = new HashMap<>();

        ExplodingPool(int count, BigInteger[] weights, Explode explode, Keep keep) {
            this.count = count;
            this.weights = weights;
            this.explode = explode;
            this.keep = keep;
            this.sides = weights.length;
            this.binomials = binomials((Explode.MAX_EXPLOSIONS + 1) * count);
            this.faces = new int[sides];
            this.explodingLeft = new BigInteger[sides + 1];
            this.othersLeft = new BigInteger[sides + 1];
            explodingLeft[sides] = BigInteger.ZERO;
            othersLeft[sides] = BigInteger.ZERO;
            for (int position = sides - 1; position >= 0; position--) {
                int face = keep.highest() ? sides - position : position + 1;
                boolean explodes = explode.matches(face, sides);
                faces[position] = face;
                explodingLeft[position] =
                        explodingLeft[position + 1].add(explodes ? weights[face - 1] : BigInteger.ZERO);
                othersLeft[position] = othersLeft[position + 1].add(explodes ? BigInteger.ZERO : weights[face - 1]);
            }
        }

        /** The ways to reach each sum, among the weight of a die to the power of the most dice there may be. */
        @Nullable
        BigInteger[] ways() {
            int most = Explode.MAX_EXPLOSIONS;
            var weight = explodingLeft[0].add(othersLeft[0]);
            // The ways for some dice to add a number of dice in all, none of them eleven.
            var adding = new BigInteger[count + 1][];
            adding[0] = new BigInteger[] {BigInteger.ONE};
            for (int dice = 1; dice <= count; dice++) {
                adding[dice] = new BigInteger[most * dice + 1];
                Arrays.fill(adding[dice], BigInteger.ZERO);
                for (int added = 0; added < adding[dice - 1].length; added++) {
                    for (int more = 0; more <= most; more++) {
                        adding[dice][added + more] = adding[dice][added + more].add(adding[dice - 1][added]);
                    }
                }
            }
            var result = new @Nullable BigInteger[(most + 1) * count * sides + 1];
            // Some dice bring eleven exploding faces and no other one.
            for (int full = 0; full <= count; full++) {
                int others = count - full;
                for (int added = 0; added <= most * others; added++) {
                    int exploding = (most + 1) * full + added;
                    // Each die that stops early stands for as many cases as the dice it didn't add would have.
                    var cases = binomials[count][full]
                            .multiply(adding[others][added])
                            .multiply(weight.pow(most * others - added));
                    addAll(result, count(0, exploding, others, keep.kept(exploding + others)), 0, cases);
                }
            }
            return result;
        }

        /** Adds ways to reach sums, each moved by a number and counted a number of times. */
        private static void addAll(
                @Nullable BigInteger[] sums, @Nullable BigInteger[] added, int moved, BigInteger times) {
            for (int sum = 0; sum < added.length; sum++) {
                if (added[sum] != null && added[sum].signum() != 0) {
                    add(sums, sum + moved, added[sum].multiply(times));
                }
            }
        }

        /**
         * The ways for dice of both kinds to reach each sum of the dice still to keep, with the faces from a position
         * on.
         */
        private @Nullable BigInteger[] count(int position, int exploding, int others, int toKeep) {
            if (toKeep == 0) {
                // The dice that are left show any face of their kind, if there is one: zero to the power zero is one.
                return new BigInteger[] {
                    explodingLeft[position].pow(exploding).multiply(othersLeft[position].pow(others))
                };
            }
            if (position == sides) {
                return new BigInteger[] {BigInteger.ZERO};
            }
            var key = List.of(position, exploding, others, toKeep);
            var known = counted.get(key);
            if (known != null) {
                return known;
            }
            int face = faces[position];
            var weight = weights[face - 1];
            boolean explodes = explode.matches(face, sides);
            int dice = explodes ? exploding : others;
            var sums = new @Nullable BigInteger[toKeep * sides + 1];
            var showing = BigInteger.ONE;
            for (int shown = 0; shown <= (weight.signum() == 0 ? 0 : dice); shown++) {
                int taken = Math.min(shown, toKeep);
                var rest = count(
                        position + 1,
                        explodes ? exploding - shown : exploding,
                        explodes ? others : others - shown,
                        toKeep - taken);
                addAll(sums, rest, taken * face, binomials[dice][shown].multiply(showing));
                showing = showing.multiply(weight);
            }
            counted.put(key, sums);
            return sums;
        }
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
