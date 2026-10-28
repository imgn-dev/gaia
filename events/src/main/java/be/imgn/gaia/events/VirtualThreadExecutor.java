package be.imgn.gaia.events;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.Executor;

/**
 * Runs each task on a new virtual thread: the executor of {@link OnVirtualThread @OnVirtualThread}. Unlike
 * {@link java.util.concurrent.Executors#newVirtualThreadPerTaskExecutor()}, it needs no shutdown.
 *
 * @since 0.1.2
 */
public final class VirtualThreadExecutor implements Executor {

    /** Creates the executor, as the code that subscribes {@code @OnVirtualThread} methods does. */
    public VirtualThreadExecutor() {}

    /**
     * Starts a virtual thread that runs a task.
     *
     * @param task the task
     */
    @Override
    public void execute(Runnable task) {
        Thread.ofVirtual().start(requireNonNull(task, "task"));
    }
}
