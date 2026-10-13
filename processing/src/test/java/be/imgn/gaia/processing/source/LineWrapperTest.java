package be.imgn.gaia.processing.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Line breaks at wrap points, with a column limit of 12 and a continuation indentation of two spaces. */
class LineWrapperTest {

    private static final String CONTINUED = "  ";

    private static String wrap(Consumer<LineWrapper> writes) {
        StringBuilder out = new StringBuilder();
        LineWrapper wrapper = new LineWrapper(out, 12);
        writes.accept(wrapper);
        wrapper.close();
        return out.toString();
    }

    @Nested
    class Spaces {

        @Test
        void writeASpaceWhenTheRestFitsExactly() {
            assertThat(wrap(w -> {
                        w.append("hello");
                        w.wrappingSpace(CONTINUED);
                        w.append("world!");
                    }))
                    .isEqualTo("hello world!");
        }

        @Test
        void breakTheLineWhenTheRestGoesOneColumnPast() {
            assertThat(wrap(w -> {
                        w.append("hello");
                        w.wrappingSpace(CONTINUED);
                        w.append("worlds!");
                    }))
                    .isEqualTo("hello\n  worlds!");
        }

        @Test
        void decideEachWrapPointOnItsOwn() {
            assertThat(wrap(w -> {
                        for (String word : List.of("one", "two", "six", "ten", "red", "big")) {
                            w.append(word);
                            w.wrappingSpace(CONTINUED);
                        }
                        w.append("end");
                    }))
                    .isEqualTo("one two six\n  ten red\n  big end");
        }

        @Test
        void countTheTextWrittenInPiecesAfterAWrapPoint() {
            assertThat(wrap(w -> {
                        w.append("abcdefgh");
                        w.wrappingSpace(CONTINUED);
                        w.append("ij");
                        w.append("kl");
                    }))
                    .isEqualTo("abcdefgh\n  ijkl");
        }

        @Test
        void breakEvenBeforeTextTooLongForAnyLine() {
            assertThat(wrap(w -> {
                        w.wrappingSpace(CONTINUED);
                        w.append("averyveryverylongword");
                    }))
                    .isEqualTo("\n  averyveryverylongword");
        }

        @Test
        void leaveTextWithoutWrapPointsAlone() {
            assertThat(wrap(w -> w.append("averyveryverylongword"))).isEqualTo("averyveryverylongword");
        }
    }

    @Nested
    class ZeroWidth {

        @Test
        void writeNothingWhenTheRestFits() {
            assertThat(wrap(w -> {
                        w.append("call(");
                        w.zeroWidthSpace(CONTINUED);
                        w.append("value);");
                    }))
                    .isEqualTo("call(value);");
        }

        @Test
        void breakTheLineWhenTheRestDoesNotFit() {
            assertThat(wrap(w -> {
                        w.append("call(");
                        w.zeroWidthSpace(CONTINUED);
                        w.append("values);");
                    }))
                    .isEqualTo("call(\n  values);");
        }

        @Test
        void neverBreakAtTheStartOfALine() {
            assertThat(wrap(w -> {
                        w.zeroWidthSpace(CONTINUED);
                        w.append("averyveryverylongword");
                    }))
                    .isEqualTo("averyveryverylongword");
        }
    }

    @Nested
    class LineBreaks {

        @Test
        void decideOnTheTextUpToTheFirstLineBreak() {
            assertThat(wrap(w -> {
                        w.append("hello");
                        w.wrappingSpace(CONTINUED);
                        w.append("world!\nand a much longer line");
                    }))
                    .isEqualTo("hello world!\nand a much longer line");
            assertThat(wrap(w -> {
                        w.append("hello");
                        w.wrappingSpace(CONTINUED);
                        w.append("worlds!\nshort");
                    }))
                    .isEqualTo("hello\n  worlds!\nshort");
        }

        @Test
        void countColumnsFromTheLastLineBreak() {
            assertThat(wrap(w -> {
                        w.append("a much longer first line\nab");
                        w.wrappingSpace(CONTINUED);
                        w.append("cdefghi");
                        w.wrappingSpace(CONTINUED);
                        w.append("jk");
                    }))
                    .isEqualTo("a much longer first line\nab cdefghi\n  jk");
        }
    }
}
