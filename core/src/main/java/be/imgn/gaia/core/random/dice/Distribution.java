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
import java.util.function.IntBinaryOperator;
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

    private static Distribution constant(int value) {
        return new Distribution(Map.of(new Outcome(value, Set.of()), BigInteger.ONE), BigInteger.ONE);
    }

    /** Computes the distribution of one node from those of its children. */
    static Distribution of(DiceNode<Distribution> node, Budget budget) {
        return switch (node) {
            case DiceNode.Constant<Distribution> constant -> constant(constant.value());
            case DiceNode.Variable<Distribution> variable -> throw new AssertionError(variable.name());
            case DiceNode.Bound<Distribution> bound -> bound.value();
            case DiceNode.Dice<Distribution> dice -> dice(dice, budget);
            case DiceNode.Negate<Distribution> negate ->
                negate.operand().map(outcome -> new Outcome(Math.negateExact(outcome.value()), outcome.tags()), budget);
            case DiceNode.Arithmetic<Distribution> arithmetic ->
                arithmetic.left().combine(arithmetic.right(), arithmetic.operator()::apply, budget);
            case DiceNode.Comparison<Distribution> comparison ->
                comparison
                        .left()
                        .combine(
                                comparison.right(),
                                (left, right) -> comparison.relation().test(left, right) ? 1 : 0,
                                budget);
            case DiceNode.Group<Distribution> group -> group(group, budget);
            case DiceNode.Extremum<Distribution> extremum -> extremum(extremum, budget);
            case DiceNode.Repeat<Distribution> repeat -> repeat(repeat.count(), repeat.operand(), budget);
        };
    }

    private static Distribution extremum(DiceNode.Extremum<Distribution> extremum, Budget budget) {
        var result = extremum.operands().getFirst();
        for (var operand : extremum.operands().subList(1, extremum.operands().size())) {
            result = result.combine(operand, extremum.extreme()::apply, budget);
        }
        return result;
    }

    /** The sum of a number of independent copies of an operand, for each number that the count may be. */
    private static Distribution repeat(Distribution count, Distribution operand, Budget budget) {
        int most = 0;
        for (var times : count.cases.keySet()) {
            Rolls.checkTimes(times.value());
            most = Math.max(most, times.value());
        }
        // The sums of no copy, of one copy, and so on.
        var sums = new ArrayList<Distribution>();
        sums.add(constant(0));
        for (int copies = 1; copies <= most; copies++) {
            sums.add(sums.getLast().combine(operand, Math::addExact, budget));
        }
        var cases = new HashMap<Outcome, BigInteger>();
        for (var times : count.cases.entrySet()) {
            var sum = sums.get(times.getKey().value());
            // Brings every number of copies to the same number of cases.
            var weight = times.getValue()
                    .multiply(operand.total.pow(most - times.getKey().value()));
            budget.spend(sum.cases.size());
            sum.cases.forEach((outcome, ways) -> cases.merge(
                    new Outcome(
                            outcome.value(),
                            union(outcome.tags(), times.getKey().tags())),
                    ways.multiply(weight),
                    BigInteger::add));
        }
        return new Distribution(cases, count.total.multiply(operand.total.pow(most)));
    }

    private static Distribution group(DiceNode.Group<Distribution> group, Budget budget) {
        var result = group.inner();
        for (var tag : group.tags()) {
            // The value stays that of the group, so that the next conditions and tags test it too. An outcome that
            // fails a condition is marked until every condition of the tag is tested.
            for (var condition : tag.conditions()) {
                result = result.combineOutcomes(
                        condition.operand(),
                        (outcome, operand) -> {
                            var tags = union(outcome.tags(), operand.tags());
                            boolean holds = condition.relation().test(outcome.value(), operand.value());
                            return new Outcome(outcome.value(), holds ? tags : union(tags, FAILED));
                        },
                        budget);
            }
            var name = Set.of(tag.name());
            result = result.map(
                    outcome -> {
                        if (!outcome.tags().contains(FAILED_MARK)) {
                            return new Outcome(outcome.value(), union(outcome.tags(), name));
                        }
                        var tags = new HashSet<>(outcome.tags());
                        tags.remove(FAILED_MARK);
                        return new Outcome(outcome.value(), tags);
                    },
                    budget);
        }
        return result;
    }

    private static Distribution dice(DiceNode.Dice<Distribution> node, Budget budget) {
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
                var pool = Pools.pool(node, count.getKey().value(), die.faces, budget);
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
            budget.spend(part.pool.ways().size());
            part.pool
                    .ways()
                    .forEach((sum, ways) ->
                            cases.merge(new Outcome(sum, part.tags), ways.multiply(weight), BigInteger::add));
        }
        return new Distribution(cases, node.count().total.multiply(sidesTotal).multiply(scale));
    }

    private Distribution map(UnaryOperator<Outcome> function, Budget budget) {
        budget.spend(cases.size());
        var mapped = new HashMap<Outcome, BigInteger>();
        cases.forEach((outcome, count) -> mapped.merge(function.apply(outcome), count, BigInteger::add));
        return new Distribution(mapped, total);
    }

    /** Combines the values of this distribution with those of another, independent one: the tags add up. */
    private Distribution combine(Distribution other, IntBinaryOperator function, Budget budget) {
        return combineOutcomes(
                other,
                (left, right) ->
                        new Outcome(function.applyAsInt(left.value(), right.value()), union(left.tags(), right.tags())),
                budget);
    }

    /** Combines each outcome of this distribution with each one of another, independent one. */
    private Distribution combineOutcomes(Distribution other, BinaryOperator<Outcome> function, Budget budget) {
        budget.spend((long) cases.size() * other.cases.size());
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
