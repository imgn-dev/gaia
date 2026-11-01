package be.imgn.gaia.plugin.internal;

import be.imgn.gaia.plugin.PluginId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The plugins of the plugins directory, in the order they start: a plugin after the plugins it requires.
 *
 * @param ordered the plugins that one folder each holds: first those in order, then those in a cycle, by id
 * @param cyclic the plugins that require themselves, through other plugins, or require such a plugin
 * @param duplicates the plugins that several folders hold, with those folders
 * @param requires the plugins each plugin of {@code ordered} requires, in order
 */
record Plan(
        List<Folder> ordered,
        Set<PluginId> cyclic,
        Map<PluginId, List<Folder>> duplicates,
        Map<PluginId, List<PluginId>> requires) {

    /** Orders the folders of the plugins directory. */
    static Plan of(List<Folder> folders) {
        var byName = new TreeMap<String, Folder>();
        var duplicates = new LinkedHashMap<PluginId, List<Folder>>();
        folders.stream()
                .collect(Collectors.groupingBy(Folder::id, LinkedHashMap::new, Collectors.toList()))
                .forEach((id, list) -> {
                    if (list.size() == 1) {
                        byName.put(id.name(), list.getFirst());
                    } else {
                        duplicates.put(id, List.copyOf(list));
                    }
                });
        var requires = new HashMap<PluginId, List<PluginId>>();
        for (var folder : byName.values()) {
            requires.put(
                    folder.id(),
                    folder.requires().stream()
                            .filter(name -> byName.containsKey(name)
                                    && !name.equals(folder.id().name()))
                            .sorted()
                            .map(PluginId::new)
                            .toList());
        }
        var placed = new LinkedHashMap<PluginId, Folder>();
        var progress = true;
        while (progress) {
            progress = false;
            for (var iterator = byName.values().iterator(); iterator.hasNext(); ) {
                var folder = iterator.next();
                if (placed.keySet().containsAll(requires.getOrDefault(folder.id(), List.of()))) {
                    placed.put(folder.id(), folder);
                    iterator.remove();
                    progress = true;
                }
            }
        }
        var ordered = Stream.concat(placed.values().stream(), byName.values().stream())
                .toList();
        var cyclic = byName.values().stream().map(Folder::id).collect(Collectors.toUnmodifiableSet());
        return new Plan(ordered, cyclic, Map.copyOf(duplicates), Map.copyOf(requires));
    }

    /** Returns every folder, those of {@link #ordered()} first, then those of the duplicates. */
    List<Folder> all() {
        var all = new ArrayList<>(ordered);
        duplicates.values().forEach(all::addAll);
        return List.copyOf(all);
    }

    /** Returns whether a folder holds the plugin. */
    boolean contains(PluginId id) {
        return duplicates.containsKey(id)
                || ordered.stream().anyMatch(folder -> folder.id().equals(id));
    }

    /** Returns whether a plugin and its folder are among those that may start: one folder each, no cycle. */
    boolean canStart(Folder folder) {
        return !cyclic.contains(folder.id()) && ordered.stream().anyMatch(folder::sameAs);
    }

    /** Returns the plugin and the plugins it requires, directly or not. */
    Set<PluginId> withRequirements(PluginId id) {
        var all = new LinkedHashMap<PluginId, Boolean>();
        var pending = new ArrayList<>(List.of(id));
        while (!pending.isEmpty()) {
            var next = pending.removeLast();
            if (all.putIfAbsent(next, Boolean.TRUE) == null) {
                pending.addAll(requires.getOrDefault(next, List.of()));
            }
        }
        return all.keySet();
    }
}
