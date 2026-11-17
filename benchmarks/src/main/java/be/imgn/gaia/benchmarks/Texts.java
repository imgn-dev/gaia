package be.imgn.gaia.benchmarks;

import java.nio.charset.StandardCharsets;
import java.util.SplittableRandom;

/** Generated text: lines of about 80 characters, of ASCII or of mixed UTF-8, always the same for a size. */
final class Texts {

    private static final String[] ASCII_WORDS = {
        "lorem", "ipsum", "dolor", "sit", "amet", "consectetur", "adipiscing", "elit", "sed", "do", "eiusmod",
        "tempor", "incididunt", "ut", "labore", "et", "dolore", "magna", "aliqua", "12345", "0x7f", "{\"k\":1}"
    };

    // About one character in eight is not ASCII: two-, three- and four-byte sequences in UTF-8.
    private static final String[] MIXED_WORDS = {
        "lorem",
        "ipsum",
        "déjà",
        "vu",
        "naïve",
        "café",
        "straße",
        "日本語",
        "中文",
        "текст",
        "λόγος",
        "😀ok",
        "amet",
        "elit",
        "sed",
        "do",
        "über",
        "ñandú",
        "12345",
        "0x7f",
        "{\"k\":1}",
        "señor"
    };

    // The seed of the random choice of words, so that a size always gives the same text.
    private static final long SEED = 42;

    // The length past which a line ends.
    private static final int LINE_LENGTH = 75;

    private Texts() {}

    /**
     * Returns a text.
     *
     * @param size the size of the text in UTF-8, at least; it ends with the first word past it
     * @param kind {@code ascii} or {@code mixed}
     * @return the text
     */
    static String text(int size, String kind) {
        var words = switch (kind) {
            case "ascii" -> ASCII_WORDS;
            case "mixed" -> MIXED_WORDS;
            default -> throw new IllegalArgumentException("Unknown kind: " + kind);
        };
        var random = new SplittableRandom(SEED);
        var text = new StringBuilder(size);
        var bytes = 0;
        var lineLength = 0;
        while (bytes < size) {
            var word = words[random.nextInt(words.length)];
            text.append(word);
            bytes += word.getBytes(StandardCharsets.UTF_8).length + 1;
            lineLength += word.length() + 1;
            if (lineLength > LINE_LENGTH) {
                text.append('\n');
                lineLength = 0;
            } else {
                text.append(' ');
            }
        }
        return text.toString();
    }
}
