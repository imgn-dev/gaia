package be.imgn.gaia.core.base;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static be.imgn.gaia.core.base.Preconditions.checkState;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class PreconditionsTest {

    @Test
    void acceptsATrueArgumentExpression() {
        assertThatCode(() -> {
                    checkArgument(true);
                    checkArgument(true, "message");
                    checkArgument(true, "%d", 1);
                })
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAFalseArgumentExpression() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> checkArgument(false))
                .withMessage(null);
    }

    @Test
    void rejectsAFalseArgumentExpressionWithAMessage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> checkArgument(false, "count must be 100%"))
                .withMessage("count must be 100%");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> checkArgument(false, (Object) null))
                .withMessage("null");
    }

    @Test
    void rejectsAFalseArgumentExpressionWithAFormattedMessage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> checkArgument(false, "%s must be at most %d", "count", 3))
                .withMessage("count must be at most 3");
    }

    @Test
    void acceptsATrueStateExpression() {
        assertThatCode(() -> {
                    checkState(true);
                    checkState(true, "message");
                    checkState(true, "%d", 1);
                })
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAFalseStateExpression() {
        assertThatIllegalStateException().isThrownBy(() -> checkState(false)).withMessage(null);
    }

    @Test
    void rejectsAFalseStateExpressionWithAMessage() {
        assertThatIllegalStateException()
                .isThrownBy(() -> checkState(false, "Already closed"))
                .withMessage("Already closed");
    }

    @Test
    void rejectsAFalseStateExpressionWithAFormattedMessage() {
        assertThatIllegalStateException()
                .isThrownBy(() -> checkState(false, "%d of %s", 2, null))
                .withMessage("2 of null");
    }

    @Test
    void formatsInTheRootLocale() {
        var locale = Locale.getDefault(Locale.Category.FORMAT);
        Locale.setDefault(Locale.Category.FORMAT, Locale.FRANCE);
        try {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> checkArgument(false, "%.1f", 1.5))
                    .withMessage("1.5");
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, locale);
        }
    }

    @Test
    void doesNotFormatTheMessageOfATrueExpression() {
        var param = new Object() {
            @Override
            public String toString() {
                throw new AssertionError("formatted");
            }
        };

        assertThatCode(() -> checkArgument(true, "%s", param)).doesNotThrowAnyException();
        assertThatCode(() -> checkState(true, "%s", param)).doesNotThrowAnyException();
    }
}
