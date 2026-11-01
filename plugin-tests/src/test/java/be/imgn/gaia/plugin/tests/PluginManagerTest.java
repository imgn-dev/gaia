package be.imgn.gaia.plugin.tests;

import static be.imgn.gaia.plugin.tests.Fixtures.plugin;
import static be.imgn.gaia.plugin.tests.Fixtures.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import be.imgn.gaia.core.base.Version;
import be.imgn.gaia.plugin.Plugin;
import be.imgn.gaia.plugin.PluginId;
import be.imgn.gaia.plugin.PluginInfo;
import be.imgn.gaia.plugin.PluginManager;
import be.imgn.gaia.plugin.PluginState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PluginManagerTest {

    private static final String ANSWER_ONE = "context.register(IntSupplier.class, () -> 1);";

    @TempDir
    Path temp;

    private final List<PluginManager> managers = new ArrayList<>();
    private Path plugins;
    private Path stateFile;
    private Path data;
    private Fixtures fixtures;

    @BeforeEach
    void setUp() {
        plugins = temp.resolve("plugins");
        stateFile = temp.resolve("config/plugins.properties");
        data = temp.resolve("data");
        fixtures = new Fixtures(temp.resolve("work"));
    }

    @AfterEach
    void closeManagers() {
        managers.forEach(PluginManager::close);
    }

    @Test
    void startsThePluginsOfItsDirectory() throws IOException {
        install("a", "fixture.a", "1.2.3", plugin("fixture.a", ANSWER_ONE));

        var manager = start();

        assertThat(values(manager)).containsExactly(1);
        assertThat(manager.plugins())
                .containsExactly(new PluginInfo(
                        id("fixture.a"), Version.of(1, 2, 3), plugins.resolve("a"), new PluginState.Started()));
    }

    @Test
    void startsAPluginAfterThePluginsItRequires() throws IOException {
        installAAndB();

        var manager = start();

        assertThat(values(manager)).containsExactly(1, 2);
        assertThat(manager.plugins()).extracting(PluginInfo::id).containsExactly(id("fixture.a"), id("fixture.b"));
    }

    @Test
    void disablesAPluginAndThePluginsThatRequireIt() throws IOException {
        installAAndB();
        var manager = start();

        manager.disable(id("fixture.a"));

        assertThat(values(manager)).isEmpty();
        assertThat(manager.plugins())
                .extracting(PluginInfo::state)
                .containsExactly(
                        new PluginState.Disabled(),
                        new PluginState.Unresolved("It requires fixture.a, which is disabled"));
        assertThat(Files.readString(stateFile)).contains("fixture.a=disabled");
    }

    @Test
    void remembersWhichPluginsAreDisabled() throws IOException {
        install("a", "fixture.a", "1.0.0", plugin("fixture.a", ANSWER_ONE));
        var first = start();
        first.disable(id("fixture.a"));
        first.close();

        var second = start();

        assertThat(second.plugins()).extracting(PluginInfo::state).containsExactly(new PluginState.Disabled());
    }

    @Test
    void enablesAPluginAndThePluginsItRequires() throws IOException {
        installAAndB();
        Files.createDirectories(stateFile.getParent());
        Files.writeString(stateFile, "fixture.a=disabled\nfixture.b=disabled\n");
        var manager = start();

        manager.enable(id("fixture.b"));

        assertThat(values(manager)).containsExactly(1, 2);
        assertThat(Files.readString(stateFile)).doesNotContain("fixture");
    }

    @Test
    void tellsOfTheExtensionsAddedAndRemoved() throws IOException {
        install("a", "fixture.a", "1.0.0", plugin("fixture.a", ANSWER_ONE));
        var manager = start();
        var added = new ArrayList<IntSupplier>();
        var removed = new ArrayList<IntSupplier>();

        manager.extensions(IntSupplier.class).subscribe(added::add, removed::add);
        manager.disable(id("fixture.a"));

        assertThat(added).hasSize(1);
        assertThat(removed).containsExactlyElementsOf(added);
    }

    @ParameterizedTest
    @CsvSource({"1.2.3, 1.2.0, true", "1.2.3, 1.3.0, false", "2.0.0, 1.2.0, false"})
    void startsAPluginOnlyWithTheVersionsItRequires(String versionOfA, String atLeast, boolean starts)
            throws IOException {
        var a = install("a", "fixture.a", versionOfA, plugin("fixture.a", ANSWER_ONE));
        install(
                "b",
                "fixture.b",
                "1.0.0",
                plugin(
                        "fixture.b",
                        "@be.imgn.gaia.plugin.RequiresVersion(module = \"fixture.a\", atLeast = \"" + atLeast + "\")",
                        "requires fixture.a;",
                        ""),
                a);

        var manager = start();

        assertThat(manager.plugins().get(1).state())
                .isInstanceOf(starts ? PluginState.Started.class : PluginState.Unresolved.class);
    }

    @Test
    void holdsBackAPluginThatRequiresAMissingModule() throws IOException {
        var elsewhere =
                fixtures.compile(temp.resolve("elsewhere"), "fixture.a", "1.0.0", plugin("fixture.a", ANSWER_ONE));
        install("b", "fixture.b", "1.0.0", plugin("fixture.b", "", "requires fixture.a;", ""), elsewhere);

        var manager = start();

        assertThat(manager.plugins().getFirst().state())
                .isInstanceOfSatisfying(
                        PluginState.Unresolved.class,
                        state -> assertThat(state.reason()).contains("fixture.a"));
    }

    @Test
    void holdsBackAPluginThatTwoFoldersHold() throws IOException {
        install("a1", "fixture.a", "1.0.0", plugin("fixture.a", ANSWER_ONE));
        install("a2", "fixture.a", "2.0.0", plugin("fixture.a", ANSWER_ONE));

        var manager = start();

        assertThat(values(manager)).isEmpty();
        assertThat(manager.plugins())
                .hasSize(2)
                .allSatisfy(plugin -> assertThat(plugin.state()).isInstanceOf(PluginState.Unresolved.class));
    }

    @Test
    void holdsBackAPluginWhoseFolderHasAModuleOfTheHost() throws IOException {
        var folder = install("a", "fixture.a", "1.0.0", plugin("fixture.a", ANSWER_ONE));
        fixtures.compile(folder, "org.assertj.core", "1.0.0", Map.of("module-info.java", "module org.assertj.core {}"));

        var manager = start();

        assertThat(manager.plugins())
                .extracting(PluginInfo::state)
                .containsExactly(new PluginState.Unresolved(
                        "Its folder holds org.assertj.core, which the host or another plugin has already"));
    }

    @Test
    void leavesOutAFolderThatHoldsNoPlugin() throws IOException {
        fixtures.compile(
                plugins.resolve("library"),
                "fixture.library",
                "1.0.0",
                Map.of("module-info.java", "module fixture.library {}"));

        assertThat(start().plugins()).isEmpty();
    }

    @Test
    void failsAPluginWhoseStartThrows() throws IOException {
        install(
                "a",
                "fixture.a",
                "1.0.0",
                plugin("fixture.a", ANSWER_ONE + "throw new IllegalStateException(\"No way\");"));

        var manager = start();

        assertThat(values(manager)).isEmpty();
        assertThat(manager.plugins().getFirst().state())
                .isInstanceOfSatisfying(
                        PluginState.Failed.class,
                        state -> assertThat(state.cause())
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessage("No way"));
    }

    @Test
    void failsAPluginThatDoesNotStartInTime() throws IOException {
        install("a", "fixture.a", "1.0.0", plugin("fixture.a", "Thread.sleep(60_000);"));

        var manager = start(builder().timeout(Duration.ofMillis(200)));

        assertThat(manager.plugins().getFirst().state())
                .isInstanceOfSatisfying(
                        PluginState.Failed.class,
                        state -> assertThat(state.cause()).isInstanceOf(TimeoutException.class));
    }

    @Test
    void failsAPluginThatRegistersAnExtensionOfAClass() throws IOException {
        install("a", "fixture.a", "1.0.0", plugin("fixture.a", "context.register(Object.class, new Object());"));

        var manager = start();

        assertThat(manager.plugins().getFirst().state())
                .isInstanceOfSatisfying(
                        PluginState.Failed.class,
                        state -> assertThat(state.cause()).isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void runsExtensionsWithThePluginsClassLoaderAsContextClassLoader() throws IOException {
        install(
                "a",
                "fixture.a",
                "1.0.0",
                plugin(
                        "fixture.a",
                        "context.register(IntSupplier.class, () -> "
                                + "Thread.currentThread().getContextClassLoader() == Main.class.getClassLoader() "
                                + "? 1 : 0);"));
        var host = Thread.currentThread().getContextClassLoader();

        assertThat(values(start())).containsExactly(1);
        assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(host);
    }

    @Test
    void keepsPluginsOutOfTheHostsServiceLoaderLookups() throws IOException {
        install(
                "a",
                "fixture.a",
                "1.0.0",
                plugin(
                        "fixture.a",
                        "context.register(IntSupplier.class, () -> { "
                                + "Thread.currentThread().setContextClassLoader(Main.class.getClassLoader()); "
                                + "return 1; });"));
        var manager = start();

        assertThat(values(manager)).containsExactly(1);
        assertThat(ServiceLoader.load(Plugin.class).stream()).isEmpty();
        assertThat(ServiceLoader.load(ModuleLayer.boot(), Plugin.class).stream())
                .isEmpty();
    }

    @Test
    void runsServicesWithTheHostsContextClassLoader() throws IOException {
        var host = Thread.currentThread().getContextClassLoader();
        IntSupplier service = () -> Objects.equals(Thread.currentThread().getContextClassLoader(), host) ? 1 : 0;
        install(
                "a",
                "fixture.a",
                "1.0.0",
                plugin(
                        "fixture.a",
                        "var service = context.service(IntSupplier.class).orElseThrow(); "
                                + "context.register(IntSupplier.class, () -> service.getAsInt() + 10);"));

        assertThat(values(start(builder().service(IntSupplier.class, service)))).containsExactly(11);
    }

    @Test
    void runsTheExecutorWithThePluginsClassLoaderAsContextClassLoader() throws IOException {
        install(
                "a",
                "fixture.a",
                "1.0.0",
                plugin(
                        "fixture.a",
                        "int same = context.executor().submit(() -> "
                                + "Thread.currentThread().getContextClassLoader() == Main.class.getClassLoader() "
                                + "? 1 : 0).get(); "
                                + "context.register(IntSupplier.class, () -> same);"));

        assertThat(values(start())).containsExactly(1);
    }

    @Test
    void givesEachPluginADataDirectory() throws IOException {
        install(
                "a",
                "fixture.a",
                "1.0.0",
                plugin(
                        "fixture.a",
                        "context.register(IntSupplier.class, () -> "
                                + "java.nio.file.Files.isDirectory(context.dataDirectory()) ? 1 : 0);"));

        assertThat(values(start())).containsExactly(1);
        assertThat(data.resolve("fixture.a")).isDirectory();
    }

    @Test
    void closesThePluginsResourcesWhenItStops() throws IOException {
        var closed = new AtomicInteger();
        IntSupplier counter = closed::incrementAndGet;
        install(
                "a",
                "fixture.a",
                "1.0.0",
                plugin(
                        "fixture.a",
                        "var counter = context.service(IntSupplier.class).orElseThrow(); "
                                + "context.closeOnStop(counter::getAsInt);"));
        var manager = start(builder().service(IntSupplier.class, counter));

        manager.disable(id("fixture.a"));

        assertThat(closed).hasValue(1);
    }

    @Test
    void reloadsAPluginFromItsFolder() throws IOException {
        var folder = install("a", "fixture.a", "1.0.0", plugin("fixture.a", ANSWER_ONE));
        var manager = start();
        Fixtures.delete(folder);
        install("a", "fixture.a", "2.0.0", plugin("fixture.a", "context.register(IntSupplier.class, () -> 2);"));

        manager.reload(id("fixture.a"));

        assertThat(values(manager)).containsExactly(2);
        assertThat(manager.plugins()).extracting(PluginInfo::version).containsExactly(Version.of(2, 0, 0));
    }

    @Test
    void refreshFindsThePluginsAddedAndRemoved() throws IOException {
        var a = install("a", "fixture.a", "1.0.0", plugin("fixture.a", ANSWER_ONE));
        var manager = start();
        install("c", "fixture.c", "1.0.0", plugin("fixture.c", "context.register(IntSupplier.class, () -> 3);"));
        Fixtures.delete(a);

        manager.refresh();

        assertThat(values(manager)).containsExactly(3);
        assertThat(manager.plugins()).extracting(PluginInfo::id).containsExactly(id("fixture.c"));
    }

    @Test
    void stopsEveryPluginWhenClosed() throws IOException {
        installAAndB();
        var manager = start();

        manager.close();

        assertThat(values(manager)).isEmpty();
        assertThatIllegalStateException().isThrownBy(manager::plugins);
    }

    @Test
    void rejectsAnIdThatNoPluginHas() {
        var manager = start();

        assertThatIllegalArgumentException().isThrownBy(() -> manager.enable(id("fixture.none")));
    }

    @Test
    void needsAPluginsDirectory() {
        assertThatIllegalStateException()
                .isThrownBy(() -> PluginManager.newBuilder()
                        .stateFile(stateFile)
                        .dataDirectory(data)
                        .start());
    }

    @Test
    void offersOnlyInterfacesAsServices() {
        assertThatIllegalArgumentException().isThrownBy(() -> builder().service(Object.class, new Object()));
    }

    @Test
    void rejectsATimeoutThatIsNotPositive() {
        assertThatIllegalArgumentException().isThrownBy(() -> builder().timeout(Duration.ZERO));
    }

    private void installAAndB() throws IOException {
        var a = install(
                "2-a",
                "fixture.a",
                "1.0.0",
                with(
                        plugin("fixture.a", "", "exports fixture.a.api;", ANSWER_ONE),
                        "fixture/a/api/Answer.java",
                        "package fixture.a.api; "
                                + "public final class Answer { public static int value() { return 1; } }"));
        install(
                "1-b",
                "fixture.b",
                "1.0.0",
                plugin(
                        "fixture.b",
                        "",
                        "requires fixture.a;",
                        "context.register(IntSupplier.class, () -> fixture.a.api.Answer.value() + 1);"),
                a);
    }

    private Path install(String folder, String module, String version, Map<String, String> sources, Path... modulePath)
            throws IOException {
        return fixtures.compile(plugins.resolve(folder), module, version, sources, modulePath);
    }

    private PluginManager.Builder builder() {
        return PluginManager.newBuilder()
                .directory(plugins)
                .stateFile(stateFile)
                .dataDirectory(data)
                .timeout(Duration.ofSeconds(5));
    }

    private PluginManager start() {
        return start(builder());
    }

    private PluginManager start(PluginManager.Builder builder) {
        var manager = builder.start();
        managers.add(manager);
        return manager;
    }

    private static List<Integer> values(PluginManager manager) {
        return manager.extensions(IntSupplier.class).stream()
                .map(IntSupplier::getAsInt)
                .toList();
    }

    private static PluginId id(String name) {
        return new PluginId(name);
    }
}
