package be.imgn.gaia.core.random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class RandomBagTest {

    @Test
    void drawRemovesTheElement() {
        var bag = RandomBag.of(List.of("a", "b", "c"));

        assertThat(bag.draw(new ScriptedRandom(1))).isEqualTo("b");
        assertThat(bag.size()).isEqualTo(2);
        // The last element took the place of the drawn one.
        assertThat(bag.draw(new ScriptedRandom(1))).isEqualTo("c");
        assertThat(bag.draw(new ScriptedRandom(0))).isEqualTo("a");
        assertThat(bag.isEmpty()).isTrue();
    }

    @Test
    void everyElementIsDrawnOnce() {
        var elements = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        var bag = RandomBag.of(elements);

        var drawn = bag.draw(10, new SplittableRandom(1));

        assertThat(drawn).containsExactlyInAnyOrderElementsOf(elements).isNotEqualTo(elements);
    }

    @Test
    void anElementInTheBagTwiceIsDrawnTwice() {
        var bag = RandomBag.of(List.of("a", "a", "b"));

        assertThat(bag.draw(3, new SplittableRandom(2))).containsExactlyInAnyOrder("a", "a", "b");
    }

    @Test
    void anEmptyBagHasNothingToDraw() {
        var bag = RandomBag.of(List.of("a"));
        bag.draw(new ScriptedRandom(0));

        assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(() -> bag.draw(new ScriptedRandom(0)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RandomBag.of(List.of("a")).draw(2, new ScriptedRandom()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RandomBag.of(List.of("a")).draw(-1, new ScriptedRandom()));
        assertThat(RandomBag.of(List.of("a")).draw(0, new ScriptedRandom())).isEmpty();
    }

    @Test
    void refillPutsTheDrawnElementsBack() {
        var bag = RandomBag.of(List.of("a", "b", "c"));
        bag.draw(2, new SplittableRandom(3));

        bag.refill();

        assertThat(bag.size()).isEqualTo(3);
        assertThat(bag.draw(3, new SplittableRandom(4))).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void addedElementsAreDrawnAndRefilled() {
        var bag = RandomBag.of(List.of("a", "b"));
        bag.draw(new ScriptedRandom(0));

        bag.add("c");
        bag.add("d");

        assertThat(bag.size()).isEqualTo(3);
        assertThat(bag.draw(3, new SplittableRandom(5))).containsExactlyInAnyOrder("b", "c", "d");
        bag.refill();
        assertThat(bag.draw(4, new SplittableRandom(6))).containsExactlyInAnyOrder("a", "b", "c", "d");
    }

    @Test
    void aBagMayStartEmpty() {
        var bag = RandomBag.<String>of(List.of());

        bag.add("a");

        assertThat(bag.draw(new ScriptedRandom(0))).isEqualTo("a");
    }

    @Test
    void theBagKeepsACopy() {
        var elements = new ArrayList<>(List.of("a"));
        var bag = RandomBag.of(elements);

        elements.add("b");

        assertThat(bag.size()).isEqualTo(1);
    }

    @Test
    void refillingSelectsEveryElementBeforeAnyAgain() {
        var elements = List.of("a", "b", "c", "d");
        var selector = RandomBag.of(elements).refilling();

        var selected = selector.stream(new SplittableRandom(7)).limit(12).toList();

        assertThat(selected.subList(0, 4)).containsExactlyInAnyOrderElementsOf(elements);
        assertThat(selected.subList(4, 8)).containsExactlyInAnyOrderElementsOf(elements);
        assertThat(selected.subList(8, 12)).containsExactlyInAnyOrderElementsOf(elements);
    }

    @Test
    void refillingNeedsAnElement() {
        assertThatIllegalStateException()
                .isThrownBy(() -> RandomBag.of(List.of()).refilling());
    }

    @Test
    @SuppressWarnings("NullAway")
    void nullsAreRejected() {
        var bag = RandomBag.of(List.of("a"));

        assertThatNullPointerException().isThrownBy(() -> RandomBag.of(null));
        assertThatNullPointerException().isThrownBy(() -> bag.add(null));
        assertThatNullPointerException().isThrownBy(() -> bag.draw(null));
        assertThatNullPointerException().isThrownBy(() -> bag.draw(1, null));
    }
}
