package be.imgn.gaia.events;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.events.spi.SubscriberIndex;
import java.lang.System.Logger.Level;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Delivers each event it is given to the subscribers of the event's class and of all its supertypes.
 *
 * <pre>{@code
 * EventBus bus = EventBus.create();
 * bus.subscribe(OrderPlaced.class, order -> ship(order));
 * bus.register(new OrderListener());
 * bus.publish(new OrderPlaced(42));
 * }</pre>
 *
 * <p>An event reaches each subscription once, the subscribers of a type before those of its supertypes, and those of
 * one type in the order they were subscribed. The supertypes of a class come after it, a type always before its own
 * supertypes and {@code Object} last; among types that are not supertypes of one another, the superclass comes before
 * the interfaces.
 *
 * <p>A bus built without an {@linkplain Builder#executor(Executor) executor} delivers each event on the thread that
 * publishes it, before {@link #publish(Object)} returns. An event published from a subscriber of the same bus is queued
 * for that thread, and delivered once the event being delivered has reached all its subscribers: the events then arrive
 * in the order they were published. A bus with an executor hands it one task per event, which delivers the event to its
 * subscribers in order; separate events may be delivered concurrently.
 *
 * <p>An exception thrown by a subscriber goes to the {@linkplain Builder#exceptionHandler(BiConsumer) exception
 * handler}, and the event goes on to its other subscribers. By default, the handler logs the exception at
 * {@link Level#ERROR} with {@link System.Logger}. An {@link Error} is not caught, nor is an exception thrown by the
 * handler: it propagates from the delivery, and the events queued meanwhile on the thread are dropped.
 *
 * <p>A bus is thread-safe. Subscribers can be added and removed while events are delivered: each delivery goes to the
 * subscribers a type had when the delivery reached it.
 *
 * @since 0.1.2
 */
public final class EventBus {

    private static final System.Logger LOGGER = System.getLogger(EventBus.class.getName());

    private static final ClassValue<List<Class<?>>> SUPERTYPES = new ClassValue<>() {
        @Override
        protected List<Class<?>> computeValue(Class<?> type) {
            return supertypes(type);
        }
    };

    private static final ClassValue<List<SubscriberIndex>> INDEXES = new ClassValue<>() {
        @Override
        protected List<SubscriberIndex> computeValue(Class<?> type) {
            var superclass = type.getSuperclass();
            var indexes = new ArrayList<SubscriberIndex>();
            if (superclass != null) {
                indexes.addAll(get(superclass));
            }
            index(type).ifPresent(indexes::add);
            return List.copyOf(indexes);
        }
    };

    /** The events each bus being delivered to on the current thread has yet to deliver there. */
    private static final ThreadLocal<Map<EventBus, ArrayDeque<Object>>> QUEUES =
            ThreadLocal.withInitial(IdentityHashMap::new);

    private final @Nullable Executor executor;
    private final BiConsumer<Object, Exception> exceptionHandler;
    private final ConcurrentMap<Class<?>, List<Registration<?>>> registrations = new ConcurrentHashMap<>();

    private EventBus(Builder builder) {
        this.executor = builder.executor;
        this.exceptionHandler = builder.exceptionHandler;
    }

    /**
     * Returns a bus that delivers events on the thread that publishes them, and logs the exceptions of subscribers.
     *
     * @return a new bus
     */
    public static EventBus create() {
        return newBuilder().build();
    }

    /**
     * Returns a builder of a bus, which delivers events on the thread that publishes them, and logs the exceptions of
     * subscribers, unless told otherwise.
     *
     * @return a new builder
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Subscribes to the events of a type and of its subtypes.
     *
     * @param eventType the type of the events
     * @param subscriber what receives them
     * @param <E> the type of the events
     * @return the subscription, which unsubscribes when closed
     * @throws IllegalArgumentException if {@code eventType} is primitive: no event is
     */
    public <E> Subscription subscribe(Class<E> eventType, Consumer<? super E> subscriber) {
        var registration = registration(eventType, subscriber);
        add(registration);
        return new Subscription(() -> remove(registration));
    }

    /**
     * Subscribes the {@link Subscribe @Subscribe} methods of a listener, those of its class and of the classes it
     * extends, through the code {@code gaia-events-processor} generated for each of these classes. That code is found
     * with {@link ServiceLoader}, from the class loader of each class, or from its module layer if it is in a named
     * module: such a module declares the generated classes with {@code provides}, as the {@code register-services} goal
     * of {@code gaia-maven-plugin} adds.
     *
     * <p>The methods of a superclass are subscribed before those of its subclasses, and those of one class in the order
     * they are declared. A method that overrides a {@code @Subscribe} method receives its events, without the
     * annotation.
     *
     * @param listener the listener
     * @return the subscription of all its methods, which unsubscribes them when closed
     * @throws IllegalArgumentException if neither the class of the listener nor any class it extends has generated
     *     code: none has {@code @Subscribe} methods, or none was compiled with {@code gaia-events-processor}
     */
    public Subscription register(Object listener) {
        requireNonNull(listener, "listener");
        var indexes = INDEXES.get(listener.getClass());
        checkArgument(
                !indexes.isEmpty(),
                "%s has no @Subscribe methods, or was not compiled with gaia-events-processor",
                listener.getClass().getName());
        var registered = new ArrayList<Registration<?>>();
        var registrar = new SubscriberIndex.Registrar() {
            @Override
            public <E> void subscribe(Class<E> eventType, Consumer<? super E> subscriber) {
                registered.add(registration(eventType, subscriber));
            }
        };
        for (var index : indexes) {
            index.subscribe(listener, registrar);
        }
        registered.forEach(this::add);
        return new Subscription(() -> registered.forEach(this::remove));
    }

    /**
     * Delivers an event to the subscribers of its class and of its supertypes.
     *
     * @param event the event
     */
    public void publish(Object event) {
        requireNonNull(event, "event");
        if (executor != null) {
            executor.execute(() -> deliver(event));
            return;
        }
        var queues = QUEUES.get();
        var pending = queues.get(this);
        if (pending != null) {
            pending.add(event);
            return;
        }
        pending = new ArrayDeque<>();
        queues.put(this, pending);
        try {
            for (Object next = event; next != null; next = pending.poll()) {
                deliver(next);
            }
        } finally {
            queues.remove(this);
            if (queues.isEmpty()) {
                QUEUES.remove();
            }
        }
    }

    // Whatever a subscriber throws goes to the handler, checked exceptions thrown sneakily included, so that one
    // subscriber cannot keep an event from the others.
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void deliver(Object event) {
        for (var type : SUPERTYPES.get(event.getClass())) {
            for (var registration : registrations.getOrDefault(type, List.of())) {
                try {
                    registration.deliver(event);
                } catch (Exception exception) {
                    exceptionHandler.accept(event, exception);
                }
            }
        }
    }

    private void add(Registration<?> registration) {
        registrations.compute(registration.eventType, (type, current) -> {
            var updated = new ArrayList<Registration<?>>();
            if (current != null) {
                updated.addAll(current);
            }
            updated.add(registration);
            return List.copyOf(updated);
        });
    }

    private void remove(Registration<?> registration) {
        registrations.computeIfPresent(registration.eventType, (type, current) -> {
            var updated = new ArrayList<>(current);
            updated.remove(registration);
            return updated.isEmpty() ? null : List.copyOf(updated);
        });
    }

    private static <E> Registration<E> registration(Class<E> eventType, Consumer<? super E> subscriber) {
        requireNonNull(eventType, "eventType");
        requireNonNull(subscriber, "subscriber");
        checkArgument(!eventType.isPrimitive(), "eventType must not be primitive: %s", eventType);
        return new Registration<>(eventType, subscriber);
    }

    private static void log(Object event, Exception exception) {
        LOGGER.log(Level.ERROR, () -> "A subscriber failed on " + event, exception);
    }

    /** Returns a class and all its supertypes, each type before its own supertypes. */
    private static List<Class<?>> supertypes(Class<?> type) {
        // Breadth first, so that among unrelated types the nearer come first, and the superclass before interfaces.
        var found = new LinkedHashSet<Class<?>>();
        var toVisit = new ArrayDeque<Class<?>>();
        toVisit.add(type);
        while (!toVisit.isEmpty()) {
            var next = toVisit.remove();
            if (found.add(next)) {
                var superclass = next.getSuperclass();
                if (superclass != null) {
                    toVisit.add(superclass);
                }
                toVisit.addAll(List.of(next.getInterfaces()));
            }
        }
        // Then each type is moved after its subtypes: an interface reached early can be a supertype of one reached
        // later, and Object, reached through the superclasses, is a supertype of the interfaces.
        var remaining = new ArrayList<>(found);
        var ordered = new ArrayList<Class<?>>(remaining.size());
        while (!remaining.isEmpty()) {
            var next = remaining.stream()
                    .filter(candidate -> remaining.stream()
                            .noneMatch(other -> other != candidate && candidate.isAssignableFrom(other)))
                    .findFirst()
                    .orElseThrow();
            ordered.add(next);
            remaining.remove(next);
        }
        return List.copyOf(ordered);
    }

    /** Returns the generated index of a class, if it has one. */
    private static Optional<SubscriberIndex> index(Class<?> type) {
        if (type.getClassLoader() == null
                || type.isAnonymousClass()
                || type.isLocalClass()
                || type.isHidden()
                || type.isArray()) {
            return Optional.empty();
        }
        var name = indexName(type);
        var module = type.getModule();
        var layer = module.getLayer();
        var loader = module.isNamed() && layer != null
                ? ServiceLoader.load(layer, SubscriberIndex.class)
                : ServiceLoader.load(SubscriberIndex.class, type.getClassLoader());
        return loader.stream()
                .filter(provider -> provider.type().getName().equals(name))
                .findFirst()
                .map(ServiceLoader.Provider::get);
    }

    /**
     * Returns the binary name of the index generated for a class: in its package, its binary name without the package,
     * preceded by {@code $$} and followed by {@code $Subscribers}.
     */
    private static String indexName(Class<?> type) {
        var packageName = type.getPackageName();
        var prefix = packageName.isEmpty() ? "" : packageName + ".";
        return prefix + "$$" + type.getName().substring(prefix.length()) + "$Subscribers";
    }

    /** Builds an {@link EventBus}. */
    public static final class Builder {

        private @Nullable Executor executor;
        private BiConsumer<Object, Exception> exceptionHandler = EventBus::log;

        private Builder() {}

        /**
         * Makes the bus deliver each event in a task of an executor, rather than on the thread that publishes it.
         *
         * <pre>{@code
         * EventBus bus = EventBus.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor()).build();
         * }</pre>
         *
         * @param executor the executor, which runs one task per event
         * @return this builder
         */
        public Builder executor(Executor executor) {
            this.executor = requireNonNull(executor, "executor");
            return this;
        }

        /**
         * Sets what handles an exception thrown by a subscriber, instead of logging it.
         *
         * @param exceptionHandler what receives the event and the exception, on the thread that delivered the event
         * @return this builder
         */
        public Builder exceptionHandler(BiConsumer<Object, Exception> exceptionHandler) {
            this.exceptionHandler = requireNonNull(exceptionHandler, "exceptionHandler");
            return this;
        }

        /**
         * Builds the bus.
         *
         * @return a new bus
         */
        public EventBus build() {
            return new EventBus(this);
        }
    }

    /** A subscriber of the events of a type. Compared by identity, so that each subscription removes its own. */
    private static final class Registration<E> {

        private final Class<E> eventType;
        private final Consumer<? super E> subscriber;

        Registration(Class<E> eventType, Consumer<? super E> subscriber) {
            this.eventType = eventType;
            this.subscriber = subscriber;
        }

        void deliver(Object event) {
            subscriber.accept(eventType.cast(event));
        }
    }
}
