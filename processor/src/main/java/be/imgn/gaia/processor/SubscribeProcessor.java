package be.imgn.gaia.processor;

import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.StepProcessor;
import java.util.List;

/**
 * Generates, for each class that declares {@code @Subscribe} methods, or methods with a subscribing annotation, the
 * {@code SubscriberIndex} that subscribes them, and registers it as a provider of that service.
 */
public final class SubscribeProcessor extends StepProcessor {

    /** Creates the processor, as javac does. */
    public SubscribeProcessor() {}

    @Override
    protected List<Step> steps(ProcessingContext context) {
        return List.of(new SubscribeStep(context));
    }
}
