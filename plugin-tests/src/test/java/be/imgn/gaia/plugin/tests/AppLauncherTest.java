package be.imgn.gaia.plugin.tests;

import static be.imgn.gaia.plugin.tests.Fixtures.plugin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import be.imgn.gaia.plugin.AppLauncher;
import be.imgn.gaia.plugin.PluginManager;
import be.imgn.gaia.plugin.PluginState;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppLauncherTest {

    private static final String PROPERTY = "be.imgn.gaia.plugin.tests.launched";

    @TempDir
    Path temp;

    private Fixtures fixtures;
    private Path app;

    @BeforeEach
    void setUp() {
        fixtures = new Fixtures(temp.resolve("work"));
        app = temp.resolve("app");
    }

    @Test
    void runsTheApplicationInALayerOfItsOwn() throws Exception {
        fixtures.compile(
                app,
                "fixture.app",
                "1.0.0",
                Map.of(
                        "module-info.java",
                        "module fixture.app { requires be.imgn.gaia.plugin; }",
                        "fixture/app/Main.java",
                        """
                        package fixture.app;

                        public final class Main {
                            public static void main(String[] args) {
                                var layer = Main.class.getModule().getLayer();
                                var own = layer != ModuleLayer.boot()
                                        && Thread.currentThread().getContextClassLoader() == Main.class.getClassLoader()
                                        && layer.findModule("be.imgn.gaia.plugin").orElseThrow()
                                                == ModuleLayer.boot().findModule("be.imgn.gaia.plugin").orElseThrow();
                                System.setProperty(args[0], String.valueOf(own));
                            }
                        }
                        """));
        // A copy of a module the boot layer has, which the application must not get.
        fixtures.compile(
                app, "be.imgn.gaia.plugin", "1.0.0", Map.of("module-info.java", "module be.imgn.gaia.plugin {}"));

        try {
            var layer = AppLauncher.launch(app, "fixture.app", "fixture.app.Main", PROPERTY);

            assertThat(System.getProperty(PROPERTY)).isEqualTo("true");
            assertThat(layer.findModule("fixture.app")).isPresent();
            assertThat(ModuleLayer.boot().findModule("fixture.app")).isEmpty();
        } finally {
            System.clearProperty(PROPERTY);
        }
    }

    @Test
    void keepsTheApplicationOutOfItsPluginsReach() throws Exception {
        fixtures.compile(app, "fixture.app", "1.0.0", main("public static void main(String[] args) {}"));
        AppLauncher.launch(app, "fixture.app", "fixture.app.Main");
        var plugins = temp.resolve("plugins");
        fixtures.compile(
                plugins.resolve("a"), "fixture.a", "1.0.0", plugin("fixture.a", "", "requires fixture.app;", ""), app);

        try (var manager = PluginManager.newBuilder()
                .directory(plugins)
                .stateFile(temp.resolve("plugins.properties"))
                .dataDirectory(temp.resolve("data"))
                .start()) {
            assertThat(manager.plugins().getFirst().state())
                    .isInstanceOfSatisfying(
                            PluginState.Unresolved.class,
                            state -> assertThat(state.reason()).contains("fixture.app"));
        }
    }

    @Test
    void passesOnWhatTheMainMethodThrows() throws IOException {
        fixtures.compile(
                app,
                "fixture.app",
                "1.0.0",
                main("public static void main(String[] args) throws java.io.IOException { "
                        + "throw new java.io.IOException(\"No way\"); }"));

        assertThatIOException()
                .isThrownBy(() -> AppLauncher.launch(app, "fixture.app", "fixture.app.Main"))
                .withMessage("No way");
    }

    @Test
    void rejectsAMainModuleTheDirectoryDoesNotHold() throws IOException {
        fixtures.compile(app, "fixture.app", "1.0.0", main("public static void main(String[] args) {}"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> AppLauncher.launch(app, "fixture.other", "fixture.other.Main"));
    }

    @Test
    void rejectsAMainClassWithoutMainMethod() throws IOException {
        fixtures.compile(app, "fixture.app", "1.0.0", main(""));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> AppLauncher.launch(app, "fixture.app", "fixture.app.Main"));
    }

    private static Map<String, String> main(String body) {
        return Map.of(
                "module-info.java",
                "module fixture.app {}",
                "fixture/app/Main.java",
                "package fixture.app; public final class Main { " + body + " }");
    }
}
