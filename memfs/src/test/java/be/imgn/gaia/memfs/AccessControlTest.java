package be.imgn.gaia.memfs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AccessControlTest {

    @Test
    void enforcesNothingWithoutACurrentUser() throws IOException {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            Path file = Files.writeString(fileSystem.getPath("file"), "content");
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));
            Files.setOwner(file, fileSystem.getUserPrincipalLookupService().lookupPrincipalByName("bob"));

            assertThat(Files.readString(file)).isEqualTo("content");
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Nested
    class Posix {

        private final FileSystem fileSystem = MemoryFileSystems.newFileSystem(
                Configuration.unix().toBuilder().currentUser("alice", "staff").build());

        @AfterEach
        void close() throws IOException {
            fileSystem.close();
        }

        private Path path(String path) {
            return fileSystem.getPath(path);
        }

        private void permissions(Path path, String permissions) throws IOException {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
        }

        private void owner(Path path, String user) throws IOException {
            UserPrincipal owner = fileSystem.getUserPrincipalLookupService().lookupPrincipalByName(user);
            Files.setOwner(path, owner);
        }

        @Test
        void checksTheBitsOfTheOwner() throws IOException {
            Path file = Files.writeString(path("file"), "content");

            permissions(file, "-w-------");

            assertThatExceptionOfType(AccessDeniedException.class)
                    .isThrownBy(() -> Files.readString(file))
                    .withMessage("file");
            Files.writeString(file, "changed");
            permissions(file, "r--------");
            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> Files.writeString(file, "x"));
            assertThat(Files.readString(file)).isEqualTo("changed");
            assertThat(Files.isReadable(file)).isTrue();
            assertThat(Files.isWritable(file)).isFalse();
            assertThat(Files.isExecutable(file)).isFalse();
        }

        @Test
        void checksTheBitsOfTheGroupAndOthers() throws IOException {
            Path shared = Files.writeString(path("shared"), "content");
            Path foreign = Files.writeString(path("foreign"), "content");
            permissions(shared, "---r-----");
            owner(shared, "bob");
            Files.setAttribute(
                    foreign,
                    "posix:group",
                    fileSystem.getUserPrincipalLookupService().lookupPrincipalByGroupName("x"));
            permissions(foreign, "rw-rw----");

            assertThat(Files.readString(shared)).isEqualTo("content");
            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> Files.writeString(shared, "x"));
            assertThat(Files.isReadable(foreign)).isTrue();
            owner(foreign, "bob");
            assertThat(Files.isReadable(foreign)).isFalse();
        }

        @Test
        void needsToSearchDirectoriesOnTheWay() throws IOException {
            Path directory = Files.createDirectory(path("dir"));
            Files.writeString(directory.resolve("file"), "content");

            permissions(directory, "rw-------");

            assertThatExceptionOfType(AccessDeniedException.class)
                    .isThrownBy(() -> Files.readString(directory.resolve("file")));
            permissions(directory, "rwx------");
            assertThat(Files.readString(directory.resolve("file"))).isEqualTo("content");
        }

        @Test
        void needsToWriteDirectoriesToChangeTheirEntries() throws IOException {
            Path directory = Files.createDirectory(path("dir"));
            Path file = Files.createFile(directory.resolve("file"));

            permissions(directory, "r-x------");

            assertThatExceptionOfType(AccessDeniedException.class)
                    .isThrownBy(() -> Files.createFile(directory.resolve("new")));
            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> Files.delete(file));
            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> Files.move(file, path("moved")));
            assertThatExceptionOfType(AccessDeniedException.class)
                    .isThrownBy(() -> Files.createDirectory(directory.resolve("sub")));
            assertThatExceptionOfType(AccessDeniedException.class)
                    .isThrownBy(() -> Files.createSymbolicLink(directory.resolve("link"), file));
            assertThatExceptionOfType(AccessDeniedException.class)
                    .isThrownBy(() -> Files.createLink(directory.resolve("link"), file));
        }

        @Test
        void needsToReadDirectoriesToListThem() throws IOException {
            Path directory = Files.createDirectory(path("dir"));

            permissions(directory, "-wx------");

            assertThatExceptionOfType(AccessDeniedException.class)
                    .isThrownBy(() -> fileSystem.provider().newDirectoryStream(directory, entry -> true));
        }

        @Test
        void letsOnlyTheOwnerChangePermissions() throws IOException {
            Path file = Files.createFile(path("file"));
            owner(file, "bob");

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> permissions(file, "rwxrwxrwx"));
            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> owner(file, "alice"));
        }

        @Test
        void neverDeniesRoot() throws IOException {
            try (FileSystem root = MemoryFileSystems.newFileSystem(
                    Configuration.unix().toBuilder().currentUser("root").build())) {
                Path file = Files.writeString(root.getPath("file"), "content");
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));
                Files.setOwner(file, root.getUserPrincipalLookupService().lookupPrincipalByName("bob"));

                assertThat(Files.readString(file)).isEqualTo("content");
                root.provider().checkAccess(file, AccessMode.READ, AccessMode.WRITE, AccessMode.EXECUTE);
            }
        }

        @Test
        void checksAccessModes() throws IOException {
            Path file = Files.createFile(path("file"));
            permissions(file, "r-x------");

            fileSystem.provider().checkAccess(file, AccessMode.READ, AccessMode.EXECUTE);

            assertThatExceptionOfType(AccessDeniedException.class)
                    .isThrownBy(() -> fileSystem.provider().checkAccess(file, AccessMode.WRITE));
        }
    }

    @Nested
    class Windows {

        private final FileSystem fileSystem = MemoryFileSystems.newFileSystem(Configuration.windows().toBuilder()
                .currentUser("alice", "Users")
                .build());

        @AfterEach
        void close() throws IOException {
            fileSystem.close();
        }

        @Test
        void refusesToWriteOrDeleteReadOnlyFiles() throws IOException {
            Path file = Files.writeString(fileSystem.getPath("file"), "content");

            Files.setAttribute(file, "dos:readonly", true);

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> Files.writeString(file, "x"));
            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> Files.delete(file));
            assertThat(Files.readString(file)).isEqualTo("content");
            Files.setAttribute(file, "dos:readonly", false);
            Files.delete(file);
        }

        @Test
        void followsTheFirstMatchingEntryOfAnAccessControlList() throws IOException {
            Path file = Files.writeString(fileSystem.getPath("file"), "content");
            AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);
            var users = fileSystem.getUserPrincipalLookupService().lookupPrincipalByGroupName("Users");
            var everyone = fileSystem.getUserPrincipalLookupService().lookupPrincipalByName("EVERYONE@");
            var bob = fileSystem.getUserPrincipalLookupService().lookupPrincipalByName("bob");

            view.setAcl(List.of(
                    entry(AclEntryType.ALLOW, bob, AclEntryPermission.WRITE_DATA),
                    entry(AclEntryType.DENY, users, AclEntryPermission.WRITE_DATA),
                    entry(AclEntryType.ALLOW, everyone, AclEntryPermission.READ_DATA, AclEntryPermission.WRITE_DATA)));

            assertThat(Files.readString(file)).isEqualTo("content");
            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> Files.writeString(file, "x"));
            assertThat(Files.isExecutable(file)).isFalse();
        }

        private AclEntry entry(AclEntryType type, UserPrincipal principal, AclEntryPermission... permissions) {
            return AclEntry.newBuilder()
                    .setType(type)
                    .setPrincipal(principal)
                    .setPermissions(permissions)
                    .build();
        }
    }
}
