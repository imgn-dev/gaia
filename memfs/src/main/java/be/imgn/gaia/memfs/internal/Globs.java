package be.imgn.gaia.memfs.internal;

import be.imgn.gaia.memfs.Configuration;
import be.imgn.gaia.memfs.NameMatching;
import be.imgn.gaia.memfs.PathStyle;
import java.nio.file.PathMatcher;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Path matchers, for the {@code glob} and {@code regex} syntaxes of {@link java.nio.file.FileSystem#getPathMatcher}.
 * They match the string of a path, ignoring case if names do, or if the paths are Windows paths.
 */
final class Globs {

    private static final String REGEX_META = ".^$+*?|()[]{}\\";

    private Globs() {}

    static PathMatcher matcher(String syntaxAndPattern, Configuration configuration) {
        int colon = syntaxAndPattern.indexOf(':');
        if (colon <= 0) {
            throw new IllegalArgumentException("Not syntax:pattern: " + syntaxAndPattern);
        }
        String syntax = syntaxAndPattern.substring(0, colon).toLowerCase(Locale.ROOT);
        String pattern = syntaxAndPattern.substring(colon + 1);
        boolean windows = configuration.style() == PathStyle.WINDOWS;
        String regex = switch (syntax) {
            case "glob" -> toRegex(pattern, windows);
            case "regex" -> pattern;
            default -> throw new UnsupportedOperationException("Syntax '" + syntax + "' not recognized");
        };
        int flags = windows || configuration.nameMatching() != NameMatching.EXACT
                ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                : 0;
        Pattern compiled = Pattern.compile(regex, flags);
        return path -> compiled.matcher(path.toString()).matches();
    }

    static String toRegex(String glob, boolean windows) {
        return new Translation(glob, windows).regex();
    }

    private static void escape(char c, StringBuilder regex) {
        if (REGEX_META.indexOf(c) >= 0) {
            regex.append('\\');
        }
        regex.append(c);
    }

    /** The translation of a glob into a regular expression, character by character. */
    private static final class Translation {

        private final String glob;
        private final boolean windows;
        private final String notSeparator;
        private final StringBuilder regex = new StringBuilder("^");
        private boolean inGroup;
        private int index;

        Translation(String glob, boolean windows) {
            this.glob = glob;
            this.windows = windows;
            this.notSeparator = windows ? "[^\\\\/]" : "[^/]";
        }

        String regex() {
            while (index < glob.length()) {
                translate(glob.charAt(index++));
            }
            if (inGroup) {
                throw new PatternSyntaxException("Missing '}'", glob, glob.length() - 1);
            }
            return regex.append('$').toString();
        }

        private void translate(char c) {
            switch (c) {
                case '\\' -> escaped();
                case '/' -> regex.append(windows ? "[\\\\/]" : "/");
                case '*' -> star();
                case '?' -> regex.append(notSeparator);
                case '[' -> bracket();
                default -> {
                    if ("{},".indexOf(c) >= 0) {
                        group(c);
                    } else {
                        escape(c, regex);
                    }
                }
            }
        }

        private void escaped() {
            if (index == glob.length()) {
                throw new PatternSyntaxException("No character to escape", glob, index - 1);
            }
            escape(glob.charAt(index++), regex);
        }

        private void star() {
            if (index < glob.length() && glob.charAt(index) == '*') {
                regex.append(".*");
                index++;
            } else {
                regex.append(notSeparator).append('*');
            }
        }

        private void group(char c) {
            if (c == '{') {
                if (inGroup) {
                    throw new PatternSyntaxException("Cannot nest groups", glob, index - 1);
                }
                regex.append("(?:");
                inGroup = true;
            } else if (c == '}') {
                regex.append(inGroup ? ")" : "\\}");
                inGroup = false;
            } else {
                regex.append(inGroup ? "|" : ",");
            }
        }

        // A set of characters, never a separator; "!" negates it.
        private void bracket() {
            int start = index - 1;
            regex.append("[[^").append(windows ? "\\\\/" : "/").append("]&&[");
            if (index < glob.length() && glob.charAt(index) == '!') {
                regex.append('^');
                index++;
            }
            boolean first = true;
            while (index < glob.length() && (glob.charAt(index) != ']' || first)) {
                inBracket(glob.charAt(index++), first);
                first = false;
            }
            if (index == glob.length()) {
                throw new PatternSyntaxException("Missing ']'", glob, start);
            }
            regex.append("]]");
            index++;
        }

        private void inBracket(char c, boolean first) {
            if (c == '[' || c == '\\' || c == '&' || (c == '^' && first)) {
                regex.append('\\');
            }
            regex.append(c);
        }
    }
}
