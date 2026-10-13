package be.imgn.gaia.processing.tests;

import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.StepProcessor;
import java.util.List;

/** A processor registered in this module's descriptor, for {@code ProcessorAssert.isRegistered()} to find. */
public final class RegisteredProcessor extends StepProcessor {

    /** Creates the processor; the service loader does. */
    public RegisteredProcessor() {}

    @Override
    protected List<Step> steps(ProcessingContext context) {
        return List.of();
    }
}
