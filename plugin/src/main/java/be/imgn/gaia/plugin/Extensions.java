package be.imgn.gaia.plugin;

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The extensions of an extension point, as a live view: plugins register and unregister extensions while the host runs.
 *
 * <p>Extensions come in the order of their plugins, a plugin after the plugins it requires, then in the order each
 * plugin registered them.
 *
 * <p>Each extension is a wrapper of the one its plugin registered, which runs each call with that plugin's class loader
 * as the thread's context class loader, and restores the caller's afterwards: a plugin cannot leave its class loader on
 * the host's thread, where the host's {@link java.util.ServiceLoader} lookups would find its providers.
 *
 * <p>Whoever keeps an extension must drop it when it is removed, or its plugin cannot be unloaded:
 * {@link #subscribe(Consumer, Consumer)} tells when.
 *
 * @param <T> the type of the extension point
 * @since 0.1.3
 */
public interface Extensions<T> extends Iterable<T> {

    /**
     * Returns an iterator over the extensions registered when it is created. Extensions registered or unregistered
     * meanwhile do not change it.
     *
     * @return an iterator over the current extensions
     */
    @Override
    Iterator<T> iterator();

    /**
     * Returns a stream of the extensions registered when it is created. Extensions registered or unregistered meanwhile
     * do not change it.
     *
     * @return a stream of the current extensions
     */
    Stream<T> stream();

    /**
     * Tells of each extension as it is added and removed, until the registration is closed.
     *
     * <p>{@code added} gets the extensions registered at the time of this call first, right away. Then each extension
     * registered later goes to {@code added}, and each one unregistered to {@code removed}, on the thread that
     * registers or unregisters it. An extension goes to {@code removed} before its plugin stops.
     *
     * @param added gets each extension added
     * @param removed gets each extension removed
     * @return the registration, which stops telling when it is closed
     */
    Registration subscribe(Consumer<? super T> added, Consumer<? super T> removed);
}
