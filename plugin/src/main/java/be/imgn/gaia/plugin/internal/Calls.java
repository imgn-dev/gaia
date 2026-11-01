package be.imgn.gaia.plugin.internal;

import static java.util.Objects.requireNonNullElse;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;

/**
 * Runs code the manager does not control, a plugin's or a listener's. A {@link FutureTask} runs it, and keeps whatever
 * it throws, so that one plugin's failure stays its own.
 */
final class Calls {

    private Calls() {}

    /**
     * Runs code on a thread of its own, with a context class loader, and waits for it up to a timeout. The thread is
     * interrupted if the code does not finish in time.
     *
     * @param name what the code does, which names the thread
     * @return what the code returned
     * @throws CallFailedException if the code threw, did not finish in time, or the wait was interrupted
     */
    static <T> T call(String name, @Nullable ClassLoader contextClassLoader, Duration timeout, Callable<T> code)
            throws CallFailedException {
        var task = new FutureTask<>(code);
        var thread = Thread.ofPlatform().name(name).daemon().unstarted(task);
        thread.setContextClassLoader(contextClassLoader);
        thread.start();
        try {
            return task.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (ExecutionException e) {
            throw new CallFailedException(requireNonNullElse(e.getCause(), e));
        } catch (TimeoutException e) {
            task.cancel(true);
            throw new CallFailedException(new TimeoutException(name + " did not finish within " + timeout));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            task.cancel(true);
            throw new CallFailedException(e);
        }
    }

    /**
     * Runs code on the current thread.
     *
     * @return what the code threw, or an empty optional if it returned normally
     */
    static Optional<Throwable> attempt(Runnable code) {
        var task = new FutureTask<>(code, Boolean.TRUE);
        task.run();
        return task.state() == Future.State.FAILED ? Optional.of(task.exceptionNow()) : Optional.empty();
    }
}
