package be.imgn.gaia.core.tests.event;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Queues the tasks it is given, until {@link #runTasks()} runs them. The queue is shared by all instances, which the
 * event bus creates, and so is their count.
 */
final class QueueExecutor implements Executor {

    private static final List<Runnable> TASKS = new ArrayList<>();
    private static final AtomicInteger CREATED = new AtomicInteger();

    QueueExecutor() {
        CREATED.incrementAndGet();
    }

    @Override
    public void execute(Runnable task) {
        TASKS.add(task);
    }

    /** Runs the queued tasks, in the order they were queued, and empties the queue. */
    static void runTasks() {
        var tasks = List.copyOf(TASKS);
        TASKS.clear();
        tasks.forEach(Runnable::run);
    }

    static int queued() {
        return TASKS.size();
    }

    static int created() {
        return CREATED.get();
    }

    /** Forgets the queued tasks and the instances created. */
    static void reset() {
        TASKS.clear();
        CREATED.set(0);
    }
}
