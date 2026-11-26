package be.imgn.gaia.core.random.dice;

import be.imgn.gaia.core.random.dice.Chances.Outcome;
import be.imgn.gaia.core.random.dice.DiceNode.Keep;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

/**
 * The outcomes of an expression, each with the number of equally likely cases that lead to it, among {@code total}
 * cases. Counts are kept whole, and only turned to fractions when a chance is asked for.
 */
final class Distribution {

    private final Map<Outcome, BigInteger> cases;
    private final BigInteger total;

    private Distribution(Map<Outcome, BigInteger> cases, BigInteger total) {
        this.cases = cases;
        this.total = total;
    }

    Map<Outcome, BigInteger> cases() {
        return cases;
    }

    BigInteger total() {
        return total;
    }

    /** Computes the distribution of one node from those of its children. */
    static Distribution of(DiceNode<Distribution> node) {
        return switch (node) {
            case DiceNode.Constant<Distribution> constant ->
                new Distribution(Map.of(new Outcome(constant.value(), Set.of()), BigInteger.ONE), BigInteger.ONE);
            case DiceNode.Variable<Distribution> variable -> throw new AssertionError(variable.name());
            case DiceNode.Bound<Distribution> bound -> bound.value();
            case DiceNode.Dice<Distribution> dice -> dice(dice.count(), dice.sides(), dice.keep());
            case DiceNode.Negate<Distribution> negate ->
                negate.operand().map(outcome -> new Outcome(Math.negateExact(outcome.value()), outcome.tags()));
            case DiceNode.Arithmetic<Distribution> arithmetic ->
                arithmetic
                        .left()
                        .combine(
                                arithmetic.right(),
                                (left, right) -> new Outcome(
                                        arithmetic.operator().apply(left.value(), right.value()),
                                        union(left.tags(), right.tags())));
            case DiceNode.Comparison<Distribution> comparison ->
                comparison
                        .left()
                        .combine(
                                comparison.right(),
                                (left, right) -> new Outcome(
                                        comparison.relation().test(left.value(), right.value()) ? 1 : 0,
                                        union(left.tags(), right.tags())));
            case DiceNode.Group<Distribution> group -> group(group);
        };
    }

    private static Distribution group(DiceNode.Group<Distribution> group) {
        var result = group.inner();
        for (var tag : group.tags()) {
            var name = Set.of(tag.name());
            var condition = tag.condition();
            if (condition == null) {
                result = result.map(outcome -> new Outcome(outcome.value(), union(outcome.tags(), name)));
            } else {
                // The value stays that of the group, so that the next tags test it too.
                result = result.combine(
                        condition.operand(),
                        (outcome, operand) -> new Outcome(
                                outcome.value(),
                                condition.relation().test(outcome.value(), operand.value())
                                        ? union(union(outcome.tags(), operand.tags()), name)
                                        : union(outcome.tags(), operand.tags())));
            }
        }
        return result;
    }

    private static Distribution dice(Distribution count, Distribution sides, Keep keep) {
        // One pool for each number of dice and of sides, weighted by the cases that lead to those numbers.
        record Part(BigInteger weight, Set<String> tags, Pools.Pool pool) {}
        var parts = new ArrayList<Part>();
        var pools = new HashMap<List<Integer>, Pools.Pool>();
        var scale = BigInteger.ONE;
        for (var countCase : count.cases.entrySet()) {
            for (var sidesCase : sides.cases.entrySet()) {
                int dice = countCase.getKey().value();
                int faces = sidesCase.getKey().value();
                var pool = pools.computeIfAbsent(List.of(dice, faces), key -> Pools.pool(dice, faces, keep));
                parts.add(new Part(
                        countCase.getValue().multiply(sidesCase.getValue()),
                        union(countCase.getKey().tags(), sidesCase.getKey().tags()),
                        pool));
                scale = scale.multiply(pool.total()).divide(scale.gcd(pool.total()));
            }
        }
        var cases = new HashMap<Outcome, BigInteger>();
        for (var part : parts) {
            // Brings every pool to the same number of cases.
            var weight = part.weight.multiply(scale.divide(part.pool.total()));
            var ways = part.pool.ways();
            for (int sum = 0; sum < ways.length; sum++) {
                if (ways[sum] != null) {
                    cases.merge(new Outcome(sum, part.tags), ways[sum].multiply(weight), BigInteger::add);
                }
            }
        }
        return new Distribution(cases, count.total.multiply(sides.total).multiply(scale));
    }

    private Distribution map(UnaryOperator<Outcome> function) {
        var mapped = new HashMap<Outcome, BigInteger>();
        cases.forEach((outcome, count) -> mapped.merge(function.apply(outcome), count, BigInteger::add));
        return new Distribution(mapped, total);
    }

    /** Combines each outcome of this distribution with each one of another, independent one. */
    private Distribution combine(Distribution other, BinaryOperator<Outcome> function) {
        var combined = new HashMap<Outcome, BigInteger>();
        for (var left : cases.entrySet()) {
            for (var right : other.cases.entrySet()) {
                combined.merge(
                        function.apply(left.getKey(), right.getKey()),
                        left.getValue().multiply(right.getValue()),
                        BigInteger::add);
            }
        }
        return new Distribution(combined, total.multiply(other.total));
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        if (right.isEmpty() || left.equals(right)) {
            return left;
        }
        if (left.isEmpty()) {
            return right;
        }
        var union = new HashSet<>(left);
        union.addAll(right);
        return Set.copyOf(union);
    }
}
