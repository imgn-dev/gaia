package be.imgn.gaia.plugin.internal;

import be.imgn.gaia.plugin.Registration;
import java.util.concurrent.atomic.AtomicBoolean;

/** A registration that undoes what it registered on its first close only. */
final class OnceRegistration implements Registration {

    private final AtomicBoolean closed = new AtomicBoolean();
    private final Runnable undo;

    OnceRegistration(Runnable undo) {
        this.undo = undo;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            undo.run();
        }
    }
}
