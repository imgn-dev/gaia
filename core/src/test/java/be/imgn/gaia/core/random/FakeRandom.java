package be.imgn.gaia.core.random;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.random.RandomGenerator;

/** A source of randomness whose dice show the faces it was given, in order. */
final class FakeRandom implements RandomGenerator {

    private final Deque<Integer> faces = new ArrayDeque<>();

    FakeRandom(int... faces) {
        for (int face : faces) {
            this.faces.add(face);
        }
    }

    @Override
    public int nextInt(int bound) {
        int face = faces.remove();
        assertThat(face).isBetween(1, bound);
        return face - 1;
    }

    @Override
    public long nextLong() {
        throw new UnsupportedOperationException();
    }

    boolean isExhausted() {
        return faces.isEmpty();
    }
}
