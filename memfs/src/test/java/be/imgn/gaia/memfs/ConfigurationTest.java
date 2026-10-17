package be.imgn.gaia.memfs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConfigurationTest {

    @Nested
    class Presets {

        @Test
        void unixIsLinuxLike() {
            Configuration unix = Configuration.unix();

            assertThat(unix.style()).isEqualTo(PathStyle.UNIX);
            assertThat(unix.roots()).containsExactly("/");
            assertThat(unix.workingDirectory()).isEqualTo("/work");
            assertThat(unix.nameMatching()).isEqualTo(NameMatching.EXACT);
            assertThat(unix.unicodeNormalization()).isEmpty();
            assertThat(unix.attributeViews()).containsExactlyInAnyOrder("basic", "owner", "posix", "unix");
            assertThat(unix.blockSize()).isEqualTo(8192);
            assertThat(unix.maxSize()).isEqualTo(4L * 1024 * 1024 * 1024);
            assertThat(unix.maxNameLength()).isEqualTo(255);
            assertThat(unix.watchServicePollingInterval()).isEqualTo(Duration.ofSeconds(1));
            assertThat(unix.features()).containsExactlyInAnyOrder(Feature.values());
            assertThat(unix.defaultAttributes()).isEmpty();
            assertThat(unix.currentUser()).isEmpty();
            assertThat(unix.currentGroups()).isEmpty();
            assertThat(unix.clock()).isSameAs(InstantSource.system());
        }

        @Test
        void macOSMatchesNamesIgnoringCaseAndNormalization() {
            Configuration macOS = Configuration.macOS();

            assertThat(macOS.style()).isEqualTo(PathStyle.UNIX);
            assertThat(macOS.nameMatching()).isEqualTo(NameMatching.IGNORE_CASE_UNICODE);
            assertThat(macOS.unicodeNormalization()).contains(Normalizer.Form.NFD);
            assertThat(macOS.attributeViews()).contains("posix");
        }

        @Test
        void windowsIsWindowsLike() {
            Configuration windows = Configuration.windows();

            assertThat(windows.style()).isEqualTo(PathStyle.WINDOWS);
            assertThat(windows.roots()).containsExactly("C:\\");
            assertThat(windows.workingDirectory()).isEqualTo("C:\\work");
            assertThat(windows.nameMatching()).isEqualTo(NameMatching.IGNORE_CASE_UNICODE);
            assertThat(windows.attributeViews()).containsExactlyInAnyOrder("basic", "owner", "dos", "acl", "user");
        }

        @Test
        void forCurrentPlatformLooksLikeTheHost() {
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            Configuration current = Configuration.forCurrentPlatform();

            assertThat(current.style()).isEqualTo(os.startsWith("windows") ? PathStyle.WINDOWS : PathStyle.UNIX);
        }

        @Test
        void haveADescription() {
            assertThat(Configuration.unix()).hasToString("Configuration[UNIX, roots=[/], workingDirectory=/work]");
        }
    }

    @Nested
    class Builder {

        @Test
        void setsEverything() {
            InstantSource clock = InstantSource.fixed(Instant.EPOCH);
            Configuration configuration = Configuration.newBuilder(PathStyle.WINDOWS)
                    .roots("C:\\", "d:/", "\\\\server\\share")
                    .workingDirectory("D:\\home")
                    .blockSize(512)
                    .maxSize(1024 * 1024)
                    .nameMatching(NameMatching.IGNORE_CASE_ASCII)
                    .unicodeNormalization(Normalizer.Form.NFC)
                    .attributeViews("dos", "user")
                    .defaultAttribute("dos:hidden", true)
                    .clock(clock)
                    .watchServicePollingInterval(Duration.ofMillis(10))
                    .features(Feature.LINKS)
                    .maxNameLength(8)
                    .currentUser("alice", "staff", "wheel")
                    .build();

            assertThat(configuration.roots()).containsExactly("C:\\", "d:\\", "\\\\server\\share\\");
            assertThat(configuration.workingDirectory()).isEqualTo("D:\\home");
            assertThat(configuration.blockSize()).isEqualTo(512);
            assertThat(configuration.maxSize()).isEqualTo(1024 * 1024);
            assertThat(configuration.nameMatching()).isEqualTo(NameMatching.IGNORE_CASE_ASCII);
            assertThat(configuration.unicodeNormalization()).contains(Normalizer.Form.NFC);
            assertThat(configuration.attributeViews()).containsExactlyInAnyOrder("basic", "dos", "user");
            assertThat(configuration.defaultAttributes()).containsEntry("dos:hidden", true);
            assertThat(configuration.clock()).isSameAs(clock);
            assertThat(configuration.watchServicePollingInterval()).isEqualTo(Duration.ofMillis(10));
            assertThat(configuration.features()).containsExactly(Feature.LINKS);
            assertThat(configuration.maxNameLength()).isEqualTo(8);
            assertThat(configuration.currentUser()).contains("alice");
            assertThat(configuration.currentGroups()).containsExactly("staff", "wheel");
        }

        @Test
        void addsTheViewsOthersExtend() {
            assertThat(Configuration.newBuilder(PathStyle.UNIX)
                            .attributeViews("unix")
                            .build()
                            .attributeViews())
                    .containsExactlyInAnyOrder("basic", "owner", "posix", "unix");
            assertThat(Configuration.newBuilder(PathStyle.WINDOWS)
                            .attributeViews("acl")
                            .build()
                            .attributeViews())
                    .containsExactlyInAnyOrder("basic", "owner", "acl");
            assertThat(Configuration.newBuilder(PathStyle.UNIX)
                            .attributeViews()
                            .build()
                            .attributeViews())
                    .containsExactly("basic");
        }

        @Test
        void copiesAConfiguration() {
            Configuration original = Configuration.unix().toBuilder()
                    .currentUser("bob", "users")
                    .features(Feature.SYMBOLIC_LINKS)
                    .build();

            Configuration copy = original.toBuilder().build();
            Configuration withoutUser = original.toBuilder().noCurrentUser().build();

            assertThat(copy.currentUser()).contains("bob");
            assertThat(copy.features()).containsExactly(Feature.SYMBOLIC_LINKS);
            assertThat(copy.attributeViews()).isEqualTo(original.attributeViews());
            assertThat(withoutUser.currentUser()).isEmpty();
            assertThat(withoutUser.currentGroups()).isEmpty();
            assertThat(Configuration.unix().toBuilder()
                            .unicodeNormalization(null)
                            .build()
                            .unicodeNormalization())
                    .isEmpty();
        }

        @Test
        void acceptsDefaultAttributesOfEachKind() {
            Configuration configuration = Configuration.newBuilder(PathStyle.UNIX)
                    .attributeViews("posix", "dos", "acl")
                    .defaultAttribute("owner:owner", "carol")
                    .defaultAttribute("posix:group", "staff")
                    .defaultAttribute("posix:permissions", "rwx------")
                    .defaultAttribute("dos:readonly", false)
                    .defaultAttribute("dos:system", true)
                    .defaultAttribute("dos:archive", true)
                    .defaultAttribute("acl:acl", List.of())
                    .build();
            Configuration fromSet = Configuration.unix().toBuilder()
                    .defaultAttribute("posix:permissions", Set.of(PosixFilePermission.OWNER_READ))
                    .build();

            assertThat(configuration.defaultAttributes())
                    .containsEntry("owner:owner", "carol")
                    .containsEntry("posix:permissions", PosixFilePermissions.fromString("rwx------"))
                    .containsEntry("acl:acl", List.<AclEntry>of());
            assertThat(fromSet.defaultAttributes())
                    .containsEntry("posix:permissions", Set.of(PosixFilePermission.OWNER_READ));
        }
    }

    @Nested
    class Rejects {

        @Test
        void rootsThatAreNotRootsOfTheStyle() {
            Configuration.Builder unix = Configuration.newBuilder(PathStyle.UNIX);
            Configuration.Builder windows = Configuration.newBuilder(PathStyle.WINDOWS);

            assertThatIllegalArgumentException().isThrownBy(() -> unix.roots("/tmp"));
            assertThatIllegalArgumentException().isThrownBy(() -> unix.roots("relative"));
            assertThatIllegalArgumentException().isThrownBy(() -> unix.roots("/", "/"));
            assertThatIllegalArgumentException().isThrownBy(() -> windows.roots("C:"));
            assertThatIllegalArgumentException().isThrownBy(() -> windows.roots("\\"));
            assertThatIllegalArgumentException().isThrownBy(() -> windows.roots("C:\\dir"));
            assertThatIllegalArgumentException().isThrownBy(() -> windows.roots("C:\\", "c:/"));
            assertThatIllegalArgumentException().isThrownBy(() -> windows.roots("C:\\", "|:\\"));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> windows.roots("\\\\server"))
                    .withMessageContaining("Not a root");
        }

        @Test
        void workingDirectoriesThatAreNotAbsoluteOnARoot() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Configuration.newBuilder(PathStyle.UNIX)
                            .workingDirectory("work")
                            .build())
                    .withMessage("Not an absolute path: work");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Configuration.newBuilder(PathStyle.WINDOWS)
                            .workingDirectory("D:\\work")
                            .build())
                    .withMessageContaining("is not on a root");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Configuration.newBuilder(PathStyle.WINDOWS)
                            .workingDirectory("C:work")
                            .build());
        }

        @Test
        void sizesThatAreNotPositive() {
            Configuration.Builder builder = Configuration.newBuilder(PathStyle.UNIX);

            assertThatIllegalArgumentException().isThrownBy(() -> builder.blockSize(0));
            assertThatIllegalArgumentException().isThrownBy(() -> builder.maxSize(-1));
            assertThatIllegalArgumentException().isThrownBy(() -> builder.maxNameLength(0));
            assertThatIllegalArgumentException().isThrownBy(() -> builder.watchServicePollingInterval(Duration.ZERO));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> builder.blockSize(4096).maxSize(1024).build())
                    .withMessageContaining("larger than the capacity");
        }

        @Test
        void unknownViewsAndAttributes() {
            Configuration.Builder builder = Configuration.newBuilder(PathStyle.UNIX);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> builder.attributeViews("posix", "jimfs"))
                    .withMessage("Unknown attribute view: jimfs");
            assertThatIllegalArgumentException().isThrownBy(() -> builder.defaultAttribute("basic:size", 1L));
            assertThatIllegalArgumentException().isThrownBy(() -> builder.defaultAttribute("dos:hidden", "yes"));
            assertThatIllegalArgumentException().isThrownBy(() -> builder.defaultAttribute("owner:owner", 1));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> builder.defaultAttribute("posix:permissions", "rwz------"));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> builder.defaultAttribute("posix:permissions", List.of("OWNER_READ")));
            assertThatIllegalArgumentException().isThrownBy(() -> builder.defaultAttribute("acl:acl", "none"));
            assertThatIllegalArgumentException().isThrownBy(() -> builder.defaultAttribute("acl:acl", List.of(1)));
        }

        @Test
        void defaultAttributesOfUnsupportedViews() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Configuration.unix().toBuilder()
                            .defaultAttribute("dos:hidden", true)
                            .build())
                    .withMessage("The default attribute dos:hidden is of an unsupported view");
        }
    }
}
