package be.imgn.gaia.memfs.internal;

import be.imgn.gaia.memfs.NameMatching;
import java.text.Normalizer;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** The keys names are looked up by, so that names that match have the same key. */
final class Names {

    private final NameMatching matching;
    private final Normalizer.@Nullable Form form;

    Names(NameMatching matching, Normalizer.@Nullable Form form) {
        this.matching = matching;
        this.form = form;
    }

    String key(String name) {
        String normalized = form == null ? name : Normalizer.normalize(name, form);
        return switch (matching) {
            case EXACT -> normalized;
            case IGNORE_CASE_ASCII -> asciiLowerCase(normalized);
            case IGNORE_CASE_UNICODE -> normalized.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);
        };
    }

    private static String asciiLowerCase(String name) {
        StringBuilder lower = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            lower.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
        }
        return lower.toString();
    }
}
