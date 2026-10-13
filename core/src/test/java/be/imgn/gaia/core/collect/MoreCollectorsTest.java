package be.imgn.gaia.core.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MoreCollectorsTest {

    @Test
    void collectsTheOnlyElement() {
        assertThat(Stream.of("a").collect(MoreCollectors.onlyElement())).isEqualTo("a");
    }

    @Test
    void rejectsAnEmptyStream() {
        assertThatThrownBy(() -> Stream.empty().collect(MoreCollectors.onlyElement()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsMoreThanOneElement() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Stream.of("a", "b", "c").collect(MoreCollectors.onlyElement()))
                .withMessageContaining("a, b");
    }

    @Test
    void rejectsMoreThanOneElementInAParallelStream() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> IntStream.range(0, 10_000).boxed().parallel().collect(MoreCollectors.onlyElement()));
    }

    @Test
    void findsTheOnlyElementOfAParallelStream() {
        assertThat(IntStream.range(0, 10_000)
                        .boxed()
                        .parallel()
                        .filter(i -> i == 4_242)
                        .collect(MoreCollectors.onlyElement()))
                .isEqualTo(4_242);
    }

    @Test
    void rejectsANullElement() {
        assertThatNullPointerException()
                .isThrownBy(() -> Stream.of((String) null).collect(MoreCollectors.onlyElement()));
    }

    @Test
    void toOptionalCollectsTheOnlyElement() {
        assertThat(Stream.of("a").collect(MoreCollectors.toOptional())).contains("a");
    }

    @Test
    void toOptionalCollectsAnEmptyStreamAsEmpty() {
        assertThat(Stream.<String>empty().collect(MoreCollectors.toOptional())).isEmpty();
    }

    @Test
    void toOptionalRejectsMoreThanOneElement() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Stream.of("a", "b").collect(MoreCollectors.toOptional()))
                .withMessageContaining("a, b");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> IntStream.range(0, 10_000).boxed().parallel().collect(MoreCollectors.toOptional()));
    }

    @Test
    void toOptionalRejectsANullElement() {
        assertThatNullPointerException()
                .isThrownBy(() -> Stream.of((String) null).collect(MoreCollectors.toOptional()));
    }
}
