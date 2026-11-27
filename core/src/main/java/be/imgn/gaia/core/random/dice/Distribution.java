package be.imgn.gaia.core.random.dice;

import be.imgn.gaia.core.random.dice.DiceChances.Outcome;
import be.imgn.gaia.core.random.dice.DiceNode.Sides;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

/**
 * The outcomes of an expression, each with the number of equally likely cases that lead to it, among {@code total}
 * cases. Counts are kept whole, and only turned to fractions when a chance is asked for.
 */
final class Distribution {

    // Not a tag name, so that it never hides a tag.
    private static final String FAILED_MARK = "!";
    private static final Set<String> FAILED = Set.of(FAILED_MARK);

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
            case DiceNode.Dice<Distribution> dice -> dice(dice);
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
            // The value stays that of the group, so that the next conditions and tags test it too. An outcome that
            // fails a condition is marked until every condition of the tag is tested.
            for (var condition : tag.conditions()) {
                result = result.combine(condition.operand(), (outcome, operand) -> {
                    var tags = union(outcome.tags(), operand.tags());
                    return new Outcome(
                            outcome.value(),
                            condition.relation().test(outcome.value(), operand.value()) ? tags : union(tags, FAILED));
                });
            }
            var name = Set.of(tag.name());
            result = result.map(outcome -> {
                if (!outcome.tags().contains(FAILED_MARK)) {
                    return new Outcome(outcome.value(), union(outcome.tags(), name));
                }
                var tags = new HashSet<>(outcome.tags());
                tags.remove(FAILED_MARK);
                return new Outcome(outcome.value(), tags);
            });
        }
        return result;
    }

    private static Distribution dice(DiceNode.Dice<Distribution> node) {
        // The faces of the die, for each number of sides that it may have, with the cases that lead to it.
        record Die(DieFaces faces, Set<String> tags, BigInteger cases) {}
        var dice = new ArrayList<Die>();
        var sidesTotal = BigInteger.ONE;
        switch (node.sides()) {
            case Sides.Standard<Distribution> standard -> {
                sidesTotal = standard.number().total;
                standard.number().cases.forEach((sides, cases) -> {
                    Rolls.checkDice(0, sides.value());
                    dice.add(new Die(DieFaces.numbered(sides.value()), sides.tags(), cases));
                });
            }
            case Sides.Custom<Distribution> custom ->
                dice.add(new Die(DieFaces.listed(custom.faces()), Set.of(), BigInteger.ONE));
        }
        // One pool for each number of dice and each die, weighted by the cases that lead to them.
        record Part(BigInteger weight, Set<String> tags, Pools.Pool pool) {}
        var parts = new ArrayList<Part>();
        var scale = BigInteger.ONE;
        for (var count : node.count().cases.entrySet()) {
            for (var die : dice) {
                var pool = Pools.pool(count.getKey().value(), die.faces, node.reroll(), node.explode(), node.keep());
                parts.add(new Part(
                        count.getValue().multiply(die.cases),
                        union(count.getKey().tags(), die.tags),
                        pool));
                scale = scale.multiply(pool.total()).divide(scale.gcd(pool.total()));
            }
        }
        var cases = new HashMap<Outcome, BigInteger>();
        for (var part : parts) {
            // Brings every pool to the same number of cases.
            var weight = part.weight.multiply(scale.divide(part.pool.total()));
            part.pool
                    .ways()
                    .forEach((sum, ways) ->
                            cases.merge(new Outcome(sum, part.tags), ways.multiply(weight), BigInteger::add));
        }
        return new Distribution(cases, node.count().total.multiply(sidesTotal).multiply(scale));
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
