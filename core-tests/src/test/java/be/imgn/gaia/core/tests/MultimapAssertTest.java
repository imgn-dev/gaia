package be.imgn.gaia.core.tests;

import static be.imgn.gaia.core.testing.MultimapAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import be.imgn.gaia.core.collect.HashSetMultimap;
import be.imgn.gaia.core.collect.ListMultimap;
import be.imgn.gaia.core.collect.Multimaps;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MultimapAssertTest {

    private static final ListMultimap<String, Integer> EMPTY =
            Stream.<String>empty().collect(Multimaps.toListMultimap(name -> name, String::length));

    private static ListMultimap<String, Integer> scores() {
        return Stream.of(Map.entry("alice", 3), Map.entry("alice", 5), Map.entry("bob", 4))
                .collect(Multimaps.toListMultimap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Test
    void passesWhenTheMultimapMatches() {
        assertThat(scores())
                .isNotEmpty()
                .hasSize(3)
                .containsKeys("alice", "bob")
                .doesNotContainKeys("carol")
                .containsEntry("alice", 5)
                .doesNotContainEntry("bob", 5)
                .hasValues("alice", 3, 5)
                .hasValues("carol");
        assertThat(EMPTY).isEmpty();
    }

    @Test
    void comparesTheValuesOfASetMultimapInAnyOrder() {
        var multimap = HashSetMultimap.<String, Integer>create();
        multimap.putAll("alice", List.of(3, 5));

        assertThat(multimap).hasValues("alice", 5, 3);
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(scores()).hasValues("alice", 5, 3));
    }

    @Test
    void reportsWhatFails() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(scores()).isEmpty())
                .withMessage("Expected {alice=[3, 5], bob=[4]} to be empty");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(EMPTY).isNotEmpty())
                .withMessage("Expected {} not to be empty");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(scores()).hasSize(2))
                .withMessage("Expected {alice=[3, 5], bob=[4]} to have 2 entries, but it has 3");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(scores()).containsKeys("alice", "carol", "dave"))
                .withMessage("Expected {alice=[3, 5], bob=[4]} to contain the keys carol, dave");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(scores()).doesNotContainKeys("bob", "carol"))
                .withMessage("Expected {alice=[3, 5], bob=[4]} not to contain the keys bob");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(scores()).containsEntry("bob", 5))
                .withMessage("Expected {alice=[3, 5], bob=[4]} to contain bob=5");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(scores()).doesNotContainEntry("bob", 4))
                .withMessage("Expected {alice=[3, 5], bob=[4]} not to contain bob=4");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(scores()).hasValues("alice", 3))
                .withMessage("Expected the values of alice in {alice=[3, 5], bob=[4]} to be [3], but they were [3, 5]");
    }

    // Passing null is the point.
    @Test
    @SuppressWarnings("NullAway")
    void failsOnANullMultimapAndRejectsNullArguments() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(
                        () -> assertThat((ListMultimap<String, Integer>) null).isEmpty());
        assertThatNullPointerException()
                .isThrownBy(() -> assertThat(scores()).containsKeys("alice", null))
                .withMessage("keys[1]");
        assertThatNullPointerException()
                .isThrownBy(() -> assertThat(scores()).doesNotContainKeys((String[]) null))
                .withMessage("keys");
        assertThatNullPointerException()
                .isThrownBy(() -> assertThat(scores()).containsEntry(null, 3))
                .withMessage("key");
        assertThatNullPointerException()
                .isThrownBy(() -> assertThat(scores()).doesNotContainEntry("bob", null))
                .withMessage("value");
        assertThatNullPointerException()
                .isThrownBy(() -> assertThat(scores()).hasValues(null))
                .withMessage("key");
        assertThatNullPointerException()
                .isThrownBy(() -> assertThat(scores()).hasValues("alice", 3, null))
                .withMessage("values[1]");
    }
}
