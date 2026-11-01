package be.imgn.gaia.plugin.internal;

import be.imgn.gaia.plugin.Registration;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/** The extensions that plugins registered, by extension point, and the listeners of each point. */
final class ExtensionRegistry {

    private static final System.Logger LOGGER = System.getLogger(ExtensionRegistry.class.getName());

    private final Object lock = new Object();
    private final Map<Class<?>, List<Listener>> listeners = new HashMap<>();

    // Replaced, never changed, so that the host reads the extensions without waiting.
    private volatile Map<Class<?>, List<Entry>> entries = Map.of();
    private long sequence;

    /**
     * Registers an extension, and tells the listeners of its point.
     *
     * @param order the position of the extension's plugin, a plugin after the plugins it requires
     * @return the registration, which removes the extension
     */
    Registration add(Class<?> point, Object extension, int order) {
        Entry entry;
        synchronized (lock) {
            entry = new Entry(extension, order, sequence++);
            var list = new ArrayList<>(entries.getOrDefault(point, List.of()));
            list.add(entry);
            list.sort(Comparator.comparingInt(Entry::order).thenComparingLong(Entry::sequence));
            entries = with(point, list);
            tellAll(point, extension, Listener::added);
        }
        return new OnceRegistration(() -> remove(point, entry));
    }

    /** Returns the extensions of a point, in order. */
    List<Object> extensions(Class<?> point) {
        return entries.getOrDefault(point, List.of()).stream()
                .map(Entry::extension)
                .toList();
    }

    /** Tells a listener of the current extensions of a point, then of each one added or removed. */
    Registration subscribe(Class<?> point, Consumer<Object> added, Consumer<Object> removed) {
        var listener = new Listener(added, removed);
        synchronized (lock) {
            listeners.computeIfAbsent(point, p -> new ArrayList<>()).add(listener);
            for (var extension : extensions(point)) {
                tell(added, extension);
            }
        }
        return new OnceRegistration(() -> unsubscribe(point, listener));
    }

    private void remove(Class<?> point, Entry entry) {
        synchronized (lock) {
            var list = new ArrayList<>(entries.getOrDefault(point, List.of()));
            if (list.remove(entry)) {
                entries = with(point, list);
                tellAll(point, entry.extension(), Listener::removed);
            }
        }
    }

    private void unsubscribe(Class<?> point, Listener listener) {
        synchronized (lock) {
            var list = listeners.get(point);
            if (list != null) {
                list.remove(listener);
            }
        }
    }

    private Map<Class<?>, List<Entry>> with(Class<?> point, List<Entry> list) {
        var copy = new HashMap<>(entries);
        if (list.isEmpty()) {
            copy.remove(point);
        } else {
            copy.put(point, List.copyOf(list));
        }
        return Map.copyOf(copy);
    }

    private void tellAll(Class<?> point, Object extension, Function<Listener, Consumer<Object>> consumer) {
        for (var listener : List.copyOf(listeners.getOrDefault(point, List.of()))) {
            tell(consumer.apply(listener), extension);
        }
    }

    private static void tell(Consumer<Object> consumer, Object extension) {
        Calls.attempt(() -> consumer.accept(extension))
                .ifPresent(failure -> LOGGER.log(Level.WARNING, "A listener of extensions failed", failure));
    }

    private record Entry(Object extension, int order, long sequence) {}

    // Not a record: two subscriptions with the same consumers are still two subscriptions.
    private static final class Listener {

        private final Consumer<Object> added;
        private final Consumer<Object> removed;

        Listener(Consumer<Object> added, Consumer<Object> removed) {
            this.added = added;
            this.removed = removed;
        }

        Consumer<Object> added() {
            return added;
        }

        Consumer<Object> removed() {
            return removed;
        }
    }
}
