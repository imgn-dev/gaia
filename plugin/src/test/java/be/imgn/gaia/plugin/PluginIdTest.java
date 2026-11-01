package be.imgn.gaia.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PluginIdTest {

    @Test
    void acceptsAModuleName() {
        assertThat(new PluginId("com.example.blame").name()).isEqualTo("com.example.blame");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "com..blame", "com.example.", "com.1blame", "com.example.class", "com.example-blame"})
    void rejectsWhatIsNotAModuleName(String name) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PluginId(name))
                .withMessageContaining(name);
    }

    @Test
    @SuppressWarnings("NullAway")
    void rejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> new PluginId(null)).withMessage("name");
    }

    @Test
    void equalsAnIdWithTheSameName() {
        assertThat(new PluginId("com.example.blame"))
                .isEqualTo(new PluginId("com.example.blame"))
                .isNotEqualTo(new PluginId("com.example.git"))
                .hasSameHashCodeAs(new PluginId("com.example.blame"));
    }

    @Test
    void printsAsItsName() {
        assertThat(new PluginId("com.example.blame")).hasToString("com.example.blame");
    }
}
