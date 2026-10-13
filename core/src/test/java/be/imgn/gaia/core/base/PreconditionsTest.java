package be.imgn.gaia.core.base;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class PreconditionsTest {

    @Test
    void acceptsATrueArgumentExpression() {
        assertThatCode(() -> {
                    Preconditions.checkArgument(true);
                    Preconditions.checkArgument(true, "message");
                    Preconditions.checkArgument(true, "%d", 1);
                })
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAFalseArgumentExpression() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Preconditions.checkArgument(false))
                .withMessage(null);
    }

    @Test
    void rejectsAFalseArgumentExpressionWithAMessage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Preconditions.checkArgument(false, "count must be 100%"))
                .withMessage("count must be 100%");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Preconditions.checkArgument(false, (Object) null))
                .withMessage("null");
    }

    @Test
    void rejectsAFalseArgumentExpressionWithAFormattedMessage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Preconditions.checkArgument(false, "%s must be at most %d", "count", 3))
                .withMessage("count must be at most 3");
    }

    @Test
    void acceptsATrueStateExpression() {
        assertThatCode(() -> {
                    Preconditions.checkState(true);
                    Preconditions.checkState(true, "message");
                    Preconditions.checkState(true, "%d", 1);
                })
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAFalseStateExpression() {
        assertThatIllegalStateException()
                .isThrownBy(() -> Preconditions.checkState(false))
                .withMessage(null);
    }

    @Test
    void rejectsAFalseStateExpressionWithAMessage() {
        assertThatIllegalStateException()
                .isThrownBy(() -> Preconditions.checkState(false, "Already closed"))
                .withMessage("Already closed");
    }

    @Test
    void rejectsAFalseStateExpressionWithAFormattedMessage() {
        assertThatIllegalStateException()
                .isThrownBy(() -> Preconditions.checkState(false, "%d of %s", 2, null))
                .withMessage("2 of null");
    }

    @Test
    void formatsInTheRootLocale() {
        var locale = Locale.getDefault(Locale.Category.FORMAT);
        Locale.setDefault(Locale.Category.FORMAT, Locale.FRANCE);
        try {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Preconditions.checkArgument(false, "%.1f", 1.5))
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

        assertThatCode(() -> Preconditions.checkArgument(true, "%s", param)).doesNotThrowAnyException();
        assertThatCode(() -> Preconditions.checkState(true, "%s", param)).doesNotThrowAnyException();
    }
}
