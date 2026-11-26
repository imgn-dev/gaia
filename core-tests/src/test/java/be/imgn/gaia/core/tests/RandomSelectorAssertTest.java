package be.imgn.gaia.core.tests;

import static be.imgn.gaia.core.testing.RandomSelectorAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import be.imgn.gaia.core.collect.Maps;
import be.imgn.gaia.core.random.MutableRandomSelector;
import be.imgn.gaia.core.random.RandomSelector;
import be.imgn.gaia.core.random.dice.DiceExpression;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class RandomSelectorAssertTest {

    private static final RandomSelector<String> RARITY =
            RandomSelector.weighted(Maps.map("common", 70, "rare", 25, "legendary", 5));

    @Test
    void aSelectorThatFollowsTheWeightsPasses() {
        assertThat(RARITY).selectsInProportion(Maps.map("common", 70, "rare", 25, "legendary", 5));
        assertThat(RARITY).selectsInProportion(Maps.map("common", 14, "rare", 5, "legendary", 1, "never", 0));
    }

    @Test
    void selectorsSelectWithTheirChances() {
        var mutable = new MutableRandomSelector<String>();
        mutable.put("goblin", 10);
        mutable.put("dragon", 1);

        assertThat(RARITY).selectsWithItsChances();
        assertThat(RandomSelector.uniform(List.of("a", "b", "c", "a"))).selectsWithItsChances();
        assertThat(RandomSelector.constant("a")).selectsWithItsChances();
        assertThat(mutable).selectsWithItsChances();
        assertThat(DiceExpression.parse("3d6").selector()).selectsWithItsChances();
        assertThat(DiceExpression.parse("4d6dl1").chances().selector())
                .selectsWith(DiceExpression.parse("4d6dl1").chances().values());
        assertThat(RandomSelector.flatten(RandomSelector.uniform(List.of(RARITY, RandomSelector.constant("rare")))))
                .selectsWithItsChances();
    }

    @Test
    void aSelectorThatIsOffFails() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() ->
                        assertThat(RARITY).selectsInProportion(Maps.map("common", 68, "rare", 27, "legendary", 5)))
                .withMessageContaining("too far from them");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(DiceExpression.parse("2d6").selector())
                        .selectsWith(DiceExpression.parse("1d11+1").chances().values()));
    }

    @Test
    void anUnexpectedElementFails() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(RARITY).selectsInProportion(Maps.map("common", 70, "rare", 30)))
                .withMessageContaining("legendary was selected");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() ->
                        assertThat(RARITY).selectsInProportion(Maps.map("common", 70, "rare", 30, "legendary", 0)));
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(RandomSelector.uniform(List.of("a", "b")))
                        .selectsWith(RandomSelector.constant("a").chances()));
    }

    @Test
    void moreSelectionsFindSmallerDifferences() {
        var almost = Maps.map("common", 70.5, "rare", 24.5, "legendary", 5);

        assertThat(RARITY).inSelections(10_000).selectsInProportion(almost);
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(RARITY).inSelections(2_000_000).selectsInProportion(almost));
    }

    @Test
    void theSourceOfRandomnessMayBeGiven() {
        assertThat(RARITY).usingRandom(new SplittableRandom(42)).selectsWithItsChances();
    }

    @Test
    void tooFewSelectionsAreRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> assertThat(RARITY).inSelections(50).selectsWithItsChances())
                .withMessageContaining("select more");
        assertThatIllegalArgumentException().isThrownBy(() -> assertThat(RARITY).inSelections(0));
    }

    @Test
    void aNullSelectorFails() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat((RandomSelector<String>) null).selectsWithItsChances());
    }

    @Test
    void aSelectorThatDoesNotKnowItsChancesCannotBeComparedWithThem() {
        RandomSelector<Integer> die = random -> random.nextInt(1, 7);

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> assertThat(die).selectsWithItsChances());
        assertThat(die).selectsWith(DiceExpression.parse("1d6").chances().values());
    }
}
