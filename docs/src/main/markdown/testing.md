# Testing

Each library has a testing library, for the tests of the code that uses it. Declare them in the test scope:

```xml
<dependency>
  <groupId>be.imgn.gaia</groupId>
  <artifactId>gaia-core-testing</artifactId>
  <scope>test</scope>
</dependency>
```

The testing libraries work with any test framework, and their assertions are AssertJ assertions.
`gaia-processing-testing` also has a JUnit extension.

For code that works on files, the [memory file system](memfs.md) gives each test a file system of its own.

## gaia-core-testing

### Time

[`FakeTicker`](api:be.imgn.gaia.core.testing.FakeTicker) is a `Ticker` that only moves when told to, so that tests of
timing code are exact and instant:

```java
FakeTicker ticker = new FakeTicker();
Stopwatch stopwatch = Stopwatch.started(ticker);
ticker.advance(Duration.ofSeconds(2));
assertThat(stopwatch.elapsed()).isEqualTo(Duration.ofSeconds(2));
```

### Platforms

Code that receives a [`Platform`](core.md#platform) can be tested on any operating system, whatever the one the tests run
on, in two ways.

[`FakePlatform`](api:be.imgn.gaia.core.testing.FakePlatform) has the operating system of your choice and its
directories under a root directory, such as a test's temporary directory, so the real ones are never touched:

```java
Platform platform = FakePlatform.newBuilder(OperatingSystem.WINDOWS, temporaryDirectory).build();
```

Its directories are a [`FakeDirectories`](api:be.imgn.gaia.core.testing.FakeDirectories) unless others are
given; its builder sets each directory on its own.

[`PlatformPreset`](api:be.imgn.gaia.core.testing.PlatformPreset) instead runs the real detection of the
directories of an operating system, from the system properties and environment variables a JVM typically has there:

```java
PlatformPreset windows = PlatformPreset.newBuilder(OperatingSystem.WINDOWS).userName("alice").build();
Platform platform = Platform.newBuilder()
        .systemProperties(windows.systemProperties())
        .environment(windows.environment())
        .fileSystem(fileSystem)
        .detect();
```

Its builder changes the user name and home directory, sets the XDG variables, and adds or removes system properties
and environment variables.

### Assertions

[`RangeAssert`](api:be.imgn.gaia.core.testing.RangeAssert) has AssertJ assertions on a [`Range`](core.md#collections),
which report the values or ranges that fail:

```java
assertThat(Range.closedOpen(0, 10))
        .contains(0, 9)
        .doesNotContain(10)
        .encloses(Range.closed(2, 5))
        .isConnectedTo(Range.atLeast(10));
```

[`MultisetAssert`](api:be.imgn.gaia.core.testing.MultisetAssert) has AssertJ's assertions on collections, where the
size counts occurrences, and assertions on the count of each element:

```java
assertThat(words).hasCount("foo", 2).hasDistinctSize(3).containsExactlyCounts(Map.of("foo", 2, "bar", 1, "baz", 1));
```

[`MultimapAssert`](api:be.imgn.gaia.core.testing.MultimapAssert) checks the keys, entries and values of a
`Multimap`. `hasValues` compares the values of a key in order in a `ListMultimap`, and in any order in a
`SetMultimap`:

```java
assertThat(scores).hasSize(3).containsKeys("alice", "bob").hasValues("alice", 3, 5).containsEntry("bob", 4);
```

Statically import each `assertThat` next to AssertJ's: Java picks the most specific one for each argument.

## gaia-processing-testing

[`TestCompiler`](api:be.imgn.gaia.processing.testing.TestCompiler) compiles in-memory sources with javac, in process,
running the given annotation processors, and returns a
[`Compilation`](api:be.imgn.gaia.processing.testing.Compilation) with the diagnostics and the generated files:

```java
Compilation compilation = TestCompiler.javac()
        .withProcessors(new MapperProcessor())
        .withOptions("-Xlint:all,-processing", "-Werror")
        .compile(TestCompiler.source("example.Foo", """
                package example;

                @Mapper(target = Bar.class)
                interface Foo {}
                """));
```

`withResource` gives the compilation a file a processor reads with `Filer.getResource`, in the class output, where a
build tool copies the resources before compiling, or in the source path, which rules out compiling a `module-info`:

```java
Compilation compilation = TestCompiler.javac()
        .withProcessors(new ViewProcessor())
        .withResource(StandardLocation.CLASS_OUTPUT, "example/MainView.toml", """
                title = "Main"
                """)
        .compile(TestCompiler.source("example.MainView", "..."));
```

An overload takes the content as bytes, for a binary resource such as an image.

[`CompilationAssert`](api:be.imgn.gaia.processing.testing.CompilationAssert) asserts on it:

```java
assertThat(compilation).succeeded().hadNoWarnings();
assertThat(compilation).hadErrorContaining("must be a class").inFile("example/Foo.java").onLine(3);
```

[`ProcessorAssert`](api:be.imgn.gaia.processing.testing.ProcessorAssert) checks that javac can find a processor,
through `META-INF/services` or `module-info.java`: `assertThat(MapperProcessor.class).isRegistered()`.

[`CompilationExtension`](api:be.imgn.gaia.processing.testing.CompilationExtension) gives JUnit tests the `Elements`
and `Types` of a running compilation, to test code that works on `javax.lang.model` without writing a processor
around it:

```java
@ExtendWith(CompilationExtension.class)
class TypesTest {
    @Test
    void stringIsACharSequence(Elements elements, Types types) {
        TypeMirror string = elements.getTypeElement("java.lang.String").asType();
        TypeMirror charSequence = elements.getTypeElement("java.lang.CharSequence").asType();
        assertThat(types.isAssignable(string, charSequence)).isTrue();
    }
}
```

Each test gets its own compilation, unless the class is annotated with
[`@SharedCompilation`](api:be.imgn.gaia.processing.testing.SharedCompilation).
