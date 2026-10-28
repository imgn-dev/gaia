package be.imgn.gaia.core.event;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that receives events once its listener is {@linkplain EventBus#register(Object) registered}: the
 * events of the type of its parameter, and of the subtypes of that type.
 *
 * <pre>{@code
 * class OrderListener {
 *     @Subscribe
 *     void onPlaced(OrderPlaced event) {
 *         ...
 *     }
 * }
 * }</pre>
 *
 * <p>{@code gaia-processor}, on the annotation processor path, generates for each class with such methods the code that
 * subscribes them, which calls them directly. It reports an error for a method that this code cannot call as a
 * subscriber:
 *
 * <ul>
 *   <li>a private method, or one in a private nested class: the generated code is in the package of the class;
 *   <li>a static method;
 *   <li>a method of an interface;
 *   <li>a method that does not take exactly one parameter, or whose parameter is of a primitive type, an array type, a
 *       type variable or a parameterized type, or of a type that is not accessible from the package of the class;
 *   <li>a method that does not return {@code void};
 *   <li>a method that declares checked exceptions;
 *   <li>a method that overrides a {@code @Subscribe} method, or one with a {@linkplain SubscribingAnnotation
 *       subscribing annotation}: registering the listener subscribes the overridden method, which calls the overriding
 *       one, so annotating both would deliver each event twice. Overriding such a method without the annotation is
 *       enough;
 *   <li>a method that also has a subscribing annotation.
 * </ul>
 *
 * <p>javac hands annotation processors no method of a local or anonymous class, so the processor cannot report the
 * annotation there, and generates nothing for it: registering an instance of such a class subscribes only the methods
 * of the classes it extends.
 *
 * <p>The annotation is retained in class files, so that the processor sees it on the methods of classes compiled
 * before, but not at run time: nothing reads it by reflection.
 *
 * @since 0.1.2
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Subscribe {}
