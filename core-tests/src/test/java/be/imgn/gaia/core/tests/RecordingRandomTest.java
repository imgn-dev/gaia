package be.imgn.gaia.core.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import be.imgn.gaia.core.collect.Maps;
import be.imgn.gaia.core.random.RandomSelector;
import be.imgn.gaia.core.random.RandomTable;
import be.imgn.gaia.core.random.dice.DiceExpression;
import be.imgn.gaia.core.testing.FakeRandom;
import be.imgn.gaia.core.testing.RecordingRandom;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class RecordingRandomTest {

    @Test
    void itGivesTheNumbersOfTheWrappedGenerator() {
        var recording = new RecordingRandom(new SplittableRandom(1));
        var plain = new SplittableRandom(1);

        assertThat(recording.nextInt(6)).isEqualTo(plain.nextInt(6));
        assertThat(recording.nextLong()).isEqualTo(plain.nextLong());
        assertThat(recording.nextDouble()).isEqualTo(plain.nextDouble());
        assertThat(recording.nextBoolean()).isEqualTo(plain.nextBoolean());
        assertThat(recording.nextInt(10, 20)).isEqualTo(plain.nextInt(10, 20));
        assertThat(recording.size()).isEqualTo(5);
    }

    @Test
    void aRollIsPlayedAgain() {
        var expression = DiceExpression.parse("(4d6dl1:high>=15)+2d20kh1");
        var recording = new RecordingRandom(new SplittableRandom(2));

        var rolled = expression.roll(recording);
        var replay = recording.replay();

        assertThat(expression.roll(replay)).isEqualTo(rolled);
        assertThat(replay.isExhausted()).isTrue();
        assertThat(recording.size()).isEqualTo(6);
    }

    @Test
    void aDrawIsPlayedAgain() {
        var table = RandomTable.<String>builder()
                .add(3, "gold", DiceExpression.parse("2d6").selector())
                .add(1, "gem", random -> random.nextBoolean() ? 1 : 2)
                .add(2, "scroll", random -> (int) (random.nextDouble() * 3))
                .draws(4)
                .build();
        var selector = RandomSelector.weighted(Maps.map("a", 1, "b", 3));
        var recording = new RecordingRandom(new SplittableRandom(3));

        var drawn = table.draw(recording);
        var selected = selector.next(recording);
        var replay = recording.replay();

        assertThat(table.draw(replay)).isEqualTo(drawn);
        assertThat(selector.next(replay)).isEqualTo(selected);
        assertThat(replay.isExhausted()).isTrue();
    }

    @Test
    void methodsBuiltOnTheOthersArePlayedAgain() {
        var recording = new RecordingRandom(new SplittableRandom(4));

        double bounded = recording.nextDouble(10, 20);
        float fraction = recording.nextFloat();
        double gaussian = recording.nextGaussian();
        var replay = recording.replay();

        assertThat(replay.nextDouble(10, 20)).isEqualTo(bounded);
        assertThat(replay.nextFloat()).isEqualTo(fraction);
        assertThat(replay.nextGaussian()).isEqualTo(gaussian);
        assertThat(replay.isExhausted()).isTrue();
    }

    @Test
    void toStringIsTheScriptOfAFakeRandom() {
        var recording = new RecordingRandom(
                new FakeRandom().add(5, 0).add(0.25).add(-3L).add(true, false).add(7));

        recording.nextInt(6);
        recording.nextInt(6);
        recording.nextDouble();
        recording.nextLong();
        recording.nextBoolean();
        recording.nextBoolean();
        recording.nextInt();

        assertThat(recording).hasToString("new FakeRandom().add(5, 0).add(0.25).add(-3L).add(true, false).add(7)");
        assertThat(new RecordingRandom(new SplittableRandom(5))).hasToString("new FakeRandom()");
    }

    @Test
    @SuppressWarnings("NullAway")
    void aNullGeneratorIsRejected() {
        assertThatNullPointerException().isThrownBy(() -> new RecordingRandom(null));
    }
}
