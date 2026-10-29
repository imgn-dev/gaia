package be.imgn.gaia.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.maven.plugin.MojoExecutionException;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;

/** Rewrites the {@code provides} directives of a compiled {@code module-info.class}. */
final class ModuleInfo {

    static final String FILE_NAME = "module-info.class";

    private static final int API = Opcodes.ASM9;

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
     * @throws MojoExecutionException if ASM cannot read the file: malformed, or from a Java version newer than ASM
     */
    static boolean provide(Path file, Map<String, List<String>> providers) throws IOException, MojoExecutionException {
        var original = Files.readAllBytes(file);
        var writer = new ClassWriter(0);
        byte[] patched;
        try {
            new ClassReader(original).accept(new ProvidingClass(writer, providers), 0);
            patched = writer.toByteArray();
        } catch (IllegalArgumentException
                | IndexOutOfBoundsException
                | ClassCastException
                | NegativeArraySizeException e) {
            // ASM rejects a class file of a Java version it does not support with IllegalArgumentException; a
            // malformed one fails with whichever of these reading it runs into.
            var asm = Objects.requireNonNullElse(
                    ClassReader.class.getPackage().getImplementationVersion(), "(unknown version)");
            throw new MojoExecutionException(
                    "Could not rewrite " + file + " with ASM " + asm + ": " + e.getMessage(), e);
        }
        if (Arrays.equals(original, patched)) {
            return false;
        }
        Files.write(file, patched);
        return true;
    }

    private static String internalName(String binaryName) {
        return binaryName.replace('.', '/');
    }

    private static final class ProvidingClass extends ClassVisitor {

        private final Map<String, List<String>> providers;

        ProvidingClass(ClassVisitor next, Map<String, List<String>> providers) {
            super(API, next);
            this.providers = providers;
        }

        @Override
        public ModuleVisitor visitModule(String name, int access, @Nullable String version) {
            return new ProvidingModule(super.visitModule(name, access, version), providers);
        }
    }

    private static final class ProvidingModule extends ModuleVisitor {

        // The services whose directive is still to write, by internal name.
        private final Map<String, List<String>> pending = new LinkedHashMap<>();

        ProvidingModule(ModuleVisitor next, Map<String, List<String>> providers) {
            super(API, next);
            providers.forEach((service, implementations) -> pending.put(internalName(service), implementations));
        }

        @Override
        public void visitProvide(String service, String... providers) {
            var replacement = pending.remove(service);
            if (replacement == null) {
                super.visitProvide(service, providers);
            } else {
                provide(service, replacement);
            }
        }

        @Override
        public void visitEnd() {
            pending.forEach(this::provide);
            super.visitEnd();
        }

        private void provide(String service, List<String> providers) {
            // A class file cannot hold a directive without providers.
            if (providers.isEmpty()) {
                return;
            }
            super.visitProvide(
                    service, providers.stream().map(ModuleInfo::internalName).toArray(String[]::new));
        }
    }
}
