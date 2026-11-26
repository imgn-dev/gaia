package be.imgn.gaia.core.random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

import be.imgn.gaia.core.collect.Multisets;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class RandomTableTest {

    @Test
    void anEmptyTableGivesNothing() {
        var table = RandomTable.<String>builder().build();

        assertThat(table.draw(new ScriptedRandom())).isEmpty();
    }

    @Test
    void alwaysEntriesAreGivenByEveryDraw() {
        var table = RandomTable.<String>builder()
                .always("key")
                .always("coin", 3)
                .always("arrow", random -> random.nextInt(1, 7))
                .always("none", 0)
                .build();

        var drawn = table.draw(new ScriptedRandom(4));

        assertThat(drawn)
                .isEqualTo(Multisets.multiset("key", "coin", "coin", "coin", "arrow", "arrow", "arrow", "arrow"));
    }

    @Test
    void oneWeightedEntryIsDrawnByDefault() {
        var table = RandomTable.<String>builder().add(1, "a").add(1, "b", 2).build();

        assertThat(table.draw(new ScriptedRandom(0))).isEqualTo(Multisets.multiset("a"));
        assertThat(table.draw(new ScriptedRandom(1))).isEqualTo(Multisets.multiset("b", "b"));
    }

    @Test
    void theNumberOfDrawsMayBeFixedOrSelected() {
        var builder = RandomTable.<String>builder().add(1, "a").add(1, "b");

        var twice = builder.draws(2).build();
        var selected = builder.draws(random -> random.nextInt(1, 4)).build();
        var never = builder.draws(0).build();

        assertThat(twice.draw(new ScriptedRandom(0, 0))).isEqualTo(Multisets.multiset("a", "a"));
        assertThat(selected.draw(new ScriptedRandom(3, 0, 1, 1))).isEqualTo(Multisets.multiset("a", "b", "b"));
        assertThat(never.draw(new ScriptedRandom())).isEmpty();
    }

    @Test
    void aQuantityIsSelectedEachTimeItsEntryIs() {
        var table = RandomTable.<String>builder()
                .add(1, "gold", random -> random.nextInt(10, 101))
                .draws(2)
                .build();

        assertThat(table.draw(new ScriptedRandom(10, 15)).count("gold")).isEqualTo(25);
    }

    @Test
    void anEntryMayGiveNothing() {
        var table = RandomTable.<String>builder().add(1, "a").addNothing(1).build();

        assertThat(table.draw(new ScriptedRandom(1))).isEmpty();
    }

    @Test
    void aTableMayDrawFromOtherTables() {
        var gems = RandomTable.<String>builder()
                .add(1, "ruby")
                .add(1, "diamond")
                .draws(2)
                .build();
        var chest = RandomTable.<Object>builder()
                .always("key")
                .alwaysTable(gems)
                .add(1, "gold", 5)
                .addTable(1, gems)
                .build();

        // The gems that are always given, then the entry of the chest, then its gems.
        var drawn = chest.draw(new ScriptedRandom(0, 0, 1, 1, 0));

        assertThat(drawn).isEqualTo(Multisets.<Object>multiset("key", "ruby", "ruby", "ruby", "diamond"));
    }

    @Test
    void entriesAreDrawnInProportionToTheirWeights() {
        var table = RandomTable.<String>builder()
                .add(60, "gold")
                .add(30, "gem")
                .addNothing(10)
                .add(0, "never")
                .build();
        var random = new SplittableRandom(1);
        int draws = 100_000;
        int gold = 0;
        int nothing = 0;

        for (int i = 0; i < draws; i++) {
            var drawn = table.draw(random);
            assertThat(drawn).doesNotContain("never");
            gold += drawn.count("gold");
            nothing += drawn.isEmpty() ? 1 : 0;
        }

        assertThat((double) gold / draws).isCloseTo(0.6, within(0.01));
        assertThat((double) nothing / draws).isCloseTo(0.1, within(0.01));
    }

    @Test
    void drawsWithoutRepeatsSelectEachEntryOnce() {
        var table = RandomTable.<String>builder()
                .add(5, "a")
                .add(3, "b")
                .add(1, "c")
                .add(1, "d")
                .add(0, "never")
                .draws(3)
                .withoutRepeats()
                .build();
        var random = new SplittableRandom(2);

        for (int i = 0; i < 1_000; i++) {
            var drawn = table.draw(random);
            assertThat(drawn).hasSize(3).doesNotContain("never");
            assertThat(drawn.elementSet()).hasSize(3);
        }
    }

    @Test
    void drawsWithoutRepeatsStopWhenEveryEntryIsSelected() {
        var table = RandomTable.<String>builder()
                .add(2, "a")
                .add(1, "b", 2)
                .add(0, "never")
                .draws(5)
                .withoutRepeats()
                .build();

        assertThat(table.draw(new SplittableRandom(3))).isEqualTo(Multisets.multiset("a", "b", "b"));
    }

    @Test
    void drawsWithoutRepeatsSelectByWeightAmongTheEntriesLeft() {
        var table = RandomTable.<String>builder()
                .add(2, "a")
                .add(1, "b")
                .add(1, "c")
                .draws(2)
                .withoutRepeats()
                .build();
        var random = new SplittableRandom(4);
        int draws = 200_000;
        int withoutA = 0;

        for (int i = 0; i < draws; i++) {
            withoutA += table.draw(random).contains("a") ? 0 : 1;
        }

        // "b" then "c", or "c" then "b": twice a quarter, then one in three.
        assertThat((double) withoutA / draws).isCloseTo(1.0 / 6, within(0.005));
    }

    @Test
    void aBuiltTableDoesNotChangeWithItsBuilder() {
        var builder = RandomTable.<String>builder().always("a");
        var table = builder.build();

        builder.always("b").add(1, "c");

        assertThat(table.draw(new ScriptedRandom())).isEqualTo(Multisets.multiset("a"));
    }

    @Test
    void theDrawnMultisetBelongsToTheCaller() {
        var table = RandomTable.<String>builder().always("a").build();

        var drawn = table.draw(new ScriptedRandom());
        drawn.add("b");

        assertThat(table.draw(new ScriptedRandom())).isEqualTo(Multisets.multiset("a"));
    }

    @Test
    void negativeSelectedCountsAreRejected() {
        var quantity = RandomTable.<String>builder().always("a", random -> -1).build();
        var draws =
                RandomTable.<String>builder().add(1, "a").draws(random -> -1).build();

        assertThatIllegalStateException()
                .isThrownBy(() -> quantity.draw(new ScriptedRandom()))
                .withMessage("Negative quantity: -1");
        assertThatIllegalStateException()
                .isThrownBy(() -> draws.draw(new ScriptedRandom()))
                .withMessage("Negative number of draws: -1");
    }

    @Test
    void invalidArgumentsAreRejected() {
        var builder = RandomTable.<String>builder();

        assertThatIllegalArgumentException().isThrownBy(() -> builder.always("a", -1));
        assertThatIllegalArgumentException().isThrownBy(() -> builder.add(1, "a", -1));
        assertThatIllegalArgumentException().isThrownBy(() -> builder.add(-1, "a"));
        assertThatIllegalArgumentException().isThrownBy(() -> builder.addNothing(Double.NaN));
        assertThatIllegalArgumentException().isThrownBy(() -> builder.draws(-1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RandomTable.<String>builder().add(0, "a").build());
    }

    @Test
    @SuppressWarnings("NullAway")
    void nullsAreRejected() {
        var builder = RandomTable.<String>builder();

        assertThatNullPointerException().isThrownBy(() -> builder.always(null));
        assertThatNullPointerException().isThrownBy(() -> builder.always("a", null));
        assertThatNullPointerException().isThrownBy(() -> builder.alwaysTable(null));
        assertThatNullPointerException().isThrownBy(() -> builder.add(1, null));
        assertThatNullPointerException().isThrownBy(() -> builder.add(1, "a", null));
        assertThatNullPointerException().isThrownBy(() -> builder.addTable(1, null));
        assertThatNullPointerException().isThrownBy(() -> builder.draws(null));
        assertThatNullPointerException().isThrownBy(() -> builder.build().draw(null));
    }
}
