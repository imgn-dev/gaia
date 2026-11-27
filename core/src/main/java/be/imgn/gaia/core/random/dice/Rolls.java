package be.imgn.gaia.core.random.dice;

import be.imgn.gaia.core.random.dice.DiceNode.Explode;
import be.imgn.gaia.core.random.dice.DiceNode.Relation;
import be.imgn.gaia.core.random.dice.DiceNode.Reroll;
import be.imgn.gaia.core.random.dice.DiceNode.Sides;
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
            case DiceNode.Extremum<RolledExpression> extremum ->
                new RolledNode.Extremum<>(
                        extremum.extreme(),
                        extremum.operands(),
                        extremum.operands().stream()
                                .mapToInt(RolledExpression::value)
                                .reduce(extremum.extreme()::apply)
                                .orElseThrow());
            // A repeat rolls its operand several times: it's rolled from the expression, not from one rolled child.
            case DiceNode.Repeat<RolledExpression> repeat -> throw new AssertionError(repeat);
        };
        return new RolledExpression(rolled);
    }

    /** Rolls an expression, from its leaves, each operand of a repeat as many times as it's repeated. */
    static RolledExpression roll(DiceExpression expression, RandomGenerator random) {
        if (!(expression.node() instanceof DiceNode.Repeat<DiceExpression>(var count, var operand))) {
            return roll(expression.node().<RolledExpression>map(child -> roll(child, random)), random);
        }
        var times = roll(count, random);
        checkTimes(times.value());
        var rolls = new ArrayList<RolledExpression>();
        int sum = 0;
        for (int i = 0; i < times.value(); i++) {
            rolls.add(roll(operand, random));
            sum = Math.addExact(sum, rolls.getLast().value());
        }
        return new RolledExpression(new RolledNode.Repeat<>(times, rolls, sum));
    }

    /** Checks a number of times that a value is repeated. */
    static void checkTimes(int times) {
        if (times < 0) {
            throw new ArithmeticException("Negative number of times: " + times);
        }
        if (times > DiceNode.Repeat.MAX_TIMES) {
            throw new ArithmeticException("Too many times: " + times);
        }
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
        var die = faces(node.sides());
        checkDice(count, die.size());
        checkRules(node.reroll(), node.explode(), die);
        var faces = new ArrayList<Integer>();
        var rerolled = new ArrayList<Boolean>();
        var exploded = new ArrayList<Boolean>();
        for (int i = 0; i < count; i++) {
            // One die, then the dice it adds one after the other.
            boolean explodes = true;
            for (int added = 0; explodes; added++) {
                int face = die.face(random.nextInt(die.size()));
                // Rolled again once, or for as long as its face asks for it.
                for (boolean again = node.reroll().matches(face, die.lowest()); again; ) {
                    faces.add(face);
                    rerolled.add(true);
                    exploded.add(false);
                    face = die.face(random.nextInt(die.size()));
                    again = node.reroll().times() == Reroll.Times.ALWAYS
                            && node.reroll().matches(face, die.lowest());
                }
                explodes = added < Explode.MAX_EXPLOSIONS && node.explode().matches(face, die.highest());
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
                sum = Math.addExact(sum, node.tally().amount(faces.get(i)));
            }
        }
        return new RolledNode.Dice<>(
                node.count(), node.sides(), node.reroll(), node.explode(), node.keep(), node.tally(), dice, sum);
    }

    /** The faces of a die whose number of sides was rolled, if it has one. */
    private static DieFaces faces(Sides<RolledExpression> sides) {
        return switch (sides) {
            case Sides.Standard<RolledExpression> standard -> {
                checkDice(0, standard.number().value());
                yield DieFaces.numbered(standard.number().value());
            }
            case Sides.Custom<RolledExpression> custom -> DieFaces.listed(custom.faces());
        };
    }

    /** Checks that some face ends the rerolls, and that some face doesn't explode. */
    static void checkRules(Reroll reroll, Explode explode, DieFaces die) {
        if (reroll.times() == Reroll.Times.ALWAYS && rerolled(reroll, die) == die.size()) {
            throw new ArithmeticException("Every face is rerolled: " + reroll);
        }
        boolean always = switch (explode.faces()) {
            case NONE -> false;
            case HIGHEST -> die.lowest() == die.highest();
            case MATCHING -> die.matching(explode.relation(), explode.value()) == die.size();
        };
        if (always) {
            throw new ArithmeticException("Every face explodes: " + explode);
        }
    }

    /** How many faces of a die a rule rerolls. */
    static int rerolled(Reroll reroll, DieFaces die) {
        return switch (reroll.times()) {
            case NEVER -> 0;
            case ONCE, ALWAYS ->
                reroll.target() == Reroll.Target.LOWEST
                        ? die.matching(Relation.EQUAL, die.lowest())
                        : die.matching(reroll.relation(), reroll.value());
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
