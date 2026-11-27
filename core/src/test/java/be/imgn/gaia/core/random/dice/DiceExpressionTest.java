package be.imgn.gaia.core.random.dice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import be.imgn.gaia.core.random.dice.DiceNode.Explode;
import be.imgn.gaia.core.random.dice.DiceNode.Keep;
import be.imgn.gaia.core.random.dice.DiceNode.Operator;
import be.imgn.gaia.core.random.dice.DiceNode.Relation;
import be.imgn.gaia.core.random.dice.DiceNode.Reroll;
import be.imgn.gaia.core.random.dice.DiceNode.Sides;
import be.imgn.gaia.core.random.dice.DiceNode.Tally;
import java.util.Arrays;
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
                "(2d10:tier1<=11:tier2>=12<=16:tier3>=17)",
                "(1d20:success>=DC<DC+10)",
                "(1d20:odd>=(1d20>=10))",
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

        var dice = DiceExpression.of(new DiceNode.Dice<>(
                constant(4),
                new Sides.Standard<>(constant(6)),
                Reroll.NONE,
                Explode.NONE,
                new Keep(Keep.Mode.DROP_LOWEST, 1),
                Tally.SUM));
        var variable = DiceExpression.of(new DiceNode.Variable<DiceExpression>("STR"));
        assertThat(expression.node()).isEqualTo(new DiceNode.Arithmetic<>(Operator.ADD, dice, variable));
    }

    @Test
    void parseGivesTagsTheirConditions() {
        var expression = DiceExpression.parse("(1d20:attack:critical>=20:middling>=8<=AC+2)");

        var group = (DiceNode.Group<DiceExpression>) expression.node();
        assertThat(group.tags())
                .containsExactly(
                        new DiceNode.Tag<>("attack", List.of()),
                        new DiceNode.Tag<>(
                                "critical", List.of(new DiceNode.Condition<>(Relation.GREATER_OR_EQUAL, constant(20)))),
                        new DiceNode.Tag<>(
                                "middling",
                                List.of(
                                        new DiceNode.Condition<>(Relation.GREATER_OR_EQUAL, constant(8)),
                                        new DiceNode.Condition<>(
                                                Relation.LESS_OR_EQUAL, DiceExpression.parse("AC+2")))));
        assertThat(group.children())
                .containsExactly(DiceExpression.parse("1d20"), constant(20), constant(8), DiceExpression.parse("AC+2"));
    }

    @ParameterizedTest
    @CsvSource({"11, tier1", "12, tier2", "16, tier2", "17, tier3"})
    void aTagAppliesWhenAllItsConditionsHold(int roll, String tier) {
        var expression = DiceExpression.parse("(1d20:tier1<=11:tier2>=12<=16:tier3>=17)");

        var rolled = expression.roll(new FakeRandom(roll));

        assertThat(rolled.tags()).containsExactly(tier);
        assertThat(rolled).hasToString("(1d20[" + roll + "]:" + tier + ") = " + roll);
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
        String prefix = expression.<String>fold(DiceExpressionTest::prefix);

        assertThat(dice).isEqualTo(1);
        assertThat(depth).isEqualTo(6);
        assertThat(prefix).isEqualTo("(>= (+ (+ (d 1 20) STR) BONUS) AC)");
    }

    private static String prefix(DiceNode<String> node) {
        return switch (node) {
            case DiceNode.Constant<String>(var value) -> Integer.toString(value);
            case DiceNode.Variable<String>(var name) -> name;
            case DiceNode.Bound<String>(var name, var value) -> name;
            case DiceNode.Dice<String> dice ->
                "(d " + dice.count() + " " + dice.children().getLast() + ")";
            case DiceNode.Negate<String>(var operand) -> "(- " + operand + ")";
            case DiceNode.Arithmetic<String>(var operator, var left, var right) ->
                "(" + operator.symbol() + " " + left + " " + right + ")";
            case DiceNode.Comparison<String>(var relation, var left, var right) ->
                "(" + relation.symbol() + " " + left + " " + right + ")";
            case DiceNode.Group<String>(var inner, var tags) -> inner;
            case DiceNode.Extremum<String>(var extreme, var operands) ->
                "(" + extreme.symbol() + " " + String.join(" ", operands) + ")";
            case DiceNode.Repeat<String>(var count, var operand) -> "(x " + count + " " + operand + ")";
        };
    }

    @Test
    void foldCoversEveryKindOfNode() {
        var expression = DiceExpression.parse("max(2x(1d6), -STR) >= 3");

        assertThat(expression.<String>fold(DiceExpressionTest::prefix)).isEqualTo("(>= (max (x 2 (d 1 6)) (- STR)) 3)");
    }

    @Test
    void foldCanRebuildAnExpression() {
        var expression = DiceExpression.parse("1d6+2d6");

        var doubled = expression.<DiceExpression>fold(node -> switch (node) {
            case DiceNode.Dice<DiceExpression>(var count, var sides, var reroll, var explode, var keep, var tally) ->
                DiceExpression.of(new DiceNode.Dice<>(
                        DiceExpression.of(new DiceNode.Arithmetic<>(Operator.MULTIPLY, count, constant(2))),
                        sides,
                        reroll,
                        explode,
                        keep,
                        tally));
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
        assertThat(DiceExpression.of(new DiceNode.Dice<>(constant(-1), sum))).hasToString("(-1)d(1+2)");
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

    @ParameterizedTest
    @ValueSource(
            strings = {
                "2d8r=1",
                "2d8r<=2",
                "2d8rr<=2",
                "3d6x",
                "3d6x>=5",
                "4d6r=1dl1",
                "4d6rr<=2xkh3",
                "2d10x=10>=15",
                "(2d6x:big>=10)",
            })
    void rerollsAndExplosionsAreWrittenBack(String text) {
        assertThat(DiceExpression.parse(text)).hasToString(text);
    }

    @Test
    void aBareRerollIsOfTheLowestFace() {
        var bare = DiceExpression.parse("2d8r");

        assertThat(bare).hasToString("2d8r").isNotEqualTo(DiceExpression.parse("2d8r=1"));
        assertThat(DiceExpression.parse("2d8rr")).hasToString("2d8rr");
        assertThat(bare.roll(new FakeRandom(1, 8, 2))).hasToString("2d8r[1r, 8, 2] = 10");
        assertThat(bare.chances().outcomes())
                .isEqualTo(DiceExpression.parse("2d8r=1").chances().outcomes());
        assertThat(DiceExpression.parse("(2d8r)>=10").node()).isInstanceOf(DiceNode.Comparison.class);
        assertThat(DiceExpression.parse("(2d8r)>=10")).hasToString("(2d8r)>=10");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "4d[-1..1]",
                "1d[0..9]",
                "1d[1,2,2,3,3,4]+1d[1,3..6,8]",
                "6d[0,0,0,0,1,1]",
                "3d[1..6,6]kh2",
                "2d[-2,5]r=-2",
                "2d[-3..-1,1..3]x>=-1",
                "1d[7]",
                "STRd[1..3,5..7]rr",
            })
    void customDiceAreWrittenBack(String text) {
        assertThat(DiceExpression.parse(text)).hasToString(text);
    }

    @ParameterizedTest
    @CsvSource({
        "'d[ 1 , 2 , 3 ]', '1d[1..3]'",
        "'d[1,2]', '1d[1,2]'",
        "'d[3..3]', '1d[3]'",
        "'2 d [ -1 .. 1 ] kh 1', '2d[-1..1]kh1'",
        "'d[1..3,3..5]', '1d[1..3,3..5]'",
        "'d[1,3,4,5,6,8]', '1d[1,3..6,8]'",
    })
    void customDiceAreNormalized(String text, String expected) {
        assertThat(DiceExpression.parse(text)).hasToString(expected).isEqualTo(DiceExpression.parse(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"d[]", "d[1", "d[3..1]", "d[1,,2]", "d[A]", "d[1..20000]", "d[1..]", "d[1.5]"})
    void facesThatAreNotAListOfNumbersAreRejected(String text) {
        assertThatExceptionOfType(DiceParseException.class).isThrownBy(() -> DiceExpression.parse(text));
    }

    @Test
    void customDiceAreInspectable() {
        var dice = (DiceNode.Dice<DiceExpression>)
                DiceExpression.parse("2d[0,0,1]").node();

        assertThat(dice.sides()).isEqualTo(new Sides.Custom<DiceExpression>(List.of(0, 0, 1)));
        assertThat(dice.children()).containsExactly(constant(2));
        assertThat(((DiceNode.Dice<DiceExpression>) DiceExpression.parse("2d6").node()).children())
                .containsExactly(constant(2), constant(6));
        assertThatIllegalArgumentException().isThrownBy(() -> new Sides.Custom<DiceExpression>(List.of()));
    }

    @ParameterizedTest
    @CsvSource({"'d[3,1,2]', 4", "'d[6,1..6]', 4", "'d[1,0,1,0,0,0]', 4", "'d[5,-2]', 4", "'d[1..3, 2..4]', 8"})
    void customFacesAreListedInAscendingOrder(String text, int position) {
        assertThatExceptionOfType(DiceParseException.class)
                .isThrownBy(() -> DiceExpression.parse(text))
                .withMessageContaining("ascending order")
                .satisfies(e -> assertThat(e.position()).isEqualTo(position));
        assertThatIllegalArgumentException().isThrownBy(() -> new Sides.Custom<DiceExpression>(List.of(3, 1, 2)));
    }

    @Test
    void customDiceShowTheFaceAtTheRandomIndex() {
        var rolled = DiceExpression.parse("4d[-1..1]").roll(new FakeRandom(1, 3, 2, 3));

        assertThat(rolled.value()).isEqualTo(1);
        assertThat(rolled).hasToString("4d[-1..1][-1, 1, 0, 1] = 1");
    }

    @Test
    void customDiceExplodeOnTheirHighestFaceAndRerollTheirLowest() {
        var exploding = DiceExpression.parse("2d[0,0,1]x").roll(new FakeRandom(3, 3, 1, 2));
        var rerolled = DiceExpression.parse("2d[2..5]r").roll(new FakeRandom(1, 4, 3));

        assertThat(exploding).hasToString("2d[0,0,1]x[1x, 1x, 0, 0] = 2");
        assertThat(rerolled).hasToString("2d[2..5]r[2r, 5, 4] = 9");
    }

    @ParameterizedTest
    @CsvSource({
        "4 d 6 rr <= 2 x >= 6 dl 1, 4d6rr<=2x>=6dl1",
        "2 d6, 2d6",
        "STR d 6 kh 1, STRd6kh1",
        "1d6 x >= 5, 1d6x>=5",
        "1d6x >=5, 1d6x>=5",
        "2d8 r, 2d8r",
        "2d8r < 3, 2d8r<3",
        "d %, 1d100",
        "( 1d6 x ) >= 5, (1d6x)>=5",
    })
    void spacesDoNotMatter(String text, String expected) {
        assertThat(DiceExpression.parse(text)).hasToString(expected).isEqualTo(DiceExpression.parse(expected));
    }

    @Test
    void aComparisonOperatorAfterARuleIsItsCondition() {
        var exploding =
                (DiceNode.Dice<DiceExpression>) DiceExpression.parse("1d6x>=5").node();

        assertThat(exploding.explode()).isEqualTo(new Explode(Explode.Faces.MATCHING, Relation.GREATER_OR_EQUAL, 5));
        assertThat(DiceExpression.parse("1d6x>=5>=10").node()).isInstanceOf(DiceNode.Comparison.class);
        assertThat(DiceExpression.parse("1d6x+1>=5")).hasToString("1d6x+1>=5");
    }

    @Test
    void comparingDiceWithABareRuleTakesParentheses() {
        var compared = DiceExpression.parse("(1d6x)>=5");

        assertThat(compared.node()).isInstanceOf(DiceNode.Comparison.class);
        assertThat(compared).hasToString("(1d6x)>=5");
        assertThat(DiceExpression.parse("(1d20:low<=(1d4x):high>=18)")).hasToString("(1d20:low<=(1d4x):high>=18)");
        assertThat(DiceExpression.parse("(1d20:low<=1d4x:high>=18)")).hasToString("(1d20:low<=1d4x:high>=18)");
        assertThat(DiceExpression.parse("(1d20:between>=(1d4r)<=(1d4x))"))
                .hasToString("(1d20:between>=(1d4r)<=(1d4x))");
        // Without them, what follows the operator must be the number of a condition.
        assertThatExceptionOfType(DiceParseException.class)
                .isThrownBy(() -> DiceExpression.parse("1d6x>=AC"))
                .withMessageContaining("between parentheses");
        assertThatExceptionOfType(DiceParseException.class)
                .isThrownBy(() -> DiceExpression.parse("2d8r>=(1+1)"))
                .withMessageContaining("between parentheses");
    }

    @Test
    void rulesAreParsedInOrder() {
        var dice = (DiceNode.Dice<DiceExpression>)
                DiceExpression.parse("4d6rr<=2x>=6dl1").node();

        assertThat(dice.reroll())
                .isEqualTo(new Reroll(Reroll.Times.ALWAYS, Reroll.Target.MATCHING, Relation.LESS_OR_EQUAL, 2));
        assertThat(dice.explode()).isEqualTo(new Explode(Explode.Faces.MATCHING, Relation.GREATER_OR_EQUAL, 6));
        assertThat(dice.keep()).isEqualTo(new Keep(Keep.Mode.DROP_LOWEST, 1));
        assertThat(dice.isPlain()).isFalse();
        assertThat(((DiceNode.Dice<DiceExpression>) DiceExpression.parse("4d6").node()).isPlain())
                .isTrue();
        assertThatExceptionOfType(DiceParseException.class).isThrownBy(() -> DiceExpression.parse("4d6xr=1"));
        assertThatExceptionOfType(DiceParseException.class).isThrownBy(() -> DiceExpression.parse("4d6dl1x"));
        assertThatExceptionOfType(DiceParseException.class).isThrownBy(() -> DiceExpression.parse("4d6r<="));
    }

    @Test
    void aDieIsRerolledOnce() {
        var rolled = DiceExpression.parse("3d6r<=2").roll(new FakeRandom(1, 2, 5, 2, 4));

        // The first die shows 1, then 2, which is kept; the last one shows 2, then 4.
        assertThat(rolled.value()).isEqualTo(11);
        assertThat(rolled).hasToString("3d6r<=2[1r, 2, 5, 2r, 4] = 11");
    }

    @Test
    void aDieIsRerolledUntilItNoLongerMatches() {
        var rolled = DiceExpression.parse("2d6rr<=2").roll(new FakeRandom(1, 2, 2, 5, 3));

        assertThat(rolled.value()).isEqualTo(8);
        assertThat(rolled).hasToString("2d6rr<=2[1r, 2r, 2r, 5, 3] = 8");
    }

    @Test
    void aDieExplodesAndTheAddedDieMayExplodeToo() {
        var rolled = DiceExpression.parse("2d6x").roll(new FakeRandom(6, 6, 2, 3));

        assertThat(rolled.value()).isEqualTo(17);
        assertThat(rolled).hasToString("2d6x[6x, 6x, 2, 3] = 17");
        var dice = (RolledNode.Dice<RolledExpression>) rolled.node();
        assertThat(dice.dice())
                .containsExactly(
                        new RolledNode.Die(6, true, false, true),
                        new RolledNode.Die(6, true, false, true),
                        new RolledNode.Die(2, true),
                        new RolledNode.Die(3, true));
    }

    @Test
    void aDieExplodesOnAConditionAtMostTenTimes() {
        var faces = new int[12];
        Arrays.fill(faces, 5);
        faces[11] = 1;

        var rolled = DiceExpression.parse("1d6x>=5+1d6").roll(new FakeRandom(faces));

        // Eleven dice of 5, of which the last one doesn't explode, then the other die.
        assertThat(rolled.value()).isEqualTo(56);
    }

    @Test
    void rerollsExplosionsAndKeepsCombine() {
        var rolled = DiceExpression.parse("3d6r=1xkh2").roll(new FakeRandom(1, 6, 4, 2, 3));

        // 1 rerolled to 6, which explodes into 4; then 2 and 3: the two highest are 6 and 4.
        assertThat(rolled.value()).isEqualTo(10);
        assertThat(rolled).hasToString("3d6r=1xkh2[1r, 6x, 4, ~2~, ~3~] = 10");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "1d6rr<=6",
                "1d6rr>=1",
                "1d6rr!=7",
                "1d1x",
                "1d6x<=6",
                "1d6x!=0",
                "1d[2,2]rr",
                "1d[3,3,3]x",
                "1d1rr"
            })
    void rulesThatNeverEndAreRejected(String text) {
        var expression = DiceExpression.parse(text);

        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> expression.roll(new SplittableRandom(1)));
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(expression::chances);
    }

    @Test
    void rulesAreChecked() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Reroll(Reroll.Times.NEVER, Reroll.Target.MATCHING, Relation.LESS, 3));
        assertThatIllegalArgumentException().isThrownBy(() -> new Explode(Explode.Faces.HIGHEST, Relation.EQUAL, 6));
        assertThat(Reroll.NONE).hasToString("");
        assertThat(Explode.NONE).hasToString("");
        assertThat(Explode.HIGHEST).hasToString("x");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "6d6c>=5",
                "6d6kh3c>=5",
                "6d10x=10c>=8",
                "4d[-1..1]c=1",
                "6d6c>=5>=3",
                "max(1d6,1d8)",
                "min(1d20,1d20)+5",
                "max(1d4-1,1)",
                "max(1d6)",
                "6x(4d6dl1)",
                "6x4d6dl1",
                "(1d4)x(1d6+1)",
                "max(3x1d6,10)",
                "STRx1d6",
                "2x3x1d4",
                "-2x1d6",
                "2x-1d6",
                "(2d6x)x1d4",
                "2d6xx1d4",
                "2d6kh1x1d4",
                "2d6x>=5x1d4",
                "2d6c>=5x1d4",
                "(2d6)x1d4",
                "(2d6r)x1d4",
            })
    void countsExtremaAndRepeatsAreWrittenBack(String text) {
        assertThat(DiceExpression.parse(text)).hasToString(text);
    }

    @ParameterizedTest
    @CsvSource({
        "'6 x ( 4d6 dl 1 )', '6x(4d6dl1)'",
        "'max ( 1d6 , 1d8 )', 'max(1d6,1d8)'",
        "'6d6 c >= 5', '6d6c>=5'",
        "'min( 1 , 2 , 3 )', 'min(1,2,3)'",
    })
    void countsExtremaAndRepeatsIgnoreSpaces(String text, String expected) {
        assertThat(DiceExpression.parse(text)).hasToString(expected).isEqualTo(DiceExpression.parse(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"6d6c", "6d6c5", "mox(1,2)", "max()", "max(1", "min 1", "6x", "max(1,)", "m", "6d6c>=5kh3"})
    void malformedCountsExtremaAndRepeatsAreRejected(String text) {
        assertThatExceptionOfType(DiceParseException.class).isThrownBy(() -> DiceExpression.parse(text));
    }

    @Test
    void successesAreCountedAmongTheDiceThatCount() {
        var all = DiceExpression.parse("6d6c>=5").roll(new FakeRandom(5, 6, 1, 2, 6, 4));
        var kept = DiceExpression.parse("4d6kh2c>=5").roll(new FakeRandom(6, 4, 5, 5));

        assertThat(all.value()).isEqualTo(3);
        assertThat(all).hasToString("6d6c>=5[5, 6, 1, 2, 6, 4] = 3");
        assertThat(kept.value()).isEqualTo(2);
        assertThat(DiceExpression.parse("4d6kl2c>=5")
                        .roll(new FakeRandom(6, 4, 5, 5))
                        .value())
                .isEqualTo(1);
    }

    @Test
    void theLowestOrTheHighestValueIsTaken() {
        var highest = DiceExpression.parse("max(1d6,1d8)").roll(new FakeRandom(3, 7));
        var lowest = DiceExpression.parse("min(1d6,1d8,2)").roll(new FakeRandom(3, 7));

        assertThat(highest.value()).isEqualTo(7);
        assertThat(highest).hasToString("max(1d6[3],1d8[7]) = 7");
        assertThat(lowest.value()).isEqualTo(2);
        assertThat(DiceExpression.parse("max(1d4-1,1)").roll(new FakeRandom(1)).value())
                .isEqualTo(1);
    }

    @Test
    void aRepeatEvaluatesItsOperandEachTime() {
        var rolled = DiceExpression.parse("3x2d6").roll(new FakeRandom(1, 2, 3, 4, 5, 6));

        assertThat(rolled.value()).isEqualTo(21);
        assertThat(rolled).hasToString("3x(2d6[1, 2]; 2d6[3, 4]; 2d6[5, 6]) = 21");
        var repeat = (RolledNode.Repeat<RolledExpression>) rolled.node();
        assertThat(repeat.rolls()).extracting(RolledExpression::value).containsExactly(3, 7, 11);
        assertThat(repeat.children()).hasSize(4);
        // A product evaluates its operand once.
        assertThat(DiceExpression.parse("3*2d6").roll(new FakeRandom(1, 2)).value())
                .isEqualTo(9);
    }

    @Test
    void theNumberOfTimesMayBeRolled() {
        var rolled = DiceExpression.parse("(1d2)x1d6").roll(new FakeRandom(2, 3, 4));

        assertThat(rolled.value()).isEqualTo(7);
        assertThat(DiceExpression.parse("0x1d6").roll(new FakeRandom()).value()).isZero();
        assertThat(DiceExpression.parse("2x(1d6:six=6)")
                        .roll(new FakeRandom(6, 2))
                        .tags())
                .containsExactly("six");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1001x1", "(0-1)x1"})
    void aNumberOfTimesIsBounded(String text) {
        var expression = DiceExpression.parse(text);

        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> expression.roll(new SplittableRandom(1)));
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(expression::chances);
    }

    @Test
    void textNestedTooDeeplyIsRejected() {
        var deep = "(".repeat(150) + "1" + ")".repeat(150);
        var negated = "-".repeat(150) + "1";
        var repeated = "2x".repeat(150) + "1";
        var huge = "(".repeat(100_000) + "1";

        assertThatExceptionOfType(DiceParseException.class)
                .isThrownBy(() -> DiceExpression.parse(deep))
                .withMessageContaining("Nested more than 100 deep");
        assertThatExceptionOfType(DiceParseException.class).isThrownBy(() -> DiceExpression.parse(negated));
        assertThatExceptionOfType(DiceParseException.class).isThrownBy(() -> DiceExpression.parse(repeated));
        assertThatExceptionOfType(DiceParseException.class).isThrownBy(() -> DiceExpression.parse(huge));
        assertThat(DiceExpression.parse("(".repeat(50) + "1d6" + ")".repeat(50))
                        .roll(new FakeRandom(4))
                        .value())
                .isEqualTo(4);
        // What follows a deep part isn't deep.
        assertThat(DiceExpression.parse("((((1))))+((((2))))+((((3))))")
                        .roll(new FakeRandom())
                        .value())
                .isEqualTo(6);
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
        assertThatIllegalArgumentException().isThrownBy(() -> new DiceNode.Tag<DiceExpression>("a-b", List.of()));
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
