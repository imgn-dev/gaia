package be.imgn.gaia.memfs;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AttributesTest {

    private final Instant[] now = {Instant.parse("2026-01-01T00:00:00Z")};
    private final FileSystem unix = MemoryFileSystems.newFileSystem(
            Configuration.unix().toBuilder().clock(() -> now[0]).build());
    private final FileSystem windows = MemoryFileSystems.newFileSystem(
            Configuration.windows().toBuilder().clock(() -> now[0]).build());

    @AfterEach
    void close() throws IOException {
        unix.close();
        windows.close();
    }

    private void advance(int seconds) {
        now[0] = now[0].plusSeconds(seconds);
    }

    private static FileTime time(String instant) {
        return FileTime.from(Instant.parse(instant));
    }

    @Nested
    class Basic {

        @Test
        void datesFilesWithTheClock() throws IOException {
            Path file = Files.createFile(unix.getPath("file"));
            advance(10);
            Files.writeString(file, "x");
            advance(10);
            Files.readString(file);

            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);

            assertThat(attributes.creationTime()).isEqualTo(time("2026-01-01T00:00:00Z"));
            assertThat(attributes.lastModifiedTime()).isEqualTo(time("2026-01-01T00:00:10Z"));
            assertThat(attributes.lastAccessTime()).isEqualTo(time("2026-01-01T00:00:20Z"));
            assertThat(attributes.size()).isEqualTo(1);
            assertThat(attributes.isRegularFile()).isTrue();
            assertThat(attributes.isDirectory()).isFalse();
            assertThat(attributes.isSymbolicLink()).isFalse();
            assertThat(attributes.isOther()).isFalse();
            assertThat(attributes.fileKey()).isNotNull();
        }

        @Test
        void modifiesADirectoryWhenItsEntriesChange() throws IOException {
            Path directory = Files.createDirectory(unix.getPath("dir"));
            advance(5);
            Files.createFile(directory.resolve("file"));

            assertThat(Files.getLastModifiedTime(directory)).isEqualTo(time("2026-01-01T00:00:05Z"));
        }

        @Test
        void setsTimes() throws IOException {
            Path file = Files.createFile(unix.getPath("file"));
            BasicFileAttributeView view = Files.getFileAttributeView(file, BasicFileAttributeView.class);

            view.setTimes(time("2020-01-01T00:00:00Z"), null, time("2019-01-01T00:00:00Z"));
            Files.setAttribute(file, "lastAccessTime", time("2021-01-01T00:00:00Z"));

            assertThat(view.name()).isEqualTo("basic");
            assertThat(view.readAttributes().lastModifiedTime()).isEqualTo(time("2020-01-01T00:00:00Z"));
            assertThat(view.readAttributes().creationTime()).isEqualTo(time("2019-01-01T00:00:00Z"));
            assertThat(Files.getAttribute(file, "basic:lastAccessTime")).isEqualTo(time("2021-01-01T00:00:00Z"));
            Files.setAttribute(file, "basic:creationTime", time("2018-01-01T00:00:00Z"));
            assertThat(Files.getAttribute(file, "creationTime")).isEqualTo(time("2018-01-01T00:00:00Z"));
        }

        @Test
        void readsByName() throws IOException {
            Path file = Files.writeString(unix.getPath("file"), "abc");

            Map<String, Object> all = Files.readAttributes(file, "*");
            Map<String, Object> some = Files.readAttributes(file, "basic:size,isDirectory");

            assertThat(all)
                    .containsKeys(
                            "lastModifiedTime",
                            "lastAccessTime",
                            "creationTime",
                            "size",
                            "isRegularFile",
                            "isDirectory",
                            "isSymbolicLink",
                            "isOther",
                            "fileKey");
            assertThat(some).containsExactly(Map.entry("size", 3L), Map.entry("isDirectory", false));
        }

        @Test
        void readsSymbolicLinksThemselvesWhenAsked() throws IOException {
            Path target = Files.writeString(unix.getPath("target"), "content");
            Path link = Files.createSymbolicLink(unix.getPath("link"), target);

            assertThat(Files.readAttributes(link, BasicFileAttributes.class).isRegularFile())
                    .isTrue();
            assertThat(Files.readAttributes(link, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
                            .isSymbolicLink())
                    .isTrue();
            assertThat(Files.getAttribute(link, "size", LinkOption.NOFOLLOW_LINKS))
                    .isEqualTo(6L);
        }

        @Test
        void refusesUnknownOrReadOnlyAttributes() throws IOException {
            Path file = Files.createFile(unix.getPath("file"));

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Files.readAttributes(file, "basic:color"))
                    .withMessage("'color' not recognized");
            assertThatIllegalArgumentException().isThrownBy(() -> Files.setAttribute(file, "size", 1L));
            assertThatExceptionOfType(ClassCastException.class)
                    .isThrownBy(() -> Files.setAttribute(file, "lastModifiedTime", "yesterday"));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> Files.readAttributes(file, "jimfs:*"));
        }

        @Test
        void cannotBeGivenWhenCreatingAFile() {
            FileAttribute<FileTime> time = new FileAttribute<>() {
                @Override
                public String name() {
                    return "basic:lastModifiedTime";
                }

                @Override
                public FileTime value() {
                    return FileTime.fromMillis(0);
                }
            };

            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> Files.createFile(unix.getPath("file"), time));
            assertThat(Files.exists(unix.getPath("file"))).isFalse();
        }

        @Test
        void setsTheModificationTimeByName() throws IOException {
            Path file = Files.createFile(unix.getPath("file"));

            Files.setAttribute(file, "lastModifiedTime", time("2000-01-01T00:00:00Z"));

            assertThat(Files.getLastModifiedTime(file)).isEqualTo(time("2000-01-01T00:00:00Z"));
        }

        @Test
        void needsTheFile() {
            Path missing = unix.getPath("missing");
            BasicFileAttributeView view = Files.getFileAttributeView(missing, BasicFileAttributeView.class);

            assertThatExceptionOfType(NoSuchFileException.class).isThrownBy(view::readAttributes);
            assertThatExceptionOfType(NoSuchFileException.class)
                    .isThrownBy(() -> Files.readAttributes(missing, BasicFileAttributes.class));
            assertThatExceptionOfType(NoSuchFileException.class)
                    .isThrownBy(() -> Files.setAttribute(missing, "lastModifiedTime", FileTime.fromMillis(0)));
        }
    }

    @Nested
    class Posix {

        @Test
        void readsAndChangesPermissionsOwnersAndGroups() throws IOException {
            Path file = Files.createFile(unix.getPath("file"));
            UserPrincipal alice = unix.getUserPrincipalLookupService().lookupPrincipalByName("alice");
            GroupPrincipal staff = unix.getUserPrincipalLookupService().lookupPrincipalByGroupName("staff");
            PosixFileAttributeView view = Files.getFileAttributeView(file, PosixFileAttributeView.class);

            Files.setOwner(file, alice);
            view.setGroup(staff);
            view.setPermissions(PosixFilePermissions.fromString("rwxr-----"));
            PosixFileAttributes attributes = Files.readAttributes(file, PosixFileAttributes.class);

            assertThat(view.name()).isEqualTo("posix");
            assertThat(attributes.owner()).isEqualTo(alice);
            assertThat(attributes.group()).isEqualTo(staff);
            assertThat(PosixFilePermissions.toString(attributes.permissions())).isEqualTo("rwxr-----");
            assertThat(attributes.isRegularFile()).isTrue();
            assertThat(attributes.size()).isZero();
            assertThat(attributes.fileKey()).isNotNull();
            assertThat(view.getOwner()).isEqualTo(alice);
            assertThat(Files.getOwner(file).getName()).isEqualTo("alice");
            assertThat(Files.readAttributes(file, "posix:owner,group,permissions"))
                    .containsKeys("owner", "group");
            assertThat(Files.readAttributes(file, "posix:*")).containsKeys("permissions", "size");
        }

        @Test
        void ownsNewFilesByDefault() throws IOException {
            Path file = Files.createFile(unix.getPath("file"));

            assertThat(Files.getOwner(file).getName()).isEqualTo("user");
            assertThat(Files.readAttributes(file, PosixFileAttributes.class)
                            .group()
                            .getName())
                    .isEqualTo("group");
        }

        @Test
        void exposesUnixAttributes() throws IOException {
            Path file = Files.createFile(unix.getPath("file"));
            Files.setOwner(file, unix.getUserPrincipalLookupService().lookupPrincipalByName("root"));

            Files.setAttribute(file, "unix:mode", 0640);
            Map<String, Object> attributes = Files.readAttributes(file, "unix:*");

            assertThat(attributes.get("mode")).isEqualTo(0100640);
            assertThat(attributes.get("uid")).isEqualTo(0);
            assertThat((Integer) attributes.get("gid")).isGreaterThanOrEqualTo(1000);
            assertThat(attributes.get("nlink")).isEqualTo(1);
            assertThat(attributes).containsKeys("dev", "ino", "rdev", "ctime", "permissions");
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
                    .isEqualTo("rw-r-----");
            assertThat(Files.getAttribute(unix.getPath("/work"), "unix:mode")).isEqualTo(040755);
            Path link = Files.createSymbolicLink(unix.getPath("link"), file);
            assertThat((Integer) Files.getAttribute(link, "unix:mode", LinkOption.NOFOLLOW_LINKS) & 0170000)
                    .isEqualTo(0120000);
        }

        @Test
        void refusesValuesOfTheWrongType() throws IOException {
            Path file = Files.createFile(unix.getPath("file"));

            assertThatExceptionOfType(ClassCastException.class)
                    .isThrownBy(() -> Files.setAttribute(file, "posix:permissions", "rwx------"));
            assertThatExceptionOfType(ClassCastException.class)
                    .isThrownBy(() -> Files.setAttribute(file, "posix:permissions", Set.of("OWNER_READ")));
            assertThatExceptionOfType(ClassCastException.class)
                    .isThrownBy(() -> Files.setAttribute(file, "owner:owner", "alice"));
            assertThatIllegalArgumentException().isThrownBy(() -> Files.setAttribute(file, "unix:ino", 1L));
        }

        @Test
        void hasNoDosOrAclViews() throws IOException {
            Path file = Files.createFile(unix.getPath("file"));

            assertThat(Files.getFileAttributeView(file, DosFileAttributeView.class))
                    .isNull();
            assertThat(Files.getFileAttributeView(file, AclFileAttributeView.class))
                    .isNull();
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> Files.readAttributes(file, DosFileAttributes.class));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> Files.setAttribute(file, "dos:hidden", true));
        }
    }

    @Nested
    class Dos {

        @Test
        void setsFlags() throws IOException {
            Path file = Files.createFile(windows.getPath("file"));
            DosFileAttributeView view = Files.getFileAttributeView(file, DosFileAttributeView.class);

            view.setReadOnly(true);
            view.setHidden(true);
            view.setSystem(true);
            view.setArchive(true);
            DosFileAttributes attributes = view.readAttributes();

            assertThat(view.name()).isEqualTo("dos");
            assertThat(attributes.isReadOnly()).isTrue();
            assertThat(attributes.isHidden()).isTrue();
            assertThat(attributes.isSystem()).isTrue();
            assertThat(attributes.isArchive()).isTrue();
            assertThat(attributes.isRegularFile()).isTrue();
            assertThat(attributes.size()).isZero();
            assertThat(attributes.lastAccessTime()).isNotNull();
            Files.setAttribute(file, "dos:readonly", false);
            assertThat(Files.readAttributes(file, "dos:*"))
                    .containsEntry("readonly", false)
                    .containsEntry("hidden", true)
                    .containsKey("size");
            assertThatIllegalArgumentException().isThrownBy(() -> Files.setAttribute(file, "dos:compressed", true));
        }

        @Test
        void hasNoPosixView() throws IOException {
            Path file = Files.createFile(windows.getPath("file"));

            assertThat(Files.getFileAttributeView(file, PosixFileAttributeView.class))
                    .isNull();
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> Files.getPosixFilePermissions(file));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> Files.readAttributes(file, "unix:*"));
        }
    }

    @Nested
    class Acl {

        @Test
        void setsAccessControlLists() throws IOException {
            Path file = Files.createFile(windows.getPath("file"));
            UserPrincipal bob = windows.getUserPrincipalLookupService().lookupPrincipalByName("bob");
            AclEntry entry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(bob)
                    .setPermissions(AclEntryPermission.READ_DATA)
                    .build();
            AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);

            assertThat(view.getAcl()).isEmpty();
            view.setAcl(List.of(entry));
            view.setOwner(bob);

            assertThat(view.name()).isEqualTo("acl");
            assertThat(view.getAcl()).containsExactly(entry);
            assertThat(view.getOwner()).isEqualTo(bob);
            assertThat(Files.readAttributes(file, "acl:*")).containsOnlyKeys("owner", "acl");
            assertThatExceptionOfType(ClassCastException.class)
                    .isThrownBy(() -> Files.setAttribute(file, "acl:acl", List.of("everyone")));
        }

        @Test
        void exposesTheOwnerView() throws IOException {
            Path file = Files.createFile(windows.getPath("file"));
            FileOwnerAttributeView view = Files.getFileAttributeView(file, FileOwnerAttributeView.class);

            view.setOwner(windows.getUserPrincipalLookupService().lookupPrincipalByName("carol"));

            assertThat(view.name()).isEqualTo("owner");
            assertThat(view.getOwner().getName()).isEqualTo("carol");
            assertThat(Files.getAttribute(file, "owner:owner")).isEqualTo(view.getOwner());
        }
    }

    @Nested
    class User {

        @Test
        void storesBytesUnderAnyName() throws IOException {
            Path file = Files.createFile(windows.getPath("file"));
            UserDefinedFileAttributeView view = Files.getFileAttributeView(file, UserDefinedFileAttributeView.class);

            assertThat(view.write("mime", ByteBuffer.wrap("text/plain".getBytes(UTF_8))))
                    .isEqualTo(10);
            Files.setAttribute(file, "user:origin", "web".getBytes(UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(view.size("mime"));
            view.read("mime", buffer);

            assertThat(view.name()).isEqualTo("user");
            assertThat(view.list()).containsExactly("mime", "origin");
            assertThat(new String(buffer.array(), UTF_8)).isEqualTo("text/plain");
            assertThat((byte[]) Files.getAttribute(file, "user:origin"))
                    .asString(UTF_8)
                    .isEqualTo("web");
            assertThat(Files.readAttributes(file, "user:*")).containsOnlyKeys("mime", "origin");
            assertThat(Files.readAttributes(file, "user:missing")).isEmpty();
            view.delete("mime");
            assertThat(view.list()).containsExactly("origin");
        }

        @Test
        void refusesMissingAttributesAndSmallBuffers() throws IOException {
            Path file = Files.createFile(windows.getPath("file"));
            UserDefinedFileAttributeView view = Files.getFileAttributeView(file, UserDefinedFileAttributeView.class);
            view.write("data", ByteBuffer.wrap(new byte[10]));

            assertThatExceptionOfType(FileSystemException.class)
                    .isThrownBy(() -> view.size("missing"))
                    .withMessageContaining("No attribute missing");
            assertThatExceptionOfType(FileSystemException.class)
                    .isThrownBy(() -> view.read("data", ByteBuffer.allocate(5)))
                    .withMessageContaining("Buffer too small");
            assertThatExceptionOfType(ClassCastException.class)
                    .isThrownBy(() -> Files.setAttribute(file, "user:text", "not bytes"));
        }
    }

    @Nested
    class Defaults {

        @Test
        void giveNewFilesTheirAttributes() throws IOException {
            try (FileSystem configured = MemoryFileSystems.newFileSystem(Configuration.newBuilder(PathStyle.UNIX)
                    .attributeViews("posix", "dos", "acl")
                    .defaultAttribute("owner:owner", "dave")
                    .defaultAttribute("posix:group", "admins")
                    .defaultAttribute("posix:permissions", "rw-------")
                    .defaultAttribute("dos:archive", true)
                    .defaultAttribute("acl:acl", List.of())
                    .build())) {
                Path file = Files.createFile(configured.getPath("file"));
                Path directory = Files.createDirectory(configured.getPath("dir"));

                assertThat(Files.getOwner(file).getName()).isEqualTo("dave");
                assertThat(Files.readAttributes(file, PosixFileAttributes.class)
                                .group()
                                .getName())
                        .isEqualTo("admins");
                assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(directory)))
                        .isEqualTo("rw-------");
                assertThat(Files.readAttributes(file, DosFileAttributes.class).isArchive())
                        .isTrue();
            }
        }

        @Test
        void belongToTheCurrentUser() throws IOException {
            try (FileSystem configured = MemoryFileSystems.newFileSystem(
                    Configuration.unix().toBuilder().currentUser("erin", "devs").build())) {
                Path file = Files.createFile(configured.getPath("file"));

                assertThat(Files.getOwner(file).getName()).isEqualTo("erin");
                assertThat(Files.readAttributes(file, PosixFileAttributes.class)
                                .group()
                                .getName())
                        .isEqualTo("devs");
            }
        }
    }

    @Nested
    class Store {

        @Test
        void describesItsCapacityAndViews() throws IOException {
            try (FileSystem small = MemoryFileSystems.newFileSystem(
                    "store-test",
                    Configuration.unix().toBuilder()
                            .blockSize(100)
                            .maxSize(1000)
                            .build())) {
                Files.write(small.getPath("file"), new byte[150]);
                FileStore store = small.getFileStores().iterator().next();

                assertThat(store.name()).isEqualTo("store-test");
                assertThat(store).hasToString("store-test (memory)");
                assertThat(store.getTotalSpace()).isEqualTo(1000);
                assertThat(store.getUnallocatedSpace()).isEqualTo(800);
                assertThat(store.getUsableSpace()).isEqualTo(800);
                assertThat(store.getBlockSize()).isEqualTo(100);
                assertThat(store.getAttribute("totalSpace")).isEqualTo(1000L);
                assertThat(store.getAttribute("usableSpace")).isEqualTo(800L);
                assertThat(store.getAttribute("unallocatedSpace")).isEqualTo(800L);
                assertThat(store.supportsFileAttributeView("posix")).isTrue();
                assertThat(store.supportsFileAttributeView("dos")).isFalse();
                assertThat(store.supportsFileAttributeView(PosixFileAttributeView.class))
                        .isTrue();
                assertThat(store.supportsFileAttributeView(DosFileAttributeView.class))
                        .isFalse();
                assertThat(store.getFileStoreAttributeView(FileStoreAttributeView.class))
                        .isNull();
                assertThatExceptionOfType(UnsupportedOperationException.class)
                        .isThrownBy(() -> store.getAttribute("color"));
            }
        }
    }
}
