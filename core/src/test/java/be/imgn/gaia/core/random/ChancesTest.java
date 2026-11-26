package be.imgn.gaia.core.random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.within;

import be.imgn.gaia.core.collect.HashMultiset;
import be.imgn.gaia.core.collect.Maps;
import be.imgn.gaia.core.collect.Multiset;
import be.imgn.gaia.core.collect.Multisets;
import be.imgn.gaia.core.math.BigFraction;
import be.imgn.gaia.core.math.IntFraction;
import be.imgn.gaia.core.random.dice.DiceExpression;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class ChancesTest {

    @Test
    void weightedSelectorsKnowTheirChances() {
        var chances = RandomSelector.weighted(Maps.map("common", 70, "rare", 25, "legendary", 5, "never", 0))
                .chances();

        assertThat(chances.of("rare")).isEqualTo(IntFraction.of(1, 4));
        assertThat(chances.of("never")).isEqualTo(IntFraction.ZERO);
        assertThat(chances.of("unknown")).isEqualTo(IntFraction.ZERO);
        assertThat(chances.where(name -> !name.equals("common"))).isEqualTo(IntFraction.of(3, 10));
        assertThat(chances.outcomes())
                .containsExactly(entry("common", 7, 10), entry("rare", 1, 4), entry("legendary", 1, 20));
        assertThat(chances).hasToString("{common=7/10, rare=1/4, legendary=1/20}");
    }

    private static Map.Entry<String, IntFraction> entry(String name, int numerator, int denominator) {
        return Map.entry(name, IntFraction.of(numerator, denominator));
    }

    @Test
    void otherSelectorsKnowTheirChances() {
        assertThat(RandomSelector.constant("a").chances().of("a")).isEqualTo(IntFraction.ONE);
        assertThat(RandomSelector.uniform(List.of("a", "b", "a", "c")).chances().of("a"))
                .isEqualTo(IntFraction.of(1, 2));
        assertThat(RandomSelector.weighted(List.of("a", "b"), element -> 2.5)
                        .chances()
                        .of("a"))
                .isEqualTo(IntFraction.of(1, 2));
        assertThat(RandomSelector.uniform(List.of("a", "bb", "cc"))
                        .map(String::length)
                        .chances()
                        .of(2))
                .isEqualTo(IntFraction.of(2, 3));
    }

    @Test
    void flattenedSelectorsKnowTheirChances() {
        var gems = RandomSelector.weighted(Maps.map("ruby", 3, "diamond", 1));
        var weapons = RandomSelector.uniform(List.of("sword", "axe"));
        var chest = RandomSelector.flatten(
                RandomSelector.weighted(Maps.map(gems, 30, weapons, 10, RandomSelector.constant("gold"), 60)));

        var chances = chest.chances();

        assertThat(chances.of("gold")).isEqualTo(IntFraction.of(3, 5));
        assertThat(chances.of("ruby")).isEqualTo(IntFraction.of(9, 40));
        assertThat(chances.of("diamond")).isEqualTo(IntFraction.of(3, 40));
        assertThat(chances.of("axe")).isEqualTo(IntFraction.of(1, 20));
    }

    @Test
    void mutableSelectorsKnowTheirChancesAsTheyAre() {
        var selector = new MutableRandomSelector<String>();
        selector.put("goblin", 10);
        selector.put("dragon", 1);
        selector.put("never", 0);

        assertThat(selector.chances().of("dragon")).isEqualTo(IntFraction.of(1, 11));
        selector.put("dragon", 30);
        assertThat(selector.chances().of("dragon")).isEqualTo(IntFraction.of(3, 4));
        assertThat(selector.chances().outcomes()).doesNotContainKey("never");
        assertThatIllegalStateException().isThrownBy(() -> new MutableRandomSelector<String>().chances());
    }

    @Test
    void functionsDoNotKnowTheirChances() {
        RandomSelector<Integer> die = random -> random.nextInt(1, 7);

        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(die::chances);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> die.map(face -> face * 2).chances());
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> RandomBag.of(List.of(1, 2)).refilling().chances());
    }

    @Test
    void weightsThatAreNotWholeNumbersGiveLargeFractions() {
        var chances = RandomSelector.weighted(Maps.map("a", 0.1, "b", 0.3)).chances();

        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> chances.of("a"));
        assertThat(chances.exactOutcomes().getOrDefault("a", BigFraction.ZERO).doubleValue())
                .isCloseTo(0.25, within(1e-15));
        assertThat(RandomSelector.weighted(Maps.map("a", 0.5, "b", 0.25))
                        .chances()
                        .of("a"))
                .isEqualTo(IntFraction.of(2, 3));
    }

    @Test
    void diceExpressionsAreSelectors() {
        var selector = DiceExpression.parse("2d6+1").selector();

        assertThat(selector.chances().of(8)).isEqualTo(IntFraction.of(1, 6));
        assertThat(selector.stream(new SplittableRandom(1)).limit(1_000)).allMatch(value -> value >= 3 && value <= 13);
        assertThat(selector.next(new ScriptedRandom(2, 4))).isEqualTo(9);
    }

    @Test
    void chancesAreSelectors() {
        var chances = DiceExpression.parse("3d6").chances();
        var selector = chances.selector();
        var random = new SplittableRandom(2);
        int draws = 200_000;
        int tens = 0;

        for (int i = 0; i < draws; i++) {
            tens += selector.next(random) == 10 ? 1 : 0;
        }

        assertThat(selector.chances()).isEqualTo(chances.values());
        assertThat((double) tens / draws).isCloseTo(27.0 / 216, within(0.005));
        // One number, however many dice the expression has.
        assertThat(selector.next(new ScriptedRandom(0.5))).isBetween(3, 18);
    }

    @Test
    void diceChancesKeepTheirTags() {
        var all = DiceExpression.parse("(1d20:critical>=20)+5").chances().all();

        assertThat(all.where(outcome -> outcome.tags().contains("critical"))).isEqualTo(IntFraction.of(1, 20));
        assertThat(all.outcomes()).hasSize(20);
    }

    @Test
    void tablesKnowTheChancesOfTheirDraws() {
        var table = RandomTable.<String>builder()
                .always("key")
                .add(60, "gold", 5)
                .add(30, "gem")
                .addNothing(10)
                .build();

        var chances = table.chances();

        assertThat(chances.of(Multisets.multiset("key", "gem"))).isEqualTo(IntFraction.of(3, 10));
        assertThat(chances.of(Multisets.multiset("key"))).isEqualTo(IntFraction.of(1, 10));
        assertThat(chances.where(drawn -> drawn.count("gold") == 5)).isEqualTo(IntFraction.of(3, 5));
        assertThat(chances.outcomes()).hasSize(3);
    }

    @Test
    void tablesCountSeveralDraws() {
        var table =
                RandomTable.<String>builder().add(1, "a").add(1, "b").draws(2).build();

        var chances = table.chances();

        assertThat(chances.of(Multisets.multiset("a", "a"))).isEqualTo(IntFraction.of(1, 4));
        assertThat(chances.of(Multisets.multiset("a", "b"))).isEqualTo(IntFraction.of(1, 2));
        assertThat(chances.of(HashMultiset.create(List.of("b", "a")))).isEqualTo(IntFraction.of(1, 2));
    }

    @Test
    void tablesCountDiceAndOtherTables() {
        var gems =
                RandomTable.<String>builder().add(3, "ruby").add(1, "diamond").build();
        var chest = RandomTable.<String>builder()
                .add(1, "gold", DiceExpression.parse("1d4").selector())
                .addTable(1, gems)
                .draws(DiceExpression.parse("1d2").selector())
                .build();

        var chances = chest.chances();

        // One draw, half the time: gold x 3 is a quarter of the gold half.
        // Two draws: never three gold coins alone but as 1 + 2 or 2 + 1.
        assertThat(chances.where(drawn -> drawn.count("gold") == 3 && drawn.size() == 3))
                .isEqualTo(IntFraction.of(1, 16).plus(IntFraction.of(1, 64)));
        assertThat(chances.where(drawn -> drawn.contains("diamond"))).isEqualTo(IntFraction.of(23, 128));
        assertThat(chances.where(drawn -> true)).isEqualTo(IntFraction.ONE);
    }

    @Test
    void tablesAgreeWithTheirDraws() {
        var table = RandomTable.<String>builder()
                .add(2, "a", DiceExpression.parse("1d3").selector())
                .add(1, "b")
                .draws(2)
                .build();
        var chances = table.chances();
        var random = new SplittableRandom(3);
        var counts = new HashMap<Multiset<String>, Integer>();
        int draws = 200_000;

        for (int i = 0; i < draws; i++) {
            counts.merge(table.draw(random), 1, Integer::sum);
        }

        assertThat(counts.keySet()).isEqualTo(chances.outcomes().keySet());
        counts.forEach((drawn, count) ->
                assertThat((double) count / draws).isCloseTo(chances.of(drawn).doubleValue(), within(0.005)));
    }

    @Test
    void tablesNeedSelectorsThatKnowTheirChances() {
        var table = RandomTable.<String>builder()
                .add(1, "gold", random -> random.nextInt(10))
                .build();

        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(table::chances);
        assertThatIllegalStateException()
                .isThrownBy(() -> RandomTable.<String>builder()
                        .always("a", RandomSelector.constant(-1))
                        .build()
                        .chances());
    }

    @Test
    void chancesAreBuiltFromChancesOrShares() {
        var half = BigFraction.of(1, 2);

        assertThat(Chances.ofChances(Maps.map("a", half, "b", half, "c", BigFraction.ZERO))
                        .outcomes())
                .containsOnlyKeys("a", "b");
        assertThat(Chances.ofShares(Maps.map("a", BigFraction.of(3), "b", BigFraction.of(1)))
                        .of("a"))
                .isEqualTo(IntFraction.of(3, 4));
        assertThatIllegalArgumentException().isThrownBy(() -> Chances.ofChances(Maps.map("a", half)));
        assertThatIllegalArgumentException().isThrownBy(() -> Chances.ofChances(Maps.map("a", half.negate())));
        assertThatIllegalArgumentException().isThrownBy(() -> Chances.ofShares(Maps.map("a", BigFraction.ZERO)));
    }

    @Test
    void mapAddsUpEqualOutcomes() {
        var chances = DiceExpression.parse("1d6").chances().values().map(value -> value % 2 == 0 ? "even" : "odd");

        assertThat(chances.of("even")).isEqualTo(IntFraction.of(1, 2));
        assertThat(chances.outcomes()).hasSize(2);
    }
}
