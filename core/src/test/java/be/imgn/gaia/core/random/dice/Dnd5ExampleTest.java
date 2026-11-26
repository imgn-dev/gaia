package be.imgn.gaia.core.random.dice;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.core.math.IntFraction;
import be.imgn.gaia.core.random.dice.DiceNode.Keep;
import be.imgn.gaia.core.random.dice.DiceNode.Operator;
import be.imgn.gaia.core.random.dice.DiceNode.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The example of the package documentation, as it's written there. */
class Dnd5ExampleTest {

    static DiceExpression advantage(DiceExpression expression) {
        return mark(expression, "advantage");
    }

    static DiceExpression disadvantage(DiceExpression expression) {
        return mark(expression, "disadvantage");
    }

    private static DiceExpression mark(DiceExpression expression, String mark) {
        return switch (expression.node()) {
            case DiceNode.Group<DiceExpression>(var inner, var tags)
            when isD20(inner) && isMarked(tags) -> d20(tags, mark);
            case DiceNode.Dice<DiceExpression> dice when isD20(expression) -> d20(List.of(), mark);
            case DiceNode<DiceExpression> node -> DiceExpression.of(node.map(child -> mark(child, mark)));
        };
    }

    private static DiceExpression d20(List<Tag<DiceExpression>> tags, String mark) {
        var marks = new ArrayList<>(tags);
        if (tags.stream().noneMatch(tag -> tag.name().equals(mark))) {
            marks.add(new Tag<>(mark, List.of()));
        }
        boolean advantage = marks.stream().anyMatch(tag -> tag.name().equals("advantage"));
        boolean disadvantage = marks.stream().anyMatch(tag -> tag.name().equals("disadvantage"));
        var dice = DiceExpression.parse(advantage == disadvantage ? "1d20" : advantage ? "2d20kh1" : "2d20kl1");
        return DiceExpression.of(new DiceNode.Group<>(dice, marks));
    }

    private static boolean isMarked(List<Tag<DiceExpression>> tags) {
        return tags.stream().anyMatch(tag -> Set.of("advantage", "disadvantage").contains(tag.name()));
    }

    private static boolean isD20(DiceExpression expression) {
        return Set.of("1d20", "2d20kh1", "2d20kl1").contains(expression.toString());
    }

    static DiceExpression critical(DiceExpression damage) {
        return switch (damage.node()) {
            case DiceNode.Dice<DiceExpression> dice
            when dice.keep().equals(Keep.ALL) ->
                DiceExpression.of(new DiceNode.Dice<>(
                        twice(dice.count()), dice.sides(), dice.reroll(), dice.explode(), dice.keep()));
            case DiceNode.Dice<DiceExpression> dice ->
                DiceExpression.of(new DiceNode.Arithmetic<>(Operator.ADD, damage, damage));
            case DiceNode<DiceExpression> node -> DiceExpression.of(node.map(Dnd5ExampleTest::critical));
        };
    }

    private static DiceExpression twice(DiceExpression count) {
        var two = DiceExpression.parse("2");
        return count.node() instanceof DiceNode.Constant<DiceExpression>(var value)
                ? DiceExpression.of(new DiceNode.Constant<>(2 * value))
                : DiceExpression.of(new DiceNode.Arithmetic<>(Operator.MULTIPLY, count, two));
    }

    @Test
    void advantageAndDisadvantageCancelOut() {
        var attack = DiceExpression.parse("(1d20:critical>=20)+5>=15");

        assertThat(advantage(attack)).hasToString("((2d20kh1:advantage):critical>=20)+5>=15");
        assertThat(advantage(advantage(attack))).isEqualTo(advantage(attack));
        assertThat(disadvantage(attack)).hasToString("((2d20kl1:disadvantage):critical>=20)+5>=15");
        assertThat(advantage(disadvantage(attack))).hasToString("((1d20:disadvantage:advantage):critical>=20)+5>=15");
        assertThat(advantage(advantage(disadvantage(attack)))).isEqualTo(advantage(disadvantage(attack)));
        assertThat(disadvantage(advantage(advantage(attack))))
                .hasToString("((1d20:advantage:disadvantage):critical>=20)+5>=15");
    }

    @Test
    void advantageChangesTheChances() {
        var attack = DiceExpression.parse("(1d20:critical>=20)+5>=15");

        assertThat(attack.chances().of(1)).isEqualTo(IntFraction.of(11, 20));
        assertThat(advantage(attack).chances().of(1)).isEqualTo(IntFraction.of(319, 400));
        assertThat(disadvantage(attack).chances().of(1)).isEqualTo(IntFraction.of(121, 400));
        assertThat(advantage(disadvantage(attack)).chances().of(1)).isEqualTo(IntFraction.of(11, 20));
        assertThat(advantage(attack).chances().tagged("critical")).isEqualTo(IntFraction.of(39, 400));
    }

    @Test
    void aCriticalHitDoublesTheDiceOnly() {
        assertThat(critical(DiceExpression.parse("2d6+STR"))).hasToString("4d6+STR");
        assertThat(critical(DiceExpression.parse("1d8+SNEAKd6+3"))).hasToString("2d8+(SNEAK*2)d6+3");
        assertThat(critical(DiceExpression.parse("4d6dl1*2"))).hasToString("(4d6dl1+4d6dl1)*2");
        assertThat(critical(DiceExpression.parse("(1d4+1)d6"))).hasToString("((1d4+1)*2)d6");
        assertThat(critical(DiceExpression.parse("2d6r<=2+3"))).hasToString("4d6r<=2+3");
    }

    @Test
    void aCriticalHitDoublesTheDiceOfVariables() {
        var damage = DiceExpression.parse("2d6+STR").bind("STR", 3);
        var bonus = DiceExpression.parse("1d8+BONUS").bind("BONUS", DiceExpression.parse("1d4"));

        assertThat(damage.chances().mean()).isEqualTo(IntFraction.of(10));
        assertThat(critical(damage).chances().mean()).isEqualTo(IntFraction.of(17));
        assertThat(critical(bonus)).hasToString("2d8+BONUS");
        assertThat(critical(bonus).chances().max()).isEqualTo(24);
    }
}
