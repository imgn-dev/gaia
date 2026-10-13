package be.imgn.gaia.core.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EnvironmentTest {

    @Test
    void systemReadsTheRunningJvm() {
        assertThat(Environment.system().get("PATH")).isEqualTo(Optional.ofNullable(System.getenv("PATH")));
        assertThat(Environment.system().home()).isEqualTo(Optional.ofNullable(System.getenv("HOME")));
    }

    @Test
    void splitsPathOnThePathSeparator() {
        String path = String.join(File.pathSeparator, "/usr/bin", "", "\"/opt/my tools\"", "/bin");

        assertThat(Environment.of(Map.of("PATH", path)).path()).containsExactly("/usr/bin", "/opt/my tools", "/bin");
        assertThat(Environment.of(Map.of()).path()).isEmpty();
    }

    @Test
    void fixedHoldsACopyWithCaseSensitiveNames() {
        Map<String, String> values = new HashMap<>(Map.of("HOME", "/home/alice"));
        Environment environment = Environment.of(values);

        values.put("PATH", "/bin");

        assertThat(environment.home()).contains("/home/alice");
        assertThat(environment.get("home")).isEmpty();
        assertThat(environment.get("PATH")).isEmpty();
    }
}
