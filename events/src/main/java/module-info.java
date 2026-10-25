import be.imgn.gaia.events.spi.SubscriberIndex;

/**
 * An event bus that delivers events to {@link java.util.function.Consumer}s, and to the
 * {@link be.imgn.gaia.events.Subscribe @Subscribe} methods of listeners through code that
 * {@code gaia-events-processor} generates, without reflection.
 *
 * <p>JSpecify is required transitively: its annotations are part of the signatures this module exports.
 */
module be.imgn.gaia.events {
    requires be.imgn.gaia.core;
    requires transitive org.jspecify;

    exports be.imgn.gaia.events;
    exports be.imgn.gaia.events.spi;

    uses SubscriberIndex;
}
