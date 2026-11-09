package be.imgn.gaia.maven;

import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.classfile.attribute.ModuleProvideInfo;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.plugin.MojoExecutionException;

/** Rewrites the {@code provides} directives of a compiled {@code module-info.class}. */
final class ModuleInfo {

    static final String FILE_NAME = "module-info.class";

    private static final String VERSIONS = "META-INF/versions";

    private ModuleInfo() {}

    /**
     * Returns the module descriptors of compiled classes: {@code module-info.class} and, in a multi-release output, the
     * one of each release in {@code META-INF/versions}.
     *
     * @param classes the directory of compiled classes
     * @return the descriptors, the root one first, then by release
     */
    static List<Path> find(Path classes) throws IOException {
        var descriptors = new ArrayList<Path>();
        var root = classes.resolve(FILE_NAME);
        if (Files.isRegularFile(root)) {
            descriptors.add(root);
        }
        var versions = classes.resolve(VERSIONS);
        if (Files.isDirectory(versions)) {
            try (var releases = Files.list(versions)) {
                releases.filter(release -> release.getFileName().toString().matches("[1-9][0-9]*"))
                        .sorted(Comparator.comparingInt(release ->
                                Integer.parseInt(release.getFileName().toString())))
                        .map(release -> release.resolve(FILE_NAME))
                        .filter(Files::isRegularFile)
                        .forEach(descriptors::add);
            }
        }
        return descriptors;
    }

    /**
     * Makes a module descriptor provide services with the given providers. The directive of each service, if any, is
     * replaced where it stands, or removed if the service has no provider; the directives of other services are kept,
     * and those of services not provided yet are added at the end. Providing the same services again leaves the file
     * unchanged.
     *
     * @param file the {@code module-info.class} file
     * @param providers the providers of each service, by binary name, possibly none
     * @return whether the file changed
     * @throws MojoExecutionException if the JDK cannot read the file: malformed, or from a Java version newer than the
     *     JDK
     */
    static boolean provide(Path file, Map<String, List<String>> providers) throws IOException, MojoExecutionException {
        var classFile = ClassFile.of();
        byte[] patched;
        try {
            var model = classFile.parse(Files.readAllBytes(file));
            var module = model.findAttribute(Attributes.module()).orElse(null);
            if (module == null) {
                return false;
            }
            var provides = provides(module.provides(), providers);
            if (names(provides).equals(names(module.provides()))) {
                return false;
            }
            var replacement = ModuleAttribute.of(
                    module.moduleName(),
                    module.moduleFlagsMask(),
                    module.moduleVersion().orElse(null),
                    module.requires(),
                    module.exports(),
                    module.opens(),
                    module.uses(),
                    provides);
            patched = classFile.transformClass(
                    model,
                    (builder, element) -> builder.with(element instanceof ModuleAttribute ? replacement : element));
        } catch (IllegalArgumentException e) {
            // The JDK rejects a class file that is malformed, or of a Java version it does not support.
            throw new MojoExecutionException("Could not rewrite " + file + ": " + e.getMessage(), e);
        }
        Files.write(file, patched);
        return true;
    }

    private static List<ModuleProvideInfo> provides(
            List<ModuleProvideInfo> directives, Map<String, List<String>> providers) {
        // The services whose directive is still to write, by internal name.
        var pending = new LinkedHashMap<String, List<String>>();
        providers.forEach((service, implementations) -> pending.put(internalName(service), implementations));
        var provides = new ArrayList<ModuleProvideInfo>();
        for (var directive : directives) {
            var service = directive.provides().asInternalName();
            var replacement = pending.remove(service);
            if (replacement == null) {
                provides.add(directive);
            } else {
                addDirective(provides, service, replacement);
            }
        }
        pending.forEach((service, implementations) -> addDirective(provides, service, implementations));
        return provides;
    }

    private static void addDirective(List<ModuleProvideInfo> provides, String service, List<String> providers) {
        // A class file cannot hold a directive without providers.
        if (providers.isEmpty()) {
            return;
        }
        provides.add(ModuleProvideInfo.of(
                ClassDesc.ofInternalName(service),
                providers.stream().map(ClassDesc::of).toList()));
    }

    // The services and providers of directives, by internal name, to compare them.
    private static List<Map.Entry<String, List<String>>> names(List<ModuleProvideInfo> provides) {
        return provides.stream()
                .map(provide -> Map.entry(
                        provide.provides().asInternalName(),
                        provide.providesWith().stream()
                                .map(ClassEntry::asInternalName)
                                .toList()))
                .toList();
    }

    private static String internalName(String binaryName) {
        return binaryName.replace('.', '/');
    }
}
