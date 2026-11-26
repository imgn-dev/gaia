package be.imgn.gaia.core.random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

import java.util.HashMap;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class MutableRandomSelectorTest {

    private static final int DRAWS = 100_000;

    private static double share(MutableRandomSelector<String> selector, String element, long seed) {
        var random = new SplittableRandom(seed);
        int selected = 0;
        for (int i = 0; i < DRAWS; i++) {
            selected += selector.next(random).equals(element) ? 1 : 0;
        }
        return (double) selected / DRAWS;
    }

    @Test
    void elementsAreSelectedInOrderOfTheRunningTotal() {
        var selector = new MutableRandomSelector<String>();
        selector.put("a", 1);
        selector.put("b", 2);
        selector.put("c", 1);

        assertThat(selector.next(new ScriptedRandom(0.0))).isEqualTo("a");
        assertThat(selector.next(new ScriptedRandom(0.24))).isEqualTo("a");
        assertThat(selector.next(new ScriptedRandom(0.25))).isEqualTo("b");
        assertThat(selector.next(new ScriptedRandom(0.74))).isEqualTo("b");
        assertThat(selector.next(new ScriptedRandom(0.75))).isEqualTo("c");
        assertThat(selector.next(new ScriptedRandom(0.999))).isEqualTo("c");
    }

    @Test
    void elementsAreSelectedInProportionToTheirWeights() {
        var selector = new MutableRandomSelector<String>();
        selector.put("goblin", 10);
        selector.put("dragon", 1);
        selector.put("never", 0);

        assertThat(share(selector, "goblin", 1)).isCloseTo(10.0 / 11, within(0.01));
        assertThat(share(selector, "never", 2)).isZero();
        assertThat(selector.size()).isEqualTo(3);
        assertThat(selector.totalWeight()).isEqualTo(11);
    }

    @Test
    void aWeightMayChange() {
        var selector = new MutableRandomSelector<String>();
        selector.put("goblin", 10);
        selector.put("dragon", 1);

        selector.put("dragon", 30);

        assertThat(selector.weight("dragon")).isEqualTo(30);
        assertThat(selector.size()).isEqualTo(2);
        assertThat(share(selector, "dragon", 3)).isCloseTo(0.75, within(0.01));
    }

    @Test
    void anElementMayBeRemoved() {
        var selector = new MutableRandomSelector<String>();
        selector.put("a", 1);
        selector.put("b", 1);
        selector.put("c", 2);

        assertThat(selector.remove("a")).isTrue();
        assertThat(selector.remove("a")).isFalse();

        assertThat(selector.contains("a")).isFalse();
        assertThat(selector.weight("a")).isZero();
        assertThat(selector.size()).isEqualTo(2);
        assertThat(selector.totalWeight()).isEqualTo(3);
        assertThat(share(selector, "c", 4)).isCloseTo(2.0 / 3, within(0.01));
        assertThat(share(selector, "a", 5)).isZero();
    }

    @Test
    void theLastElementMayBeRemoved() {
        var selector = new MutableRandomSelector<String>();
        selector.put("a", 1);
        selector.put("b", 1);

        selector.remove("b");
        selector.remove("a");

        assertThat(selector.isEmpty()).isTrue();
        assertThat(selector.totalWeight()).isZero();
    }

    @Test
    void nothingIsSelectedWithoutAPositiveWeight() {
        var selector = new MutableRandomSelector<String>();

        assertThatIllegalStateException().isThrownBy(() -> selector.next(new SplittableRandom(1)));
        selector.put("a", 0);
        assertThatIllegalStateException().isThrownBy(() -> selector.next(new SplittableRandom(1)));
    }

    @Test
    void clearRemovesEverything() {
        var selector = new MutableRandomSelector<String>();
        selector.put("a", 1);

        selector.clear();
        selector.put("b", 1);

        assertThat(selector.size()).isEqualTo(1);
        assertThat(selector.contains("a")).isFalse();
        assertThat(selector.next(new SplittableRandom(1))).isEqualTo("b");
    }

    @Test
    void manyChangesKeepTheSelectionExact() {
        var selector = new MutableRandomSelector<Integer>();
        var expected = new HashMap<Integer, Double>();
        var random = new SplittableRandom(6);

        for (int i = 0; i < 200_000; i++) {
            int element = random.nextInt(500);
            if (random.nextInt(4) == 0) {
                assertThat(selector.remove(element)).isEqualTo(expected.remove(element) != null);
            } else {
                double weight = random.nextInt(5) == 0 ? 0 : random.nextDouble(0.001, 1_000);
                selector.put(element, weight);
                expected.put(element, weight);
            }
        }

        double total =
                expected.values().stream().mapToDouble(Double::doubleValue).sum();
        assertThat(selector.size()).isEqualTo(expected.size());
        assertThat(selector.totalWeight()).isCloseTo(total, within(total * 1e-9));
        expected.forEach(
                (element, weight) -> assertThat(selector.weight(element)).isEqualTo(weight));
        // Every selection is of an element with a positive weight, and the heaviest ones come up as they should.
        var counts = new HashMap<Integer, Integer>();
        int draws = 400_000;
        for (int i = 0; i < draws; i++) {
            counts.merge(selector.next(random), 1, Integer::sum);
        }
        counts.forEach((element, count) -> {
            double weight = expected.getOrDefault(element, 0.0);
            assertThat(weight).isPositive();
            assertThat((double) count / draws).isCloseTo(weight / total, within(0.002));
        });
    }

    @Test
    void itIsARandomSelector() {
        var selector = new MutableRandomSelector<String>();
        selector.put("a", 1);
        RandomSelector<Integer> lengths = selector.map(String::length);

        assertThat(lengths.next(new SplittableRandom(1))).isEqualTo(1);
    }

    @Test
    void invalidWeightsAreRejected() {
        var selector = new MutableRandomSelector<String>();

        assertThatIllegalArgumentException().isThrownBy(() -> selector.put("a", -1));
        assertThatIllegalArgumentException().isThrownBy(() -> selector.put("a", Double.NaN));
        assertThatIllegalArgumentException().isThrownBy(() -> selector.put("a", Double.POSITIVE_INFINITY));
        assertThat(selector.isEmpty()).isTrue();
    }

    @Test
    @SuppressWarnings("NullAway")
    void nullsAreRejected() {
        var selector = new MutableRandomSelector<String>();
        selector.put("a", 1);

        assertThatNullPointerException().isThrownBy(() -> selector.put(null, 1));
        assertThatNullPointerException().isThrownBy(() -> selector.next(null));
        assertThat(selector.contains(null)).isFalse();
        assertThat(selector.remove(null)).isFalse();
    }
}
