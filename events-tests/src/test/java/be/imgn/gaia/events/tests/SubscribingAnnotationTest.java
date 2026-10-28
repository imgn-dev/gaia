package be.imgn.gaia.events.tests;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.events.EventBus;
import be.imgn.gaia.events.tests.Listeners.FailingQueuedListener;
import be.imgn.gaia.events.tests.Listeners.OtherQueuedListener;
import be.imgn.gaia.events.tests.Listeners.QueuedListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests of the methods with a subscribing annotation, {@link Queued}, whose executor is {@link QueueExecutor}. */
class SubscribingAnnotationTest {

    private final List<String> failures = new ArrayList<>();
    private final EventBus bus = EventBus.newBuilder()
            .exceptionHandler((event, exception) -> failures.add(event + ": " + exception.getMessage()))
            .build();

    @BeforeEach
    void resetTheExecutors() {
        QueueExecutor.reset();
    }

    @Test
    void deliversThroughTheExecutorOfTheAnnotation() {
        var listener = new QueuedListener();
        bus.register(listener);

        bus.publish(new OrderPlaced(1));
        bus.publish(new OrderPlaced(2));

        assertThat(listener.received()).containsExactly("placed 1", "placed 2");
        assertThat(QueueExecutor.queued()).isEqualTo(2);
        QueueExecutor.runTasks();
        assertThat(listener.received()).containsExactly("placed 1", "placed 2", "queued 1", "queued 2");
    }

    @Test
    void createsTheExecutorOnceForEachBus() {
        var other = EventBus.create();

        bus.register(new QueuedListener());
        bus.register(new OtherQueuedListener());
        assertThat(QueueExecutor.created()).isEqualTo(1);
        other.register(new QueuedListener());
        assertThat(QueueExecutor.created()).isEqualTo(2);
    }

    @Test
    void usesTheExecutorTheBusWasBuiltWithForTheAnnotation() {
        var direct = EventBus.newBuilder().executor(Queued.class, Runnable::run).build();
        var listener = new QueuedListener();

        direct.register(listener);
        direct.publish(new OrderPlaced(1));

        assertThat(listener.received()).containsExactly("placed 1", "queued 1");
        assertThat(QueueExecutor.created()).isZero();
    }

    @Test
    void doesNotCallTheMethodOfASubscriptionClosedBeforeItsTaskRuns() {
        var listener = new QueuedListener();
        var subscription = bus.register(listener);

        bus.publish(new OrderPlaced(1));
        subscription.close();
        QueueExecutor.runTasks();

        assertThat(listener.received()).containsExactly("placed 1");
    }

    @Test
    void handsTheExceptionsOfTheMethodToTheHandler() {
        bus.register(new FailingQueuedListener());

        bus.publish(new OrderPlaced(1));
        assertThat(failures).isEmpty();
        QueueExecutor.runTasks();

        assertThat(failures).containsExactly("OrderPlaced[id=1]: failed 1");
    }

    @Test
    void handsTheExceptionsOfTheExecutorToTheHandlerAndGoesOn() {
        var rejecting = EventBus.newBuilder()
                .executor(Queued.class, task -> {
                    throw new RejectedExecutionException("rejected");
                })
                .exceptionHandler((event, exception) -> failures.add(event + ": " + exception.getMessage()))
                .build();
        var listener = new QueuedListener();
        var other = new QueuedListener();

        rejecting.register(listener);
        rejecting.register(other);
        rejecting.publish(new OrderPlaced(1));

        assertThat(failures).containsExactly("OrderPlaced[id=1]: rejected", "OrderPlaced[id=1]: rejected");
        assertThat(listener.received()).containsExactly("placed 1");
        assertThat(other.received()).containsExactly("placed 1");
    }
}
