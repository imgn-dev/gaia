import be.imgn.gaia.processing.tests.RecordingLoggerFinder;
import be.imgn.gaia.processing.tests.RegisteredProcessor;
import javax.annotation.processing.Processor;

/*
 * Tests of {@code be.imgn.gaia.processing} through {@code be.imgn.gaia.processing.testing}. Compiled as a
 * module, so they can use exported packages only, like any user.
 *
 * <p>RecordingLoggerFinder records what the test compiler logs.
 *
 * <p>Open, so that JUnit can reach every test package, including ones that exist only on some JDKs.
 */
open module be.imgn.gaia.processing.tests {
    requires be.imgn.gaia.processing;
    requires be.imgn.gaia.processing.testing;
    requires jdk.compiler;
    requires org.assertj.core;
    requires org.junit.jupiter.api;

    provides Processor with
            RegisteredProcessor;
    provides System.LoggerFinder with
            RecordingLoggerFinder;
}
