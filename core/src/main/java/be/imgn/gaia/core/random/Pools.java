package be.imgn.gaia.core.random;

import be.imgn.gaia.core.random.DiceNode.Keep;
import java.math.BigInteger;
import org.jspecify.annotations.Nullable;

/**
 * Counts the ways for a pool of identical dice to reach each sum, without enumerating the rolls.
 *
 * <p>When some dice are dropped, the faces are taken one at a time, from the end that is kept, and for each one the
 * number of dice that show it is chosen among those that remain: there are a binomial coefficient of ways to do so.
 * What is left to remember is how many dice remain and the sum of those kept so far, so rolls that agree on both are
 * counted together. Once enough dice are kept, the remaining ones may show any of the remaining faces.
 */
final class Pools {

    /** The most sides that the dice of one node have in total. */
    static final int MAX_TOTAL_SIDES = 1_000_000;

    private Pools() {}

    static Pool pool(int count, int sides, @Nullable Keep keep) {
        Rolls.checkDice(count, sides);
        if ((long) count * sides > MAX_TOTAL_SIDES) {
            throw new ArithmeticException("Too many outcomes: " + count + "d" + sides);
        }
        int kept = keep == null ? count : keep.kept(count);
        if (kept == 0) {
            return new Pool(new BigInteger[] {BigInteger.ONE}, BigInteger.ONE);
        }
        var ways = keep == null || kept == count ? sums(count, sides) : keptSums(count, sides, kept, keep.highest());
        return new Pool(ways, BigInteger.valueOf(sides).pow(count));
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
    private static @Nullable BigInteger[] keptSums(int count, int sides, int kept, boolean highest) {
        var binomials = binomials(count);
        var result = new @Nullable BigInteger[kept * sides + 1];
        // By number of remaining dice, then by sum of the dice kept so far. Dice remain to be kept in every state.
        var current = new @Nullable BigInteger[count + 1][];
        current[count] = new BigInteger[] {BigInteger.ONE};
        for (int face = 0; face < sides; face++) {
            int value = highest ? sides - face : face + 1;
            int facesLeft = sides - 1 - face;
            // The ways for some dice to show any of the faces that are left, by number of dice.
            var anyFaceLeft = new @Nullable BigInteger[count + 1];
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
                    // Every remaining die shows the last face.
                    for (int showing = facesLeft == 0 ? remaining : 0; showing <= remaining; showing++) {
                        var newWays = ways.multiply(binomials[remaining][showing]);
                        int taken = Math.min(showing, toKeep);
                        int newSum = sum + taken * value;
                        int newRemaining = remaining - showing;
                        if (taken == toKeep) {
                            if (anyFaceLeft[newRemaining] == null) {
                                anyFaceLeft[newRemaining] =
                                        BigInteger.valueOf(facesLeft).pow(newRemaining);
                            }
                            add(result, newSum, newWays.multiply(anyFaceLeft[newRemaining]));
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
