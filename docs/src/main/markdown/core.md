# Core

`gaia-core` extends the JDK with what many projects end up writing themselves: preconditions, collections, file I/O,
system properties, the [platform](platform.md) the JVM runs on, text and time.

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

On Java 24 and later, `MoreGatherers.instanceOf(type)` keeps the elements of a stream that are of a type, cast to it:

```java
Stream<Circle> circles = shapes.stream().gather(MoreGatherers.instanceOf(Circle.class));
```

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
such as `userHome()` or `javaVersion()`.

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
