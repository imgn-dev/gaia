package be.imgn.gaia.maven;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Provides;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleInfoTest {

    private static final Map<String, String> TYPES = Map.of(
            "Service", "package p; public interface Service {}",
            "Other", "package p; public interface Other {}",
            "A", "package p; public class A implements Service {}",
            "B", "package p; public class B implements Service {}",
            "C", "package p; public class C implements Other {}",
            "Outer", "package p; public class Outer { public static class Nested implements Service {} }");

    @TempDir
    Path directory;

    @Test
    void addsTheDirectivesOfNewServices() throws Exception {
        var moduleInfo = compile("module m { exports p; }");

        assertThat(ModuleInfo.provide(moduleInfo, Map.of("p.Service", List.of("p.B", "p.A", "p.Outer$Nested"))))
                .isTrue();

        assertThat(provides(moduleInfo))
                .containsExactly(Map.entry("p.Service", List.of("p.B", "p.A", "p.Outer$Nested")));
    }

    @Test
    void replacesTheDirectiveOfAService() throws Exception {
        var moduleInfo = compile("module m { provides p.Service with p.A; provides p.Other with p.C; }");

        assertThat(ModuleInfo.provide(moduleInfo, Map.of("p.Service", List.of("p.B"))))
                .isTrue();

        assertThat(provides(moduleInfo))
                .containsOnly(Map.entry("p.Service", List.of("p.B")), Map.entry("p.Other", List.of("p.C")));
    }

    @Test
    void removesTheDirectiveOfAServiceWithoutProviders() throws Exception {
        var moduleInfo = compile("module m { provides p.Service with p.A; provides p.Other with p.C; }");

        assertThat(ModuleInfo.provide(moduleInfo, Map.of("p.Service", List.of())))
                .isTrue();

        assertThat(provides(moduleInfo)).containsOnly(Map.entry("p.Other", List.of("p.C")));
    }

    @Test
    void addsNoDirectiveForANewServiceWithoutProviders() throws Exception {
        var moduleInfo = compile("module m { provides p.Other with p.C; }");

        ModuleInfo.provide(moduleInfo, Map.of("p.Service", List.of()));

        assertThat(provides(moduleInfo)).containsOnly(Map.entry("p.Other", List.of("p.C")));
    }

    @Test
    void keepsTheRestOfTheDescriptor() throws Exception {
        var moduleInfo =
                compile("open module m { requires java.logging; exports p; uses p.Other; provides p.Other with p.C; }");
        var before = descriptor(moduleInfo);

        ModuleInfo.provide(moduleInfo, Map.of("p.Service", List.of("p.A")));

        var after = descriptor(moduleInfo);
        assertThat(after.name()).isEqualTo(before.name());
        assertThat(after.modifiers()).isEqualTo(before.modifiers());
        assertThat(after.requires()).isEqualTo(before.requires());
        assertThat(after.exports()).isEqualTo(before.exports());
        assertThat(after.uses()).isEqualTo(before.uses());
        assertThat(provides(moduleInfo))
                .containsOnly(Map.entry("p.Service", List.of("p.A")), Map.entry("p.Other", List.of("p.C")));
    }

    @Test
    void leavesTheFileUnchangedWhenProvidingTheSameServicesAgain() throws Exception {
        var moduleInfo = compile("module m { provides p.Other with p.C; }");
        var providers = Map.of("p.Service", List.of("p.A", "p.B"), "p.Other", List.of("p.C"));
        ModuleInfo.provide(moduleInfo, providers);
        var patched = Files.readAllBytes(moduleInfo);

        assertThat(ModuleInfo.provide(moduleInfo, providers)).isFalse();

        assertThat(moduleInfo).hasBinaryContent(patched);
    }

    @Test
    void findsTheDescriptorOfEachReleaseOfAMultiReleaseOutput() throws Exception {
        var classes = directory.resolve("classes");
        var files = List.of(
                "module-info.class",
                "META-INF/versions/21/module-info.class",
                "META-INF/versions/9/module-info.class",
                "META-INF/versions/11/p/A.class",
                "META-INF/versions/latest/module-info.class",
                "p/module-info.class");
        for (var file : files) {
            write(classes.resolve(file), "");
        }

        assertThat(ModuleInfo.find(classes))
                .containsExactly(
                        classes.resolve("module-info.class"),
                        classes.resolve("META-INF/versions/9/module-info.class"),
                        classes.resolve("META-INF/versions/21/module-info.class"));
    }

    @Test
    void findsNoDescriptorOutsideAModule() throws Exception {
        var classes = directory.resolve("classes");
        write(classes.resolve("p/A.class"), "");

        assertThat(ModuleInfo.find(classes)).isEmpty();
        assertThat(ModuleInfo.find(directory.resolve("missing"))).isEmpty();
    }

    private Path compile(String moduleInfo) throws IOException {
        var sources = directory.resolve("src");
        var files = new ArrayList<String>();
        files.add(write(sources.resolve("module-info.java"), moduleInfo));
        TYPES.forEach((name, declaration) -> files.add(write(sources.resolve("p/" + name + ".java"), declaration)));
        var classes = directory.resolve("classes");
        var arguments = new ArrayList<>(List.of("-d", classes.toString(), "-Xlint:none"));
        arguments.addAll(files);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler.run(null, null, null, arguments.toArray(String[]::new)))
                .as("javac exit code")
                .isZero();
        return classes.resolve(ModuleInfo.FILE_NAME);
    }

    private static String write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file.toString();
    }

    private static ModuleDescriptor descriptor(Path moduleInfo) throws IOException {
        try (InputStream in = Files.newInputStream(moduleInfo)) {
            return ModuleDescriptor.read(in);
        }
    }

    private static Map<String, List<String>> provides(Path moduleInfo) throws IOException {
        return descriptor(moduleInfo).provides().stream()
                .collect(Collectors.toMap(Provides::service, Provides::providers));
    }
}
