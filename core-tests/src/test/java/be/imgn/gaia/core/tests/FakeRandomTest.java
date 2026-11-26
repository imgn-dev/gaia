package be.imgn.gaia.core.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import be.imgn.gaia.core.collect.Maps;
import be.imgn.gaia.core.random.RandomSelector;
import be.imgn.gaia.core.random.dice.DiceExpression;
import be.imgn.gaia.core.testing.FakeRandom;
import java.util.List;
import org.junit.jupiter.api.Test;

class FakeRandomTest {

    @Test
    void exactValuesAreGivenInOrder() {
        var random = new FakeRandom().add(3, -7).add(5L).add(0.25).add(true);

        assertThat(random.remaining()).isEqualTo(5);
        assertThat(random.nextInt(6)).isEqualTo(3);
        assertThat(random.nextInt()).isEqualTo(-7);
        assertThat(random.nextLong()).isEqualTo(5L);
        assertThat(random.nextDouble()).isEqualTo(0.25);
        assertThat(random.nextBoolean()).isTrue();
        assertThat(random.isExhausted()).isTrue();
    }

    @Test
    void boundedCallsTakeValuesWithinTheirBounds() {
        var random = new FakeRandom().add(5, 10).add(7L, -3L);

        assertThat(random.nextInt(6)).isEqualTo(5);
        assertThat(random.nextInt(10, 101)).isEqualTo(10);
        assertThat(random.nextLong(8)).isEqualTo(7L);
        assertThat(random.nextLong(-5, 5)).isEqualTo(-3L);
    }

    @Test
    void fractionsFitAnyCall() {
        var random = new FakeRandom().addFractions(0.5, 0.5, 0.5, 0.5, 0.5, 0.25, 0.999);

        assertThat(random.nextInt(6)).isEqualTo(3);
        assertThat(random.nextInt(10, 20)).isEqualTo(15);
        assertThat(random.nextDouble()).isEqualTo(0.5);
        assertThat(random.nextLong()).isEqualTo(Long.MIN_VALUE);
        assertThat(random.nextBoolean()).isTrue();
        assertThat(random.nextLong()).isEqualTo(1L << 62);
        assertThat(random.nextInt()).isEqualTo(Integer.MAX_VALUE - 4_294_967);
    }

    @Test
    void methodsBuiltOnTheOthersWork() {
        var random = new FakeRandom().add(0.5).addFractions(0.25);

        assertThat(random.nextDouble(10, 20)).isEqualTo(15);
        assertThat(random.nextDouble(8)).isEqualTo(2);
    }

    @Test
    void facesAreGivenToDice() {
        var random = new FakeRandom().addFaces(6, 1, 4, 4);

        var rolled = DiceExpression.parse("4d6dl1").roll(random);

        assertThat(rolled.value()).isEqualTo(14);
        assertThat(random.isExhausted()).isTrue();
    }

    @Test
    void selectorsAreDriven() {
        var uniform = RandomSelector.uniform(List.of("a", "b", "c"));
        var weighted = RandomSelector.weighted(Maps.map("rare", 1, "common", 3));

        assertThat(uniform.next(new FakeRandom().add(2))).isEqualTo("c");
        assertThat(weighted.next(new FakeRandom().addFractions(0.1))).isEqualTo("rare");
        assertThat(weighted.next(new FakeRandom().addFractions(0.9))).isEqualTo("common");
    }

    @Test
    void aCallWithNoNumberLeftFails() {
        var random = new FakeRandom();

        assertThatIllegalStateException()
                .isThrownBy(() -> random.nextInt(6))
                .withMessage("nextInt(6) was called, but no number is left");
    }

    @Test
    void aCallForAnotherTypeFails() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new FakeRandom().add(0.5).nextInt(6))
                .withMessage("nextInt(6) was called, but the next number is the double 0.5");
        assertThatIllegalStateException()
                .isThrownBy(() -> new FakeRandom().add(3).nextDouble())
                .withMessage("nextDouble() was called, but the next number is the integer 3");
        assertThatIllegalStateException()
                .isThrownBy(() -> new FakeRandom().add(3).nextLong());
        assertThatIllegalStateException()
                .isThrownBy(() -> new FakeRandom().add(3L).nextInt());
        assertThatIllegalStateException()
                .isThrownBy(() -> new FakeRandom().add(3).nextBoolean());
    }

    @Test
    void aValueOutOfBoundsFails() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new FakeRandom().add(6).nextInt(6))
                .withMessage("nextInt(6) was called, but the next number is 6");
        assertThatIllegalStateException()
                .isThrownBy(() -> new FakeRandom().add(9).nextInt(10, 20));
        assertThatIllegalStateException()
                .isThrownBy(() -> new FakeRandom().addFaces(7).nextInt(6));
    }

    @Test
    void invalidScriptsAreRejected() {
        var random = new FakeRandom();

        assertThatIllegalArgumentException().isThrownBy(() -> random.add(1.0));
        assertThatIllegalArgumentException().isThrownBy(() -> random.add(-0.1));
        assertThatIllegalArgumentException().isThrownBy(() -> random.addFractions(1));
        assertThatIllegalArgumentException().isThrownBy(() -> random.addFaces(0));
        assertThatIllegalArgumentException().isThrownBy(() -> random.nextInt(0));
        assertThatIllegalArgumentException().isThrownBy(() -> random.nextInt(5, 5));
    }
}
