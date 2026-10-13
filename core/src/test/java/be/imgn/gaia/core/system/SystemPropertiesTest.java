package be.imgn.gaia.core.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

class SystemPropertiesTest {

    // Every standard property of System.getProperties() but the two optional ones.
    static final List<Function<SystemProperties, Object>> REQUIRED_BY_JAVA_SE = List.of(
            SystemProperties::javaVersion,
            SystemProperties::javaVersionDate,
            SystemProperties::javaVendor,
            SystemProperties::javaVendorUrl,
            SystemProperties::javaHome,
            SystemProperties::javaVmSpecificationVersion,
            SystemProperties::javaVmSpecificationVendor,
            SystemProperties::javaVmSpecificationName,
            SystemProperties::javaVmVersion,
            SystemProperties::javaVmVendor,
            SystemProperties::javaVmName,
            SystemProperties::javaSpecificationVersion,
            SystemProperties::javaSpecificationVendor,
            SystemProperties::javaSpecificationName,
            SystemProperties::javaClassVersion,
            SystemProperties::javaClassPath,
            SystemProperties::javaLibraryPath,
            SystemProperties::javaIoTmpdir,
            SystemProperties::osName,
            SystemProperties::osArch,
            SystemProperties::osVersion,
            SystemProperties::fileSeparator,
            SystemProperties::pathSeparator,
            SystemProperties::lineSeparator,
            SystemProperties::userName,
            SystemProperties::userHome,
            SystemProperties::userDir,
            SystemProperties::nativeEncoding,
            SystemProperties::stdoutEncoding,
            SystemProperties::stderrEncoding);

    private final SystemProperties properties = SystemProperties.system();

    @ParameterizedTest
    @FieldSource("REQUIRED_BY_JAVA_SE")
    void systemDefinesEveryPropertyJavaSeRequires(Function<SystemProperties, Object> property) {
        assertThat(property.apply(properties)).isNotNull();
    }

    @Test
    void systemReadsTheRunningJvm() {
        // java.version has no build number, unlike Runtime.version(), which reads java.runtime.version.
        assertThat(properties.javaVersion().version())
                .isEqualTo(Runtime.version().version());
        assertThat(properties.javaSpecificationVersion())
                .isEqualTo(Runtime.version().feature());
        assertThat(properties.userHome()).isEqualTo(System.getProperty("user.home"));
        assertThat(properties.lineSeparator()).isEqualTo(System.lineSeparator());
    }

    @Test
    void systemReadsTheCurrentValueOnEachCall() {
        String name = "be.imgn.gaia.test." + UUID.randomUUID();
        try {
            assertThat(properties.get(name)).isEmpty();

            System.setProperty(name, "value");

            assertThat(properties.get(name)).contains("value");
        } finally {
            System.clearProperty(name);
        }
    }

    @Test
    void rejectsAnEmptyName() {
        assertThatThrownBy(() -> properties.get(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The name of a system property must not be empty");
        assertThatThrownBy(() -> SystemProperties.of(Map.of()).get(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The name of a system property must not be empty");
    }

    @Nested
    class Fixed {

        @Test
        void typesTheValues() {
            SystemProperties properties = SystemProperties.of(Map.of(
                    "java.version", "21.0.4+7-LTS",
                    "java.version.date", "2024-07-16",
                    "java.vendor.url", "https://adoptium.net/",
                    "java.specification.version", "21",
                    "java.specification.maintenance.version", "1",
                    "native.encoding", "UTF-8"));

            assertThat(properties.javaVersion()).isEqualTo(Runtime.Version.parse("21.0.4+7-LTS"));
            assertThat(properties.javaVersionDate()).isEqualTo(LocalDate.of(2024, 7, 16));
            assertThat(properties.javaVendorUrl()).isEqualTo(URI.create("https://adoptium.net/"));
            assertThat(properties.javaSpecificationVersion()).isEqualTo(21);
            assertThat(properties.javaSpecificationMaintenanceVersion()).hasValue(1);
            assertThat(properties.nativeEncoding()).isEqualTo(StandardCharsets.UTF_8);
        }

        @Test
        void splitsPathListsOnItsOwnPathSeparatorAndSkipsEmptyEntries() {
            SystemProperties properties = SystemProperties.of(Map.of(
                    "path.separator", ";",
                    "java.class.path", "C:\\app\\a.jar;;C:\\app\\b.jar;",
                    "jdk.module.path", "mods"));

            assertThat(properties.javaClassPath()).containsExactly("C:\\app\\a.jar", "C:\\app\\b.jar");
            assertThat(properties.jdkModulePath()).containsExactly("mods");
            assertThat(properties.jdkModuleUpgradePath()).isEmpty();
        }

        @Test
        void returnsTheOptionalPropertiesAsEmptyWhenNotSet() {
            SystemProperties properties = SystemProperties.of(Map.of());

            assertThat(properties.javaVendorVersion()).isEmpty();
            assertThat(properties.javaSpecificationMaintenanceVersion()).isEmpty();
            assertThat(properties.jdkModuleMain()).isEmpty();
            assertThat(properties.jdkModuleMainClass()).isEmpty();
            assertThat(properties.fileEncoding()).isEmpty();
        }

        @Test
        void namesARequiredPropertyItLacks() {
            assertThatThrownBy(SystemProperties.of(Map.of())::userHome)
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("System property user.home is not set");
        }

        @Test
        void holdsACopy() {
            Map<String, String> values = new HashMap<>(Map.of("user.home", "/home/alice"));
            SystemProperties properties = SystemProperties.of(values);

            values.put("os.name", "Linux");

            assertThat(properties.userHome()).isEqualTo("/home/alice");
            assertThat(properties.get("os.name")).isEmpty();
        }
    }
}
