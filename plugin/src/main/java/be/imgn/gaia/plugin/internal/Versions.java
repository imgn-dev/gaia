package be.imgn.gaia.plugin.internal;

import be.imgn.gaia.core.base.Version;
import be.imgn.gaia.plugin.RequiresVersion;
import java.lang.annotation.AnnotationFormatError;
import java.util.Arrays;
import java.util.Optional;

/** Checks the {@link RequiresVersion} annotations of a plugin's module against the modules its layer reads. */
final class Versions {

    private Versions() {}

    /**
     * Checks the versions a plugin requires.
     *
     * @param plugin the plugin's module
     * @param layer the plugin's layer, whose modules and parents hold the modules the plugin requires
     * @return why the plugin cannot be loaded, or an empty optional if every version suits it
     */
    static Optional<String> problem(Module plugin, ModuleLayer layer) {
        RequiresVersion[] requirements;
        try {
            requirements = plugin.getAnnotationsByType(RequiresVersion.class);
        } catch (AnnotationFormatError | TypeNotPresentException e) {
            return Optional.of("Its @RequiresVersion annotations cannot be read: " + e.getMessage());
        }
        return Arrays.stream(requirements)
                .map(requirement -> problem(requirement, layer))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<String> problem(RequiresVersion requirement, ModuleLayer layer) {
        var module = requirement.module();
        var lowest = Version.tryParse(requirement.atLeast());
        if (lowest.isEmpty()) {
            return Optional.of("It requires " + module + " " + requirement.atLeast() + ", not a semantic version");
        }
        var actual = layer.findModule(module)
                .flatMap(found -> found.getDescriptor().rawVersion())
                .flatMap(Version::tryParse);
        if (actual.isEmpty()) {
            return Optional.of("It requires " + module + " " + lowest.get()
                    + " or later, which is missing or has no semantic version");
        }
        var suits = actual.get().compareTo(lowest.get()) >= 0
                && actual.get().major() == lowest.get().major();
        return suits
                ? Optional.empty()
                : Optional.of("It requires " + module + " " + lowest.get() + " or a later "
                        + lowest.get().major() + ".x version, but it is " + actual.get());
    }
}
