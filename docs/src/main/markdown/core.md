# Core

`gaia-core` extends the JDK with what many projects end up writing themselves: preconditions, collections, file I/O,
system properties, the [platform](#platform) the JVM runs on, text, math, [random selection](#random-selection),
[dice](#dice), [versions](#versions) and time. It also has an [event bus](event-bus.md).

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

`MoreGatherers` has the gatherers the JDK lacks:

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

A sink writes a file in one of the usual ways, `replacing`, `appending` or `creatingNew`, or opens it with the
options given to `of`:

```java
CharSink.appending(log, UTF_8).write(entry);
ByteSink.creatingNew(file).write(bytes);
ByteSink.of(file, CREATE, APPEND, SYNC).write(bytes);
```

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

[`IntFraction`](api:be.imgn.gaia.core.math.IntFraction) is an exact fraction of two `int` values, kept in lowest terms
with a positive denominator, so that equal values are equal objects:

```java
var half = IntFraction.of(2, 4);            // 1/2
half.plus(IntFraction.of(1, 3));            // 5/6
half.times(IntFraction.of(-4));             // -2
half.compareTo(IntFraction.of(3, 5)) < 0;   // true
half.doubleValue();                         // 0.5
```

Intermediate results are computed on `long` values; a result that doesn't fit once reduced throws
`ArithmeticException`. [`LongFraction`](api:be.imgn.gaia.core.math.LongFraction) is the same on `long` values, and
[`BigFraction`](api:be.imgn.gaia.core.math.BigFraction) on `BigInteger` values, which never overflows. Each converts
to the other ones, with `toBigFraction()` or `toIntFractionExact()` for instance, and `BigFraction.of(0.1)` is the
fraction that a `double` is exactly.

The three extend [`Fraction`](api:be.imgn.gaia.core.math.Fraction), a sealed `Number` with what they have in common
whatever their size: fractions of any class compare by value and convert to one another. An operation on two
`Fraction`s returns one of the larger of their two classes, while the operations of each class on its own fractions
return that class, so `IntFraction.plus(IntFraction)` is an `IntFraction`. A result that doesn't fit throws
`ArithmeticException` instead of getting a larger class. Fractions of different classes are never equal, as an
`Integer` never equals a `Long`.

## Random selection

[`RandomSelector`](api:be.imgn.gaia.core.random.RandomSelector) selects an element at random, uniformly or by weight,
with the `RandomGenerator` it's given:

```java
var rarity = RandomSelector.weighted(Maps.map("common", 70, "rare", 25, "legendary", 5));
rarity.next(random);                            // "common", 70% of the time
rarity.stream(random).limit(10).toList();

var monster = RandomSelector.weighted(bestiary, Monster::frequency);
var card = RandomSelector.uniform(deck);
```

A weighted selection takes the same time whatever the number of elements, since the weights are turned into an alias
table when the selector is created, and takes a single `nextLong()` from the generator. A weight of zero is allowed, and its element is never selected. With a seeded
generator, the selections depend on the order of the elements, so give `weighted` a map with a defined order, such as
one from `Maps.map`.

`RandomSelector` is a functional interface, so any function of a `RandomGenerator` is a selector, and selectors
combine: `map` transforms the selected element, `constant` always selects the same one, and `flatten` selects a
selector, then an element from it, which lets a table of elements refer to other tables:

```java
var gems = RandomSelector.weighted(Maps.map(ruby, 3, diamond, 1));
var weapons = RandomSelector.uniform(List.of(sword, axe, bow));
var chest = RandomSelector.flatten(
        RandomSelector.weighted(Maps.map(gems, 30, weapons, 10, RandomSelector.constant(gold), 60)));
```

[`RandomTable`](api:be.imgn.gaia.core.random.RandomTable) draws several elements at once, as a
[`Multiset`](api:be.imgn.gaia.core.collect.Multiset). A table has entries that every draw gives, and weighted entries
of which a draw selects one, or as many as `draws` says. An entry is an element in some quantity, another table, or
nothing:

```java
var gems = RandomTable.<Item>builder()
        .add(3, ruby)
        .add(1, diamond)
        .build();
var chest = RandomTable.<Item>builder()
        .always(key)
        .add(60, gold, random -> random.nextInt(10, 101))
        .addTable(30, gems)
        .addNothing(10)
        .draws(random -> random.nextInt(1, 4))
        .build();

Multiset<Item> drawn = chest.draw(random);      // such as [key, gold x 57, ruby]
```

A quantity or a number of draws is fixed, or a `RandomSelector<Integer>`, such as a [dice expression](#dice) with
`DiceExpression.parse("2d6").selector()`. With `withoutRepeats()`, a draw selects each weighted entry at most once,
as for the ten different commons of a booster of cards: `.draws(10).withoutRepeats()`. Tables are immutable and only refer to tables built before them, so they
never refer to each other in a circle.

### Chances

[`Chances`](api:be.imgn.gaia.core.random.Chances) are the exact chances of each outcome, as `IntFraction`s. A
selector made by `RandomSelector` knows the chances of its elements, and so do those made from it with `map` and
`flatten`; a table knows the chances of each multiset it may draw:

```java
var rarity = RandomSelector.weighted(Maps.map("common", 70, "rare", 25, "legendary", 5));
rarity.chances().of("rare");                                // 1/4
rarity.chances().where(name -> !name.equals("common"));     // 3/10

var table = RandomTable.<String>builder()
        .add(60, "gold", DiceExpression.parse("2d6").selector())
        .add(30, "gem")
        .addNothing(10)
        .build();
table.chances().where(drawn -> drawn.count("gold") >= 10);  // 1/10
```

A selector that is only a function of a `RandomGenerator` doesn't know its chances, and `chances()` then throws
`UnsupportedOperationException`. Weights that are whole numbers give simple fractions; a weight such as `0.1` is the
`double` nearest to a tenth, whose exact fraction has very large terms, so `of` may throw `ArithmeticException` where
`exactOutcomes()`, which returns `BigFraction`s, doesn't. `chances.selector()` goes the other way: it selects each
outcome with its chance, in constant time.

### Changing weights

[`MutableRandomSelector`](api:be.imgn.gaia.core.random.MutableRandomSelector) is a weighted selector whose elements
and weights change. Adding, removing, reweighting and selecting each take a time that grows with the logarithm of the
number of elements; for weights that don't change, `RandomSelector.weighted` selects faster:

```java
var spawns = new MutableRandomSelector<Monster>();
spawns.put(goblin, 10);
spawns.put(dragon, 1);
spawns.next(random);                            // a goblin, ten times out of eleven
spawns.put(dragon, 5);
spawns.remove(goblin);
```

### Without putting back

[`RandomBag`](api:be.imgn.gaia.core.random.RandomBag) draws elements without putting them back, like cards from a
deck. `refill` puts the drawn elements back, and `refilling` is a selector that refills the bag when it's empty, so
that every element comes up once before any comes up again:

```java
var deck = RandomBag.of(cards);
var hand = deck.draw(5, random);                // five different cards
deck.refill();

var track = RandomBag.of(playlist).refilling();
track.next(random);                             // every track once, then all of them again
```

[`RandomSamples`](api:be.imgn.gaia.core.random.RandomSamples) takes several different elements in one call: from a
collection, uniformly or by weight, or from a stream of any length, of which it keeps only the sample in memory:

```java
RandomSamples.sample(players, 3, random);
RandomSamples.weightedSample(prizes, Prize::tickets, 3, random);
lines.collect(RandomSamples.toSample(100, random));
```

[`RandomPermutation`](api:be.imgn.gaia.core.random.RandomPermutation) is the numbers from zero to a size in a random
order, each exactly once. Past a few thousand numbers the order is computed, not stored, so it visits a range of any
size without shuffling it:

```java
var order = RandomPermutation.of(1_000_000_000_000L, random);
order.get(0);                                   // the first number
order.stream().limit(100);                      // a hundred different numbers
order.indexOf(order.get(42));                   // 42
```

A computed order looks random, but not every order may come up: where each one must be as likely, shuffle a list.

### Seeds

[`Seed`](api:be.imgn.gaia.core.random.Seed) derives seeds from a seed, by name or by number, so that each part of a
program has a random sequence of its own, which depends on the seed and on what the part is, not on what was generated
before it:

```java
var world = Seed.of("my world");
var chunk = world.derive("terrain").derive(x).derive(y).generator();    // the same chunk, whenever it's generated
var loot = world.derive("loot").derive(chestId).generator();
```

Deriving is part of the contract of `Seed`: a seed derives the same seeds in every version and on every JVM.
`generator()` returns a `SplittableRandom`, and `generator("L64X128MixRandom")` a generator of another algorithm.

### Random strings

[`RandomStrings`](api:be.imgn.gaia.core.random.RandomStrings) makes identifiers, tokens and codes from the characters
of an alphabet, each with the same chance:

```java
var secure = new SecureRandom();
RandomStrings.token(128, secure);                               // 22 URL-safe characters, 128 bits
RandomStrings.string(8, RandomStrings.UNAMBIGUOUS, secure);     // a code to read aloud, such as "K7WQ4MZX"
RandomStrings.length(128, RandomStrings.HEXADECIMAL);           // 32
```

A string that must not be guessed needs a `SecureRandom`, not a seeded generator.

In tests, a [`FakeRandom`](testing.md#randomness) gives the numbers that the test scripted, and
[`RandomSelectorAssert`](testing.md#randomness) checks how often a selector selects each element.

## Dice

[`DiceExpression`](api:be.imgn.gaia.core.random.dice.DiceExpression) parses dice expressions, rolls them, and computes
the exact chances of their outcomes:

```java
var attack = DiceExpression.parse("(1d20:critical>=20:miss=1)+STR+BONUS>=AC");
attack.variables();                             // [STR, BONUS, AC]

var bound = attack.bind(Map.of("STR", 3, "BONUS", 2, "AC", 15));
var rolled = bound.roll(random);
rolled.value();                                 // 1 if the attack hits, 0 if it misses
rolled.tags();                                  // [critical] if the die shows 20
rolled.toString();                              // "(1d20[20]:critical)+STR[3]+BONUS[2]>=AC[15] = 1"

var chances = bound.chances();
chances.of(1);                                  // 11/20
chances.tagged("critical");                     // 1/20
```

An expression is made of whole numbers, variables in upper case, `+ - * /` with a division that rounds down, a
unary `-`, one comparison among `= != < <= > >=`, which evaluates to 1 or 0, and parentheses. Dice are a count, `d`
and a number of sides, `%` for 100, or a list of faces such as `4d[-1..1]` or `6d[0,0,0,0,1,1]`, optionally followed by the dice that count in the sum: `2d20kh1` keeps the
highest die, `2d20kl1` the lowest, and `4d6dl1` and `4d6dh1` drop the lowest and the highest. Before that, `2d6r<=2`
rerolls once a die that shows 2 or less, `2d6rr<=2` rerolls it until it shows more, `r` alone rerolls the lowest face, and `3d6x` or `3d6x>=5` makes a
die that shows its highest face, or 5 or more, add another die that may explode too. A comparison operator after
`r`, `rr` or `x` is always the condition of that rule: comparing such dice takes parentheses, as in `(3d6x)>=10`.
Spaces are allowed between any two parts and never change the meaning. A group between
parentheses may end with tags, such as `:fire`, which always applies, or `:critical>=20` and `:tier2>=12<=16`, which
apply when the value of the group satisfies all their conditions.

`roll` and `chances` throw
[`UnresolvedVariableException`](api:be.imgn.gaia.core.random.dice.UnresolvedVariableException) until every variable has a
value. `bind` returns a new expression, in which the variable is still written as its name, and may give a variable
another expression as its value.

An expression is a tree of [`DiceNode`](api:be.imgn.gaia.core.random.dice.DiceNode) records. `node()` returns its root,
and `fold` reduces the tree from its leaves: the function is given each node with its children already replaced by
what it returned for them, and tells the nodes apart with a `switch`:

```java
int dice = expression.<Integer>fold(node -> switch (node) {
    case DiceNode.Dice<Integer> d -> 1;
    default -> node.children().stream().mapToInt(Integer::intValue).sum();
});
```

`toString`, `variables`, `bind`, `roll` and `chances` are all folds.
[`RolledExpression`](api:be.imgn.gaia.core.random.dice.RolledExpression), which `roll` returns, is inspected the same
way, as a tree of [`RolledNode`](api:be.imgn.gaia.core.random.dice.RolledNode) records that hold the value of each node,
the face of each die and whether it was kept, and the tags that apply:

```java
List<Integer> faces = rolled.<List<Integer>>fold(node -> switch (node) {
    case RolledNode.Dice<List<Integer>> d -> d.dice().stream().map(RolledNode.Die::face).toList();
    default -> node.children().stream().flatMap(List::stream).toList();
});
```

[`DiceChances`](api:be.imgn.gaia.core.random.dice.DiceChances) counts the outcomes exactly, values and tags
together, and returns `IntFraction`s:

```java
var chances = DiceExpression.parse("(2d20kh1:critical>=20)+5").chances();
chances.of(25);                                 // 39/400
chances.atLeast(15);                            // 319/400
chances.mean();                                 // 753/40
chances.where(outcome -> outcome.value() >= 15 && !outcome.tags().contains("critical"));   // 7/10
```

Dice that are kept or dropped aren't enumerated roll by roll: their faces are taken one at a time, counting the ways
for each number of dice to show that face, so `30d20kh3` is counted at once. The counts are whole numbers of any
size, but a chance is an `IntFraction`: asking for one that doesn't fit once reduced, such as a single outcome of
`15d20`, throws `ArithmeticException`.

A die that is rerolled is counted as a die whose faces aren't equally likely, and dice that explode by the number of
dice they add, ten at most for each, whether some are then dropped or not.

Custom dice list their faces between brackets, in ascending order, where `..` stands for the faces in between and a
face listed twice comes up twice as often. They roll, reroll, explode and are counted like any other dice:

```java
DiceExpression.parse("4d[-1..1]").chances().of(0);                  // 19/81, four Fudge dice
DiceExpression.parse("6d[0,0,0,0,1,1]").chances().mean();           // 2, six dice that count successes on 5 or 6
DiceExpression.parse("1d[1,2,2,3,3,4]+1d[1,3,4,5,6,8]").chances();  // Sicherman dice: the chances of 2d6
```

An expression is also a selector: `expression.selector()` rolls it on each selection, and
`expression.chances().selector()` selects a value with its chance without rolling any die. Both know their
[chances](#chances), as `chances.values()` gives them.

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
