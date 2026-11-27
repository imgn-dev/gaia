package be.imgn.gaia.core.random.dice;

import be.imgn.gaia.core.random.dice.DiceNode.Explode;
import be.imgn.gaia.core.random.dice.DiceNode.Extreme;
import be.imgn.gaia.core.random.dice.DiceNode.Keep;
import be.imgn.gaia.core.random.dice.DiceNode.Operator;
import be.imgn.gaia.core.random.dice.DiceNode.Sides;
import be.imgn.gaia.core.random.dice.DiceNode.Tally;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/** Writes expressions and rolled expressions back as text, with the parentheses that the precedences require. */
final class Rendering {

    private static final int COMPARISON = 0;
    private static final int SUM = 1;
    private static final int PRODUCT = 2;
    private static final int UNARY = 3;
    private static final int DICE = 4;

    // Dice after which an x can't be their explosion: they have one already, or a rule that comes after it.
    private static final int CLOSED_DICE = 5;
    private static final int PRIMARY = 6;

    private Rendering() {}

    static Rendered render(DiceNode<Rendered> node) {
        return switch (node) {
            case DiceNode.Constant<Rendered> constant -> constant(constant.value());
            case DiceNode.Variable<Rendered> variable -> new Rendered(variable.name(), PRIMARY);
            case DiceNode.Bound<Rendered> bound -> new Rendered(bound.name(), PRIMARY);
            case DiceNode.Dice<Rendered> dice -> rules(dice);
            case DiceNode.Negate<Rendered> negate -> negate(negate.operand());
            case DiceNode.Arithmetic<Rendered> arithmetic ->
                arithmetic(arithmetic.operator(), arithmetic.left(), arithmetic.right());
            case DiceNode.Comparison<Rendered> comparison ->
                new Rendered(
                        beforeRelation(comparison.left())
                                + comparison.relation().symbol()
                                + comparison.right().at(SUM),
                        COMPARISON);
            case DiceNode.Group<Rendered> group -> group(group);
            case DiceNode.Extremum<Rendered> extremum -> extremum(extremum.extreme(), extremum.operands());
            case DiceNode.Repeat<Rendered> repeat ->
                new Rendered(
                        repeat.count().at(CLOSED_DICE) + "x" + repeat.operand().at(UNARY), UNARY);
        };
    }

    static Rendered render(RolledNode<Rendered> node) {
        return switch (node) {
            case RolledNode.Constant<Rendered> constant -> constant(constant.value());
            case RolledNode.Bound<Rendered> bound -> new Rendered(bound.name() + "[" + bound.value() + "]", PRIMARY);
            case RolledNode.Dice<Rendered> dice ->
                new Rendered(
                        dice(dice.count(), dice.sides())
                                + dice.reroll()
                                + dice.explode()
                                + dice.keep()
                                + dice.tally()
                                + faces(dice.dice()),
                        DICE);
            case RolledNode.Negate<Rendered> negate -> negate(negate.operand());
            case RolledNode.Arithmetic<Rendered> arithmetic ->
                arithmetic(arithmetic.operator(), arithmetic.left(), arithmetic.right());
            case RolledNode.Comparison<Rendered> comparison ->
                new Rendered(
                        beforeRelation(comparison.left())
                                + comparison.relation().symbol()
                                + comparison.right().at(SUM),
                        COMPARISON);
            case RolledNode.Group<Rendered> group -> {
                var text = new StringBuilder("(").append(group.inner().text());
                for (var tag : group.appliedTags()) {
                    text.append(':').append(tag);
                }
                yield new Rendered(text.append(')').toString(), PRIMARY);
            }
            case RolledNode.Extremum<Rendered> extremum -> extremum(extremum.extreme(), extremum.operands());
            case RolledNode.Repeat<Rendered> repeat ->
                new Rendered(
                        repeat.count().at(PRIMARY)
                                + "x"
                                + repeat.rolls().stream()
                                        .map(Rendered::text)
                                        .collect(Collectors.joining("; ", "(", ")")),
                        UNARY);
        };
    }

    private static Rendered rules(DiceNode.Dice<Rendered> dice) {
        boolean open = dice.explode().equals(Explode.NONE)
                && dice.keep().equals(Keep.ALL)
                && dice.tally().equals(Tally.SUM);
        return new Rendered(
                dice(dice.count(), dice.sides()) + dice.reroll() + dice.explode() + dice.keep() + dice.tally(),
                open ? DICE : CLOSED_DICE);
    }

    private static Rendered group(DiceNode.Group<Rendered> group) {
        var text = new StringBuilder("(").append(group.inner().text());
        for (var tag : group.tags()) {
            text.append(':').append(tag.name());
            var conditions = tag.conditions();
            for (int i = 0; i < conditions.size(); i++) {
                var operand = conditions.get(i).operand();
                // Only the operator of a next condition could be read as part of this operand.
                text.append(conditions.get(i).relation().symbol())
                        .append(i + 1 < conditions.size() ? beforeRelation(operand) : operand.at(SUM));
            }
        }
        return new Rendered(text.append(')').toString(), PRIMARY);
    }

    private static Rendered extremum(Extreme extreme, List<Rendered> operands) {
        return new Rendered(
                operands.stream().map(Rendered::text).collect(Collectors.joining(",", extreme.symbol() + "(", ")")),
                PRIMARY);
    }

    /** The dice in brackets: a rerolled one with an r, an exploded one with an x, a dropped one between tildes. */
    private static String faces(List<RolledNode.Die> dice) {
        var faces = new StringJoiner(", ", "[", "]");
        for (var die : dice) {
            var face = die.face() + (die.rerolled() ? "r" : die.exploded() ? "x" : "");
            faces.add(die.kept() || die.rerolled() ? face : "~" + face + "~");
        }
        return faces.toString();
    }

    /**
     * The text of a sum that a comparison operator may follow: dice that end with a bare {@code x} or {@code r} get
     * parentheses, or the operator would read as the condition of their explosions.
     */
    private static String beforeRelation(Rendered sum) {
        var text = sum.at(SUM);
        return text.endsWith("x") || text.endsWith("r") ? "(" + text + ")" : text;
    }

    private static Rendered constant(int value) {
        return new Rendered(Integer.toString(value), value < 0 ? UNARY : PRIMARY);
    }

    private static String dice(Rendered count, Sides<Rendered> sides) {
        return count.at(PRIMARY)
                + "d"
                + switch (sides) {
                    case Sides.Standard<Rendered> standard -> standard.number().at(PRIMARY);
                    case Sides.Custom<Rendered> custom -> custom.toString();
                };
    }

    private static Rendered negate(Rendered operand) {
        return new Rendered("-" + operand.at(UNARY), UNARY);
    }

    private static Rendered arithmetic(Operator operator, Rendered left, Rendered right) {
        return switch (operator) {
            case ADD, SUBTRACT -> new Rendered(left.at(SUM) + operator.symbol() + right.at(PRODUCT), SUM);
            case MULTIPLY, DIVIDE -> new Rendered(left.at(PRODUCT) + operator.symbol() + right.at(UNARY), PRODUCT);
        };
    }

    /** A text and the precedence of its outermost operation. */
    record Rendered(String text, int level) {

        String at(int minimum) {
            return level >= minimum ? text : "(" + text + ")";
        }
    }
}
