package be.imgn.gaia.core.random.dice;

import be.imgn.gaia.core.random.dice.DiceNode.Explode;
import be.imgn.gaia.core.random.dice.DiceNode.Keep;
import be.imgn.gaia.core.random.dice.DiceNode.Reroll;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts the ways for a pool of identical dice to reach each sum, without enumerating the rolls.
 *
 * <p>When some dice are dropped, the faces are taken one at a time, from the end that is kept, and for each one the
 * number of dice that show it is chosen among those that remain: there are a binomial coefficient of ways to do so.
 * What is left to remember is how many dice remain and the sum of those kept so far, so rolls that agree on both are
 * counted together. Once enough dice are kept, the remaining ones may show any of the remaining faces.
 *
 * <p>Each face has a weight, the number of ways for a die to end on it: how many times the die has it, then, for a die
 * that is rerolled, the ways for the rerolls to end on it. A face that is always rerolled has none, and a face that is
 * rerolled once has those of the second roll only.
 */
final class Pools {

    /** The most faces that the dice of one node have in total. */
    static final int MAX_TOTAL_SIDES = 1_000_000;

    private Pools() {}

    static Pool pool(int count, DieFaces die, Reroll reroll, Explode explode, Keep keep) {
        Rolls.checkDice(count, die.size());
        if ((long) count * die.size() > MAX_TOTAL_SIDES) {
            throw new ArithmeticException("Too many outcomes: " + count + " dice of " + die.size() + " faces");
        }
        Rolls.checkRules(reroll, explode, die);
        var faces = new Faces(die, reroll, explode);
        if (!explode.equals(Explode.NONE)) {
            var ways =
                    keep.equals(Keep.ALL) ? power(chain(faces), count) : new ExplodingPool(count, faces, keep).ways();
            return new Pool(ways, faces.weight.pow((Explode.MAX_EXPLOSIONS + 1) * count));
        }
        int kept = keep.kept(count);
        if (kept == 0) {
            return new Pool(Map.of(0, BigInteger.ONE), BigInteger.ONE);
        }
        Map<Integer, BigInteger> ways;
        if (kept < count) {
            ways = keptSums(count, faces, kept, keep.highest());
        } else if (reroll.equals(Reroll.NONE) && die.lowest() == 1 && die.highest() == die.size()) {
            ways = sums(count, die.size());
        } else {
            ways = power(faces.byValue(), count);
        }
        return new Pool(ways, faces.weight.pow(count));
    }

    /**
     * The ways for one die and the dice it adds to reach each sum, among the weight of a die to the power of the most
     * dice there may be.
     */
    private static Map<Integer, BigInteger> chain(Faces faces) {
        // The last die that may be added doesn't explode.
        var chain = faces.byValue();
        var scale = BigInteger.ONE;
        for (int added = 1; added <= Explode.MAX_EXPLOSIONS; added++) {
            scale = scale.multiply(faces.weight);
            var longer = new HashMap<Integer, BigInteger>();
            for (int face = 0; face < faces.values.length; face++) {
                int value = faces.values[face];
                var weight = faces.weights[face];
                if (weight.signum() == 0) {
                    continue;
                }
                if (faces.explodes[face]) {
                    chain.forEach((sum, ways) -> add(longer, Math.addExact(sum, value), ways.multiply(weight)));
                } else {
                    // A die that stops there is as many cases as the longer chains have.
                    add(longer, value, weight.multiply(scale));
                }
            }
            chain = longer;
        }
        return chain;
    }

    /** The ways for a number of independent copies of something to reach each sum. */
    private static Map<Integer, BigInteger> power(Map<Integer, BigInteger> ways, int count) {
        Map<Integer, BigInteger> result = Map.of(0, BigInteger.ONE);
        for (int copy = 0; copy < count; copy++) {
            var next = new HashMap<Integer, BigInteger>();
            result.forEach((sum, sumWays) -> ways.forEach(
                    (value, valueWays) -> add(next, Math.addExact(sum, value), sumWays.multiply(valueWays))));
            result = next;
        }
        return result;
    }

    /**
     * The ways to reach each sum with all the dice, when their faces are numbered from one: each die turns the counts
     * into their sums over a window.
     */
    private static Map<Integer, BigInteger> sums(int count, int sides) {
        BigInteger[] current = {BigInteger.ONE};
        for (int dice = 1; dice <= count; dice++) {
            var next = new BigInteger[dice * sides + 1];
            // The ways to reach the sums from sum - sides to sum - 1 with one die less.
            var window = BigInteger.ZERO;
            for (int sum = 0; sum < next.length; sum++) {
                next[sum] = window;
                if (sum < current.length) {
                    window = window.add(current[sum]);
                }
                int leaving = sum - sides;
                if (leaving >= 0 && leaving < current.length) {
                    window = window.subtract(current[leaving]);
                }
            }
            current = next;
        }
        var ways = new HashMap<Integer, BigInteger>();
        for (int sum = 0; sum < current.length; sum++) {
            if (current[sum].signum() != 0) {
                ways.put(sum, current[sum]);
            }
        }
        return ways;
    }

    /** The ways to reach each sum of the {@code kept} highest or lowest dice, where {@code 0 < kept < count}. */
    private static Map<Integer, BigInteger> keptSums(int count, Faces faces, int kept, boolean highest) {
        var binomials = binomials(count);
        var result = new HashMap<Integer, BigInteger>();
        // By number of remaining dice, then by sum of the dice kept so far. Dice remain to be kept in every state.
        List<Map<Integer, BigInteger>> current = states(count);
        current.get(count).put(0, BigInteger.ONE);
        var weightLeft = faces.weight;
        for (int taken = 0; taken < faces.values.length; taken++) {
            int face = highest ? faces.values.length - 1 - taken : taken;
            int value = faces.values[face];
            var weight = faces.weights[face];
            weightLeft = weightLeft.subtract(weight);
            // The ways for some dice to show this face, and to show any of the faces that are left, by number of dice.
            var showingThis = powers(weight, count);
            var showingLeft = powers(weightLeft, count);
            List<Map<Integer, BigInteger>> next = states(count);
            for (int remaining = count - kept + 1; remaining <= count; remaining++) {
                int toKeep = kept - (count - remaining);
                // Every remaining die shows this face if no other one is left; none does if it has no weight.
                int least = weightLeft.signum() == 0 ? remaining : 0;
                int most = weight.signum() == 0 ? 0 : remaining;
                for (var state : current.get(remaining).entrySet()) {
                    for (int showing = least; showing <= most; showing++) {
                        var ways = state.getValue()
                                .multiply(binomials[remaining][showing])
                                .multiply(showingThis[showing]);
                        int counted = Math.min(showing, toKeep);
                        int sum = Math.addExact(state.getKey(), Math.multiplyExact(counted, value));
                        if (counted == toKeep) {
                            add(result, sum, ways.multiply(showingLeft[remaining - showing]));
                        } else {
                            add(next.get(remaining - showing), sum, ways);
                        }
                    }
                }
            }
            current = next;
        }
        return result;
    }

    private static List<Map<Integer, BigInteger>> states(int count) {
        var states = new ArrayList<Map<Integer, BigInteger>>(count + 1);
        for (int remaining = 0; remaining <= count; remaining++) {
            states.add(new HashMap<>());
        }
        return states;
    }

    private static BigInteger[] powers(BigInteger base, int most) {
        var powers = new BigInteger[most + 1];
        powers[0] = BigInteger.ONE;
        for (int exponent = 1; exponent <= most; exponent++) {
            powers[exponent] = powers[exponent - 1].multiply(base);
        }
        return powers;
    }

    private static void add(Map<Integer, BigInteger> ways, int sum, BigInteger added) {
        if (added.signum() != 0) {
            ways.merge(sum, added, BigInteger::add);
        }
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

    /** The different faces of a die, from the lowest, each with its weight and whether it explodes. */
    private static final class Faces {

        private final int[] values;
        private final BigInteger[] weights;
        private final boolean[] explodes;
        private final BigInteger weight;

        Faces(DieFaces die, Reroll reroll, Explode explode) {
            var counts = die.counts();
            var size = BigInteger.valueOf(die.size());
            var rerolled = BigInteger.valueOf(Rolls.rerolled(reroll, die));
            values = new int[counts.size()];
            weights = new BigInteger[counts.size()];
            explodes = new boolean[counts.size()];
            int face = 0;
            for (var count : counts.entrySet()) {
                boolean again = reroll.matches(count.getKey(), die.lowest());
                var times = BigInteger.valueOf(count.getValue());
                values[face] = count.getKey();
                weights[face] = switch (reroll.times()) {
                    case NEVER -> times;
                    // Among the rolls of two dice: the first one if it stays, and any second one after a reroll.
                    case ONCE -> times.multiply(again ? rerolled : rerolled.add(size));
                    case ALWAYS -> again ? BigInteger.ZERO : times;
                };
                explodes[face] = explode.matches(count.getKey(), die.highest());
                face++;
            }
            weight = Arrays.stream(weights).reduce(BigInteger.ZERO, BigInteger::add);
        }

        Map<Integer, BigInteger> byValue() {
            var byValue = new HashMap<Integer, BigInteger>();
            for (int face = 0; face < values.length; face++) {
                add(byValue, values[face], weights[face]);
            }
            return byValue;
        }
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
        private final Faces faces;
        private final Keep keep;
        private final BigInteger[][] binomials;

        // The faces in the order they are taken, from the end that is kept, and the weight of the faces of each
        // kind from each position on.
        private final int[] order;
        private final BigInteger[] explodingLeft;
        private final BigInteger[] othersLeft;
        private final Map<List<Integer>, Map<Integer, BigInteger>> counted = new HashMap<>();

        ExplodingPool(int count, Faces faces, Keep keep) {
            this.count = count;
            this.faces = faces;
            this.keep = keep;
            this.binomials = binomials((Explode.MAX_EXPLOSIONS + 1) * count);
            int size = faces.values.length;
            this.order = new int[size];
            this.explodingLeft = new BigInteger[size + 1];
            this.othersLeft = new BigInteger[size + 1];
            explodingLeft[size] = BigInteger.ZERO;
            othersLeft[size] = BigInteger.ZERO;
            for (int position = size - 1; position >= 0; position--) {
                int face = keep.highest() ? size - 1 - position : position;
                order[position] = face;
                var weight = faces.weights[face];
                explodingLeft[position] =
                        explodingLeft[position + 1].add(faces.explodes[face] ? weight : BigInteger.ZERO);
                othersLeft[position] = othersLeft[position + 1].add(faces.explodes[face] ? BigInteger.ZERO : weight);
            }
        }

        /** The ways to reach each sum, among the weight of a die to the power of the most dice there may be. */
        Map<Integer, BigInteger> ways() {
            int most = Explode.MAX_EXPLOSIONS;
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
            var result = new HashMap<Integer, BigInteger>();
            // Some dice bring eleven exploding faces and no other one.
            for (int full = 0; full <= count; full++) {
                int others = count - full;
                for (int added = 0; added <= most * others; added++) {
                    int exploding = (most + 1) * full + added;
                    // Each die that stops early stands for as many cases as the dice it didn't add would have.
                    var cases = binomials[count][full]
                            .multiply(adding[others][added])
                            .multiply(faces.weight.pow(most * others - added));
                    addAll(result, count(0, exploding, others, keep.kept(exploding + others)), 0, cases);
                }
            }
            return result;
        }

        /** Adds ways to reach sums, each moved by a number and counted a number of times. */
        private static void addAll(
                Map<Integer, BigInteger> sums, Map<Integer, BigInteger> added, int moved, BigInteger times) {
            added.forEach((sum, ways) -> add(sums, Math.addExact(sum, moved), ways.multiply(times)));
        }

        /**
         * The ways for dice of both kinds to reach each sum of the dice still to keep, with the faces from a position
         * on.
         */
        private Map<Integer, BigInteger> count(int position, int exploding, int others, int toKeep) {
            if (toKeep == 0) {
                // The dice that are left show any face of their kind, if there is one: zero to the power zero is one.
                var ways = explodingLeft[position].pow(exploding).multiply(othersLeft[position].pow(others));
                return ways.signum() == 0 ? Map.of() : Map.of(0, ways);
            }
            if (position == order.length) {
                return Map.of();
            }
            var key = List.of(position, exploding, others, toKeep);
            var known = counted.get(key);
            if (known != null) {
                return known;
            }
            int face = order[position];
            var weight = faces.weights[face];
            boolean explodes = faces.explodes[face];
            int dice = explodes ? exploding : others;
            var sums = new HashMap<Integer, BigInteger>();
            var showing = BigInteger.ONE;
            for (int shown = 0; shown <= (weight.signum() == 0 ? 0 : dice); shown++) {
                int taken = Math.min(shown, toKeep);
                var rest = count(
                        position + 1,
                        explodes ? exploding - shown : exploding,
                        explodes ? others : others - shown,
                        toKeep - taken);
                addAll(
                        sums,
                        rest,
                        Math.multiplyExact(taken, faces.values[face]),
                        binomials[dice][shown].multiply(showing));
                showing = showing.multiply(weight);
            }
            counted.put(key, sums);
            return sums;
        }
    }

    /**
     * The number of rolls, among {@code total} equally likely ones, that reach each sum.
     *
     * @param ways the number of rolls by sum, for the sums that some roll reaches
     * @param total the number of rolls
     */
    record Pool(Map<Integer, BigInteger> ways, BigInteger total) {}
}
