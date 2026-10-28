package be.imgn.gaia.events;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Subscribes a method as {@link Subscribe @Subscribe} does, but has each of its events delivered on a new virtual
 * thread, with {@link VirtualThreadExecutor}.
 *
 * <pre>{@code
 * class Indexer {
 *     @OnVirtualThread
 *     void onPlaced(OrderPlaced event) {
 *         ...
 *     }
 * }
 * }</pre>
 *
 * <p>The events reach the method in no particular order, and the method can run on several threads at once: the
 * listener must be thread-safe. As for any {@linkplain SubscribingAnnotation subscribing annotation},
 * {@link EventBus.Builder#executor(Class, java.util.concurrent.Executor)} can give the bus another executor for it.
 *
 * @since 0.1.2
 */
@SubscribingAnnotation(executor = VirtualThreadExecutor.class)
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface OnVirtualThread {}
