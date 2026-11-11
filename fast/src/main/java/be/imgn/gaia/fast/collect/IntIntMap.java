package be.imgn.gaia.fast.collect;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static be.imgn.gaia.core.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * A hash map from {@code int} keys to {@code int} values, which stores both without boxing them.
 *
 * <p>Every key is allowed, {@code 0} included. Where {@link java.util.Map} returns {@code null}, this map returns its
 * <em>missing value</em>, set when it is created: {@link #get} for a key it does not contain, and {@link #put} and
 * {@link #remove} when the key had no value before. The missing value is {@code 0} unless the map is created with
 * another one, and it can be stored as any other value: {@link #containsKey} tells the two apart.
 *
 * <p>Its entries are walked with a {@link Cursor} rather than an iterator, which would box them:
 *
 * <pre>{@code
 * for (var cursor = map.cursor(); cursor.next(); ) {
 *     if (cursor.value() < 0) {
 *         cursor.remove();
 *     }
 * }
 * }</pre>
 *
 * <p>Each entry is kept in one {@code long} of an array, its key and its value together, at a slot that a
 * multiplicative hash of the key picks; a key that finds its slot taken goes to the next free one. The map grows,
 * doubling its capacity, when its entries would fill more than its load factor of the slots, {@code 0.5} unless it is
 * created with another one. A lower load factor makes lookups faster and takes more memory. The map never shrinks, and
 * holds at most {@code 2^30} times its load factor entries.
 *
 * <p>The order of the entries is unspecified, and changes when the map grows. The map is not thread-safe. While a
 * cursor walks it, or while {@link #forEach} or {@link #removeIf} runs, changing the map other than through the cursor
 * or {@code removeIf} has unspecified results: there is no check for it.
 *
 * @since 0.2.0
 */
public final class IntIntMap {

    private static final int DEFAULT_EXPECTED_SIZE = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.5f;
    // The largest power of two that an array can hold.
    private static final int MAX_CAPACITY = 1 << 30;
    // 2^32 divided by the golden ratio. Multiplying by it mixes every bit of the key into the high bits, which pick
    // the slot (Fibonacci hashing): keys in a sequence or with a common stride spread over the whole table.
    private static final int PHI = 0x9E3779B9;
    // A free slot: no key, and no value.
    private static final long FREE = 0;

    private final int missingValue;
    private final float loadFactor;

    // Each entry with its key in the low 32 bits and its value in the high 32 bits, so that one read gets both. An
    // entry is never FREE, since the key 0 is kept apart. At least one slot is always free: lookups and cursors rely on
    // it.
    private long[] table;
    // Shifting a hashed key right by it leaves the slot number.
    private int shift;
    // The table grows before a new entry when it holds at least that many. A tiny load factor can leave it below
    // size after growing: it then grows again on the next new entry.
    private int threshold;
    // The number of entries in the table, without the key 0.
    private int size;
    private boolean hasZeroKey;
    private int zeroValue;

    /** Creates a map with room for 16 entries, the missing value {@code 0} and the load factor {@code 0.5}. */
    public IntIntMap() {
        this(DEFAULT_EXPECTED_SIZE, 0, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a map with room for a number of entries, the missing value {@code 0} and the load factor {@code 0.5}.
     *
     * @param expectedSize the number of entries the map holds before it grows
     * @throws IllegalArgumentException if {@code expectedSize} is negative or larger than the map can ever hold
     */
    public IntIntMap(int expectedSize) {
        this(expectedSize, 0, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a map with room for a number of entries, a missing value and the load factor {@code 0.5}.
     *
     * @param expectedSize the number of entries the map holds before it grows
     * @param missingValue the value returned for keys the map does not contain
     * @throws IllegalArgumentException if {@code expectedSize} is negative or larger than the map can ever hold
     */
    public IntIntMap(int expectedSize, int missingValue) {
        this(expectedSize, missingValue, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a map with room for a number of entries, a missing value and a load factor.
     *
     * @param expectedSize the number of entries the map holds before it grows
     * @param missingValue the value returned for keys the map does not contain
     * @param loadFactor the share of its slots the map fills before it grows
     * @throws IllegalArgumentException if {@code loadFactor} is not greater than {@code 0} and less than {@code 1}, or
     *     if {@code expectedSize} is negative or larger than the map can ever hold with that load factor
     */
    public IntIntMap(int expectedSize, int missingValue, float loadFactor) {
        checkArgument(
                loadFactor > 0 && loadFactor < 1, "loadFactor must be greater than 0 and less than 1: %s", loadFactor);
        this.loadFactor = loadFactor;
        checkArgument(expectedSize >= 0, "expectedSize must not be negative: %s", expectedSize);
        checkArgument(
                expectedSize <= thresholdFor(MAX_CAPACITY),
                "expectedSize must be at most %s with the load factor %s: %s",
                thresholdFor(MAX_CAPACITY),
                loadFactor,
                expectedSize);
        this.missingValue = missingValue;
        var capacity = 2;
        while (thresholdFor(capacity) < expectedSize) {
            capacity <<= 1;
        }
        this.table = allocate(capacity);
    }

    /**
     * Returns the value returned for keys the map does not contain.
     *
     * @return the missing value
     */
    public int missingValue() {
        return missingValue;
    }

    /**
     * Returns the number of entries.
     *
     * @return the number of entries
     */
    public int size() {
        return hasZeroKey ? size + 1 : size;
    }

    /**
     * Returns whether the map has no entries.
     *
     * @return whether the map is empty
     */
    public boolean isEmpty() {
        return size == 0 && !hasZeroKey;
    }

    /**
     * Returns whether the map has an entry for a key.
     *
     * @param key the key
     * @return whether the map contains the key
     */
    public boolean containsKey(int key) {
        if (key == 0) {
            return hasZeroKey;
        }
        var table = this.table;
        var mask = table.length - 1;
        for (var index = slotOf(key); ; index = (index + 1) & mask) {
            var entry = table[index];
            if (keyOf(entry) == key) {
                return true;
            }
            if (entry == FREE) {
                return false;
            }
        }
    }

    /**
     * Returns the value of a key.
     *
     * @param key the key
     * @return the value of the key, or the {@linkplain #missingValue() missing value} if the map does not contain it
     */
    public int get(int key) {
        if (key == 0) {
            return hasZeroKey ? zeroValue : missingValue;
        }
        var table = this.table;
        var mask = table.length - 1;
        for (var index = slotOf(key); ; index = (index + 1) & mask) {
            var entry = table[index];
            if (keyOf(entry) == key) {
                return valueOf(entry);
            }
            if (entry == FREE) {
                return missingValue;
            }
        }
    }

    /**
     * Sets the value of a key.
     *
     * @param key the key
     * @param value its new value
     * @return the value the key had, or the {@linkplain #missingValue() missing value} if the map did not contain it
     * @throws IllegalStateException if the map does not contain the key and is full: it holds at most {@code 2^30}
     *     times its load factor entries
     */
    public int put(int key, int value) {
        if (key == 0) {
            var previous = hasZeroKey ? zeroValue : missingValue;
            hasZeroKey = true;
            zeroValue = value;
            return previous;
        }
        var table = this.table;
        var mask = table.length - 1;
        var index = slotOf(key);
        for (; ; index = (index + 1) & mask) {
            var entry = table[index];
            if (keyOf(entry) == key) {
                table[index] = entryOf(key, value);
                return valueOf(entry);
            }
            if (entry == FREE) {
                break;
            }
        }
        if (size >= threshold) {
            grow();
            insertAbsent(this.table, entryOf(key, value));
        } else {
            table[index] = entryOf(key, value);
        }
        size++;
        return missingValue;
    }

    /**
     * Removes the entry of a key.
     *
     * @param key the key
     * @return the value the key had, or the {@linkplain #missingValue() missing value} if the map did not contain it
     */
    public int remove(int key) {
        if (key == 0) {
            if (!hasZeroKey) {
                return missingValue;
            }
            hasZeroKey = false;
            return zeroValue;
        }
        var table = this.table;
        var mask = table.length - 1;
        for (var index = slotOf(key); ; index = (index + 1) & mask) {
            var entry = table[index];
            if (keyOf(entry) == key) {
                removeAt(index);
                return valueOf(entry);
            }
            if (entry == FREE) {
                return missingValue;
            }
        }
    }

    /** Removes every entry. The map keeps its capacity. */
    public void clear() {
        if (size > 0) {
            Arrays.fill(table, FREE);
            size = 0;
        }
        hasZeroKey = false;
    }

    /**
     * Returns a cursor over the entries, positioned before the first one.
     *
     * @return a new cursor
     */
    public Cursor cursor() {
        return new Cursor();
    }

    /**
     * Performs an action on each entry.
     *
     * @param action the action, given each key and its value
     */
    public void forEach(IntIntConsumer action) {
        requireNonNull(action, "action");
        if (hasZeroKey) {
            action.accept(0, zeroValue);
        }
        for (var entry : table) {
            if (entry != FREE) {
                action.accept(keyOf(entry), valueOf(entry));
            }
        }
    }

    /**
     * Removes the entries that meet a condition.
     *
     * @param filter the condition, given each key and its value
     * @return whether any entry was removed
     */
    public boolean removeIf(IntIntPredicate filter) {
        requireNonNull(filter, "filter");
        var removed = false;
        if (hasZeroKey && filter.test(0, zeroValue)) {
            hasZeroKey = false;
            removed = true;
        }
        // One pass, from the slot after a free one to that free slot, as a cursor walks: an entry is either removed,
        // or moved back to the first free slot on its probe, as if it were put again. Entries only move back to slots
        // already passed, and only in a run of entries where this call freed a slot.
        var table = this.table;
        var mask = table.length - 1;
        var end = freeIndex(table) + 1;
        var holes = false;
        var count = 0;
        for (var position = end - table.length; position < end; position++) {
            var index = position & mask;
            var entry = table[index];
            if (entry == FREE) {
                // A slot that was already free: the run of entries ends.
                holes = false;
            } else if (filter.test(keyOf(entry), valueOf(entry))) {
                table[index] = FREE;
                holes = true;
                count++;
            } else if (holes) {
                var target = slotOf(keyOf(entry));
                while (target != index && table[target] != FREE) {
                    target = (target + 1) & mask;
                }
                if (target != index) {
                    table[target] = entry;
                    table[index] = FREE;
                }
            }
        }
        size -= count;
        return removed || count > 0;
    }

    /**
     * Returns whether an object is an {@code IntIntMap} with the same entries. Neither the missing value nor the load
     * factor are compared.
     *
     * @param obj the object
     * @return whether it is equal to this map
     */
    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof IntIntMap other)
                || other.size != size
                || other.hasZeroKey != hasZeroKey
                || (hasZeroKey && other.zeroValue != zeroValue)) {
            return false;
        }
        for (var entry : table) {
            if (entry != FREE) {
                var key = keyOf(entry);
                if (!other.containsKey(key) || other.get(key) != valueOf(entry)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Returns the sum, over the entries, of each key XOR its value: the hash code that a {@link java.util.Map} from
     * {@code Integer} to {@code Integer} with the same entries has.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        var hashCode = hasZeroKey ? zeroValue : 0;
        for (var entry : table) {
            hashCode += keyOf(entry) ^ valueOf(entry);
        }
        return hashCode;
    }

    /**
     * Returns the entries, as {@link java.util.AbstractMap#toString()} does: {@code {1=10, 2=20}}.
     *
     * @return the entries
     */
    @Override
    public String toString() {
        var builder = new StringBuilder("{");
        forEach((key, value) -> {
            if (builder.length() > 1) {
                builder.append(", ");
            }
            builder.append(key).append('=').append(value);
        });
        return builder.append('}').toString();
    }

    private static long entryOf(int key, int value) {
        return (long) value << Integer.SIZE | Integer.toUnsignedLong(key);
    }

    private static int keyOf(long entry) {
        return (int) entry;
    }

    private static int valueOf(long entry) {
        return (int) (entry >>> Integer.SIZE);
    }

    // Sets the capacity, in slots, and returns the new table.
    private long[] allocate(int capacity) {
        shift = Integer.SIZE - Integer.numberOfTrailingZeros(capacity);
        threshold = thresholdFor(capacity);
        return new long[capacity];
    }

    private int thresholdFor(int capacity) {
        // At least one entry, and at least one free slot.
        return Math.min(capacity - 1, Math.max(1, (int) (capacity * (double) loadFactor)));
    }

    // The slot where the probe for a key starts: the high bits of its hash.
    private int slotOf(int key) {
        return (key * PHI) >>> shift;
    }

    // The index of the first free slot.
    private static int freeIndex(long[] table) {
        var index = 0;
        while (table[index] != FREE) {
            index++;
        }
        return index;
    }

    private void insertAbsent(long[] table, long entry) {
        var mask = table.length - 1;
        var index = slotOf(keyOf(entry));
        while (table[index] != FREE) {
            index = (index + 1) & mask;
        }
        table[index] = entry;
    }

    private void grow() {
        var capacity = table.length;
        if (capacity == MAX_CAPACITY) {
            throw new IllegalStateException("The map is full: " + size() + " entries");
        }
        var old = table;
        var grown = allocate(capacity * 2);
        for (var entry : old) {
            if (entry != FREE) {
                insertAbsent(grown, entry);
            }
        }
        table = grown;
    }

    // Removes the entry at an index, then moves back the entries after it that their probe would no longer reach,
    // rather than leaving a tombstone: lookups never get slower, however many entries come and go.
    private void removeAt(int index) {
        var table = this.table;
        var mask = table.length - 1;
        var free = index;
        var next = (index + 1) & mask;
        long entry;
        while ((entry = table[next]) != FREE) {
            // The entry can take the free slot if that slot is on its probe: from its first slot to where it is.
            if (((next - slotOf(keyOf(entry))) & mask) >= ((next - free) & mask)) {
                table[free] = entry;
                free = next;
            }
            next = (next + 1) & mask;
        }
        table[free] = FREE;
        size--;
    }

    /**
     * A position among the entries of an {@link IntIntMap}, which it walks without boxing them. A cursor starts before
     * the first entry; each call to {@link #next()} moves it to the next one, until it returns {@code false}.
     *
     * <pre>{@code
     * for (var cursor = map.cursor(); cursor.next(); ) {
     *     total += cursor.value();
     * }
     * }</pre>
     *
     * <p>The cursor can change the value of its entry, and remove it: it then still visits every other entry exactly
     * once. Changing the map otherwise while it walks it has unspecified results.
     *
     * @since 0.2.0
     */
    public final class Cursor {

        private static final int NO_ENTRY = -2;
        private static final int ZERO_KEY = -1;

        private final long[] table;
        private final int mask;
        // Positions count up to the slot after a free one, from one table length before it: a position is at the
        // index position & mask, negative positions included. They stay in range at any capacity. The cursor visits
        // the key 0 last.
        private int position;
        private final int end;
        private boolean zeroKeyPending = hasZeroKey;
        // The index of the current entry, or one of the constants above.
        private int current = NO_ENTRY;
        // The current entry, read once by next().
        private long entry;

        private Cursor() {
            table = IntIntMap.this.table;
            mask = table.length - 1;
            // Walking from the slot after a free one to that free slot, the cursor never meets an entry whose probe
            // started before it: removing an entry then only moves entries it has not visited, and only to the
            // current slot, which it looks at again, or to slots it has not reached.
            end = freeIndex(table) + 1;
            position = end - table.length;
        }

        /**
         * Moves to the next entry.
         *
         * @return whether there is one; if not, the cursor stays past the last entry
         */
        public boolean next() {
            var table = this.table;
            var mask = this.mask;
            var end = this.end;
            for (var at = position; at < end; at++) {
                var found = table[at & mask];
                if (found != FREE) {
                    position = at + 1;
                    current = at & mask;
                    entry = found;
                    return true;
                }
            }
            position = end;
            if (zeroKeyPending) {
                zeroKeyPending = false;
                current = ZERO_KEY;
                entry = entryOf(0, zeroValue);
                return true;
            }
            current = NO_ENTRY;
            return false;
        }

        /**
         * Returns the key of the current entry.
         *
         * @return the key
         * @throws IllegalStateException if the cursor is not on an entry: {@link #next()} was not called, returned
         *     {@code false}, or {@link #remove()} was called since
         */
        public int key() {
            checkOnEntry();
            return keyOf(entry);
        }

        /**
         * Returns the value of the current entry.
         *
         * @return the value
         * @throws IllegalStateException if the cursor is not on an entry: {@link #next()} was not called, returned
         *     {@code false}, or {@link #remove()} was called since
         */
        public int value() {
            checkOnEntry();
            return valueOf(entry);
        }

        /**
         * Sets the value of the current entry.
         *
         * @param value its new value
         * @throws IllegalStateException if the cursor is not on an entry: {@link #next()} was not called, returned
         *     {@code false}, or {@link #remove()} was called since
         */
        public void setValue(int value) {
            checkOnEntry();
            entry = entryOf(keyOf(entry), value);
            if (current == ZERO_KEY) {
                zeroValue = value;
            } else {
                table[current] = entry;
            }
        }

        /**
         * Removes the current entry from the map. The cursor is then on no entry, until {@link #next()} moves it to the
         * entry after the removed one.
         *
         * @throws IllegalStateException if the cursor is not on an entry: {@link #next()} was not called, returned
         *     {@code false}, or {@link #remove()} was called since
         */
        public void remove() {
            checkOnEntry();
            if (current == ZERO_KEY) {
                hasZeroKey = false;
            } else {
                removeAt(current);
                if (table[current] != FREE) {
                    // An entry moved back to the current slot: look at it next.
                    position--;
                }
            }
            current = NO_ENTRY;
        }

        private void checkOnEntry() {
            checkState(current != NO_ENTRY, "The cursor is not on an entry");
        }
    }
}
