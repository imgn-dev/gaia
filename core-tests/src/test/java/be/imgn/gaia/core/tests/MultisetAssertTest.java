package be.imgn.gaia.core.tests;

import static be.imgn.gaia.core.testing.MultisetAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import be.imgn.gaia.core.collect.Multiset;
import be.imgn.gaia.core.collect.Multisets;
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
    void reportsTheCountsThatFail() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(WORDS).hasCount("foo", 3))
                .withMessage("Expected the count of foo in [foo, foo, bar, baz] to be 3, but it was 2");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(WORDS).hasDistinctSize(4))
                .withMessage("Expected [foo, foo, bar, baz] to have 4 distinct elements, but it has 3");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(WORDS).containsExactlyCounts(Map.of("foo", 2, "bar", 1)))
                .withMessageStartingWith("Expected [foo, foo, bar, baz] to have the counts {")
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
}
