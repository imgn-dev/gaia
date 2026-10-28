package be.imgn.gaia.events.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import be.imgn.gaia.events.EventBus;
import be.imgn.gaia.events.OnVirtualThread;
import be.imgn.gaia.events.VirtualThreadExecutor;
import be.imgn.gaia.events.tests.Listeners.FailingVirtualThreadListener;
import be.imgn.gaia.events.tests.Listeners.VirtualThreadListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Tests of {@link OnVirtualThread} and {@link VirtualThreadExecutor}. */
class VirtualThreadTest {

    @Test
    void deliversOnAVirtualThread() throws InterruptedException {
        var bus = EventBus.create();
        var listener = new VirtualThreadListener();

        bus.register(listener);
        bus.publish(new OrderPlaced(1));

        assertThat(listener.awaitDelivery()).isTrue();
        assertThat(listener.onVirtualThread()).isTrue();
    }

    @Test
    void handsTheExceptionsOfTheMethodToTheHandler() throws InterruptedException {
        List<String> failures = new CopyOnWriteArrayList<>();
        var handled = new CountDownLatch(1);
        var bus = EventBus.newBuilder()
                .exceptionHandler((event, exception) -> {
                    failures.add(event + ": " + exception.getMessage());
                    handled.countDown();
                })
                .build();

        bus.register(new FailingVirtualThreadListener());
        bus.publish(new OrderPlaced(1));

        assertThat(handled.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(failures).containsExactly("OrderPlaced[id=1]: failed 1");
    }

    @Test
    void runsEachTaskOnANewVirtualThread() throws InterruptedException {
        List<Thread> threads = new CopyOnWriteArrayList<>();
        var ran = new CountDownLatch(2);
        var executor = new VirtualThreadExecutor();

        for (var i = 0; i < 2; i++) {
            executor.execute(() -> {
                threads.add(Thread.currentThread());
                ran.countDown();
            });
        }

        assertThat(ran.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(threads).hasSize(2).allMatch(Thread::isVirtual).doesNotHaveDuplicates();
    }

    // Passing null is the point.
    @Test
    @SuppressWarnings("NullAway")
    void rejectsANullTask() {
        assertThatNullPointerException()
                .isThrownBy(() -> new VirtualThreadExecutor().execute(null))
                .withMessage("task");
    }
}
