package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.ProcessorAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

class ProcessorAssertTest {

    @Test
    void acceptsARegisteredProcessor() {
        assertThat(RegisteredProcessor.class).isRegistered();
    }

    @Test
    void rejectsAProcessorThatIsNotRegistered() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(InfoProcessor.class).isRegistered())
                .withMessageContaining("Expected be.imgn.gaia.processing.tests.InfoProcessor to be registered")
                .withMessageContaining("be.imgn.gaia.processing.tests.RegisteredProcessor");
    }
}
