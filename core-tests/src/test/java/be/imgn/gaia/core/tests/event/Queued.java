package be.imgn.gaia.core.tests.event;

import be.imgn.gaia.core.event.SubscribingAnnotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Subscribes a method whose events {@link QueueExecutor} queues. */
@SubscribingAnnotation(executor = QueueExecutor.class)
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
@interface Queued {}
