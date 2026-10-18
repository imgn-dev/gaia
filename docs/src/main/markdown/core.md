# Core

`gaia-core` extends the JDK with what many projects end up writing themselves: preconditions, collections, file I/O,
system properties, the [platform](#platform) the JVM runs on, text and time.

```xml
<dependency>
  <groupId>be.imgn.gaia</groupId>
  <artifactId>gaia-core</artifactId>
</dependency>
```

## Preconditions

[`Preconditions`](api:be.imgn.gaia.core.base.Preconditions) has `checkArgument` and `checkState`, which throw
`IllegalArgumentException` or `IllegalStateException` when a check fails, with an optional message or
`String.format` message format:

```java
checkArgument(count >= 0, "count must not be negative: %d", count);
```

[`Throwables`](api:be.imgn.gaia.core.base.Throwables) creates such exceptions from the exception that caused them:

```java
throw Throwables.illegalArgument(e, "Not a port: %s", text);
```

## Collections

[`Sets`](api:be.imgn.gaia.core.collect.Sets) makes unmodifiable sets that keep the order their elements are given in.
`set(...)` and `setCopy(...)` behave like `Set.of` and `Set.copyOf`, and the `toSet()` collector like
`Collectors.toUnmodifiableSet()`, except for iteration order.

A [`Multiset`](api:be.imgn.gaia.core.collect.Multiset) counts the occurrences of each element, with `count`,
`add(element, n)`, `remove(element, n)`, `setCount`, and live `elementSet()` and `entrySet()` views:

- [`HashMultiset`](api:be.imgn.gaia.core.collect.HashMultiset)`.create()` is backed by a hash map.
- [`TreeMultiset`](api:be.imgn.gaia.core.collect.TreeMultiset)`.create()` keeps its elements sorted, as a
  [`SortedMultiset`](api:be.imgn.gaia.core.collect.SortedMultiset) with first, last and nearest entries, live range
  views and a `reversed()` view.
- [`Multisets`](api:be.imgn.gaia.core.collect.Multisets)`.multiset(...)`, `multisetCopy(...)` and the
  `toMultiset()` collector make unmodifiable multisets that iterate in the order their elements first appear.

[`MoreCollectors`](api:be.imgn.gaia.core.collect.MoreCollectors) has `onlyElement()`, which collects the only element
of a stream, and `toOptional()`, which collects zero or one element.

On Java 24 and later, `MoreGatherers` has the gatherers the JDK lacks:

```java
Stream<Circle> circles = shapes.stream().gather(MoreGatherers.instanceOf(Circle.class));
lines.stream().gather(MoreGatherers.takeWhileInclusive(line -> !line.equals("END")));  // up to and including END
scores.stream().gather(MoreGatherers.max(3, Comparator.naturalOrder()));               // the top three, greatest first
```

| Gatherer | Keeps |
|---|---|
| `instanceOf(type)` | The elements of a type, cast to it |
| `distinctBy(key)` | The first element of each key |
| `distinctAdjacent()`, `distinctAdjacentBy(key)` | The elements that differ from the one right before, or whose key does |
| `takeWhileInclusive(predicate)` | The elements while the predicate holds, and the first that fails it |
| `groupRuns(key)` | The runs of consecutive elements with equal keys, as lists |
| `takeLast(n)`, `dropLast(n)` | The last `n` elements, or all but them |
| `mapWithIndex(mapper)`, `filterWithIndex(predicate)` | The elements mapped, or filtered, with their index |
| `everyNth(n)` | The elements at index 0, `n`, `2n`, and so on |
| `max(n, comparator)`, `min(n, comparator)` | The `n` greatest or least elements, in order |

They accept `null` elements, as streams do. In a parallel stream, `instanceOf`, `takeWhileInclusive`, `takeLast`,
`max` and `min` run in parallel; the others depend on the elements before each one, so they run sequentially.

`gaia-core` is a multi-release jar that adds `MoreGatherers` on Java 24. Tools that validate multi-release jars
strictly, such as `jar --validate`, report it as a public class missing from the Java 21 version. That doesn't affect
compiling or running.

## Files and streams

[`AtomicFiles`](api:be.imgn.gaia.core.io.AtomicFiles) writes a file so that readers, or the next run after a crash,
see either its old content or its new one, never a part of it. The file keeps its permissions:

```java
AtomicFiles.writeString(file, text);
AtomicFiles.write(file, out -> ImageIO.write(image, "png", out));
```

The callback form commits the file only if the callback returns normally.

[`ByteSource`](api:be.imgn.gaia.core.io.ByteSource), [`CharSource`](api:be.imgn.gaia.core.io.CharSource),
[`ByteSink`](api:be.imgn.gaia.core.io.ByteSink) and [`CharSink`](api:be.imgn.gaia.core.io.CharSink) are files, URLs,
arrays or strings to read or write as a whole, each read or write opening and closing its own stream:

```java
List<String> lines = CharSource.of(file, UTF_8).readLines();
byte[] magic = ByteSource.of(uri).slice(0, 4).read();
CharSink.of(file, UTF_8).writeLines(lines);
```

Each is a functional interface, so a lambda that opens a stream makes one.

## System properties and environment variables

[`SystemProperties`](api:be.imgn.gaia.core.system.SystemProperties) and
[`Environment`](api:be.imgn.gaia.core.system.Environment) read system properties and environment variables from the
running JVM, or from fixed values in tests. `SystemProperties` has a typed method for each standard Java property,
such as `userHome()` or `javaVersion()`. Paths come back as `Path`s of the default file system. For the directories
of an application, or paths in a file system a test provides, the [platform](#platform) is often the better fit.

## Platform

The `platform` package tells what the JVM runs on, and where applications keep their files there. Up to 0.1.1, it
was a library of its own, `gaia-platform`, with its fakes in `gaia-platform-testing`.

### The current platform

[`Platform`](api:be.imgn.gaia.core.platform.Platform)`.current()` detects the platform of the running JVM from its
system properties and environment variables, once. It tells the operating system with `isLinux()`, `isMacOS()` and
`isWindows()`; on other Unix-like systems, such as FreeBSD, all three are `false`. It also gives the file system all
its paths belong to, and the base directories of the platform.

Code that receives a `Platform` instead of calling `current()` itself can be tested against any platform: see
[`gaia-core-testing`](testing.md#platforms).

### Application directories

[`ApplicationDirectories`](api:be.imgn.gaia.core.platform.ApplicationDirectories) are the directories where one
application keeps its configuration, data, state, cache and runtime files, kept apart on every operating system:

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

### Base directories

[`Directories`](api:be.imgn.gaia.core.platform.Directories) are the base directories, shared by all applications, as
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

## Text

[`Splitter`](api:be.imgn.gaia.core.text.Splitter) splits text on a character, a literal string or a regular
expression, with optional trimming, omission of empty parts and a limit:

```java
Splitter.on(',').trimResults().omitEmptyStrings().split(" a, ,b ,, c ");  // [a, b, c]
Splitter.on("::").split("a::b");                                           // [a, b]
```

Unlike `String.split`, a string separator is literal, and nothing is dropped unless asked.

[`NamingConvention`](api:be.imgn.gaia.core.text.NamingConvention) converts names between naming conventions:
`LOWER_CAMEL`, `UPPER_CAMEL`, `LOWER_HYPHEN`, `LOWER_UNDERSCORE`, `UPPER_UNDERSCORE` and `LOWER_DOT`:

```java
NamingConvention.LOWER_CAMEL.toLowerHyphen("timeoutSeconds");       // timeout-seconds
NamingConvention.UPPER_UNDERSCORE.toLowerHyphen("TIMEOUT_SECONDS");  // timeout-seconds
NamingConvention.LOWER_HYPHEN.toUpperCamel("http-url");              // HttpUrl
NamingConvention.LOWER_HYPHEN.from("TIMEOUT_SECONDS");               // timeout-seconds
```

`source.to(target, text)` converts to a convention chosen at run time, and `from` converts text that follows any
convention. Text that several conventions accept, such as `name`, is a single word, so it converts the same from any
of them.

A conversion splits the text into words, then joins them in the target convention, whatever the default locale. In
camel case, a word starts at an uppercase letter after a lowercase letter or a digit, and at the last uppercase letter
of a run followed by a lowercase letter: `httpURLPath` gives `http-url-path`, `ipv4Address` gives `ipv4-address`, and
`utf8` stays one word. Joined in camel case, a word keeps only its first letter in uppercase, so `http-url` gives
`httpUrl`. Text that doesn't follow the convention it is converted from, or no convention at all for `from`, throws
`IllegalArgumentException`.

## Time

[`Stopwatch`](api:be.imgn.gaia.core.time.Stopwatch) measures elapsed time on a monotonic
[`Ticker`](api:be.imgn.gaia.core.time.Ticker), with `start()`, `stop()` and `reset()`. Its `toString()` reads like
`1.234 s`:

```java
Stopwatch stopwatch = Stopwatch.started();
load();
log.log(INFO, "Loaded in {0}", stopwatch);
```

In tests, a [`FakeTicker`](testing.md#gaia-core-testing) makes timing code exact and instant.
