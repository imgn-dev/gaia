package be.imgn.gaia.core.random.dice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import be.imgn.gaia.core.random.dice.DiceNode.Keep;
import be.imgn.gaia.core.random.dice.DiceNode.Operator;
import be.imgn.gaia.core.random.dice.DiceNode.Relation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DiceExpressionTest {

    private static final String ATTACK = "(1d20:critical>=20:miss=1)+STR+BONUS>=AC";

    private static DiceExpression constant(int value) {
        return DiceExpression.of(new DiceNode.Constant<>(value));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "20",
                "STR",
                "_A1",
                "1d20",
                "4d6dl1",
                "2d20kh1",
                "2d20kl1",
                "3d6dh2",
                "LEVELd6",
                "(1+1)d(2*3)",
                "1d20+5",
                "1d8+2d6-1",
                "2*3+4",
                "2*(3+4)",
                "7/2",
                "-1d4",
                "--3",
                "2-(3-4)",
                "2-3-4",
                "8/(4/2)",
                "(1d20)",
                "(1d20:attack)",
                "(1d20:attack:critical>=20)",
                "(1d20:low<=-1)",
                "(1d20:hit>=AC)",
                "1d20>=15",
                "1d20=20",
                "1d20!=1",
                "1d20<10",
                "1d20<=10",
                "1d20>10",
                "(1d20>=15)*2",
                ATTACK,
            })
    void toStringRestoresTheText(String text) {
        assertThat(DiceExpression.parse(text)).hasToString(text);
    }

    @ParameterizedTest
    @CsvSource({
        "d20, 1d20",
        "d%, 1d100",
        "3d%, 3d100",
        "' 1d20 + 5 ', 1d20+5",
        "'( 1d20 : critical >= 20 ) >= AC', (1d20:critical>=20)>=AC",
        "2 * ( 3 + 4 ), 2*(3+4)",
        "- 3, -3",
    })
    void toStringNormalizesTheText(String text, String expected) {
        var expression = DiceExpression.parse(text);

        assertThat(expression).hasToString(expected);
        assertThat(DiceExpression.parse(expected)).isEqualTo(expression);
    }

    @Test
    void parseBuildsTheTree() {
        var expression = DiceExpression.parse("4d6dl1+STR");

        var dice = DiceExpression.of(new DiceNode.Dice<>(constant(4), constant(6), new Keep(Keep.Mode.DROP_LOWEST, 1)));
        var variable = DiceExpression.of(new DiceNode.Variable<DiceExpression>("STR"));
        assertThat(expression.node()).isEqualTo(new DiceNode.Arithmetic<>(Operator.ADD, dice, variable));
    }

    @Test
    void parseGivesTagsTheirConditions() {
        var expression = DiceExpression.parse("(1d20:attack:critical>=20)");

        var group = (DiceNode.Group<DiceExpression>) expression.node();
        assertThat(group.tags())
                .containsExactly(
                        new DiceNode.Tag<>("attack", null),
                        new DiceNode.Tag<>(
                                "critical", new DiceNode.Condition<>(Relation.GREATER_OR_EQUAL, constant(20))));
        assertThat(group.children()).containsExactly(DiceExpression.parse("1d20"), constant(20));
    }

    @Test
    void operatorsOfTheSamePrecedenceGroupFromTheLeft() {
        assertThat(DiceExpression.parse("10-3-2").roll(new FakeRandom()).value())
                .isEqualTo(5);
        assertThat(DiceExpression.parse("2+3*4").roll(new FakeRandom()).value()).isEqualTo(14);
        assertThat(DiceExpression.parse("-2*3").roll(new FakeRandom()).value()).isEqualTo(-6);
    }

    @ParameterizedTest
    @CsvSource({
        "'', 0",
        "1d, 2",
        "1+, 2",
        "(1d20, 5",
        "1d20), 4",
        "1d20kx1, 4",
        "1d20kh, 6",
        "2d6d8, 3",
        "str, 0",
        "1 2, 2",
        "1d20>=10>=5, 8",
        "(1d20:), 6",
        "(1d20:9a), 6",
        "1 d6, 2",
        "99999999999, 0",
        "1d20!5, 4",
    })
    void parseReportsWhereItFails(String text, int position) {
        assertThatExceptionOfType(DiceParseException.class)
                .isThrownBy(() -> DiceExpression.parse(text))
                .satisfies(e -> assertThat(e.position()).isEqualTo(position))
                .withMessageContaining("at index " + position);
    }

    @Test
    void foldReducesTheTreeFromTheLeaves() {
        var expression = DiceExpression.parse(ATTACK);

        int dice = expression.<Integer>fold(node -> switch (node) {
            case DiceNode.Dice<Integer> d -> 1;
            default -> node.children().stream().mapToInt(Integer::intValue).sum();
        });
        int depth = expression.<Integer>fold(node -> switch (node) {
            case DiceNode.Constant<Integer> constant -> 1;
            case DiceNode.Variable<Integer> variable -> 1;
            default -> 1 + Collections.max(node.children());
        });
        String prefix = expression.<String>fold(node -> switch (node) {
            case DiceNode.Constant<String>(var value) -> Integer.toString(value);
            case DiceNode.Variable<String>(var name) -> name;
            case DiceNode.Bound<String>(var name, var value) -> name;
            case DiceNode.Dice<String>(var count, var sides, var keep) -> "(d " + count + " " + sides + ")";
            case DiceNode.Negate<String>(var operand) -> "(- " + operand + ")";
            case DiceNode.Arithmetic<String>(var operator, var left, var right) ->
                "(" + operator.symbol() + " " + left + " " + right + ")";
            case DiceNode.Comparison<String>(var relation, var left, var right) ->
                "(" + relation.symbol() + " " + left + " " + right + ")";
            case DiceNode.Group<String>(var inner, var tags) -> inner;
        });

        assertThat(dice).isEqualTo(1);
        assertThat(depth).isEqualTo(6);
        assertThat(prefix).isEqualTo("(>= (+ (+ (d 1 20) STR) BONUS) AC)");
    }

    @Test
    void foldCanRebuildAnExpression() {
        var expression = DiceExpression.parse("1d6+2d6");

        var doubled = expression.<DiceExpression>fold(node -> switch (node) {
            case DiceNode.Dice<DiceExpression>(var count, var sides, var keep) ->
                DiceExpression.of(new DiceNode.Dice<>(
                        DiceExpression.of(new DiceNode.Arithmetic<>(Operator.MULTIPLY, count, constant(2))),
                        sides,
                        keep));
            default -> DiceExpression.of(node);
        });

        assertThat(doubled).hasToString("(1*2)d6+(2*2)d6");
    }

    @Test
    void toStringAddsTheParenthesesThatATreeNeeds() {
        var sum = DiceExpression.of(new DiceNode.Arithmetic<>(Operator.ADD, constant(1), constant(2)));
        var product = DiceExpression.of(new DiceNode.Arithmetic<>(Operator.MULTIPLY, sum, constant(-3)));
        var negated = DiceExpression.of(new DiceNode.Negate<>(product));

        assertThat(product).hasToString("(1+2)*-3");
        assertThat(negated).hasToString("-((1+2)*-3)");
        assertThat(DiceExpression.of(new DiceNode.Dice<>(constant(-1), sum, Keep.ALL)))
                .hasToString("(-1)d(1+2)");
    }

    @Test
    void variablesAreListedInOrderOfAppearance() {
        assertThat(DiceExpression.parse(ATTACK).variables()).containsExactly("STR", "BONUS", "AC");
        assertThat(DiceExpression.parse("Ad6+A+B").variables()).containsExactly("A", "B");
        assertThat(DiceExpression.parse("1d20").variables()).isEmpty();
    }

    @Test
    void bindGivesAValueToAVariable() {
        var expression = DiceExpression.parse(ATTACK);

        var bound = expression.bind("STR", 3);

        assertThat(bound.variables()).containsExactly("BONUS", "AC");
        assertThat(bound).hasToString(ATTACK).isNotEqualTo(expression);
        assertThat(expression.variables()).containsExactly("STR", "BONUS", "AC");
    }

    @Test
    void bindIgnoresUnknownNames() {
        var expression = DiceExpression.parse("1d20+STR");

        assertThat(expression.bind("DEX", 2)).isEqualTo(expression);
        assertThat(expression.bind(Map.of("DEX", 2, "STR", 3)).variables()).isEmpty();
    }

    @Test
    void bindToAnExpressionKeepsItsVariables() {
        var expression = DiceExpression.parse("1d20+ATTACK");

        var bound = expression.bind("ATTACK", DiceExpression.parse("STR+PROFICIENCY"));

        assertThat(bound.variables()).containsExactly("STR", "PROFICIENCY");
        assertThat(bound.bind(Map.of("STR", 3, "PROFICIENCY", 2))
                        .roll(new FakeRandom(10))
                        .value())
                .isEqualTo(15);
    }

    @Test
    void bindAsksAFunctionForEachVariable() {
        var expression = DiceExpression.parse("1d20+STR+SNEAKd6+OTHER");

        var bound = expression.bind(name -> switch (name) {
            case "STR" -> constant(3);
            case "SNEAK" -> DiceExpression.parse("1+1");
            default -> null;
        });

        assertThat(bound.variables()).containsExactly("OTHER");
        assertThat(bound).hasToString("1d20+STR+SNEAKd6+OTHER");
        assertThat(bound.bind("OTHER", 0).roll(new FakeRandom(10, 4, 5)).value())
                .isEqualTo(22);
    }

    @Test
    void allDiceCountByDefault() {
        var dice = (DiceNode.Dice<DiceExpression>) DiceExpression.parse("3d6").node();

        assertThat(dice.keep()).isSameAs(Keep.ALL);
        assertThat(Keep.ALL.kept(3)).isEqualTo(3);
        assertThat(Keep.ALL).hasToString("");
        assertThatIllegalArgumentException().isThrownBy(() -> new Keep(Keep.Mode.ALL, 1));
    }

    @Test
    void aBoundExpressionIsEvaluatedForEachOccurrence() {
        var expression = DiceExpression.parse("X+X").bind("X", DiceExpression.parse("1d6"));

        assertThat(expression.roll(new FakeRandom(2, 5)).value()).isEqualTo(7);
    }

    @Test
    void rollAndChancesNeedEveryVariable() {
        var expression = DiceExpression.parse(ATTACK).bind("BONUS", 2);

        assertThatExceptionOfType(UnresolvedVariableException.class)
                .isThrownBy(() -> expression.roll(new SplittableRandom(1)))
                .withMessage("Unresolved variables: STR, AC")
                .satisfies(e -> assertThat(e.variables()).containsExactly("STR", "AC"));
        assertThatExceptionOfType(UnresolvedVariableException.class).isThrownBy(expression::chances);
    }

    @Test
    void rollEvaluatesTheExpression() {
        var expression = DiceExpression.parse(ATTACK).bind(Map.of("STR", 3, "BONUS", 2, "AC", 15));

        var hit = expression.roll(new FakeRandom(10));
        var miss = expression.roll(new FakeRandom(9));
        var critical = expression.roll(new FakeRandom(20));
        var fumble = expression.roll(new FakeRandom(1));

        assertThat(hit.value()).isEqualTo(1);
        assertThat(hit.tags()).isEmpty();
        assertThat(hit).hasToString("(1d20[10])+STR[3]+BONUS[2]>=AC[15] = 1");
        assertThat(miss.value()).isZero();
        assertThat(critical.tags()).containsExactly("critical");
        assertThat(critical).hasToString("(1d20[20]:critical)+STR[3]+BONUS[2]>=AC[15] = 1");
        assertThat(fumble.tags()).containsExactly("miss");
        assertThat(fumble.value()).isZero();
    }

    @Test
    void rollRollsTheDiceFromLeftToRight() {
        var random = new FakeRandom(3, 4, 8);

        var rolled = DiceExpression.parse("2d4+1d8").roll(random);

        assertThat(rolled.value()).isEqualTo(15);
        assertThat(rolled).hasToString("2d4[3, 4]+1d8[8] = 15");
        assertThat(random.isExhausted()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "4d6dl1, 14, '4d6dl1[6, ~1~, 4, 4] = 14'",
        "4d6kh3, 14, '4d6kh3[6, ~1~, 4, 4] = 14'",
        "4d6kl1, 1, '4d6kl1[~6~, 1, ~4~, ~4~] = 1'",
        "4d6dh1, 9, '4d6dh1[~6~, 1, 4, 4] = 9'",
        "4d6kh1, 6, '4d6kh1[6, ~1~, ~4~, ~4~] = 6'",
        "4d6dh3, 1, '4d6dh3[~6~, 1, ~4~, ~4~] = 1'",
        "4d6kl2, 5, '4d6kl2[~6~, 1, 4, ~4~] = 5'",
        "4d6kh9, 15, '4d6kh9[6, 1, 4, 4] = 15'",
        "4d6dl9, 0, '4d6dl9[~6~, ~1~, ~4~, ~4~] = 0'",
        "4d6kh0, 0, '4d6kh0[~6~, ~1~, ~4~, ~4~] = 0'",
    })
    void rollKeepsAndDropsDice(String text, int value, String rolledText) {
        var rolled = DiceExpression.parse(text).roll(new FakeRandom(6, 1, 4, 4));

        assertThat(rolled.value()).isEqualTo(value);
        assertThat(rolled).hasToString(rolledText);
    }

    @Test
    void rolledTreeIsInspectable() {
        var rolled = DiceExpression.parse("(4d6dl1:high>=15)+2").roll(new FakeRandom(6, 1, 4, 4));

        var sum = (RolledNode.Arithmetic<RolledExpression>) rolled.node();
        var group = (RolledNode.Group<RolledExpression>) sum.left().node();
        var dice = (RolledNode.Dice<RolledExpression>) group.inner().node();
        List<Integer> faces = rolled.<List<Integer>>fold(node -> switch (node) {
            case RolledNode.Dice<List<Integer>> d ->
                d.dice().stream().map(RolledNode.Die::face).toList();
            default -> node.children().stream().flatMap(List::stream).toList();
        });

        assertThat(sum.value()).isEqualTo(16);
        assertThat(group.value()).isEqualTo(14);
        assertThat(group.tags()).singleElement().satisfies(tag -> {
            assertThat(tag.name()).isEqualTo("high");
            assertThat(tag.applied()).isFalse();
        });
        assertThat(group.appliedTags()).isEmpty();
        assertThat(dice.dice())
                .containsExactly(
                        new RolledNode.Die(6, true),
                        new RolledNode.Die(1, false),
                        new RolledNode.Die(4, true),
                        new RolledNode.Die(4, true));
        assertThat(faces).containsExactly(6, 1, 4, 4);
        assertThat(rolled.tags()).isEmpty();
        assertThat(rolled).hasToString("(4d6dl1[6, ~1~, 4, 4])+2 = 16");
    }

    @Test
    void rollUsesTheRolledCountAndSides() {
        var rolled = DiceExpression.parse("(1d4)d(1d8)").roll(new FakeRandom(2, 6, 5, 3));

        assertThat(rolled.value()).isEqualTo(8);
        assertThat(rolled).hasToString("(1d4[2])d(1d8[6])[5, 3] = 8");
    }

    @Test
    void comparisonsHoldOrNot() {
        var rolled = DiceExpression.parse("1d20>=15").roll(new FakeRandom(15));

        assertThat(((RolledNode.Comparison<RolledExpression>) rolled.node()).holds())
                .isTrue();
        assertThat(DiceExpression.parse("(1d20>=15)*2").roll(new FakeRandom(14)).value())
                .isZero();
    }

    @Test
    void divisionRoundsDown() {
        assertThat(DiceExpression.parse("7/2").roll(new FakeRandom()).value()).isEqualTo(3);
        assertThat(DiceExpression.parse("-7/2").roll(new FakeRandom()).value()).isEqualTo(-4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1/0", "1d6/(1-1)", "(0-1)d6", "1d0", "10001d6", "2147483647+1", "2147483647*2"})
    void rollRejectsWhatCannotBeEvaluated(String text) {
        var expression = DiceExpression.parse(text);

        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> expression.roll(new SplittableRandom(1)));
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(expression::chances);
    }

    @Test
    void rollStaysWithinTheFaces() {
        var expression = DiceExpression.parse("3d6");
        var random = new SplittableRandom(42);

        for (int i = 0; i < 1_000; i++) {
            assertThat(expression.roll(random).value()).isBetween(3, 18);
        }
    }

    @Test
    void namesAreChecked() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DiceNode.Variable<DiceExpression>("str"));
        assertThatIllegalArgumentException().isThrownBy(() -> new DiceNode.Variable<DiceExpression>("1A"));
        assertThatIllegalArgumentException().isThrownBy(() -> new DiceNode.Variable<DiceExpression>(""));
        assertThatIllegalArgumentException().isThrownBy(() -> new DiceNode.Bound<>("a", constant(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new DiceNode.Tag<DiceExpression>("a-b", null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Keep(Keep.Mode.KEEP_HIGHEST, -1));
    }

    @Test
    @SuppressWarnings("NullAway")
    void nullsAreRejected() {
        var expression = DiceExpression.parse("1d20");

        assertThatNullPointerException().isThrownBy(() -> DiceExpression.parse(null));
        assertThatNullPointerException().isThrownBy(() -> DiceExpression.of(null));
        assertThatNullPointerException().isThrownBy(() -> expression.fold(null));
        assertThatNullPointerException().isThrownBy(() -> expression.roll(null));
        assertThatNullPointerException().isThrownBy(() -> expression.bind(null, 1));
        assertThatNullPointerException().isThrownBy(() -> expression.bind("A", null));
    }
}
