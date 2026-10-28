package be.imgn.gaia.core.event.spi;

import java.lang.annotation.Annotation;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Subscribes the {@link be.imgn.gaia.core.event.Subscribe @Subscribe} methods a class declares, and those with a
 * {@linkplain be.imgn.gaia.core.event.SubscribingAnnotation subscribing annotation}: implemented by the code
 * {@code gaia-processor} generates for that class, and found by
 * {@link be.imgn.gaia.core.event.EventBus#register(Object)} with {@link java.util.ServiceLoader}.
 *
 * <p>Applications do not implement it. For a class {@code com.example.Outer.Listener}, the processor generates
 * {@code com.example.$$Outer$Listener$Subscribers}, the binary name of the class after its package, preceded by
 * {@code $$} and followed by {@code $Subscribers}, and registers it as a provider of this service. The event bus finds
 * it by that name.
 *
 * @since 0.1.2
 */
public interface SubscriberIndex {

    /**
     * Returns the class whose methods this index subscribes.
     *
     * @return the class that declares the methods
     */
    Class<?> listenerType();

    /**
     * Subscribes the methods of a listener.
     *
     * @param listener an instance of {@link #listenerType()}, or of a subclass of it
     * @param registrar what subscribes each method
     */
    void subscribe(Object listener, Registrar registrar);

    /**
     * Subscribes the methods of a listener, on behalf of the event bus.
     *
     * @since 0.1.2
     */
    interface Registrar {

        /**
         * Subscribes a method to the events of a type and of its subtypes.
         *
         * @param eventType the type of the events
         * @param subscriber the method, as a method reference
         * @param <E> the type of the events
         * @throws IllegalArgumentException if {@code eventType} is primitive
         */
        <E> void subscribe(Class<E> eventType, Consumer<? super E> subscriber);

        /**
         * Subscribes a method with a {@linkplain be.imgn.gaia.core.event.SubscribingAnnotation subscribing annotation}
         * to the events of a type and of its subtypes, delivered through the executor of the annotation.
         *
         * @param eventType the type of the events
         * @param annotation the subscribing annotation of the method
         * @param executor what creates the executor the annotation names, if the bus has none for it yet
         * @param subscriber the method, as a method reference
         * @param <E> the type of the events
         * @throws IllegalArgumentException if {@code eventType} is primitive
         */
        <E> void subscribe(
                Class<E> eventType,
                Class<? extends Annotation> annotation,
                Supplier<? extends Executor> executor,
                Consumer<? super E> subscriber);
    }
}
