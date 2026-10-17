package be.imgn.gaia.processing.testing;

import static java.util.Objects.requireNonNull;

import java.util.List;
import javax.lang.model.SourceVersion;

/** Argument checks shared by the public types of this package. */
final class Checks {

    private static final String PACKAGE_INFO = "package-info";
    private static final String MODULE_INFO = "module-info";

    private Checks() {}

    /**
     * Copies a list, naming the parameter and the index of a null element, which {@link List#copyOf} does not.
     *
     * @param <T> the type of the elements
     * @param elements the list to copy
     * @param name the name of the parameter
     * @return an unmodifiable copy of {@code elements}
     */
    static <T> List<T> copyOfNonNull(List<? extends T> elements, String name) {
        requireNonNull(elements, name);
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i) == null) {
                throw new NullPointerException(name + "[" + i + "]");
            }
        }
        return List.copyOf(elements);
    }

    /**
     * Checks the name of a compilation unit: the qualified name of its top-level type, {@code module-info}, or
     * {@code package-info} after the name of its package, if any.
     *
     * @param qualifiedName the name to check
     * @throws IllegalArgumentException if {@code qualifiedName} is none of those
     */
    static void checkSourceName(String qualifiedName) {
        if (qualifiedName.equals(MODULE_INFO) || qualifiedName.equals(PACKAGE_INFO)) {
            return;
        }
        String name = qualifiedName.endsWith("." + PACKAGE_INFO)
                ? qualifiedName.substring(0, qualifiedName.length() - PACKAGE_INFO.length() - 1)
                : qualifiedName;
        // An identifier may contain control characters, which javac ignores but a file URI cannot hold.
        if (!SourceVersion.isName(name) || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "Not a qualified type name, module-info or package-info: \"" + qualifiedName + "\"");
        }
    }

    /**
     * Checks the path of a file relative to a location.
     *
     * @param relativePath the path to check
     * @throws IllegalArgumentException if {@code relativePath} is empty or starts with {@code /}
     */
    static void checkRelativePath(String relativePath) {
        if (relativePath.isEmpty() || relativePath.startsWith("/")) {
            throw new IllegalArgumentException(
                    "Not a path relative to a location, such as example/Foo.java: \"" + relativePath + "\"");
        }
    }
}
