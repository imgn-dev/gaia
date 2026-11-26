package be.imgn.gaia.core.random.dice;

import be.imgn.gaia.core.random.dice.DiceNode.Explode;
import be.imgn.gaia.core.random.dice.DiceNode.Relation;
import be.imgn.gaia.core.random.dice.DiceNode.Reroll;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
            case DiceNode.Dice<RolledExpression> dice -> dice(dice, random);
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

    private static RolledNode<RolledExpression> dice(DiceNode.Dice<RolledExpression> node, RandomGenerator random) {
        int count = node.count().value();
        int sides = node.sides().value();
        checkDice(count, sides);
        checkRules(node.reroll(), node.explode(), sides);
        var faces = new ArrayList<Integer>();
        var rerolled = new ArrayList<Boolean>();
        var exploded = new ArrayList<Boolean>();
        for (int i = 0; i < count; i++) {
            // One die, then the dice it adds one after the other.
            boolean explodes = true;
            for (int added = 0; explodes; added++) {
                int face = random.nextInt(sides) + 1;
                // Rolled again once, or for as long as its face asks for it.
                for (boolean again = node.reroll().matches(face); again; ) {
                    faces.add(face);
                    rerolled.add(true);
                    exploded.add(false);
                    face = random.nextInt(sides) + 1;
                    again = node.reroll().times() == Reroll.Times.ALWAYS
                            && node.reroll().matches(face);
                }
                explodes = added < Explode.MAX_EXPLOSIONS && node.explode().matches(face, sides);
                faces.add(face);
                rerolled.add(false);
                exploded.add(explodes);
            }
        }
        // Among equal faces, the dice rolled first are the ones that are kept.
        var live = IntStream.range(0, faces.size())
                .filter(i -> !rerolled.get(i))
                .boxed()
                .toList();
        Comparator<Integer> byFace = Comparator.comparingInt(faces::get);
        var kept = live.stream()
                .sorted(node.keep().highest() ? byFace.reversed() : byFace)
                .limit(node.keep().kept(live.size()))
                .collect(Collectors.toSet());
        var dice = new ArrayList<RolledNode.Die>(faces.size());
        int sum = 0;
        for (int i = 0; i < faces.size(); i++) {
            dice.add(new RolledNode.Die(faces.get(i), kept.contains(i), rerolled.get(i), exploded.get(i)));
            if (kept.contains(i)) {
                sum = Math.addExact(sum, faces.get(i));
            }
        }
        return new RolledNode.Dice<>(node.count(), node.sides(), node.reroll(), node.explode(), node.keep(), dice, sum);
    }

    /** Checks that some face ends the rerolls, and that some face doesn't explode. */
    static void checkRules(Reroll reroll, Explode explode, int sides) {
        if (reroll.times() == Reroll.Times.ALWAYS && matching(reroll.relation(), reroll.value(), sides) == sides) {
            throw new ArithmeticException("Every face of a d" + sides + " is rerolled: " + reroll);
        }
        boolean always = switch (explode.faces()) {
            case NONE -> false;
            case HIGHEST -> sides == 1;
            case MATCHING -> matching(explode.relation(), explode.value(), sides) == sides;
        };
        if (always) {
            throw new ArithmeticException("Every face of a d" + sides + " explodes: " + explode);
        }
    }

    /** How many of the faces from one to a number of sides satisfy a condition. */
    static int matching(Relation relation, int value, int sides) {
        long below = Math.clamp((long) value - 1, 0, sides);
        long upTo = Math.clamp((long) value, 0, sides);
        return (int)
                switch (relation) {
                    case EQUAL -> upTo - below;
                    case NOT_EQUAL -> sides - (upTo - below);
                    case LESS -> below;
                    case LESS_OR_EQUAL -> upTo;
                    case GREATER -> sides - upTo;
                    case GREATER_OR_EQUAL -> sides - below;
                };
    }

    private static RolledNode<RolledExpression> group(DiceNode.Group<RolledExpression> group) {
        int value = group.inner().value();
        var tags = new ArrayList<RolledNode.Tag<RolledExpression>>();
        for (var tag : group.tags()) {
            boolean applied = tag.conditions().stream()
                    .allMatch(condition ->
                            condition.relation().test(value, condition.operand().value()));
            tags.add(new RolledNode.Tag<>(tag.name(), tag.conditions(), applied));
        }
        return new RolledNode.Group<>(group.inner(), tags, value);
    }
}
