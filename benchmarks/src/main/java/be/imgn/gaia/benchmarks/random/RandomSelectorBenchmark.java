package be.imgn.gaia.benchmarks.random;

import be.imgn.gaia.core.random.RandomSelector;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * The selections of {@link RandomSelector}, with a {@link SplittableRandom}. Each benchmark reports the time of one
 * selection.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class RandomSelectorBenchmark {

    private static final int BATCH = 1024;
    private static final double MAX_WEIGHT = 1_000;

    /** The number of elements: a few, a table that fits in the caches of the processor, and one that doesn't. */
    @Param({"4", "1000", "1000000"})
    public int size;

    private RandomGenerator random = new SplittableRandom(1);
    private RandomSelector<Integer> uniform = RandomSelector.constant(0);
    private RandomSelector<Integer> weighted = RandomSelector.constant(0);

    /** Creates the selectors, with weights spread over three orders of magnitude. */
    @Setup
    public void setUp() {
        random = new SplittableRandom(1);
        var elements = IntStream.range(0, size).boxed().toList();
        var weights = new SplittableRandom(2).doubles(size, 1, MAX_WEIGHT).toArray();
        uniform = RandomSelector.uniform(elements);
        weighted = RandomSelector.weighted(elements, element -> weights[element]);
    }

    /**
     * Selects elements that all have the same chance.
     *
     * @return their sum
     */
    @Benchmark
    @OperationsPerInvocation(BATCH)
    public long uniform() {
        return sum(uniform);
    }

    /**
     * Selects elements by weight.
     *
     * @return their sum
     */
    @Benchmark
    @OperationsPerInvocation(BATCH)
    public long weighted() {
        return sum(weighted);
    }

    private long sum(RandomSelector<Integer> selector) {
        var random = this.random;
        long sum = 0;
        for (int i = 0; i < BATCH; i++) {
            sum += selector.next(random);
        }
        return sum;
    }
}
