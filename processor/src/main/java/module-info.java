import be.imgn.gaia.processor.SubscribeProcessor;
import javax.annotation.processing.Processor;

/**
 * The annotation processor of {@code gaia-core}, which generates the code that subscribes the
 * {@code @Subscribe} methods of each class. It belongs on the annotation processor path of the code that declares
 * such methods, and exports nothing: javac finds the processor as a service.
 */
module be.imgn.gaia.processor {
    requires be.imgn.gaia.processing;

    provides Processor with
            SubscribeProcessor;
}
