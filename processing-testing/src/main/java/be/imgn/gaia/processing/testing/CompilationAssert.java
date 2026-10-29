package be.imgn.gaia.processing.testing;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;

/**
 * AssertJ assertions on a {@link Compilation}. Every failure message lists all the diagnostics, so a failed test shows
 * what javac said.
 *
 * <pre>{@code
 * assertThat(compilation).succeeded().hadNoWarnings();
 * assertThat(compilation).generatedSourceFile("example.FooInfo").contains("class FooInfo");
 * assertThat(compilation).failed().hadErrorContaining("not abstract").inFile("example/Foo.java").onLine(3);
 * }</pre>
 */
public final class CompilationAssert extends AbstractAssert<CompilationAssert, Compilation> {

    private CompilationAssert(@Nullable Compilation actual) {
        super(actual, CompilationAssert.class);
    }

    /**
     * Returns assertions on a compilation.
     *
     * @param actual the compilation; like any AssertJ assertion, every assertion fails if it is {@code null}
     * @return the assertions
     */
    public static CompilationAssert assertThat(@Nullable Compilation actual) {
        return new CompilationAssert(actual);
    }

    /**
     * Verifies that javac reported no error.
     *
     * @return these assertions
     */
    public CompilationAssert succeeded() {
        isNotNull();
        if (!actual.success()) {
            failWithMessage(
                    "Expected the compilation to succeed, but it failed with:%n%s",
                    DiagnosticAssert.describe(actual.diagnostics()));
        }
        return this;
    }

    /**
     * Verifies that javac reported at least one error.
     *
     * @return these assertions
     */
    public CompilationAssert failed() {
        isNotNull();
        if (actual.success()) {
            failWithMessage(
                    "Expected the compilation to fail, but it succeeded with:%n%s",
                    DiagnosticAssert.describe(actual.diagnostics()));
        }
        return this;
    }

    /**
     * Verifies that javac reported exactly {@code expected} errors.
     *
     * @param expected the number of errors
     * @return these assertions
     * @throws IllegalArgumentException if {@code expected} is negative
     */
    public CompilationAssert hadErrorCount(int expected) {
        if (expected < 0) {
            throw new IllegalArgumentException("The number of errors must not be negative: " + expected);
        }
        isNotNull();
        if (actual.errors().size() != expected) {
            failWithMessage(
                    "Expected %s error(s), but there were %s:%n%s",
                    expected, actual.errors().size(), DiagnosticAssert.describe(actual.diagnostics()));
        }
        return this;
    }

    /**
     * Verifies that the message of an error contains {@code expected}.
     *
     * @param expected the text to look for
     * @return assertions on the matching errors, such as their location
     */
    public DiagnosticAssert hadErrorContaining(String expected) {
        requireNonNull(expected, "expected");
        return hadDiagnosticContaining(EnumSet.of(Diagnostic.Kind.ERROR), "error", expected);
    }

    /**
     * Verifies that the message of a warning, mandatory or not, contains {@code expected}.
     *
     * @param expected the text to look for
     * @return assertions on the matching warnings, such as their location
     */
    public DiagnosticAssert hadWarningContaining(String expected) {
        requireNonNull(expected, "expected");
        return hadDiagnosticContaining(
                EnumSet.of(Diagnostic.Kind.WARNING, Diagnostic.Kind.MANDATORY_WARNING), "warning", expected);
    }

    /**
     * Verifies that the message of a note contains {@code expected}.
     *
     * @param expected the text to look for
     * @return assertions on the matching notes, such as their location
     */
    public DiagnosticAssert hadNoteContaining(String expected) {
        requireNonNull(expected, "expected");
        return hadDiagnosticContaining(EnumSet.of(Diagnostic.Kind.NOTE), "note", expected);
    }

    /**
     * Verifies that javac reported no warning, mandatory or not. That includes the notes javac reports instead of the
     * deprecation, removal and unchecked warnings it is not asked for, such as "Foo.java uses or overrides a deprecated
     * API".
     *
     * @return these assertions
     */
    public CompilationAssert hadNoWarnings() {
        isNotNull();
        if (!actual.diagnostics(Diagnostic.Kind.WARNING).isEmpty()
                || !actual.diagnostics(Diagnostic.Kind.MANDATORY_WARNING).isEmpty()
                || actual.diagnostics(Diagnostic.Kind.NOTE).stream().anyMatch(CompilationAssert::standsForWarnings)) {
            failWithMessage(
                    "Expected no warning, but the diagnostics were:%n%s",
                    DiagnosticAssert.describe(actual.diagnostics()));
        }
        return this;
    }

    // The notes that sum up the deprecation, removal and unchecked warnings javac was not asked to report one by one.
    private static boolean standsForWarnings(Diagnostic<?> note) {
        String code = note.getCode();
        return code != null
                && (code.startsWith("compiler.note.deprecated.")
                        || code.startsWith("compiler.note.removal.")
                        || code.startsWith("compiler.note.unchecked."));
    }

    /**
     * Verifies that no source file was generated, as when a processor has nothing to do.
     *
     * @return these assertions
     */
    public CompilationAssert generatedNoSourceFiles() {
        isNotNull();
        if (!actual.generatedSourceFiles().isEmpty()) {
            failWithMessage(
                    "Expected no generated source, but these were generated:%n%s",
                    names(actual.generatedSourceFiles()));
        }
        return this;
    }

    /**
     * Verifies that the source of a type was generated, and returns assertions on its text.
     *
     * @param qualifiedName the fully qualified name of the generated top-level type, or {@code package-info} after the
     *     name of the package, if any
     * @return assertions on the generated source text
     * @throws IllegalArgumentException if {@code qualifiedName} is not a qualified name, {@code module-info} or
     *     {@code package-info}
     * @throws UncheckedIOException if the generated source cannot be read
     */
    public AbstractStringAssert<?> generatedSourceFile(String qualifiedName) {
        requireNonNull(qualifiedName, "qualifiedName");
        Checks.checkSourceName(qualifiedName);
        return generatedFile(StandardLocation.SOURCE_OUTPUT, qualifiedName.replace('.', '/') + ".java");
    }

    /**
     * Verifies that a file was generated, and returns assertions on its text, decoded as UTF-8. Meant for text files,
     * such as sources and resources.
     *
     * @param location the location the file was generated in, such as {@link StandardLocation#CLASS_OUTPUT} for
     *     resources
     * @param relativePath the path of the file relative to the location, separated by {@code /}, such as
     *     {@code META-INF/services/javax.annotation.processing.Processor}
     * @return assertions on the generated text
     * @throws IllegalArgumentException if {@code relativePath} is empty or starts with {@code /}
     * @throws UncheckedIOException if the generated file cannot be read
     */
    public AbstractStringAssert<?> generatedFile(JavaFileManager.Location location, String relativePath) {
        requireNonNull(location, "location");
        requireNonNull(relativePath, "relativePath");
        Checks.checkRelativePath(relativePath);
        isNotNull();
        String name = "/" + location.getName() + "/" + relativePath;
        JavaFileObject file = actual.generatedFile(location, relativePath)
                .orElseThrow(() -> failure(
                        "Expected a generated file %s, but only these were generated:%n%s",
                        name, names(actual.generatedFiles())));
        try {
            return Assertions.assertThat(file.getCharContent(true).toString()).as("generated file %s", name);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String names(List<JavaFileObject> files) {
        if (files.isEmpty()) {
            return "  (none)";
        }
        return files.stream().map(file -> "  " + file.getName()).collect(Collectors.joining(System.lineSeparator()));
    }

    private DiagnosticAssert hadDiagnosticContaining(Set<Diagnostic.Kind> kinds, String kind, String expected) {
        isNotNull();
        String description = "a %s containing \"%s\"".formatted(kind, expected);
        List<Diagnostic<? extends JavaFileObject>> matching = actual.diagnostics().stream()
                .filter(diagnostic -> kinds.contains(diagnostic.getKind()))
                .filter(diagnostic -> diagnostic.getMessage(Locale.ROOT).contains(expected))
                .toList();
        if (matching.isEmpty()) {
            failWithMessage(
                    "Expected %s, but the diagnostics were:%n%s",
                    description, DiagnosticAssert.describe(actual.diagnostics()));
        }
        return new DiagnosticAssert(matching, description);
    }
}
