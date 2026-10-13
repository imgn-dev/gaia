package be.imgn.gaia.processing.tests;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.processing.testing.CompilationExtension;
import be.imgn.gaia.processing.testing.SharedCompilation;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.util.Elements;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.ExtendWith;

class SharedCompilationTest {

    @Nested
    @ExtendWith(CompilationExtension.class)
    @SharedCompilation
    class Shared {

        private static final List<Elements> SEEN = new ArrayList<>();

        @AfterAll
        static void everyTestSawTheSameCompilation() {
            assertThat(SEEN).hasSize(3);
            assertThat(SEEN).allSatisfy(elements -> assertThat(elements).isSameAs(SEEN.getFirst()));
        }

        @RepeatedTest(3)
        void recordsItsElements(Elements elements) {
            assertThat(elements.getTypeElement("java.lang.String")).isNotNull();
            SEEN.add(elements);
        }
    }

    @Nested
    @ExtendWith(CompilationExtension.class)
    class NotShared {

        private static final List<Elements> SEEN = new ArrayList<>();

        @AfterAll
        static void everyTestSawItsOwnCompilation() {
            assertThat(SEEN).hasSize(3).doesNotHaveDuplicates();
        }

        @RepeatedTest(3)
        void recordsItsElements(Elements elements) {
            SEEN.add(elements);
        }
    }
}
