# Memory file system

`gaia-memfs` is a file system that lives in memory until it is closed, for tests or scratch work. It supports the
whole `java.nio.file` API, so code written against `Path` and `Files` runs on it unchanged.

```xml
<dependency>
  <groupId>be.imgn.gaia</groupId>
  <artifactId>gaia-memfs</artifactId>
</dependency>
```

## Creating a file system

[`MemoryFileSystems`](api:be.imgn.gaia.memfs.MemoryFileSystems) creates file systems. Their files are gone once they
are closed:

```java
try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
    Path file = fileSystem.getPath("/work/hello.txt");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "Hello");
}
```

Each file system has a name, unique unless given, and its paths have URIs of the `mem` scheme with that name:
`mem://test/work/hello.txt` for the path `/work/hello.txt` of the file system `test`, or
`mem://test/C:/work/hello.txt` for `C:\work\hello.txt` on a Windows-style one. While the file system is open,
`Path.of(uri)` finds its paths from such URIs, and `FileSystems.getFileSystem(URI.create("mem://test"))` finds it.
`FileSystems.newFileSystem(URI.create("mem://test"), env)` creates one too, given its configuration under the key
`"configuration"`.

Everything is supported, except `Path.toFile()` and `FileChannel.map()`, which can't work on files in memory:

- byte channels, file channels with locks, and asynchronous file channels;
- symbolic and hard links;
- secure directory streams;
- the `basic`, `owner`, `posix`, `unix`, `dos`, `acl` and `user` attribute views;
- glob and regex path matchers;
- a watch service, which polls for changes.

The file systems are thread-safe.

## Configuration

A [`Configuration`](api:be.imgn.gaia.memfs.Configuration) sets how a file system behaves. Three presets look like
real systems:

| Preset      | Root  | Working directory | Names                                   | Attribute views                        |
|-------------|-------|-------------------|-----------------------------------------|----------------------------------------|
| `unix()`    | `/`   | `/work`           | matched exactly                         | `basic`, `owner`, `posix`, `unix`      |
| `macOS()`   | `/`   | `/work`           | case ignored, Unicode normalized to NFD | `basic`, `owner`, `posix`, `unix`      |
| `windows()` | `C:\` | `C:\work`         | case ignored                            | `basic`, `owner`, `dos`, `acl`, `user` |

`forCurrentPlatform()` picks the one that looks like the operating system the JVM runs on.

`toBuilder()` changes a configuration, and `newBuilder(PathStyle)` starts one from scratch:

```java
Configuration small = Configuration.windows().toBuilder()
        .roots("C:\\", "D:\\")
        .maxSize(16 * 1024 * 1024)
        .currentUser("alice", "staff")
        .build();
FileSystem fileSystem = MemoryFileSystems.newFileSystem(small);
```

The builder sets:

- the roots: drives and UNC shares on Windows;
- the working directory, which relative paths are resolved against;
- the block size and the capacity, which fill as files grow (8 KiB and 4 GiB unless set);
- how names are matched ([`NameMatching`](api:be.imgn.gaia.memfs.NameMatching)), their Unicode normalization, and
  their maximum length;
- the attribute views and the default attributes of new files;
- the clock that file times are taken from;
- the polling interval of the watch service;
- the optional [`Feature`](api:be.imgn.gaia.memfs.Feature)s: hard links, symbolic links, secure directory streams and
  file channels.

## Permissions

Permissions are always stored. They are enforced only when a current user is configured, with
`currentUser("alice", "staff")`: then the POSIX permissions of the `posix` view, the read-only flag of the `dos` view
and the entries of a non-empty access control list of the `acl` view are checked against that user and their groups,
and a denied operation throws `AccessDeniedException`. The user `root` is never denied.
