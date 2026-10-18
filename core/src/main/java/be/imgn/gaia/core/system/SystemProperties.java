package be.imgn.gaia.core.system;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.text.Splitter;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A source of system properties: the running JVM's, or fixed ones for tests.
 *
 * <p>Code that reads system properties through a {@code SystemProperties} it is given, rather than through
 * {@link System#getProperty(String)}, can be tested with {@link #of(Map)}.
 *
 * <p>Besides {@link #get(String)}, which reads any property, there is a typed method for each property listed in the
 * Java SE 21 documentation of {@link System#getProperties()}, followed by the ones the JDK supports. The properties
 * every Java runtime must define are returned as is, and throw {@link NoSuchElementException} if the source lacks them,
 * which only a fixed source can. The others are returned as an {@link Optional}, or as an empty list for paths.
 *
 * <p>Paths are returned in the default file system, as {@link Path#of(String, String...)} makes them. Path lists, such
 * as {@link #javaClassPath()}, are split on the path separator of the same source, with empty entries skipped. A
 * {@link be.imgn.gaia.core.platform.Platform} may be more appropriate for paths: its directories are in the file system
 * it was given, which tests can replace. {@link #get(String)} still gives any value as a string.
 */
@FunctionalInterface
public interface SystemProperties {

    /**
     * Returns the system properties of the running JVM. Each call reads the current value.
     *
     * @return the JVM's system properties
     */
    static SystemProperties system() {
        return name -> Optional.ofNullable(System.getProperty(checkName(name)));
    }

    /**
     * Returns fixed system properties.
     *
     * @param properties the properties, by name; copied
     * @return system properties holding exactly {@code properties}
     */
    static SystemProperties of(Map<String, String> properties) {
        Map<String, String> copy = NamedValues.copyOf(properties, "properties");
        return name -> Optional.ofNullable(copy.get(checkName(name)));
    }

    /**
     * Returns the value of a property.
     *
     * @param name the name of the property, such as {@code "user.home"}
     * @return the value, or empty if the property is not set
     * @throws IllegalArgumentException if {@code name} is empty
     */
    Optional<String> get(String name);

    /**
     * Returns the version of the Java Runtime Environment: the {@code java.version} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     * @throws IllegalArgumentException if the property is not a valid version
     */
    default Runtime.Version javaVersion() {
        return Runtime.Version.parse(required("java.version"));
    }

    /**
     * Returns the version date of the Java Runtime Environment: the {@code java.version.date} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     * @throws java.time.format.DateTimeParseException if the property is not a valid date
     */
    default LocalDate javaVersionDate() {
        return LocalDate.parse(required("java.version.date"));
    }

    /**
     * Returns the vendor of the Java Runtime Environment: the {@code java.vendor} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     */
    default String javaVendor() {
        return required("java.vendor");
    }

    /**
     * Returns the URL of the Java vendor: the {@code java.vendor.url} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     * @throws IllegalArgumentException if the property is not a valid URI
     */
    default URI javaVendorUrl() {
        return URI.create(required("java.vendor.url"));
    }

    /**
     * Returns the version of the Java vendor, which is optional: the {@code java.vendor.version} property.
     *
     * @return the value, or empty if it is not set
     */
    default Optional<String> javaVendorVersion() {
        return get("java.vendor.version");
    }

    /**
     * Returns the installation directory of Java: the {@code java.home} property.
     *
     * <p>The path is in the default file system, as {@link Path#of(String, String...)} makes it. A
     * {@link be.imgn.gaia.core.platform.Platform} may be more appropriate: its paths are in the file system it was
     * given, which tests can replace.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     * @throws InvalidPathException if the value is not a path of the default file system
     */
    default Path javaHome() {
        return Path.of(required("java.home"));
    }

    /**
     * Returns the version of the Java Virtual Machine specification: the feature element of the runtime version, such
     * as {@code 21}: the {@code java.vm.specification.version} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     * @throws NumberFormatException if the property is not an integer
     */
    default int javaVmSpecificationVersion() {
        return Integer.parseInt(required("java.vm.specification.version"));
    }

    /**
     * Returns the vendor of the Java Virtual Machine specification: the {@code java.vm.specification.vendor} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     */
    default String javaVmSpecificationVendor() {
        return required("java.vm.specification.vendor");
    }

    /**
     * Returns the name of the Java Virtual Machine specification: the {@code java.vm.specification.name} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     */
    default String javaVmSpecificationName() {
        return required("java.vm.specification.name");
    }

    /**
     * Returns the version of the Java Virtual Machine implementation: the {@code java.vm.version} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     * @throws IllegalArgumentException if the property is not a valid version
     */
    default Runtime.Version javaVmVersion() {
        return Runtime.Version.parse(required("java.vm.version"));
    }

    /**
     * Returns the vendor of the Java Virtual Machine implementation: the {@code java.vm.vendor} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     */
    default String javaVmVendor() {
        return required("java.vm.vendor");
    }

    /**
     * Returns the name of the Java Virtual Machine implementation: the {@code java.vm.name} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     */
    default String javaVmName() {
        return required("java.vm.name");
    }

    /**
     * Returns the version of the Java Runtime Environment specification: the feature element of the runtime version,
     * such as {@code 21}: the {@code java.specification.version} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     * @throws NumberFormatException if the property is not an integer
     */
    default int javaSpecificationVersion() {
        return Integer.parseInt(required("java.specification.version"));
    }

    /**
     * Returns the maintenance version of the Java Runtime Environment specification, which is set only after a
     * maintenance release: the {@code java.specification.maintenance.version} property.
     *
     * @return the value, or empty if it is not set
     * @throws NumberFormatException if the property is set but is not an integer
     */
    default OptionalInt javaSpecificationMaintenanceVersion() {
        return get("java.specification.maintenance.version")
                .map(Integer::parseInt)
                .map(OptionalInt::of)
                .orElseGet(OptionalInt::empty);
    }

    /**
     * Returns the vendor of the Java Runtime Environment specification: the {@code java.specification.vendor} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     */
    default String javaSpecificationVendor() {
        return required("java.specification.vendor");
    }

    /**
     * Returns the name of the Java Runtime Environment specification: the {@code java.specification.name} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     */
    default String javaSpecificationName() {
        return required("java.specification.name");
    }

    /**
     * Returns the latest class file format version the Java runtime recognizes, as {@code MAJOR.MINOR}: the
     * {@code java.class.version} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     */
    default String javaClassVersion() {
        return required("java.class.version");
    }

    /**
     * Returns the class path, split on the path separator: the {@code java.class.path} property.
     *
     * <p>The paths are in the default file system, as {@link Path#of(String, String...)} makes them. A
     * {@link be.imgn.gaia.core.platform.Platform} may be more appropriate: its paths are in the file system it was
     * given, which tests can replace.
     *
     * @return the value
     * @throws NoSuchElementException if the property or the {@code path.separator} property is not set
     * @throws IllegalArgumentException if the {@code path.separator} property is empty
     * @throws InvalidPathException if an entry is not a path of the default file system
     */
    default List<Path> javaClassPath() {
        return paths(required("java.class.path"));
    }

    /**
     * Returns the paths searched when loading libraries, split on the path separator: the {@code java.library.path}
     * property.
     *
     * <p>The paths are in the default file system, as {@link Path#of(String, String...)} makes them. A
     * {@link be.imgn.gaia.core.platform.Platform} may be more appropriate: its paths are in the file system it was
     * given, which tests can replace.
     *
     * @return the value
     * @throws NoSuchElementException if the property or the {@code path.separator} property is not set
     * @throws IllegalArgumentException if the {@code path.separator} property is empty
     * @throws InvalidPathException if an entry is not a path of the default file system
     */
    default List<Path> javaLibraryPath() {
        return paths(required("java.library.path"));
    }

    /**
     * Returns the default temporary file directory: the {@code java.io.tmpdir} property.
     *
     * <p>The path is in the default file system, as {@link Path#of(String, String...)} makes it. A
     * {@link be.imgn.gaia.core.platform.Platform} may be more appropriate: its paths are in the file system it was
     * given, which tests can replace.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     * @throws InvalidPathException if the value is not a path of the default file system
     */
    default Path javaIoTmpdir() {
        return Path.of(required("java.io.tmpdir"));
    }

    /**
     * Returns the name of the operating system: the {@code os.name} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     */
    default String osName() {
        return required("os.name");
    }

    /**
     * Returns the architecture of the operating system: the {@code os.arch} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     */
    default String osArch() {
        return required("os.arch");
    }

    /**
     * Returns the version of the operating system: the {@code os.version} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     */
    default String osVersion() {
        return required("os.version");
    }

    /**
     * Returns the file separator, {@code "/"} on Unix: the {@code file.separator} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     */
    default String fileSeparator() {
        return required("file.separator");
    }

    /**
     * Returns the path separator, {@code ":"} on Unix: the {@code path.separator} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     */
    default String pathSeparator() {
        return required("path.separator");
    }

    /**
     * Returns the line separator, {@code "\n"} on Unix: the {@code line.separator} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     */
    default String lineSeparator() {
        return required("line.separator");
    }

    /**
     * Returns the name of the user account: the {@code user.name} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     */
    default String userName() {
        return required("user.name");
    }

    /**
     * Returns the home directory of the user: the {@code user.home} property.
     *
     * <p>The path is in the default file system, as {@link Path#of(String, String...)} makes it. A
     * {@link be.imgn.gaia.core.platform.Platform} may be more appropriate: the
     * {@link be.imgn.gaia.core.platform.Directories#home() home} of its directories is in the file system it was given,
     * which tests can replace.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     * @throws InvalidPathException if the value is not a path of the default file system
     */
    default Path userHome() {
        return Path.of(required("user.home"));
    }

    /**
     * Returns the current working directory of the user: the {@code user.dir} property.
     *
     * <p>The path is in the default file system, as {@link Path#of(String, String...)} makes it. A
     * {@link be.imgn.gaia.core.platform.Platform} may be more appropriate: its paths are in the file system it was
     * given, which tests can replace.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     * @throws InvalidPathException if the value is not a path of the default file system
     */
    default Path userDir() {
        return Path.of(required("user.dir"));
    }

    /**
     * Returns the character encoding derived from the host environment and the user's settings: the
     * {@code native.encoding} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     * @throws java.nio.charset.IllegalCharsetNameException if the property is not a legal charset name
     * @throws java.nio.charset.UnsupportedCharsetException if this JVM does not support the charset the property names
     */
    default Charset nativeEncoding() {
        return Charset.forName(required("native.encoding"));
    }

    /**
     * Returns the character encoding of {@link System#out}: the {@code stdout.encoding} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     * @throws java.nio.charset.IllegalCharsetNameException if the property is not a legal charset name
     * @throws java.nio.charset.UnsupportedCharsetException if this JVM does not support the charset the property names
     */
    default Charset stdoutEncoding() {
        return Charset.forName(required("stdout.encoding"));
    }

    /**
     * Returns the character encoding of {@link System#err}: the {@code stderr.encoding} property.
     *
     * @return the value
     * @throws NoSuchElementException if the property is not set
     * @throws java.nio.charset.IllegalCharsetNameException if the property is not a legal charset name
     * @throws java.nio.charset.UnsupportedCharsetException if this JVM does not support the charset the property names
     */
    default Charset stderrEncoding() {
        return Charset.forName(required("stderr.encoding"));
    }

    /**
     * Returns the application module path, split on the path separator; empty when the application does not use one:
     * the {@code jdk.module.path} property.
     *
     * <p>Supported by the JDK, not required by the Java SE specification.
     *
     * <p>The paths are in the default file system, as {@link Path#of(String, String...)} makes them. A
     * {@link be.imgn.gaia.core.platform.Platform} may be more appropriate: its paths are in the file system it was
     * given, which tests can replace.
     *
     * @return the value
     * @throws NoSuchElementException if the property is set but the {@code path.separator} property is not
     * @throws IllegalArgumentException if the property is set but the {@code path.separator} property is empty
     * @throws InvalidPathException if an entry is not a path of the default file system
     */
    default List<Path> jdkModulePath() {
        return get("jdk.module.path").map(this::paths).orElse(List.of());
    }

    /**
     * Returns the upgrade module path, split on the path separator; empty when there is none: the
     * {@code jdk.module.upgrade.path} property.
     *
     * <p>Supported by the JDK, not required by the Java SE specification.
     *
     * <p>The paths are in the default file system, as {@link Path#of(String, String...)} makes them. A
     * {@link be.imgn.gaia.core.platform.Platform} may be more appropriate: its paths are in the file system it was
     * given, which tests can replace.
     *
     * @return the value
     * @throws NoSuchElementException if the property is set but the {@code path.separator} property is not
     * @throws IllegalArgumentException if the property is set but the {@code path.separator} property is empty
     * @throws InvalidPathException if an entry is not a path of the default file system
     */
    default List<Path> jdkModuleUpgradePath() {
        return get("jdk.module.upgrade.path").map(this::paths).orElse(List.of());
    }

    /**
     * Returns the name of the initial (main) module, if the application was launched from a module: the
     * {@code jdk.module.main} property.
     *
     * <p>Supported by the JDK, not required by the Java SE specification.
     *
     * @return the value, or empty if it is not set
     */
    default Optional<String> jdkModuleMain() {
        return get("jdk.module.main");
    }

    /**
     * Returns the main class of the initial module, if the application was launched from a module: the
     * {@code jdk.module.main.class} property.
     *
     * <p>Supported by the JDK, not required by the Java SE specification.
     *
     * @return the value, or empty if it is not set
     */
    default Optional<String> jdkModuleMainClass() {
        return get("jdk.module.main.class");
    }

    /**
     * Returns the name of the default charset. A name rather than a {@link Charset}: it can be set on the command line
     * to {@code COMPAT}, which is not a charset name: the {@code file.encoding} property.
     *
     * <p>Supported by the JDK, not required by the Java SE specification.
     *
     * @return the value, or empty if it is not set
     */
    default Optional<String> fileEncoding() {
        return get("file.encoding");
    }

    private static String checkName(String name) {
        requireNonNull(name, "name");
        checkArgument(!name.isEmpty(), "The name of a system property must not be empty");
        return name;
    }

    private String required(String name) {
        return get(name).orElseThrow(() -> new NoSuchElementException("System property " + name + " is not set"));
    }

    private List<Path> paths(String value) {
        return Splitter.on(pathSeparator()).omitEmptyStrings().split(value).stream()
                .map(Path::of)
                .toList();
    }
}
