# Platform

`gaia-platform` tells what the JVM runs on, and where applications keep their files there.

```xml
<dependency>
  <groupId>be.imgn.gaia</groupId>
  <artifactId>gaia-platform</artifactId>
</dependency>
```

## The current platform

[`Platform`](api:be.imgn.gaia.platform.Platform)`.current()` detects the platform of the running JVM from its system
properties and environment variables, once. It tells the operating system with `isLinux()`, `isMacOS()` and
`isWindows()`; on other Unix-like systems, such as FreeBSD, all three are `false`. It also gives the file system all
its paths belong to, and the base directories of the platform.

Code that receives a `Platform` instead of calling `current()` itself can be tested against any platform: see
[`gaia-platform-testing`](testing.md#gaia-platform-testing).

## Application directories

[`ApplicationDirectories`](api:be.imgn.gaia.platform.ApplicationDirectories) are the directories where one application
keeps its configuration, data, state, cache and runtime files, kept apart on every operating system:

```java
ApplicationDirectories directories = Platform.current().applicationDirectories("Quuz");
Path settings = directories.config().resolve("settings.toml");
```

The directories are not created: create each one before writing in it. For an application named `Quuz`:

| Directory   | Linux and other Unix      | macOS                                  | Windows                 |
|-------------|---------------------------|----------------------------------------|-------------------------|
| `config()`  | `~/.config/quuz`          | `~/Library/Application Support/Quuz/config` | `%APPDATA%\Quuz\config` |
| `data()`    | `~/.local/share/quuz`     | `~/Library/Application Support/Quuz/data`   | `%APPDATA%\Quuz\data`   |
| `state()`   | `~/.local/state/quuz`     | `~/Library/Application Support/Quuz/state`  | `%LOCALAPPDATA%\Quuz\state` |
| `cache()`   | `~/.cache/quuz`           | `~/Library/Caches/Quuz`                | `%LOCALAPPDATA%\Quuz\cache` |
| `runtime()` | `$XDG_RUNTIME_DIR/quuz`   | none                                   | none                    |

On Linux and other Unix-like systems, the name is in lower case with spaces replaced by `-`, under the base directories
of the XDG Base Directory Specification, which its environment variables can move.

Two overloads name the application more fully, for the operating systems that use more than its name:

- `applicationDirectories("bar", "Quuz")` adds the vendor, which Windows nests the application under:
  `%APPDATA%\bar\Quuz`.
- `applicationDirectories("foo", "bar", "Quuz")` adds the vendor's qualifier, its top-level domain, which macOS names
  the directories after: `~/Library/Application Support/foo.bar.Quuz`.

## Base directories

[`Directories`](api:be.imgn.gaia.platform.Directories) are the base directories, shared by all applications, as
defined by the [XDG Base Directory Specification](https://specifications.freedesktop.org/basedir-spec/latest/):

| Directory      | Linux and other Unix                      | macOS                           | Windows                  |
|----------------|-------------------------------------------|---------------------------------|--------------------------|
| `home()`       | `$HOME`                                   | `user.home`                     | `user.home`              |
| `dataHome()`   | `$XDG_DATA_HOME`, `~/.local/share`        | `~/Library/Application Support` | `%APPDATA%`              |
| `configHome()` | `$XDG_CONFIG_HOME`, `~/.config`           | `~/Library/Application Support` | `%APPDATA%`              |
| `stateHome()`  | `$XDG_STATE_HOME`, `~/.local/state`       | `~/Library/Application Support` | `%LOCALAPPDATA%`         |
| `cacheHome()`  | `$XDG_CACHE_HOME`, `~/.cache`             | `~/Library/Caches`              | `%LOCALAPPDATA%`         |
| `runtimeDir()` | `$XDG_RUNTIME_DIR`, or none               | none                            | none                     |
| `dataDirs()`   | `$XDG_DATA_DIRS`, `/usr/local/share:/usr/share` | `/Library/Application Support` | `%ProgramData%`   |
| `configDirs()` | `$XDG_CONFIG_DIRS`, `/etc/xdg`            | `/Library/Application Support`  | `%ProgramData%`          |

On Linux and other Unix-like systems, a variable that is unset, empty or not an absolute path gives way to the
default that follows it. On Windows, the directories come from the variables Windows sets, with their usual locations
as defaults. On Windows and macOS several of them are the same directory, so put an application's files in
subdirectories named after it, or use its [application directories](#application-directories).
