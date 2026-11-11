package be.imgn.gaia.benchmarks.fast;

import static be.imgn.gaia.benchmarks.fast.Keys.BATCH;

import be.imgn.gaia.fast.collect.IntIntMap;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
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
 * The operations of {@link IntIntMap}. Batched benchmarks report the time of one operation; the fill benchmarks report
 * the time to fill a whole map.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class IntIntMapBenchmark {

    /** The number of entries. */
    @Param({"1000", "1000000"})
    public int size;

    /** How the keys are picked. */
    @Param({"RANDOM", "SEQUENTIAL"})
    public Distribution keys = Distribution.RANDOM;

    private Keys data = Keys.create(0, Distribution.RANDOM);
    private IntIntMap map = new IntIntMap();
    private int offset;
    private long total;

    /** Fills the map with the keys. */
    @Setup
    public void setUp() {
        data = Keys.create(size, keys);
        map = new IntIntMap();
        for (var key : data.present()) {
            map.put(key, key);
        }
    }

    private int nextBatch() {
        var from = offset;
        offset = (from + BATCH) % data.hits().length;
        return from;
    }

    /**
     * Gets the values of present keys.
     *
     * @return their sum
     */
    @Benchmark
    @OperationsPerInvocation(BATCH)
    public int getHit() {
        var map = this.map;
        var hits = data.hits();
        var from = nextBatch();
        var sum = 0;
        for (var i = from; i < from + BATCH; i++) {
            sum += map.get(hits[i]);
        }
        return sum;
    }

    /**
     * Gets the values of absent keys.
     *
     * @return their sum
     */
    @Benchmark
    @OperationsPerInvocation(BATCH)
    public int getMiss() {
        var map = this.map;
        var misses = data.misses();
        var from = nextBatch();
        var sum = 0;
        for (var i = from; i < from + BATCH; i++) {
            sum += map.get(misses[i]);
        }
        return sum;
    }

    /**
     * Checks present keys.
     *
     * @return how many are present
     */
    @Benchmark
    @OperationsPerInvocation(BATCH)
    public int containsKeyHit() {
        var map = this.map;
        var hits = data.hits();
        var from = nextBatch();
        var count = 0;
        for (var i = from; i < from + BATCH; i++) {
            if (map.containsKey(hits[i])) {
                count++;
            }
        }
        return count;
    }

    /**
     * Replaces the values of present keys.
     *
     * @return the sum of the old values
     */
    @Benchmark
    @OperationsPerInvocation(BATCH)
    public int putUpdate() {
        var map = this.map;
        var hits = data.hits();
        var from = nextBatch();
        var sum = 0;
        for (var i = from; i < from + BATCH; i++) {
            sum += map.put(hits[i], i + 1);
        }
        return sum;
    }

    /**
     * Removes present keys and puts them back: one operation is a removal and a put.
     *
     * @return the sum of the removed values
     */
    @Benchmark
    @OperationsPerInvocation(BATCH)
    public int removeAndPut() {
        var map = this.map;
        var hits = data.hits();
        var from = nextBatch();
        var sum = 0;
        for (var i = from; i < from + BATCH; i++) {
            var key = hits[i];
            sum += map.remove(key);
            map.put(key, key);
        }
        return sum;
    }

    /**
     * Fills a map created with room for all the keys.
     *
     * @return the map
     */
    @Benchmark
    public IntIntMap fillPresized() {
        var map = new IntIntMap(size);
        for (var key : data.present()) {
            map.put(key, key);
        }
        return map;
    }

    /**
     * Fills a map created with the default size, which grows.
     *
     * @return the map
     */
    @Benchmark
    public IntIntMap fillGrowing() {
        var map = new IntIntMap();
        for (var key : data.present()) {
            map.put(key, key);
        }
        return map;
    }

    /**
     * Walks the whole map with its fastest idiom.
     *
     * @return the sum of the keys and values
     */
    @Benchmark
    public long iterate() {
        var sum = 0L;
        for (var cursor = map.cursor(); cursor.next(); ) {
            sum += cursor.key() + cursor.value();
        }
        return sum;
    }

    /**
     * Walks the whole map with a callback.
     *
     * @return the sum of the keys and values
     */
    @Benchmark
    public long forEach() {
        total = 0;
        map.forEach((key, value) -> total += key + value);
        return total;
    }

    /**
     * Removes the entries with an even key, about half of them, from a full map.
     *
     * @param doomed the full map
     * @return whether any entry was removed
     */
    @Benchmark
    public boolean removeIf(Doomed doomed) {
        return doomed.map.removeIf((key, value) -> (key & 1) == 0);
    }

    /** A map filled again before each call of {@link #removeIf}. */
    @State(Scope.Thread)
    public static class Doomed {

        private IntIntMap map = new IntIntMap();

        /**
         * Fills the map.
         *
         * @param benchmark the benchmark, which holds the keys
         */
        @Setup(Level.Invocation)
        public void fill(IntIntMapBenchmark benchmark) {
            var map = new IntIntMap(benchmark.size);
            for (var key : benchmark.data.present()) {
                map.put(key, key);
            }
            this.map = map;
        }
    }
}
