package be.imgn.gaia.events;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * What {@link EventBus#subscribe(Class, java.util.function.Consumer)} or {@link EventBus#register(Object)} subscribed,
 * until it is closed. The bus holds its subscribers strongly: closing the subscription is what lets them go.
 *
 * <pre>{@code
 * try (Subscription subscription = bus.subscribe(OrderPlaced.class, orders::add)) {
 *     bus.publish(new OrderPlaced(42));
 * }
 * }</pre>
 *
 * @since 0.1.2
 */
public final class Subscription implements AutoCloseable {

    private final AtomicBoolean closed = new AtomicBoolean();
    private final Runnable unsubscribe;

    Subscription(Runnable unsubscribe) {
        this.unsubscribe = requireNonNull(unsubscribe, "unsubscribe");
    }

    /**
     * Unsubscribes what this subscription subscribed, so that it receives no event published from then on. An event
     * being delivered meanwhile may still reach it, but not a method with a {@linkplain SubscribingAnnotation
     * subscribing annotation}: the tasks its executor runs from then on do not call it. Closing the subscription again
     * does nothing.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            unsubscribe.run();
        }
    }
}
