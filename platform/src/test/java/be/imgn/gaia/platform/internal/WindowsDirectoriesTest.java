package be.imgn.gaia.platform.internal;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.core.system.Environment;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WindowsDirectoriesTest {

    private static final Path USER_HOME = Path.of("C:\\Users\\alice");

    @Test
    void usesTheVariablesWindowsSets() {
        WindowsDirectories directories = new WindowsDirectories(
                FileSystems.getDefault(),
                USER_HOME.toString(),
                Environment.of(Map.of(
                        "APPDATA", "D:\\Profiles\\alice\\Roaming",
                        "LOCALAPPDATA", "D:\\Profiles\\alice\\Local",
                        "ProgramData", "D:\\ProgramData")));

        assertThat(directories.home()).isEqualTo(USER_HOME);
        assertThat(directories.dataHome()).isEqualTo(Path.of("D:\\Profiles\\alice\\Roaming"));
        assertThat(directories.configHome()).isEqualTo(Path.of("D:\\Profiles\\alice\\Roaming"));
        assertThat(directories.stateHome()).isEqualTo(Path.of("D:\\Profiles\\alice\\Local"));
        assertThat(directories.cacheHome()).isEqualTo(Path.of("D:\\Profiles\\alice\\Local"));
        assertThat(directories.runtimeDir()).isEmpty();
        assertThat(directories.dataDirs()).containsExactly(Path.of("D:\\ProgramData"));
        assertThat(directories.configDirs()).containsExactly(Path.of("D:\\ProgramData"));
    }

    @Test
    void computesTheDefaultLocationsWhenTheVariablesAreMissingOrBlank() {
        WindowsDirectories directories = new WindowsDirectories(
                FileSystems.getDefault(),
                USER_HOME.toString(),
                Environment.of(Map.of("SystemDrive", "E:", "APPDATA", " ")));

        assertThat(directories.configHome())
                .isEqualTo(USER_HOME.resolve("AppData").resolve("Roaming"));
        assertThat(directories.cacheHome())
                .isEqualTo(USER_HOME.resolve("AppData").resolve("Local"));
        assertThat(directories.dataDirs()).containsExactly(Path.of("E:\\ProgramData"));
    }

    @Test
    void defaultsTheSystemDriveToC() {
        assertThat(new WindowsDirectories(FileSystems.getDefault(), USER_HOME.toString(), Environment.of(Map.of()))
                        .configDirs())
                .containsExactly(Path.of("C:\\ProgramData"));
    }
}
