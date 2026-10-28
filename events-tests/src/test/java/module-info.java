import be.imgn.gaia.events.spi.SubscriberIndex;
import be.imgn.gaia.events.tests.$$Listeners$BaseListener$Subscribers;
import be.imgn.gaia.events.tests.$$Listeners$ExtendingListener$Subscribers;
import be.imgn.gaia.events.tests.$$Listeners$FailingQueuedListener$Subscribers;
import be.imgn.gaia.events.tests.$$Listeners$GenericListener$Subscribers;
import be.imgn.gaia.events.tests.$$Listeners$OrderListener$Subscribers;
import be.imgn.gaia.events.tests.$$Listeners$OtherQueuedListener$Subscribers;
import be.imgn.gaia.events.tests.$$Listeners$Outer$Inner$Subscribers;
import be.imgn.gaia.events.tests.$$Listeners$QueuedListener$Subscribers;
import be.imgn.gaia.events.tests.RecordingLoggerFinder;
import javax.annotation.processing.Processor;

/*
 * Tests of {@code be.imgn.gaia.events} and of its processor. Compiled as a module, so they can use exported packages
 * only, like any user.
 *
 * <p>The listeners are compiled through the processor, which generates their indexes. The provides directive lists
 * those indexes, as the register-services goal of gaia-maven-plugin would add them.
 *
 * <p>RecordingLoggerFinder records what the event bus logs.
 *
 * <p>Open, so that JUnit can reach every test class.
 */
open module be.imgn.gaia.events.tests {
    requires be.imgn.gaia.events;
    requires be.imgn.gaia.processing.testing;
    requires java.compiler;
    requires org.assertj.core;
    requires org.jspecify;
    requires org.junit.jupiter.api;

    uses Processor;

    provides SubscriberIndex with
            $$Listeners$BaseListener$Subscribers,
            $$Listeners$ExtendingListener$Subscribers,
            $$Listeners$FailingQueuedListener$Subscribers,
            $$Listeners$GenericListener$Subscribers,
            $$Listeners$OrderListener$Subscribers,
            $$Listeners$OtherQueuedListener$Subscribers,
            $$Listeners$Outer$Inner$Subscribers,
            $$Listeners$QueuedListener$Subscribers;
    provides System.LoggerFinder with
            RecordingLoggerFinder;
}
