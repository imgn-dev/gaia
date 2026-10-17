package be.imgn.gaia.processing.testing;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.assertj.core.api.AbstractAssert;

/**
 * AssertJ assertions on the diagnostics of a {@link Compilation} that matched an earlier assertion, such as
 * {@link CompilationAssert#hadErrorContaining(String)}.
 *
 * <pre>{@code
 * assertThat(compilation).hadErrorContaining("incompatible types").inFile("example/Foo.java").onLine(4);
 * }</pre>
 *
 * <p>Each method keeps the diagnostics that satisfy its condition, and fails if none does, so a chain finds the
 * diagnostic it describes even when several share the same message.
 */
public final class DiagnosticAssert
        extends AbstractAssert<DiagnosticAssert, List<Diagnostic<? extends JavaFileObject>>> {

    private final String description;

    DiagnosticAssert(List<Diagnostic<? extends JavaFileObject>> matching, String description) {
        super(List.copyOf(matching), DiagnosticAssert.class);
        this.description = description;
    }

    static String describe(Diagnostic<? extends JavaFileObject> diagnostic) {
        JavaFileObject source = diagnostic.getSource();
        String location = source == null
                ? ""
                : " at " + source.getName() + ":" + diagnostic.getLineNumber() + ":" + diagnostic.getColumnNumber();
        return "  " + diagnostic.getKind() + location + ": " + diagnostic.getMessage(Locale.ROOT);
    }

    static String describe(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        if (diagnostics.isEmpty()) {
            return "  (none)";
        }
        return diagnostics.stream().map(DiagnosticAssert::describe).collect(Collectors.joining(System.lineSeparator()));
    }

    /**
     * Verifies that a diagnostic is in a source file, whose name ends with {@code path}.
     *
     * @param path the path of the file, separated by {@code /}, such as {@code example/Foo.java}
     * @return assertions on the diagnostics in that file
     * @throws IllegalArgumentException if {@code path} is empty
     */
    public DiagnosticAssert inFile(String path) {
        requireNonNull(path, "path");
        if (path.isEmpty()) {
            throw new IllegalArgumentException("The path must not be empty");
        }
        return narrow(diagnostic -> isIn(diagnostic, path), "in " + path);
    }

    /**
     * Verifies that a diagnostic is on a line.
     *
     * @param line the line number, starting at 1
     * @return assertions on the diagnostics on that line
     * @throws IllegalArgumentException if {@code line} is less than 1
     */
    public DiagnosticAssert onLine(long line) {
        if (line < 1) {
            throw new IllegalArgumentException("Lines start at 1: " + line);
        }
        return narrow(diagnostic -> diagnostic.getLineNumber() == line, "on line " + line);
    }

    /**
     * Verifies that a diagnostic is at a column.
     *
     * @param column the column number, starting at 1
     * @return assertions on the diagnostics at that column
     * @throws IllegalArgumentException if {@code column} is less than 1
     */
    public DiagnosticAssert atColumn(long column) {
        if (column < 1) {
            throw new IllegalArgumentException("Columns start at 1: " + column);
        }
        return narrow(diagnostic -> diagnostic.getColumnNumber() == column, "at column " + column);
    }

    private static boolean isIn(Diagnostic<? extends JavaFileObject> diagnostic, String path) {
        JavaFileObject source = diagnostic.getSource();
        if (source == null) {
            return false;
        }
        String name = source.getName().replace('\\', '/');
        return name.equals(path) || name.endsWith("/" + path);
    }

    private DiagnosticAssert narrow(Predicate<Diagnostic<? extends JavaFileObject>> condition, String what) {
        isNotNull();
        List<Diagnostic<? extends JavaFileObject>> kept =
                actual.stream().filter(condition).toList();
        if (kept.isEmpty()) {
            failWithMessage("Expected %s %s, but the candidates were:%n%s", description, what, describe(actual));
        }
        return new DiagnosticAssert(kept, description + " " + what);
    }
}
