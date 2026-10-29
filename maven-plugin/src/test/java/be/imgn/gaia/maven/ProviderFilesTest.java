package be.imgn.gaia.maven;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProviderFilesTest {

    @TempDir
    Path classes;

    @Test
    void readsEveryServiceByDefault() throws Exception {
        classes("p/A", "p/B", "q/C");
        service("p.Service", "p.A", "p.B");
        service("q.Other", "q.C");

        assertThat(ProviderFiles.read(classes, List.of()))
                .containsExactly(entry("p.Service", List.of("p.A", "p.B")), entry("q.Other", List.of("q.C")));
    }

    @Test
    void sortsServicesByName() throws Exception {
        classes("p/A");
        service("z.Service", "p.A");
        service("a.Service", "p.A");
        service("m.Service", "p.A");

        assertThat(ProviderFiles.read(classes, List.of()).keySet())
                .containsExactly("a.Service", "m.Service", "z.Service");
    }

    @Test
    void readsTheFileFormatOfServiceLoader() throws Exception {
        classes("p/B", "p/A", "p/Outer$Nested");
        service("p.Service", "# Providers", "", "  p.B  ", "p.A # the other one", "\tp.Outer$Nested", "p.B", "#p.C");

        assertThat(ProviderFiles.read(classes, List.of()))
                .containsExactly(entry("p.Service", List.of("p.B", "p.A", "p.Outer$Nested")));
    }

    @Test
    void readsOnlyTheGivenServices() throws Exception {
        classes("p/A", "q/C");
        service("p.Service", "p.A");
        service("q.Other", "q.C");

        assertThat(ProviderFiles.read(classes, List.of("q.Other"))).containsExactly(entry("q.Other", List.of("q.C")));
    }

    @Test
    void readsServicesWithoutProviders() throws Exception {
        classes("p/A");
        service("p.Service", "p.A");
        service("p.Empty", "# nothing yet", "");

        assertThat(ProviderFiles.read(classes, List.of()))
                .containsExactly(entry("p.Empty", List.of()), entry("p.Service", List.of("p.A")));
    }

    @Test
    void rejectsAGivenServiceWithoutAFile() throws Exception {
        classes("p/A");
        service("p.Service", "p.A");

        assertThatExceptionOfType(MojoExecutionException.class)
                .isThrownBy(() -> ProviderFiles.read(classes, List.of("p.Service", "p.Missing")))
                .withMessage("The service p.Missing listed in <services> has no file %s", serviceFile("p.Missing"));
    }

    @Test
    void readsNothingWithoutTheDirectory() throws Exception {
        assertThat(ProviderFiles.read(classes, List.of())).isEmpty();
    }

    @Test
    void rejectsAGivenServiceWithoutTheDirectory() {
        assertThatExceptionOfType(MojoExecutionException.class)
                .isThrownBy(() -> ProviderFiles.read(classes, List.of("p.Service")))
                .withMessage("The service p.Service listed in <services> has no file %s", serviceFile("p.Service"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"p.A p.B", "p-a.A", "1p.A", "p.class", "p..A", "p/A"})
    void rejectsAnIllegalProviderName(String provider) throws Exception {
        service("p.Service", provider);

        assertThatExceptionOfType(MojoExecutionException.class)
                .isThrownBy(() -> ProviderFiles.read(classes, List.of()))
                .withMessage("Not a provider name in %s: %s", serviceFile("p.Service"), provider);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "p/Service", "p.Service.", "p.Ser vice"})
    void rejectsAnIllegalServiceName(String service) {
        assertThatExceptionOfType(MojoExecutionException.class)
                .isThrownBy(() -> ProviderFiles.read(classes, List.of(service)))
                .withMessage("Not a service name: %s", service);
    }

    @Test
    void rejectsAServiceFileWithAnIllegalName() throws Exception {
        service("not-a-service", "p.A");

        assertThatExceptionOfType(MojoExecutionException.class)
                .isThrownBy(() -> ProviderFiles.read(classes, List.of()))
                .withMessage("Not a service name: not-a-service");
    }

    @Test
    void rejectsAProviderThatIsNotAClassOfTheModule() throws Exception {
        classes("p/A");
        service("p.Service", "p.A", "p.Missing");

        assertThatExceptionOfType(MojoExecutionException.class)
                .isThrownBy(() -> ProviderFiles.read(classes, List.of()))
                .withMessage(
                        "The provider p.Missing listed in %s is not a class in %s", serviceFile("p.Service"), classes);
    }

    private void classes(String... names) throws IOException {
        for (var name : names) {
            var file = classes.resolve(name + ".class");
            Files.createDirectories(file.getParent());
            Files.write(file, new byte[0]);
        }
    }

    private void service(String service, String... lines) throws IOException {
        var file = serviceFile(service);
        Files.createDirectories(file.getParent());
        Files.write(file, List.of(lines), UTF_8);
    }

    private Path serviceFile(String service) {
        return classes.resolve(ProviderFiles.DIRECTORY).resolve(service);
    }
}
