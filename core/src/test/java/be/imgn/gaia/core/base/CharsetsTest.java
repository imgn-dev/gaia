package be.imgn.gaia.core.base;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CharsetsTest {

    @Test
    void theDefaultCharsetIsUtf8() {
        assertThat(Charsets.DEFAULT_CHARSET).isEqualTo(StandardCharsets.UTF_8);
    }
}
