package be.imgn.gaia.processing.testing;

import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import javax.annotation.processing.Processor;
import org.assertj.core.api.AbstractAssert;

/**
 * AssertJ assertions on an annotation processor class.
 *
 * <pre>{@code
 * assertThat(MapperProcessor.class).isRegistered();
 * }</pre>
 */
public final class ProcessorAssert extends AbstractAssert<ProcessorAssert, Class<? extends Processor>> {

    private ProcessorAssert(Class<? extends Processor> actual) {
        super(actual, ProcessorAssert.class);
    }

    /**
     * Returns assertions on a processor class.
     *
     * @param actual the processor class
     * @return the assertions
     */
    public static ProcessorAssert assertThat(Class<? extends Processor> actual) {
        return new ProcessorAssert(actual);
    }

    /**
     * Verifies that javac can find the processor: that the service loader lists it as a {@link Processor}, through
     * {@code META-INF/services/javax.annotation.processing.Processor} on the class path, or through {@code provides
     * javax.annotation.processing.Processor with ...} in its {@code module-info.java} on the module path. A processor
     * that is not registered silently does nothing.
     *
     * @return these assertions
     */
    public ProcessorAssert isRegistered() {
        isNotNull();
        List<Class<? extends Processor>> registered = ServiceLoader.load(Processor.class).stream()
                .map(ServiceLoader.Provider::type)
                .toList();
        if (!registered.contains(actual)) {
            failWithMessage(
                    "Expected %s to be registered as an annotation processor, but the registered ones were:%n%s",
                    actual.getName(),
                    registered.isEmpty()
                            ? "  (none)"
                            : registered.stream()
                                    .map(type -> "  " + type.getName())
                                    .collect(Collectors.joining(System.lineSeparator())));
        }
        return this;
    }
}
