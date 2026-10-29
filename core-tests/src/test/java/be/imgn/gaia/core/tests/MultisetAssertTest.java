package be.imgn.gaia.core.tests;

import static be.imgn.gaia.core.testing.MultisetAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import be.imgn.gaia.core.collect.Multiset;
import be.imgn.gaia.core.collect.Multisets;
import be.imgn.gaia.core.collect.TreeMultiset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MultisetAssertTest {

    private static final Multiset<String> WORDS = Multisets.multiset("foo", "bar", "foo", "baz");

    @Test
    void passesWhenTheCountsMatch() {
        assertThat(WORDS)
                .hasCount("foo", 2)
                .hasCount("bar", 1)
                .hasCount("quuz", 0)
                .hasDistinctSize(3)
                .containsExactlyCounts(Map.of("foo", 2, "bar", 1, "baz", 1));
    }

    @Test
    void keepsTheAssertionsOnCollections() {
        assertThat(WORDS)
                .hasSize(4)
                .contains("foo", "baz")
                .containsExactly("foo", "foo", "bar", "baz")
                .filteredOn(word -> word.startsWith("f"))
                .hasCount("foo", 2)
                .hasSize(2);
    }

    @Test
    void keepsTheOrderOfTheElementsItFilters() {
        var sorted = TreeMultiset.<String>create();
        sorted.addAll(List.of("delta", "alpha", "charlie", "bravo", "alpha"));

        assertThat(sorted)
                .filteredOn(word -> !word.equals("charlie"))
                .containsExactly("alpha", "alpha", "bravo", "delta");
    }

    @Test
    void reportsTheCountsThatFail() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(WORDS).hasCount("foo", 3))
                .withMessage("Expected the count of foo in [foo × 2, bar, baz] to be 3, but it was 2");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(WORDS).hasDistinctSize(4))
                .withMessage("Expected [foo × 2, bar, baz] to have 4 distinct elements, but it has 3");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(WORDS).containsExactlyCounts(Map.of("foo", 2, "bar", 1)))
                .withMessageStartingWith("Expected [foo × 2, bar, baz] to have the counts {")
                .withMessageEndingWith("}, but it has [foo=2, bar=1, baz=1]");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(WORDS).containsExactlyCounts(Map.of("foo", 1, "bar", 1, "baz", 1)));
    }

    @Test
    void rejectsCountsThatCannotBeRight() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> assertThat(WORDS).hasCount("foo", -1))
                .withMessage("count must not be negative: -1");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> assertThat(WORDS).containsExactlyCounts(Map.of("foo", 0)))
                .withMessage("count of foo must be positive: 0");
    }

    // Passing null is the point.
    @Test
    @SuppressWarnings("NullAway")
    void failsOnANullMultisetAndRejectsNullArguments() {
        var nullElement = new HashMap<String, Integer>();
        nullElement.put(null, 1);
        var nullCount = new HashMap<String, Integer>();
        nullCount.put("foo", null);

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat((Multiset<String>) null).hasCount("foo", 2));
        assertThatNullPointerException()
                .isThrownBy(() -> assertThat(WORDS).hasCount(null, 1))
                .withMessage("element");
        assertThatNullPointerException()
                .isThrownBy(() -> assertThat(WORDS).containsExactlyCounts(null))
                .withMessage("counts");
        assertThatNullPointerException()
                .isThrownBy(() -> assertThat(WORDS).containsExactlyCounts(nullElement))
                .withMessage("counts contains a null element");
        assertThatNullPointerException()
                .isThrownBy(() -> assertThat(WORDS).containsExactlyCounts(nullCount))
                .withMessage("counts contains a null count");
    }
}
