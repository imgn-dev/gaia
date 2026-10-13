package be.imgn.gaia.platform.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MacOSDirectoriesTest {

    @Test
    void usesTheStandardLocations() {
        MacOSDirectories directories = new MacOSDirectories(FileSystems.getDefault(), "/Users/alice");

        assertThat(directories.home()).isEqualTo(Path.of("/Users/alice"));
        assertThat(directories.dataHome()).isEqualTo(Path.of("/Users/alice/Library/Application Support"));
        assertThat(directories.configHome()).isEqualTo(Path.of("/Users/alice/Library/Application Support"));
        assertThat(directories.stateHome()).isEqualTo(Path.of("/Users/alice/Library/Application Support"));
        assertThat(directories.cacheHome()).isEqualTo(Path.of("/Users/alice/Library/Caches"));
        assertThat(directories.runtimeDir()).isEmpty();
        assertThat(directories.dataDirs()).containsExactly(Path.of("/Library/Application Support"));
        assertThat(directories.configDirs()).containsExactly(Path.of("/Library/Application Support"));
    }
}
