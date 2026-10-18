package be.imgn.gaia.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private ModuleInfo() {}

    /**
     * Makes a module descriptor provide services with the given providers. The directive of each service, if any, is
     * replaced where it stands; the directives of other services are kept, and those of services not provided yet are
     * added at the end. Providing the same services again leaves the file unchanged.
     *
     * @param file the {@code module-info.class} file
     * @param providers the providers of each service, by binary name
     * @return whether the file changed
     */
    static boolean provide(Path file, Map<String, List<String>> providers) throws IOException {
        var original = Files.readAllBytes(file);
        var writer = new ClassWriter(0);
        new ClassReader(original).accept(new ProvidingClass(writer, providers), 0);
        var patched = writer.toByteArray();
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
            super.visitProvide(
                    service, providers.stream().map(ModuleInfo::internalName).toArray(String[]::new));
        }
    }
}
