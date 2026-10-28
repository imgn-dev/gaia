package be.imgn.gaia.events;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.Executor;

/**
 * Marks an annotation that subscribes the methods it annotates as {@link Subscribe @Subscribe} does, and has the bus
 * deliver their events through an executor, such as on a given thread.
 *
 * <pre>{@code
 * @SubscribingAnnotation(executor = EdtExecutor.class)
 * @Retention(RetentionPolicy.CLASS)
 * @Target(ElementType.METHOD)
 * public @interface OnEdt {}
 *
 * public final class EdtExecutor implements Executor {
 *     @Override
 *     public void execute(Runnable task) {
 *         EventQueue.invokeLater(task);
 *     }
 * }
 *
 * class Window {
 *     @OnEdt
 *     void onPlaced(OrderPlaced event) {
 *         ...
 *     }
 * }
 * }</pre>
 *
 * <p>The bus creates the executor the first time it {@linkplain EventBus#register(Object) registers} a listener with
 * such methods, with its constructor without parameters, and shares it among all the listeners it registers with them,
 * unless it was {@linkplain EventBus.Builder#executor(Class, java.util.concurrent.Executor) built with another
 * executor} for the annotation. During the delivery of an event, the bus hands such a method a task that calls it, and
 * goes on to the other subscribers without waiting for it.
 *
 * <p>{@code gaia-events-processor} checks such methods as it checks {@code @Subscribe} ones, and reports an error for a
 * method with more than one subscribing annotation, {@code @Subscribe} included. The generated code refers to the
 * annotation, and creates the executor: it reports an error for an annotation or an executor that it cannot access from
 * the package of the listener, and for an executor that is abstract, an inner class, or without an accessible
 * constructor without parameters. On the declaration of the annotation, it reports the same problems for any package
 * the annotation can be used from: a public annotation needs a public executor, with a public constructor without
 * parameters. It reports a retention of {@code SOURCE} too: the processor needs to see the annotation on the methods of
 * classes compiled before, to report the methods that override them.
 *
 * <p>The processor only finds the methods annotated with an annotation that has this meta-annotation itself, not
 * through other annotations.
 *
 * @since 0.1.2
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.ANNOTATION_TYPE)
public @interface SubscribingAnnotation {

    /**
     * Returns the class of the executor that delivers the events, with an accessible constructor without parameters.
     *
     * @return the class of the executor
     */
    Class<? extends Executor> executor();
}
