package be.imgn.gaia.fast.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IntIntMapTest {

    // The entries a cursor visits, failing if it visits a key twice.
    static Map<Integer, Integer> entries(IntIntMap map) {
        var entries = new LinkedHashMap<Integer, Integer>();
        for (var cursor = map.cursor(); cursor.next(); ) {
            assertThat(entries.put(cursor.key(), cursor.value()))
                    .as("key %s visited twice", cursor.key())
                    .isNull();
        }
        return entries;
    }

    @Test
    void startsEmpty() {
        var map = new IntIntMap();

        assertThat(map.size()).isZero();
        assertThat(map.isEmpty()).isTrue();
        assertThat(map.missingValue()).isZero();
        assertThat(map.containsKey(0)).isFalse();
        assertThat(map.containsKey(1)).isFalse();
        assertThat(map.get(1)).isZero();
        assertThat(entries(map)).isEmpty();
    }

    @Test
    void putsAndGetsValues() {
        var map = new IntIntMap();

        assertThat(map.put(1, 10)).isZero();
        assertThat(map.put(-7, 70)).isZero();
        assertThat(map.put(Integer.MIN_VALUE, 1)).isZero();
        assertThat(map.put(Integer.MAX_VALUE, 2)).isZero();

        assertThat(map.size()).isEqualTo(4);
        assertThat(map.isEmpty()).isFalse();
        assertThat(map.get(1)).isEqualTo(10);
        assertThat(map.get(-7)).isEqualTo(70);
        assertThat(map.get(Integer.MIN_VALUE)).isEqualTo(1);
        assertThat(map.get(Integer.MAX_VALUE)).isEqualTo(2);
        assertThat(map.containsKey(-7)).isTrue();
        assertThat(map.containsKey(7)).isFalse();
    }

    @Test
    void replacesAValueAndReturnsTheOldOne() {
        var map = new IntIntMap();
        map.put(5, 50);

        assertThat(map.put(5, 51)).isEqualTo(50);

        assertThat(map.get(5)).isEqualTo(51);
        assertThat(map.size()).isEqualTo(1);
    }

    @Test
    void removesAValueAndReturnsIt() {
        var map = new IntIntMap();
        map.put(5, 50);
        map.put(6, 60);

        assertThat(map.remove(5)).isEqualTo(50);
        assertThat(map.remove(5)).isZero();

        assertThat(map.containsKey(5)).isFalse();
        assertThat(map.get(6)).isEqualTo(60);
        assertThat(map.size()).isEqualTo(1);
    }

    @Test
    void holdsTheKeyZero() {
        var map = new IntIntMap();

        assertThat(map.put(0, 42)).isZero();
        assertThat(map.containsKey(0)).isTrue();
        assertThat(map.get(0)).isEqualTo(42);
        assertThat(map.size()).isEqualTo(1);
        assertThat(map.put(0, 43)).isEqualTo(42);
        assertThat(entries(map)).containsExactly(Map.entry(0, 43));

        assertThat(map.remove(0)).isEqualTo(43);
        assertThat(map.remove(0)).isZero();
        assertThat(map.containsKey(0)).isFalse();
        assertThat(map.isEmpty()).isTrue();
    }

    @Test
    void returnsItsMissingValueForAbsentKeys() {
        var map = new IntIntMap(4, -1);

        assertThat(map.missingValue()).isEqualTo(-1);
        assertThat(map.get(3)).isEqualTo(-1);
        assertThat(map.get(0)).isEqualTo(-1);
        assertThat(map.put(3, 30)).isEqualTo(-1);
        assertThat(map.put(0, 0)).isEqualTo(-1);
        assertThat(map.remove(4)).isEqualTo(-1);
        assertThat(map.remove(0)).isZero();
    }

    @Test
    void storesItsMissingValueAsAnyOtherValue() {
        var map = new IntIntMap();

        map.put(3, 0);

        assertThat(map.containsKey(3)).isTrue();
        assertThat(map.get(3)).isZero();
        assertThat(map.size()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(floats = {0.01f, 0.5f, 0.75f, 0.99f})
    void growsPastItsExpectedSize(float loadFactor) {
        var map = new IntIntMap(0, -1, loadFactor);

        for (var key = -5_000; key < 5_000; key++) {
            map.put(key, key * 3);
        }

        assertThat(map.size()).isEqualTo(10_000);
        for (var key = -5_000; key < 5_000; key++) {
            assertThat(map.get(key)).isEqualTo(key * 3);
        }
        assertThat(map.get(5_000)).isEqualTo(-1);
    }

    @Test
    void spreadsKeysWithACommonStride() {
        var map = new IntIntMap();

        for (var i = 0; i < 1_000; i++) {
            map.put(i << 16, i);
        }

        assertThat(map.size()).isEqualTo(1_000);
        for (var i = 0; i < 1_000; i++) {
            assertThat(map.get(i << 16)).isEqualTo(i);
        }
    }

    @Test
    void clearsItsEntries() {
        var map = new IntIntMap();
        map.put(0, 1);
        map.put(1, 2);

        map.clear();

        assertThat(map.isEmpty()).isTrue();
        assertThat(map.containsKey(0)).isFalse();
        assertThat(map.containsKey(1)).isFalse();
        assertThat(entries(map)).isEmpty();
        map.put(1, 3);
        assertThat(map.get(1)).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(floats = {0.5f, 0.99f})
    void behavesAsAHashMap(float loadFactor) {
        // Few distinct keys and a high load factor make long runs of taken slots, wrapping around the table end.
        var random = new Random(42);
        var map = new IntIntMap(0, Integer.MIN_VALUE, loadFactor);
        var expected = new HashMap<Integer, Integer>();

        for (var step = 0; step < 200_000; step++) {
            var key = random.nextInt(10) == 0 ? random.nextInt() : random.nextInt(200) - 100;
            switch (random.nextInt(3)) {
                case 0 -> {
                    var value = random.nextInt();
                    assertThat(map.put(key, value)).isEqualTo(orMissing(expected.put(key, value)));
                }
                case 1 -> assertThat(map.remove(key)).isEqualTo(orMissing(expected.remove(key)));
                default -> {
                    assertThat(map.get(key)).isEqualTo(orMissing(expected.get(key)));
                    assertThat(map.containsKey(key)).isEqualTo(expected.containsKey(key));
                }
            }
            assertThat(map.size()).isEqualTo(expected.size());
            if (step % 1_000 == 0) {
                assertThat(entries(map)).isEqualTo(expected);
            }
        }
        assertThat(entries(map)).isEqualTo(expected);
    }

    private static int orMissing(@Nullable Integer value) {
        return value == null ? Integer.MIN_VALUE : value;
    }

    @Test
    void comparesItsEntries() {
        var map = new IntIntMap(4, 1);
        map.put(0, 5);
        map.put(1, 10);
        map.put(-2, 20);
        var same = new IntIntMap(1_000, 2, 0.9f);
        same.put(-2, 20);
        same.put(1, 10);
        same.put(0, 5);
        var otherValue = new IntIntMap();
        otherValue.put(0, 5);
        otherValue.put(1, 11);
        otherValue.put(-2, 20);
        var otherZeroValue = new IntIntMap();
        otherZeroValue.put(0, 6);
        otherZeroValue.put(1, 10);
        otherZeroValue.put(-2, 20);
        var otherKey = new IntIntMap();
        otherKey.put(0, 5);
        otherKey.put(1, 10);
        otherKey.put(2, 20);
        var withoutZero = new IntIntMap();
        withoutZero.put(3, 5);
        withoutZero.put(1, 10);
        withoutZero.put(-2, 20);

        assertThat(map).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(map)
                .isNotEqualTo(otherValue)
                .isNotEqualTo(otherZeroValue)
                .isNotEqualTo(otherKey)
                .isNotEqualTo(withoutZero)
                .isNotEqualTo(new IntIntMap())
                .isNotEqualTo(Map.of(0, 5, 1, 10, -2, 20));
        assertThat(map.hashCode()).isEqualTo(Map.of(0, 5, 1, 10, -2, 20).hashCode());
    }

    @Test
    void hashesAsAMapOfIntegers() {
        var map = new IntIntMap();
        var expected = new HashMap<Integer, Integer>();
        for (var key = -50; key < 50; key++) {
            map.put(key, key * 7);
            expected.put(key, key * 7);
        }
        for (var key = -50; key < 50; key += 3) {
            map.remove(key);
            expected.remove(key);
        }

        assertThat(map.hashCode()).isEqualTo(expected.hashCode());
    }

    @Test
    void printsItsEntries() {
        var map = new IntIntMap();
        assertThat(map).hasToString("{}");

        map.put(0, 1);
        map.put(7, -8);

        assertThat(map.toString()).isIn("{0=1, 7=-8}", "{7=-8, 0=1}");
    }

    @Test
    void doesEachEntry() {
        var map = new IntIntMap();
        map.put(0, 1);
        map.put(2, 3);
        map.put(-4, 5);
        var entries = new HashMap<Integer, Integer>();

        map.forEach(entries::put);

        assertThat(entries).isEqualTo(Map.of(0, 1, 2, 3, -4, 5));
    }

    @ParameterizedTest
    @ValueSource(floats = {0.5f, 0.99f})
    void removesTheEntriesThatMeetAConditionAsAHashMapDoes(float loadFactor) {
        // Few distinct keys and a high load factor make long runs of taken slots, wrapping around the table end.
        var random = new Random(11);
        for (var round = 0; round < 500; round++) {
            var map = new IntIntMap(0, 0, loadFactor);
            var expected = new HashMap<Integer, Integer>();
            var size = random.nextInt(200);
            for (var i = 0; i < size; i++) {
                var key = random.nextInt(400) - 200;
                var value = random.nextInt(10);
                map.put(key, value);
                expected.put(key, value);
            }
            var threshold = random.nextInt(11);

            var removed = map.removeIf((key, value) -> value < threshold);

            assertThat(removed).isEqualTo(expected.values().stream().anyMatch(value -> value < threshold));
            expected.values().removeIf(value -> value < threshold);
            assertThat(entries(map)).isEqualTo(expected);
            assertThat(map.size()).isEqualTo(expected.size());
            for (var key = -200; key < 200; key++) {
                assertThat(map.get(key)).isEqualTo(expected.getOrDefault(key, 0));
            }
        }
    }

    @Test
    void removesTheEntriesThatMeetACondition() {
        var map = new IntIntMap(0, 0, 0.99f);
        for (var key = -100; key <= 100; key++) {
            map.put(key, key * 2);
        }

        assertThat(map.removeIf((key, value) -> key % 3 == 0 || value > 150)).isTrue();
        assertThat(map.removeIf((key, value) -> key == 300)).isFalse();

        var expected = new HashMap<Integer, Integer>();
        for (var key = -100; key <= 100; key++) {
            if (key % 3 != 0 && key * 2 <= 150) {
                expected.put(key, key * 2);
            }
        }
        assertThat(entries(map)).isEqualTo(expected);
    }

    @Test
    void rejectsANegativeExpectedSize() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IntIntMap(-1))
                .withMessage("expectedSize must not be negative: -1");
    }

    @Test
    void rejectsAnExpectedSizeItCanNeverHold() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IntIntMap((1 << 29) + 1))
                .withMessage("expectedSize must be at most 536870912 with the load factor 0.5: 536870913");
    }

    @ParameterizedTest
    @ValueSource(floats = {0, 1, -0.5f, 1.5f, Float.NaN})
    void rejectsALoadFactorOutsideZeroAndOne(float loadFactor) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IntIntMap(0, 0, loadFactor))
                .withMessage("loadFactor must be greater than 0 and less than 1: %s", loadFactor);
    }

    @Nested
    class CursorTest {

        @Test
        void visitsEveryEntryOnce() {
            var map = new IntIntMap();
            var expected = new HashMap<Integer, Integer>();
            for (var key = -20; key <= 20; key++) {
                map.put(key, -key);
                expected.put(key, -key);
            }

            assertThat(entries(map)).isEqualTo(expected);
        }

        @Test
        void staysPastTheLastEntry() {
            var map = new IntIntMap();
            map.put(1, 2);
            var cursor = map.cursor();

            assertThat(cursor.next()).isTrue();
            assertThat(cursor.next()).isFalse();
            assertThat(cursor.next()).isFalse();
            assertThatIllegalStateException().isThrownBy(cursor::key).withMessage("The cursor is not on an entry");
        }

        @Test
        void setsTheValueOfItsEntry() {
            var map = new IntIntMap();
            map.put(0, 1);
            map.put(2, 3);

            for (var cursor = map.cursor(); cursor.next(); ) {
                cursor.setValue(cursor.value() * 10);
            }

            assertThat(entries(map)).isEqualTo(Map.of(0, 10, 2, 30));
        }

        @Test
        void removesEveryEntry() {
            var map = new IntIntMap(0, 0, 0.99f);
            for (var key = -100; key <= 100; key++) {
                map.put(key, key);
            }
            var visited = 0;

            for (var cursor = map.cursor(); cursor.next(); ) {
                cursor.remove();
                visited++;
            }

            assertThat(visited).isEqualTo(201);
            assertThat(map.isEmpty()).isTrue();
            assertThat(entries(map)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(floats = {0.5f, 0.99f})
        void visitsEveryOtherEntryOnceWhileRemoving(float loadFactor) {
            var random = new Random(7);
            for (var round = 0; round < 500; round++) {
                var map = new IntIntMap(0, 0, loadFactor);
                var expected = new HashMap<Integer, Integer>();
                var size = random.nextInt(100);
                for (var i = 0; i < size; i++) {
                    var key = random.nextInt(300) - 150;
                    map.put(key, i);
                    expected.put(key, i);
                }
                var original = new HashMap<>(expected);
                var visited = new HashMap<Integer, Integer>();

                for (var cursor = map.cursor(); cursor.next(); ) {
                    assertThat(visited.put(cursor.key(), cursor.value())).isNull();
                    if (random.nextBoolean()) {
                        expected.remove(cursor.key());
                        cursor.remove();
                    }
                }

                assertThat(visited).isEqualTo(original);
                assertThat(entries(map)).isEqualTo(expected);
            }
        }

        @Test
        void isOnNoEntryBeforeTheFirst() {
            var map = new IntIntMap();
            map.put(1, 2);
            var cursor = map.cursor();

            assertThatIllegalStateException().isThrownBy(cursor::key);
            assertThatIllegalStateException().isThrownBy(cursor::value);
            assertThatIllegalStateException().isThrownBy(() -> cursor.setValue(3));
            assertThatIllegalStateException().isThrownBy(cursor::remove);
        }

        @Test
        void isOnNoEntryAfterRemovingIt() {
            var map = new IntIntMap();
            map.put(0, 1);
            var cursor = map.cursor();
            cursor.next();

            cursor.remove();

            assertThatIllegalStateException().isThrownBy(cursor::key);
            assertThatIllegalStateException().isThrownBy(cursor::value);
            assertThatIllegalStateException().isThrownBy(() -> cursor.setValue(3));
            assertThatIllegalStateException().isThrownBy(cursor::remove);
            assertThat(map.isEmpty()).isTrue();
        }
    }
}
