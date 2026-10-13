import javax.annotation.processing.Processor;

/**
 * A harness that compiles in-memory sources through annotation processors, AssertJ assertions on the outcome, and a
 * JUnit extension that gives tests the {@link javax.lang.model.util.Elements} and
 * {@link javax.lang.model.util.Types} of a running compilation.
 *
 * <p>AssertJ and JUnit Jupiter are required transitively:
 * {@link be.imgn.gaia.processing.testing.CompilationAssert} extends AssertJ's assertions, and
 * {@link be.imgn.gaia.processing.testing.CompilationExtension} is a JUnit extension.
 */
module be.imgn.gaia.processing.testing {
    requires transitive java.compiler;
    requires transitive org.assertj.core;
    requires transitive org.junit.jupiter.api;
    requires transitive org.jspecify;

    exports be.imgn.gaia.processing.testing;

    // ProcessorAssert.isRegistered() looks processors up with the service loader.
    uses Processor;
}
