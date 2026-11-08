# Core

`gaia-core` extends the JDK with what many projects end up writing themselves: preconditions, collections, file I/O,
system properties, the [platform](#platform) the JVM runs on, text, [versions](#versions) and time. It also has an
[event bus](event-bus.md).

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
`Collectors.toUnmodifiableSet()`, except for iteration order. [`Maps`](api:be.imgn.gaia.core.collect.Maps) does the
same for maps: `map(...)`, `mapOfEntries(...)` and `mapCopy(...)` behave like `Map.of`, `Map.ofEntries` and
`Map.copyOf`, and the `toMap(...)` collectors like `Collectors.toUnmodifiableMap(...)`, except for iteration order.

```java
Map<String, Integer> ports = Maps.map("http", 80, "https", 443, "ssh", 22);
ports.keySet(); // [http, https, ssh], in that order
```

A [`Multiset`](api:be.imgn.gaia.core.collect.Multiset) counts the occurrences of each element, with `count`,
`add(element, n)`, `remove(element, n)`, `setCount`, and live `elementSet()` and `entrySet()` views:

- [`HashMultiset`](api:be.imgn.gaia.core.collect.HashMultiset)`.create()` is backed by a hash map.
- [`TreeMultiset`](api:be.imgn.gaia.core.collect.TreeMultiset)`.create()` keeps its elements sorted, as a
  [`SortedMultiset`](api:be.imgn.gaia.core.collect.SortedMultiset) with first, last and nearest entries, live range
  views and a `reversed()` view.
- [`Multisets`](api:be.imgn.gaia.core.collect.Multisets)`.multiset(...)`, `multisetCopy(...)` and the
  `toMultiset()` collector make unmodifiable multisets that iterate in the order their elements first appear.

A [`Multimap`](api:be.imgn.gaia.core.collect.Multimap) maps keys to several values. `get(key)` is a live view of the
values of a key, empty if the key is absent, and `put`, `remove(key, value)`, `removeAll(key)` and `replaceValues`
change them. Its `keySet()`, `values()`, `entries()` and `asMap()` views are live too:

- A [`ListMultimap`](api:be.imgn.gaia.core.collect.ListMultimap) keeps the values of a key in a list, in the order
  they were added, and [`HashListMultimap`](api:be.imgn.gaia.core.collect.HashListMultimap)`.create()` makes one
  backed by a hash map.
- A [`SetMultimap`](api:be.imgn.gaia.core.collect.SetMultimap) keeps them in a set, without duplicates, and
  [`HashSetMultimap`](api:be.imgn.gaia.core.collect.HashSetMultimap)`.create()` makes one backed by a hash map.
- [`Multimaps`](api:be.imgn.gaia.core.collect.Multimaps)`.listMultimapCopy(...)`, `setMultimapCopy(...)` and the
  `toListMultimap(...)` and `toSetMultimap(...)` collectors make unmodifiable multimaps that iterate in the order their
  keys first appear.

```java
ListMultimap<Character, String> byInitial = Stream.of("bob", "alice", "bill")
        .collect(Multimaps.toListMultimap(name -> name.charAt(0), name -> name));
byInitial.get('b'); // [bob, bill]
```

A [`Range`](api:be.imgn.gaia.core.collect.Range) is an interval of comparable values. `closed`, `open`, `closedOpen`,
`openClosed`, `atLeast`, `greaterThan`, `atMost`, `lessThan` and `all` make one, and `contains`, `encloses`,
`isConnected`, `intersection` and `span` compare it with values and other ranges:

```java
Range<Integer> digits = Range.closedOpen(0, 10);
digits.contains(9);                       // true
digits.intersection(Range.atLeast(5));    // Optional[[5, 10)]
digits.span(Range.closed(20, 30));        // [0, 30]
```

Its `lower()` and `upper()` bounds are a sealed [`Range.Bound`](api:be.imgn.gaia.core.collect.Range.Bound): `Closed`,
`Open` or `Unbounded`, which a `switch` can match.

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
arrays or strings to read or write as a whole, each read or write opening and closing its own reader or writer:

```java
List<String> lines = CharSource.of(file, UTF_8).readLines();
Bytes magic = ByteSource.of(uri).slice(0, 4).read();
CharSink.of(file, UTF_8).writeLines(lines);
```

Each is a functional interface, so a lambda that opens a reader or a writer makes one.

[`ByteReader`](api:be.imgn.gaia.core.io.ByteReader) and [`ByteWriter`](api:be.imgn.gaia.core.io.ByteWriter) read and
write binary formats on a stream: numbers in big-endian order, as `DataInputStream` does, or in little-endian order
with the methods that end in `Le`, runs of bytes, text and lines. [`CharReader`](api:be.imgn.gaia.core.io.CharReader)
and [`CharWriter`](api:be.imgn.gaia.core.io.CharWriter) read and write text, with the lookahead and scanning that
parsers need. A reader asks its stream only for what a call needs, so it works on sockets and pipes too:

```java
try (var in = ByteSource.of(classFile).openReader()) {
    var magic = in.readInt();
    var minor = in.readUnsignedShort();
    var major = in.readUnsignedShort();
}
try (var out = ByteSink.of(file).openWriter()) {
    out.writeIntLe(entries.size()).writeString(name);
}
```

The methods that read until they find something, such as the end of a line, take a maximum, so that untrusted input
cannot fill the memory:

```java
Optional<String> line = in.readLine(8_192);
```

[`Bytes`](api:be.imgn.gaia.core.io.Bytes) is an immutable sequence of bytes to use as a value, with hex, Base64, text
and digests:

```java
String checksum = Bytes.encode(text).digest("SHA-256").toHex();
```

## System properties and environment variables

[`SystemProperties`](api:be.imgn.gaia.core.base.SystemProperties) and
[`Environment`](api:be.imgn.gaia.core.base.Environment) read system properties and environment variables from the
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

Each name must be valid on every operating system, whichever the JVM runs on: not blank, `.` or `..`, without leading
or trailing whitespace, control characters or any of `/ \ : < > " | ? *`, not ending with a dot, and not the name of a
Windows device such as `CON`, `NUL`, `COM1` or `LPT9`, with or without an extension. To check a name before it reaches the
platform, such as one an annotation gives to an annotation processor, call
[`ApplicationDirectories.checkName`](api:be.imgn.gaia.core.platform.ApplicationDirectories), which throws the same
`IllegalArgumentException`.

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

[`Splitter`](api:be.imgn.gaia.core.base.Splitter) splits text on a character, a literal string or a regular
expression, with optional trimming, omission of empty parts and a limit:

```java
Splitter.on(',').trimResults().omitEmptyStrings().split(" a, ,b ,, c ");  // [a, b, c]
Splitter.on("::").split("a::b");                                           // [a, b]
```

Unlike `String.split`, a string separator is literal, and nothing is dropped unless asked.

[`Text`](api:be.imgn.gaia.core.base.Text) finds one occurrence in a text, to extract, remove or replace it. A pattern
selects the first or last occurrence of a character, a literal string or a regular expression, or a prefix or suffix,
and conditions narrow it down to an occurrence that is, or is not, preceded or followed by a literal string:

```java
Text.prefix("www.").removeFrom("www.example.com");                 // example.com
Text.last('.').removeFrom("archive.tar.gz");                       // archive.targz
Text.first('@').followedBy("gmail.com").replaceFrom(s, " at ");    // the first @ before gmail.com
Text.first(Pattern.compile("\\d+")).extractFrom("v12.3");          // Optional[12]
Text.suffix('/').addToIfAbsent("/api");                            // /api/
```

Conditions are checked against the whole text and aren't part of the occurrence. A prefix or suffix is an
[`Affix`](api:be.imgn.gaia.core.base.Text.Affix): a `CharSequence` of its literal that can also be added to a text that
lacks it.

[`NamingConvention`](api:be.imgn.gaia.core.base.NamingConvention) converts names between naming conventions:
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

## Math

[`IntMath`](api:be.imgn.gaia.core.math.IntMath) and [`LongMath`](api:be.imgn.gaia.core.math.LongMath) add the
integer arithmetic that `Math` lacks: greatest common divisors and least common multiples, powers of two, logarithms,
square roots and divisions with a `RoundingMode`, powers, modular powers, saturated arithmetic, overflow-free means,
factorials, binomial coefficients and an exact primality test:

```java
IntMath.gcd(-12, 18);                           // 6
IntMath.sqrt(99, RoundingMode.HALF_EVEN);       // 10
IntMath.divide(7, 2, RoundingMode.HALF_UP);     // 4
LongMath.saturatedMultiply(Long.MAX_VALUE, 2);  // Long.MAX_VALUE
LongMath.isPrime(Long.MAX_VALUE - 24);          // true
```

A result that doesn't fit throws `ArithmeticException`, except from `pow`, which wraps around, and the `saturated`
methods, which return the closest value that fits.

## Versions

[`Version`](api:be.imgn.gaia.core.base.Version) is a version number as
[Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html) defines it: a major, minor and patch version, then
optional pre-release identifiers and build metadata.

```java
Version version = Version.parse("1.4.0-rc.1+build.5");
version.preRelease();                // [rc, 1]
Version.tryParse("v1.4.0");          // Optional.empty: not a semantic version
Version.of(1, 4, 0).isPreRelease();  // false
```

`parse` throws `IllegalArgumentException` saying what is wrong with the text, and `tryParse` returns an empty
`Optional` instead. Versions are `Comparable` by precedence, exactly as the specification defines it:
`1.0.0-alpha < 1.0.0-alpha.1 < 1.0.0-beta < 1.0.0-rc.1 < 1.0.0 < 1.0.1`. Build metadata doesn't count, so `1.0.0+a` and
`1.0.0+b` compare as equal while not being `equals`: a sorted set keeps only one of them. The major, minor and patch
versions are `long`s, so a larger number is rejected, although the specification sets no limit.

## Time

[`Stopwatch`](api:be.imgn.gaia.core.base.Stopwatch) measures elapsed time on a monotonic
[`Ticker`](api:be.imgn.gaia.core.base.Ticker), with `start()`, `stop()` and `reset()`. Its `toString()` reads like
`1.234 s`:

```java
Stopwatch stopwatch = Stopwatch.started();
load();
log.log(INFO, "Loaded in {0}", stopwatch);
```

In tests, a [`FakeTicker`](testing.md#gaia-core-testing) makes timing code exact and instant.
