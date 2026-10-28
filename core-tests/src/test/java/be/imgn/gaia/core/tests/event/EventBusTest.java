package be.imgn.gaia.core.tests.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import be.imgn.gaia.core.event.EventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class EventBusTest {

    private final List<String> received = new ArrayList<>();
    private final EventBus bus = EventBus.create();

    @Test
    void deliversToTheSubscribersOfTheClassThenOfItsSupertypes() {
        bus.subscribe(Object.class, event -> received.add("object"));
        bus.subscribe(OrderEvent.class, event -> received.add("order event"));
        bus.subscribe(OrderPlaced.class, event -> received.add("placed " + event.id()));
        bus.subscribe(OrderPlaced.class, event -> received.add("placed again"));
        bus.subscribe(OrderCancelled.class, event -> received.add("cancelled"));

        bus.publish(new OrderPlaced(1));

        assertThat(received).containsExactly("placed 1", "placed again", "order event", "object");
    }

    @Test
    void putsEachSupertypeAfterItsSubtypesAndObjectLast() {
        for (var type : List.of(Object.class, K.class, J.class, I.class, B.class, C.class)) {
            bus.subscribe(type, event -> received.add(type.getSimpleName()));
        }

        bus.publish(new C());

        // J is reached before I, which extends it, and twice: through C and through I.
        assertThat(received).containsExactly("C", "B", "I", "J", "K", "Object");
    }

    @Test
    void stopsDeliveringToAClosedSubscription() {
        Consumer<OrderPlaced> subscriber = event -> received.add("placed");
        var first = bus.subscribe(OrderPlaced.class, subscriber);
        bus.subscribe(OrderPlaced.class, subscriber);

        first.close();
        first.close();
        bus.publish(new OrderPlaced(1));

        assertThat(received).containsExactly("placed");
    }

    @Test
    void deliversTheEventsSubscribersPublishOnceTheCurrentOneIsDelivered() {
        bus.subscribe(OrderPlaced.class, event -> {
            received.add("first " + event.id());
            bus.publish(new OrderCancelled(event.id()));
        });
        bus.subscribe(OrderPlaced.class, event -> received.add("second " + event.id()));
        bus.subscribe(OrderCancelled.class, event -> received.add("cancelled " + event.id()));

        bus.publish(new OrderPlaced(1));

        assertThat(received).containsExactly("first 1", "second 1", "cancelled 1");
    }

    @Test
    void deliversRightAwayTheEventsSubscribersPublishToAnotherBus() {
        var other = EventBus.create();
        other.subscribe(OrderCancelled.class, event -> received.add("cancelled " + event.id()));
        bus.subscribe(OrderPlaced.class, event -> {
            other.publish(new OrderCancelled(event.id()));
            received.add("placed " + event.id());
        });

        bus.publish(new OrderPlaced(1));

        assertThat(received).containsExactly("cancelled 1", "placed 1");
    }

    @Test
    void handsTheExceptionsOfSubscribersToTheHandlerAndGoesOn() {
        var failures = new ArrayList<String>();
        var bus = EventBus.newBuilder()
                .exceptionHandler((event, exception) -> failures.add(event + ": " + exception.getMessage()))
                .build();
        bus.subscribe(OrderPlaced.class, event -> {
            throw new IllegalStateException("failed");
        });
        bus.subscribe(OrderPlaced.class, event -> received.add("placed " + event.id()));

        bus.publish(new OrderPlaced(1));

        assertThat(failures).containsExactly("OrderPlaced[id=1]: failed");
        assertThat(received).containsExactly("placed 1");
    }

    @Test
    void logsTheExceptionsOfSubscribersByDefault() {
        bus.subscribe(OrderPlaced.class, event -> {
            throw new IllegalStateException("failed");
        });

        bus.publish(new OrderPlaced(7));

        assertThat(RecordingLoggerFinder.records(EventBus.class.getName()))
                .contains("ERROR A subscriber failed on OrderPlaced[id=7]: failed");
    }

    @Test
    void letsErrorsAndExceptionsOfTheHandlerPropagateAndDropsTheQueuedEvents() {
        bus.subscribe(OrderPlaced.class, event -> {
            bus.publish(new OrderCancelled(event.id()));
            throw new AssertionError("broken");
        });
        bus.subscribe(OrderCancelled.class, event -> received.add("cancelled " + event.id()));

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> bus.publish(new OrderPlaced(1)))
                .withMessage("broken");
        assertThat(received).isEmpty();
        bus.publish(new OrderCancelled(2));
        assertThat(received).containsExactly("cancelled 2");

        var failing = EventBus.newBuilder()
                .exceptionHandler((event, exception) -> {
                    throw new IllegalStateException("handler failed", exception);
                })
                .build();
        failing.subscribe(OrderPlaced.class, event -> {
            throw new IllegalArgumentException("failed");
        });
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> failing.publish(new OrderPlaced(3)))
                .withMessage("handler failed");
    }

    @Test
    void deliversEachEventInATaskOfTheExecutor() {
        var tasks = new ArrayList<Runnable>();
        var bus = EventBus.newBuilder().executor(tasks::add).build();
        bus.subscribe(OrderPlaced.class, event -> received.add("first " + event.id()));
        bus.subscribe(OrderPlaced.class, event -> received.add("second " + event.id()));

        bus.publish(new OrderPlaced(1));
        bus.publish(new OrderPlaced(2));

        assertThat(tasks).hasSize(2);
        assertThat(received).isEmpty();
        tasks.get(1).run();
        tasks.get(0).run();
        assertThat(received).containsExactly("first 2", "second 2", "first 1", "second 1");
    }

    // Passing null is the point.
    @Test
    @SuppressWarnings("NullAway")
    void rejectsNullsAndPrimitiveEventTypes() {
        assertThatNullPointerException()
                .isThrownBy(() -> bus.subscribe(null, event -> {}))
                .withMessage("eventType");
        assertThatNullPointerException()
                .isThrownBy(() -> bus.subscribe(OrderPlaced.class, null))
                .withMessage("subscriber");
        assertThatNullPointerException().isThrownBy(() -> bus.register(null)).withMessage("listener");
        assertThatNullPointerException().isThrownBy(() -> bus.publish(null)).withMessage("event");
        assertThatNullPointerException()
                .isThrownBy(() -> EventBus.newBuilder().executor(null))
                .withMessage("executor");
        assertThatNullPointerException()
                .isThrownBy(() -> EventBus.newBuilder().executor(null, Runnable::run))
                .withMessage("annotation");
        assertThatNullPointerException()
                .isThrownBy(() -> EventBus.newBuilder().executor(Queued.class, null))
                .withMessage("executor");
        assertThatNullPointerException()
                .isThrownBy(() -> EventBus.newBuilder().exceptionHandler(null))
                .withMessage("exceptionHandler");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> bus.subscribe(int.class, event -> {}))
                .withMessage("eventType must not be primitive: int");
    }

    interface J {}

    interface I extends J {}

    interface K {}

    static class B implements K {}

    static class C extends B implements J, I {}
}
