package be.imgn.gaia.core.platform.internal;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.core.system.Environment;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class XdgDirectoriesTest {

    private static final Path USER_HOME = Path.of("/home/alice");

    @Test
    void usesTheSpecificationDefaultsWhenNoVariableIsSet() {
        XdgDirectories directories =
                new XdgDirectories(FileSystems.getDefault(), USER_HOME.toString(), Environment.of(Map.of()));

        assertThat(directories.home()).isEqualTo(USER_HOME);
        assertThat(directories.dataHome()).isEqualTo(Path.of("/home/alice/.local/share"));
        assertThat(directories.configHome()).isEqualTo(Path.of("/home/alice/.config"));
        assertThat(directories.stateHome()).isEqualTo(Path.of("/home/alice/.local/state"));
        assertThat(directories.cacheHome()).isEqualTo(Path.of("/home/alice/.cache"));
        assertThat(directories.runtimeDir()).isEmpty();
        assertThat(directories.dataDirs()).containsExactly(Path.of("/usr/local/share"), Path.of("/usr/share"));
        assertThat(directories.configDirs()).containsExactly(Path.of("/etc/xdg"));
    }

    @Test
    void usesTheVariablesWhenSet() {
        XdgDirectories directories = new XdgDirectories(
                FileSystems.getDefault(),
                USER_HOME.toString(),
                Environment.of(Map.of(
                        "HOME", "/users/bob",
                        "XDG_DATA_HOME", "/data",
                        "XDG_CONFIG_HOME", "/config",
                        "XDG_STATE_HOME", "/state",
                        "XDG_CACHE_HOME", "/cache",
                        "XDG_RUNTIME_DIR", "/run/user/1000",
                        "XDG_DATA_DIRS", "/opt/share:/usr/share",
                        "XDG_CONFIG_DIRS", "/opt/etc:/etc/xdg")));

        assertThat(directories.home()).isEqualTo(Path.of("/users/bob"));
        assertThat(directories.dataHome()).isEqualTo(Path.of("/data"));
        assertThat(directories.configHome()).isEqualTo(Path.of("/config"));
        assertThat(directories.stateHome()).isEqualTo(Path.of("/state"));
        assertThat(directories.cacheHome()).isEqualTo(Path.of("/cache"));
        assertThat(directories.runtimeDir()).contains(Path.of("/run/user/1000"));
        assertThat(directories.dataDirs()).containsExactly(Path.of("/opt/share"), Path.of("/usr/share"));
        assertThat(directories.configDirs()).containsExactly(Path.of("/opt/etc"), Path.of("/etc/xdg"));
    }

    @Test
    void derivesTheDefaultsFromHome() {
        XdgDirectories directories = new XdgDirectories(
                FileSystems.getDefault(), USER_HOME.toString(), Environment.of(Map.of("HOME", "/users/bob")));

        assertThat(directories.configHome()).isEqualTo(Path.of("/users/bob/.config"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "relative/path", "~/data"})
    void ignoresVariablesThatAreNotAbsolutePaths(String value) {
        XdgDirectories directories = new XdgDirectories(
                FileSystems.getDefault(),
                USER_HOME.toString(),
                Environment.of(Map.of(
                        "HOME", value, "XDG_DATA_HOME", value, "XDG_RUNTIME_DIR", value, "XDG_DATA_DIRS", value)));

        assertThat(directories.home()).isEqualTo(USER_HOME);
        assertThat(directories.dataHome()).isEqualTo(Path.of("/home/alice/.local/share"));
        assertThat(directories.runtimeDir()).isEmpty();
        assertThat(directories.dataDirs()).containsExactly(Path.of("/usr/local/share"), Path.of("/usr/share"));
    }

    @Test
    void skipsTheRelativeAndEmptyEntriesOfAPathList() {
        XdgDirectories directories = new XdgDirectories(
                FileSystems.getDefault(),
                USER_HOME.toString(),
                Environment.of(Map.of("XDG_CONFIG_DIRS", "relative::/opt/etc:")));

        assertThat(directories.configDirs()).containsExactly(Path.of("/opt/etc"));
    }
}
