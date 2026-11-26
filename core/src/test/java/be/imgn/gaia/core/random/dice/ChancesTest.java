package be.imgn.gaia.core.random.dice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import be.imgn.gaia.core.math.IntFraction;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChancesTest {

    private static Chances chances(String text) {
        return DiceExpression.parse(text).chances();
    }

    @Test
    void oneDieIsUniform() {
        var chances = chances("1d6");

        assertThat(chances.outcomes().keySet()).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(chances.outcomes().values()).containsOnly(IntFraction.of(1, 6));
        assertThat(chances.of(7)).isEqualTo(IntFraction.ZERO);
        assertThat(chances.min()).isEqualTo(1);
        assertThat(chances.max()).isEqualTo(6);
        assertThat(chances.mean()).isEqualTo(IntFraction.of(7, 2));
        assertThat(chances).hasToString("{1=1/6, 2=1/6, 3=1/6, 4=1/6, 5=1/6, 6=1/6}");
    }

    @Test
    void twoDiceAreSummed() {
        var chances = chances("2d6");

        assertThat(chances.of(2)).isEqualTo(IntFraction.of(1, 36));
        assertThat(chances.of(7)).isEqualTo(IntFraction.of(1, 6));
        assertThat(chances.atLeast(10)).isEqualTo(IntFraction.of(1, 6));
        assertThat(chances.atMost(3)).isEqualTo(IntFraction.of(1, 12));
        assertThat(chances.atLeast(2)).isEqualTo(IntFraction.ONE);
        assertThat(chances.atMost(1)).isEqualTo(IntFraction.ZERO);
        assertThat(chances.mean()).isEqualTo(IntFraction.of(7));
    }

    @Test
    void attackRoll() {
        var chances = DiceExpression.parse("(1d20:critical>=20:miss=1)+STR+BONUS>=AC")
                .bind(Map.of("STR", 3, "BONUS", 2, "AC", 15))
                .chances();

        assertThat(chances.of(1)).isEqualTo(IntFraction.of(11, 20));
        assertThat(chances.outcomes())
                .containsExactly(Map.entry(0, IntFraction.of(9, 20)), Map.entry(1, IntFraction.of(11, 20)));
        assertThat(chances.tagged("critical")).isEqualTo(IntFraction.of(1, 20));
        assertThat(chances.tagged("miss")).isEqualTo(IntFraction.of(1, 20));
        assertThat(chances.tagged("unknown")).isEqualTo(IntFraction.ZERO);
        assertThat(chances.where(
                        outcome -> outcome.value() == 1 && !outcome.tags().contains("critical")))
                .isEqualTo(IntFraction.of(1, 2));
        assertThat(chances.where(
                        outcome -> outcome.value() == 1 && outcome.tags().contains("miss")))
                .isEqualTo(IntFraction.ZERO);
    }

    @Test
    void advantage() {
        var chances = chances("(2d20kh1:critical>=20)+5");

        assertThat(chances.of(25)).isEqualTo(IntFraction.of(39, 400));
        assertThat(chances.atLeast(15)).isEqualTo(IntFraction.of(319, 400));
        assertThat(chances.tagged("critical")).isEqualTo(IntFraction.of(39, 400));
        assertThat(chances.mean()).isEqualTo(IntFraction.of(753, 40));
        assertThat(chances.where(
                        outcome -> outcome.value() >= 15 && !outcome.tags().contains("critical")))
                .isEqualTo(IntFraction.of(7, 10));
    }

    @Test
    void abilityScore() {
        var chances = chances("4d6dl1");

        assertThat(chances.mean()).isEqualTo(IntFraction.of(15869, 1296));
        assertThat(chances.of(18)).isEqualTo(IntFraction.of(21, 1296));
        assertThat(chances.of(3)).isEqualTo(IntFraction.of(1, 1296));
        assertThat(chances.min()).isEqualTo(3);
        assertThat(chances.max()).isEqualTo(18);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "kh", "kl", "dh", "dl"})
    void poolsMatchTheEnumerationOfEveryRoll(String mode) {
        for (int count = 0; count <= 5; count++) {
            for (int sides = 1; sides <= 5; sides++) {
                for (int kept = 0; kept <= (mode.isEmpty() ? 0 : count + 1); kept++) {
                    var text = count + "d" + sides + (mode.isEmpty() ? "" : mode + kept);
                    var expression = DiceExpression.parse(text);

                    assertThat(expression.chances().outcomes()).as(text).isEqualTo(enumerate(expression, count, sides));
                }
            }
        }
    }

    /** Rolls every combination of faces, and counts the values that the expression evaluates to. */
    private static Map<Integer, IntFraction> enumerate(DiceExpression expression, int count, int sides) {
        var rolls = new TreeMap<Integer, Integer>();
        int[] faces = new int[count];
        Arrays.fill(faces, 1);
        int total = 0;
        while (true) {
            rolls.merge(expression.roll(new FakeRandom(faces)).value(), 1, Integer::sum);
            total++;
            int die = 0;
            while (die < count && faces[die] == sides) {
                faces[die++] = 1;
            }
            if (die == count) {
                break;
            }
            faces[die]++;
        }
        var chances = new TreeMap<Integer, IntFraction>();
        for (var roll : rolls.entrySet()) {
            chances.put(roll.getKey(), IntFraction.of(roll.getValue(), total));
        }
        return chances;
    }

    @Test
    void arithmeticCombinesIndependentValues() {
        assertThat(chances("1d4-1d4").outcomes())
                .containsEntry(0, IntFraction.of(1, 4))
                .containsEntry(-3, IntFraction.of(1, 16))
                .containsEntry(3, IntFraction.of(1, 16));
        assertThat(chances("1d4*2").outcomes().keySet()).containsExactly(2, 4, 6, 8);
        assertThat(chances("1d6/2").outcomes())
                .containsExactly(
                        Map.entry(0, IntFraction.of(1, 6)),
                        Map.entry(1, IntFraction.of(1, 3)),
                        Map.entry(2, IntFraction.of(1, 3)),
                        Map.entry(3, IntFraction.of(1, 6)));
        assertThat(chances("-1d4").outcomes().keySet()).containsExactly(-4, -3, -2, -1);
        assertThat(chances("1d6>=1d6").of(1)).isEqualTo(IntFraction.of(7, 12));
    }

    @Test
    void aBoundExpressionIsIndependentForEachOccurrence() {
        var chances = DiceExpression.parse("X+X")
                .bind("X", DiceExpression.parse("1d6"))
                .chances();

        assertThat(chances.outcomes()).isEqualTo(chances("2d6").outcomes());
    }

    @Test
    void theCountAndTheSidesMayBeRolled() {
        // One die of four sides, or two.
        var count = chances("(1d2)d4");
        // One die of two sides, or of four.
        var sides = chances("1d(1d2*2)");

        assertThat(count.of(1)).isEqualTo(IntFraction.of(1, 8));
        assertThat(count.of(2)).isEqualTo(IntFraction.of(1, 8).plus(IntFraction.of(1, 32)));
        assertThat(count.of(8)).isEqualTo(IntFraction.of(1, 32));
        assertThat(count.atLeast(1)).isEqualTo(IntFraction.ONE);
        assertThat(sides.of(1)).isEqualTo(IntFraction.of(3, 8));
        assertThat(sides.of(4)).isEqualTo(IntFraction.of(1, 8));
    }

    @Test
    void tagsFollowTheValuesThatGaveThem() {
        var chances = chances("(1d6:six=6)+(1d6:one=1)");

        assertThat(chances.tagged("six")).isEqualTo(IntFraction.of(1, 6));
        assertThat(chances.where(outcome -> outcome.tags().containsAll(Set.of("six", "one"))))
                .isEqualTo(IntFraction.of(1, 36));
        assertThat(chances.where(
                        outcome -> outcome.value() == 7 && outcome.tags().isEmpty()))
                .isEqualTo(IntFraction.of(5, 36));
        assertThat(chances.where(
                        outcome -> outcome.value() == 12 && outcome.tags().contains("six")))
                .isEqualTo(IntFraction.of(1, 36));
        assertThat(chances.of(7)).isEqualTo(IntFraction.of(1, 6));
    }

    @Test
    void aTagAppliesWhenAllItsConditionsHold() {
        var chances = chances("((2d10:critical>=19)+2:tier1<=11:tier2>=12<=16:tier3>=17)");

        assertThat(chances.tagged("tier1")).isEqualTo(IntFraction.of(9, 25));
        assertThat(chances.tagged("tier2")).isEqualTo(IntFraction.of(43, 100));
        assertThat(chances.tagged("tier3")).isEqualTo(IntFraction.of(21, 100));
        assertThat(chances.tagged("critical")).isEqualTo(IntFraction.of(3, 100));
        // Every outcome has exactly one tier.
        assertThat(chances.where(outcome -> Set.of("tier1", "tier2", "tier3").stream()
                                .filter(outcome.tags()::contains)
                                .count()
                        == 1))
                .isEqualTo(IntFraction.ONE);
        assertThat(chances("(1d6:between>=1d6<=1d6)").tagged("between")).isEqualTo(IntFraction.of(7, 27));
    }

    @Test
    void tagsMayBeUnconditionalOrComparedToDice() {
        assertThat(chances("(1d6:fire)").tagged("fire")).isEqualTo(IntFraction.ONE);
        assertThat(chances("(1d6:wins>1d6)").tagged("wins")).isEqualTo(IntFraction.of(5, 12));
        assertThat(chances("(1d6:low<=2:high>=5)")
                        .where(outcome -> outcome.tags().isEmpty()))
                .isEqualTo(IntFraction.of(1, 3));
    }

    @Test
    void largePoolsAreCountedWithoutEnumeratingTheRolls() {
        var sum = chances("100d6");
        var kept = chances("30d20kh3");
        var dropped = chances("30d20dl27");

        assertThat(sum.min()).isEqualTo(100);
        assertThat(sum.max()).isEqualTo(600);
        assertThat(sum.mean()).isEqualTo(IntFraction.of(350));
        assertThat(kept.min()).isEqualTo(3);
        assertThat(kept.max()).isEqualTo(60);
        assertThat(kept.atLeast(3)).isEqualTo(IntFraction.ONE);
        assertThat(dropped.min()).isEqualTo(3);
        assertThat(dropped.max()).isEqualTo(60);
        assertThat(chances("8d6dl2").outcomes()).isEqualTo(chances("8d6kh6").outcomes());
    }

    @Test
    void aChanceThatDoesNotFitIsRejected() {
        var chances = chances("15d20");

        assertThat(chances.atLeast(15)).isEqualTo(IntFraction.ONE);
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> chances.of(15));
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(chances::outcomes);
    }

    @Test
    void tooManyOutcomesAreRejected() {
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> chances("1001d1000"));
        assertThat(chances("1d1000000").of(1)).isEqualTo(IntFraction.of(1, 1_000_000));
    }
}
