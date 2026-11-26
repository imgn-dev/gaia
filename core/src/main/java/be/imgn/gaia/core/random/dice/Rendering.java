package be.imgn.gaia.core.random.dice;

import be.imgn.gaia.core.random.dice.DiceNode.Condition;
import be.imgn.gaia.core.random.dice.DiceNode.Operator;
import java.util.StringJoiner;
import org.jspecify.annotations.Nullable;

/** Writes expressions and rolled expressions back as text, with the parentheses that the precedences require. */
final class Rendering {

    private static final int COMPARISON = 0;
    private static final int SUM = 1;
    private static final int PRODUCT = 2;
    private static final int UNARY = 3;
    private static final int DICE = 4;
    private static final int PRIMARY = 5;

    private Rendering() {}

    static Rendered render(DiceNode<Rendered> node) {
        return switch (node) {
            case DiceNode.Constant<Rendered> constant -> constant(constant.value());
            case DiceNode.Variable<Rendered> variable -> new Rendered(variable.name(), PRIMARY);
            case DiceNode.Bound<Rendered> bound -> new Rendered(bound.name(), PRIMARY);
            case DiceNode.Dice<Rendered> dice -> new Rendered(dice(dice.count(), dice.sides()) + dice.keep(), DICE);
            case DiceNode.Negate<Rendered> negate -> negate(negate.operand());
            case DiceNode.Arithmetic<Rendered> arithmetic ->
                arithmetic(arithmetic.operator(), arithmetic.left(), arithmetic.right());
            case DiceNode.Comparison<Rendered> comparison ->
                new Rendered(
                        comparison.left().at(SUM)
                                + comparison.relation().symbol()
                                + comparison.right().at(SUM),
                        COMPARISON);
            case DiceNode.Group<Rendered> group -> {
                var text = new StringBuilder("(").append(group.inner().text());
                for (var tag : group.tags()) {
                    text.append(':').append(tag.name()).append(condition(tag.condition()));
                }
                yield new Rendered(text.append(')').toString(), PRIMARY);
            }
        };
    }

    static Rendered render(RolledNode<Rendered> node) {
        return switch (node) {
            case RolledNode.Constant<Rendered> constant -> constant(constant.value());
            case RolledNode.Bound<Rendered> bound -> new Rendered(bound.name() + "[" + bound.value() + "]", PRIMARY);
            case RolledNode.Dice<Rendered> dice -> {
                var faces = new StringJoiner(", ", "[", "]");
                for (var die : dice.dice()) {
                    faces.add(die.kept() ? Integer.toString(die.face()) : "~" + die.face() + "~");
                }
                yield new Rendered(dice(dice.count(), dice.sides()) + dice.keep() + faces, DICE);
            }
            case RolledNode.Negate<Rendered> negate -> negate(negate.operand());
            case RolledNode.Arithmetic<Rendered> arithmetic ->
                arithmetic(arithmetic.operator(), arithmetic.left(), arithmetic.right());
            case RolledNode.Comparison<Rendered> comparison ->
                new Rendered(
                        comparison.left().at(SUM)
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
        };
    }

    private static Rendered constant(int value) {
        return new Rendered(Integer.toString(value), value < 0 ? UNARY : PRIMARY);
    }

    private static String dice(Rendered count, Rendered sides) {
        return count.at(PRIMARY) + "d" + sides.at(PRIMARY);
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

    private static String condition(@Nullable Condition<Rendered> condition) {
        return condition == null
                ? ""
                : condition.relation().symbol() + condition.operand().at(UNARY);
    }

    /** A text and the precedence of its outermost operation. */
    record Rendered(String text, int level) {

        String at(int minimum) {
            return level >= minimum ? text : "(" + text + ")";
        }
    }
}
