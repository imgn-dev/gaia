import be.imgn.gaia.core.event.spi.SubscriberIndex;
import be.imgn.gaia.core.tests.event.$$Listeners$BaseListener$Subscribers;
import be.imgn.gaia.core.tests.event.$$Listeners$ExtendingListener$Subscribers;
import be.imgn.gaia.core.tests.event.$$Listeners$FailingQueuedListener$Subscribers;
import be.imgn.gaia.core.tests.event.$$Listeners$FailingVirtualThreadListener$Subscribers;
import be.imgn.gaia.core.tests.event.$$Listeners$GenericListener$Subscribers;
import be.imgn.gaia.core.tests.event.$$Listeners$OrderListener$Subscribers;
import be.imgn.gaia.core.tests.event.$$Listeners$OtherQueuedListener$Subscribers;
import be.imgn.gaia.core.tests.event.$$Listeners$Outer$Inner$Subscribers;
import be.imgn.gaia.core.tests.event.$$Listeners$QueuedListener$Subscribers;
import be.imgn.gaia.core.tests.event.$$Listeners$VirtualThreadListener$Subscribers;
import be.imgn.gaia.core.tests.event.RecordingLoggerFinder;
import javax.annotation.processing.Processor;

/*
 * Tests of {@code be.imgn.gaia.core} through {@code be.imgn.gaia.core.testing}, and of gaia-processor. Compiled as a
 * module, so they can use exported packages only, like any user.
 *
 * <p>The listeners of be.imgn.gaia.core.tests.event are compiled through the processor, which generates their indexes.
 * The provides directive lists those indexes, as the register-services goal of gaia-maven-plugin would add them.
 *
 * <p>RecordingLoggerFinder records what the event bus logs.
 *
 * <p>Open, so that JUnit can reach every test package, including ones that exist only on some JDKs.
 */
open module be.imgn.gaia.core.tests {
    requires be.imgn.gaia.core;
    requires be.imgn.gaia.core.testing;
    requires be.imgn.gaia.processing.testing;
    requires java.compiler;
    requires org.assertj.core;
    requires org.jspecify;
    requires org.junit.jupiter.api;
    requires org.junit.jupiter.params;

    uses Processor;

    provides SubscriberIndex with
            $$Listeners$BaseListener$Subscribers,
            $$Listeners$ExtendingListener$Subscribers,
            $$Listeners$FailingQueuedListener$Subscribers,
            $$Listeners$FailingVirtualThreadListener$Subscribers,
            $$Listeners$GenericListener$Subscribers,
            $$Listeners$OrderListener$Subscribers,
            $$Listeners$OtherQueuedListener$Subscribers,
            $$Listeners$Outer$Inner$Subscribers,
            $$Listeners$QueuedListener$Subscribers,
            $$Listeners$VirtualThreadListener$Subscribers;
    provides System.LoggerFinder with
            RecordingLoggerFinder;
}
