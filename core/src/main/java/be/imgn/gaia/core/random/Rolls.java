package be.imgn.gaia.core.random;

import be.imgn.gaia.core.random.DiceNode.Keep;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/** Evaluates one node whose children are already rolled. */
final class Rolls {

    /** The most dice that one node rolls. */
    static final int MAX_DICE = 10_000;

    private Rolls() {}

    static RolledExpression roll(DiceNode<RolledExpression> node, RandomGenerator random) {
        RolledNode<RolledExpression> rolled = switch (node) {
            case DiceNode.Constant<RolledExpression> constant -> new RolledNode.Constant<>(constant.value());
            case DiceNode.Variable<RolledExpression> variable -> throw new AssertionError(variable.name());
            case DiceNode.Bound<RolledExpression> bound ->
                new RolledNode.Bound<>(
                        bound.name(), bound.value(), bound.value().value());
            case DiceNode.Dice<RolledExpression> dice -> dice(dice.count(), dice.sides(), dice.keep(), random);
            case DiceNode.Negate<RolledExpression> negate ->
                new RolledNode.Negate<>(
                        negate.operand(), Math.negateExact(negate.operand().value()));
            case DiceNode.Arithmetic<RolledExpression> arithmetic ->
                new RolledNode.Arithmetic<>(
                        arithmetic.operator(),
                        arithmetic.left(),
                        arithmetic.right(),
                        arithmetic
                                .operator()
                                .apply(
                                        arithmetic.left().value(),
                                        arithmetic.right().value()));
            case DiceNode.Comparison<RolledExpression> comparison ->
                new RolledNode.Comparison<>(
                        comparison.relation(),
                        comparison.left(),
                        comparison.right(),
                        comparison
                                        .relation()
                                        .test(
                                                comparison.left().value(),
                                                comparison.right().value())
                                ? 1
                                : 0);
            case DiceNode.Group<RolledExpression> group -> group(group);
        };
        return new RolledExpression(rolled);
    }

    /** Checks a number of dice and a number of sides. */
    static void checkDice(int count, int sides) {
        if (count < 0) {
            throw new ArithmeticException("Negative number of dice: " + count);
        }
        if (count > MAX_DICE) {
            throw new ArithmeticException("Too many dice: " + count);
        }
        if (sides < 1) {
            throw new ArithmeticException("Not a number of sides: " + sides);
        }
    }

    private static RolledNode<RolledExpression> dice(
            RolledExpression count, RolledExpression sides, @Nullable Keep keep, RandomGenerator random) {
        int rolled = count.value();
        int faceCount = sides.value();
        checkDice(rolled, faceCount);
        int[] faces = new int[rolled];
        for (int i = 0; i < rolled; i++) {
            faces[i] = random.nextInt(faceCount) + 1;
        }
        boolean[] kept = new boolean[rolled];
        if (keep == null) {
            Arrays.fill(kept, true);
        } else {
            // Among equal faces, the dice rolled first are the ones that are kept.
            Comparator<Integer> byFace = Comparator.comparingInt(i -> faces[i]);
            IntStream.range(0, rolled)
                    .boxed()
                    .sorted(keep.highest() ? byFace.reversed() : byFace)
                    .limit(keep.kept(rolled))
                    .forEach(i -> kept[i] = true);
        }
        var dice = new ArrayList<RolledNode.Die>(rolled);
        int sum = 0;
        for (int i = 0; i < rolled; i++) {
            dice.add(new RolledNode.Die(faces[i], kept[i]));
            if (kept[i]) {
                sum = Math.addExact(sum, faces[i]);
            }
        }
        return new RolledNode.Dice<>(count, sides, keep, dice, sum);
    }

    private static RolledNode<RolledExpression> group(DiceNode.Group<RolledExpression> group) {
        int value = group.inner().value();
        var tags = new ArrayList<RolledNode.Tag<RolledExpression>>();
        for (var tag : group.tags()) {
            var condition = tag.condition();
            boolean applied = condition == null
                    || condition.relation().test(value, condition.operand().value());
            tags.add(new RolledNode.Tag<>(tag.name(), condition, applied));
        }
        return new RolledNode.Group<>(group.inner(), tags, value);
    }
}
