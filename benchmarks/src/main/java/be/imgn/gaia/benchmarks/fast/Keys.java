package be.imgn.gaia.benchmarks.fast;

import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;

/** The keys a benchmark works on: the same, for a size and a distribution, whichever map it measures. */
final class Keys {

    /** The number of operations each invocation of a batched benchmark performs. */
    static final int BATCH = 1024;

    private static final long SEED = 42;

    private final int[] present;
    private final int[] hits;
    private final int[] misses;

    private Keys(int[] present, int[] hits, int[] misses) {
        this.present = present;
        this.hits = hits;
        this.misses = misses;
    }

    /** The keys in the map, none of them 0. */
    int[] present() {
        return present;
    }

    /** The present keys shuffled, repeated to a multiple of {@link #BATCH}: the order lookups go in. */
    int[] hits() {
        return hits;
    }

    /** Keys not in the map, as many as {@link #hits()}. */
    int[] misses() {
        return misses;
    }

    static Keys create(int size, Distribution distribution) {
        var random = new SplittableRandom(SEED);
        var present = new int[size];
        var absent = new int[size];
        if (distribution == Distribution.SEQUENTIAL) {
            for (var i = 0; i < size; i++) {
                present[i] = i + 1;
                absent[i] = size + i + 1;
            }
        } else {
            var seen = new HashSet<Integer>();
            fillDistinct(random, seen, present);
            fillDistinct(random, seen, absent);
        }
        var length = (size + BATCH - 1) / BATCH * BATCH;
        return new Keys(present, cycle(random, present, length), cycle(random, absent, length));
    }

    private static void fillDistinct(SplittableRandom random, Set<Integer> seen, int[] keys) {
        var count = 0;
        while (count < keys.length) {
            var key = random.nextInt();
            if (key != 0 && seen.add(key)) {
                keys[count++] = key;
            }
        }
    }

    private static int[] cycle(SplittableRandom random, int[] keys, int length) {
        var shuffled = keys.clone();
        for (var i = shuffled.length - 1; i > 0; i--) {
            var j = random.nextInt(i + 1);
            var swap = shuffled[i];
            shuffled[i] = shuffled[j];
            shuffled[j] = swap;
        }
        var cycled = new int[length];
        for (var i = 0; i < length; i++) {
            cycled[i] = shuffled[i % shuffled.length];
        }
        return cycled;
    }
}
