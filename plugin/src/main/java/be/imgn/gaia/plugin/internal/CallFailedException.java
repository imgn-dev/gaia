package be.imgn.gaia.plugin.internal;

import java.io.Serial;

/** Thrown when code run through {@link Calls#call} throws, or does not finish in time. */
final class CallFailedException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Throwable failure;

    CallFailedException(Throwable failure) {
        super(failure);
        this.failure = failure;
    }

    /** Returns what the code threw, or a {@link java.util.concurrent.TimeoutException} if it did not finish in time. */
    Throwable failure() {
        return failure;
    }
}
