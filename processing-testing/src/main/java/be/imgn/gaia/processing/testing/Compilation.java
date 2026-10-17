package be.imgn.gaia.processing.testing;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Optional;
import javax.tools.Diagnostic;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

/**
 * The outcome of a {@link TestCompiler} run: whether it succeeded, every diagnostic, and every generated file.
 *
 * <p>Assert on it with {@link CompilationAssert#assertThat(Compilation)}.
 *
 * @param success whether javac reported no error
 * @param diagnostics every diagnostic, in reporting order
 * @param generatedFiles every generated file, named {@code /<LOCATION>/<relative path>}, such as
 *     {@code /SOURCE_OUTPUT/example/FooInfo.java} or {@code /CLASS_OUTPUT/example/Foo.class}
 */
public record Compilation(
        boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics, List<JavaFileObject> generatedFiles) {

    /**
     * Creates an outcome. The lists are copied.
     *
     * @param success whether javac reported no error
     * @param diagnostics every diagnostic, in reporting order
     * @param generatedFiles every generated file
     */
    public Compilation {
        diagnostics = Checks.copyOfNonNull(diagnostics, "diagnostics");
        generatedFiles = Checks.copyOfNonNull(generatedFiles, "generatedFiles");
    }

    /**
     * Returns the diagnostics of a kind.
     *
     * @param kind the kind of diagnostic
     * @return the diagnostics of {@code kind}, in reporting order
     */
    public List<Diagnostic<? extends JavaFileObject>> diagnostics(Diagnostic.Kind kind) {
        requireNonNull(kind, "kind");
        return diagnostics.stream()
                .filter(diagnostic -> diagnostic.getKind() == kind)
                .toList();
    }

    /**
     * Returns the errors.
     *
     * @return the error diagnostics, in reporting order
     */
    public List<Diagnostic<? extends JavaFileObject>> errors() {
        return diagnostics(Diagnostic.Kind.ERROR);
    }

    /**
     * Returns the generated source files.
     *
     * @return the files generated in {@link StandardLocation#SOURCE_OUTPUT}
     */
    public List<JavaFileObject> generatedSourceFiles() {
        return generatedFiles.stream()
                .filter(file -> file.getName().startsWith("/" + StandardLocation.SOURCE_OUTPUT.getName() + "/"))
                .toList();
    }

    /**
     * Returns the generated source of a type.
     *
     * @param qualifiedName the fully qualified name of the top-level type, or {@code package-info} after the name of
     *     the package, if any
     * @return the source file, or empty if none was generated
     * @throws IllegalArgumentException if {@code qualifiedName} is not a qualified name, {@code module-info} or
     *     {@code package-info}
     */
    public Optional<JavaFileObject> generatedSourceFile(String qualifiedName) {
        requireNonNull(qualifiedName, "qualifiedName");
        Checks.checkSourceName(qualifiedName);
        return generatedFile(StandardLocation.SOURCE_OUTPUT, qualifiedName.replace('.', '/') + ".java");
    }

    /**
     * Returns a generated file.
     *
     * @param location the location the file was generated in, such as {@link StandardLocation#CLASS_OUTPUT}
     * @param relativePath the path of the file relative to the location, separated by {@code /}
     * @return the file, or empty if none was generated there
     * @throws IllegalArgumentException if {@code relativePath} is empty or starts with {@code /}
     */
    public Optional<JavaFileObject> generatedFile(JavaFileManager.Location location, String relativePath) {
        requireNonNull(location, "location");
        requireNonNull(relativePath, "relativePath");
        Checks.checkRelativePath(relativePath);
        String name = "/" + location.getName() + "/" + relativePath;
        return generatedFiles.stream()
                .filter(file -> file.getName().equals(name))
                .findFirst();
    }
}
