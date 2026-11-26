package be.imgn.gaia.core.random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

import be.imgn.gaia.core.collect.HashMultiset;
import be.imgn.gaia.core.collect.Maps;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RandomSelectorTest {

    private static final int DRAWS = 200_000;

    @Test
    void constantSelectsItsElement() {
        var selector = RandomSelector.constant("a");

        assertThat(selector.next(new ScriptedRandom())).isEqualTo("a");
    }

    @Test
    void uniformSelectsByIndex() {
        var selector = RandomSelector.uniform(List.of("a", "b", "c"));

        assertThat(selector.next(new ScriptedRandom(0))).isEqualTo("a");
        assertThat(selector.next(new ScriptedRandom(2))).isEqualTo("c");
    }

    @Test
    void uniformOfOneElementUsesNoRandomness() {
        assertThat(RandomSelector.uniform(List.of("a")).next(new ScriptedRandom()))
                .isEqualTo("a");
    }

    @Test
    void uniformKeepsACopy() {
        var elements = new ArrayList<>(List.of("a", "b"));
        var selector = RandomSelector.uniform(elements);

        elements.set(1, "c");

        assertThat(selector.next(new ScriptedRandom(1))).isEqualTo("b");
    }

    @Test
    void uniformSelectsEachElementAsOften() {
        var selector = RandomSelector.uniform(List.of("a", "b", "c", "d"));

        var counts = HashMultiset.create(
                selector.stream(new SplittableRandom(1)).limit(DRAWS).toList());

        assertThat(counts.elementSet()).containsExactlyInAnyOrder("a", "b", "c", "d");
        counts.forEachEntry(
                (element, count) -> assertThat((double) count / DRAWS).isCloseTo(0.25, within(0.01)));
    }

    @Test
    void weightedTableSharesEachSlotBetweenTwoElements() {
        // Weights 1, 1, 2 and 4: half a slot, half a slot, one slot and two slots.
        var selector = RandomSelector.weighted(List.of("a", "b", "c", "d"), element -> switch (element) {
            case "a", "b" -> 1;
            case "c" -> 2;
            default -> 4;
        });

        // A fraction of the whole table: the slot, then how far into it.
        assertThat(selector.next(new ScriptedRandom(3.5 / 4))).isEqualTo("d");
        assertThat(selector.next(new ScriptedRandom(2.99 / 4))).isEqualTo("c");
        assertThat(selector.next(new ScriptedRandom(0.25 / 4))).isEqualTo("a");
        assertThat(selector.next(new ScriptedRandom(0.75 / 4))).isEqualTo("d");
        assertThat(selector.next(new ScriptedRandom(1.49 / 4))).isEqualTo("b");
        assertThat(selector.next(new ScriptedRandom(1.5 / 4))).isEqualTo("d");
    }

    @Test
    void weightedUsesOneRandomNumber() {
        var random = new ScriptedRandom(0.1, 0.9);
        var selector = RandomSelector.weighted(Maps.map("a", 1, "b", 3));

        assertThat(selector.stream(random).limit(2)).containsExactly("a", "b");
        assertThat(random.isExhausted()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 7, 100, 1_000})
    void weightedTableGivesEachElementItsShare(int size) {
        var random = new SplittableRandom(size);
        var weights = random.doubles(size, 0.001, 1_000).toArray();
        double total = Arrays.stream(weights).sum();
        var elements = IntStream.range(0, size).boxed().toList();

        var selector = (Selectors.AliasSelector<Integer>) RandomSelector.weighted(elements, i -> weights[i]);

        var chances = selector.tableChances();
        for (int i = 0; i < size; i++) {
            assertThat(chances[i]).isCloseTo(weights[i] / total, within(1e-12));
        }
    }

    @Test
    void weightedSelectsInProportionToTheWeights() {
        var selector = RandomSelector.weighted(Maps.map("common", 70, "rare", 25, "legendary", 5));

        var counts = HashMultiset.create(
                selector.stream(new SplittableRandom(2)).limit(DRAWS).toList());

        assertThat((double) counts.count("common") / DRAWS).isCloseTo(0.70, within(0.01));
        assertThat((double) counts.count("rare") / DRAWS).isCloseTo(0.25, within(0.01));
        assertThat((double) counts.count("legendary") / DRAWS).isCloseTo(0.05, within(0.01));
    }

    @Test
    void weightedNeverSelectsAWeightOfZero() {
        var selector = RandomSelector.weighted(Maps.map("never", 0, "a", 1, "b", 3));

        assertThat(selector.stream(new SplittableRandom(3)).limit(10_000)).containsOnly("a", "b");
    }

    @Test
    void weightedWithOnePositiveWeightUsesNoRandomness() {
        var selector = RandomSelector.weighted(Maps.map("never", 0, "always", 2));

        assertThat(selector.next(new ScriptedRandom())).isEqualTo("always");
    }

    @Test
    void weightedWithEqualWeightsIsUniform() {
        var selector = RandomSelector.weighted(List.of("a", "b", "c"), element -> 2.5);

        assertThat(selector.next(new ScriptedRandom(1))).isEqualTo("b");
    }

    @Test
    void weightedAcceptsAnElementSeveralTimes() {
        var selector = RandomSelector.weighted(List.of("a", "b", "a"), element -> element.equals("a") ? 1 : 6);

        var counts = HashMultiset.create(
                selector.stream(new SplittableRandom(4)).limit(DRAWS).toList());

        assertThat((double) counts.count("a") / DRAWS).isCloseTo(0.25, within(0.01));
    }

    @Test
    void weightedRejectsWhatIsNotAWeight() {
        assertThatIllegalArgumentException().isThrownBy(() -> RandomSelector.weighted(Maps.map("a", -1, "b", 2)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RandomSelector.weighted(Maps.map("a", Double.NaN, "b", 2)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RandomSelector.weighted(Maps.map("a", Double.POSITIVE_INFINITY, "b", 2)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RandomSelector.weighted(Maps.map("a", Double.MAX_VALUE, "b", Double.MAX_VALUE / 2)))
                .withMessageContaining("infinite");
    }

    @Test
    void anEmptySelectionIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> RandomSelector.uniform(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> RandomSelector.weighted(Maps.map()));
        assertThatIllegalArgumentException().isThrownBy(() -> RandomSelector.weighted(Maps.map("a", 0, "b", 0)));
    }

    @Test
    void mapTransformsTheSelectedElement() {
        var selector = RandomSelector.uniform(List.of("a", "bb", "ccc")).map(String::length);

        assertThat(selector.next(new ScriptedRandom(2))).isEqualTo(3);
    }

    @Test
    void flattenSelectsASelectorThenAnElement() {
        var letters = RandomSelector.uniform(List.of("a", "b"));
        var digits = RandomSelector.uniform(List.of("1", "2", "3"));
        var selector = RandomSelector.flatten(RandomSelector.uniform(List.of(letters, digits)));

        assertThat(selector.next(new ScriptedRandom(0, 1))).isEqualTo("b");
        assertThat(selector.next(new ScriptedRandom(1, 2))).isEqualTo("3");
    }

    @Test
    void aFunctionIsASelector() {
        RandomSelector<Integer> die = random -> random.nextInt(1, 7);

        assertThat(die.stream(new SplittableRandom(5)).limit(1_000)).allMatch(face -> face >= 1 && face <= 6);
    }

    @Test
    void streamSelectsLazily() {
        var random = new ScriptedRandom(0, 1);

        var selected = RandomSelector.uniform(List.of("a", "b")).stream(random)
                .limit(2)
                .toList();

        assertThat(selected).containsExactly("a", "b");
        assertThat(random.isExhausted()).isTrue();
    }

    @Test
    @SuppressWarnings("NullAway")
    void nullsAreRejected() {
        var selector = RandomSelector.constant("a");

        assertThatNullPointerException().isThrownBy(() -> RandomSelector.constant(null));
        assertThatNullPointerException().isThrownBy(() -> RandomSelector.uniform(null));
        assertThatNullPointerException().isThrownBy(() -> RandomSelector.uniform(Arrays.asList("a", null)));
        assertThatNullPointerException().isThrownBy(() -> RandomSelector.weighted(List.of("a"), null));
        assertThatNullPointerException().isThrownBy(() -> RandomSelector.flatten(null));
        assertThatNullPointerException().isThrownBy(() -> selector.map(null));
        assertThatNullPointerException().isThrownBy(() -> selector.stream(null));
        assertThatNullPointerException().isThrownBy(() -> selector.next(null));
        assertThatNullPointerException()
                .isThrownBy(
                        () -> RandomSelector.weighted(Maps.map("a", 1, "b", 2)).next(null));
    }
}
