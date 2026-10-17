# gaia

A family of Java libraries: utilities the JDK doesn't provide, a cross-platform API, and helpers for writing
annotation processors. Each library has a matching testing library for testing the code that uses it.

The libraries require Java 21 or later and are released together under one version.

API documentation: [latest](https://imgn-dev.github.io/gaia/api/latest/), or
[every released version](https://imgn-dev.github.io/gaia/api/).

## Libraries

| Artifact                  | Module                            | What it does                                                |
|---------------------------|-----------------------------------|-------------------------------------------------------------|
| `gaia-core`               | `be.imgn.gaia.core`               | Extensions to the JDK                                       |
| `gaia-core-testing`       | `be.imgn.gaia.core.testing`       | Test helpers for code using `gaia-core`                     |
| `gaia-platform`           | `be.imgn.gaia.platform`           | A cross-platform API over the host the JVM runs on          |
| `gaia-platform-testing`   | `be.imgn.gaia.platform.testing`   | Test helpers for code using `gaia-platform`                 |
| `gaia-processing`         | `be.imgn.gaia.processing`         | Helpers for writing annotation processors                   |
| `gaia-processing-testing` | `be.imgn.gaia.processing.testing` | Compile sources through annotation processors in tests      |

### `gaia-core`

- **`base.Preconditions`**: `checkArgument` and `checkState` throw `IllegalArgumentException` or
  `IllegalStateException` when a check fails, with an optional message or `String.format` message format:
  `checkArgument(count >= 0, "count must not be negative: %d", count)`.
- **`base.Throwables`**: `illegalArgument` and `illegalState` create an `IllegalArgumentException` or
  `IllegalStateException` from the exception that caused it, with a message or a `String.format` message format:
  `throw Throwables.illegalArgument(e, "Not a port: %s", text)`.
- **`collect.Sets`**: unmodifiable sets that keep the order their elements are given in. `set(...)` and
  `setCopy(...)` behave like `Set.of` and `Set.copyOf`, and the `toSet()` collector like
  `Collectors.toUnmodifiableSet()`, except for iteration order.
- **`collect.Multiset`**: a collection that counts the occurrences of each element, with `count`, `add(element, n)`,
  `remove(element, n)`, `setCount` and live `elementSet()` and `entrySet()` views. `HashMultiset.create()` is backed by
  a hash map. `TreeMultiset.create()` keeps its elements sorted, as a `SortedMultiset` with first, last and nearest
  entries, live range views and a `reversed()` view. `Multisets.multiset(...)`, `multisetCopy(...)` and the
  `toMultiset()` collector make unmodifiable multisets that iterate in the order their elements first appear.
- **`collect.MoreCollectors`**: `onlyElement()` collects the only element of a stream, and `toOptional()` collects
  zero or one element.
- **`collect.MoreGatherers`** (Java 24+): `instanceOf(type)` keeps the elements of a type, cast to it:
  `stream.gather(MoreGatherers.instanceOf(Circle.class))`.
- **`io.AtomicFiles`**: writes a file so that readers, or the next run after a crash, see either its old content or
  its new one, never a part of it: `write(file, bytes)`, `writeString(file, text)`, or
  `write(file, out -> ImageIO.write(image, "png", out))`. The file keeps its permissions.
- **`io.ByteSource`, `io.CharSource`, `io.ByteSink` and `io.CharSink`**: files, URLs, arrays or strings to read
  or write as a whole, each read or write opening and closing its own stream:
  `CharSource.of(file, UTF_8).readLines()`, `ByteSource.of(uri).slice(0, 4).read()`,
  `CharSink.of(file, UTF_8).writeLines(lines)`. Each is a functional interface, so a lambda that opens a stream
  makes one.
- **`system.SystemProperties` and `system.Environment`**: system properties and environment variables, read from
  the running JVM or from fixed values in tests. `SystemProperties` has a typed method for each standard Java
  property, such as `userHome()` or `javaVersion()`.
- **`text.Splitter`**: splits strings on a character, a literal string or a regular expression, with optional
  trimming, omission of empty parts and a limit.
- **`time.Stopwatch`**: measures elapsed time on a monotonic `Ticker`, with start, stop and reset. Its
  `toString()` reads like `1.234 s`.

### `gaia-core-testing`

- **`FakeTicker`**: a `Ticker` that tests advance by hand, for exact and instant tests of timing code.

### `gaia-platform`

- **`Platform`**: the operating system (`isLinux()`, `isMacOS()`, `isWindows()`), the file system, and the
  directories of the platform the JVM runs on. `Platform.current()` detects it. `Platform.newBuilder()` builds one
  from given system properties, environment variables and file system, for tests: `gaia-platform-testing` has
  typical ones for each operating system, and fakes of `Platform` and `Directories`.
- **`Directories`**: where applications store their data, configuration, state and cache files, following the XDG
  Base Directory Specification on Linux and other Unix-like systems, and the standard locations on macOS and
  Windows.
- **`ApplicationDirectories`**: one application's own configuration, data, state, cache and runtime directories,
  kept apart on every operating system: `platform.applicationDirectories("Quuz")`, or with the vendor
  (`"bar", "Quuz"`), which Windows nests the application under, and a qualifier (`"foo", "bar", "Quuz"`), which
  names the macOS directories `foo.bar.Quuz`.

### `gaia-processing`

- **`StepProcessor`** and **`Step`**: a processor is a list of steps, each processing the elements annotated with
  some annotations, or with any annotation that carries one of its `metaAnnotations()`. Rounds are handled for you:
  an element whose types another processor has yet to generate is held back until they exist, and reported as an
  error if they never do. A step can also hand elements back for the next round, and finish in `processingOver()`
  once all rounds are done. With `-Agaia.debug`, every step processor notes what each step did in each round, and
  how long it took, naming each step by its `name()`.
- **`AnnotatedElements`**: the elements a step gets in a round, by annotation and by meta-annotation.
- **`ProcessingContext`**: everything a step works with: elements, types, the filer, options, and the helpers below.
- **`AnnotationReader`**: reads annotation values with their types, defaults included. `Class` values come back as
  types, without `MirroredTypeException`. The context finds them with repeated annotations unwrapped from their
  container, by meta-annotation (such as qualifiers), or inherited through `@Inherited`.
- **`Diagnostics`**: reports errors, warnings and notes on an element, one of its annotations, or one attribute of
  it, and counts the errors. A step can also throw a `ProcessingException` there, reported as an error before
  processing goes on with the next element (`context.forEach(...)`) or step.
- **`source` package**: a code generator. Model the code with `Type` (classes, interfaces, enums, records,
  annotation types, sealed hierarchies, anonymous classes), `Method`, `Field`, `Parameter`,
  `Annotation` and `CodeBlock`, with type names as `TypeName`s, type annotations such as `@Nullable` included.
  `JavaFile` writes it out with the imports worked out, qualifying names only where they would clash, including with
  the member types a supertype brings. It uses static imports, wraps long lines at `$W`, and indents multi-line
  statements. Outside a processor, `writeTo(directory)` writes it under a source root. `Method.overriding(...)`
  starts an override from an element. `NameAllocator` hands out identifiers that don't clash. `TypeName.of(...)`
  names a `Class`, a `TypeMirror` or a generic `java.lang.reflect.Type`.
- **`GeneratedSources`**: writes generated source files, as a `JavaFile` or as text. It adds `@Generated` only where
  the generated code can see it, so a module that doesn't read `java.compiler` still compiles. It qualifies the
  `java.lang` types that a type of the same package hides. Writing the same file twice returns `false` instead of
  failing. `ClassName.generatedNameWithSuffix("Mapper")` names a generated type after its source type, as
  `Outer_Inner_Mapper` (`generatedNameWithPrefix("Generated")` gives `Generated_Outer_Inner`), and
  `isAvailable(name)` tells whether a type has that name already. Files are written in ASCII, non-ASCII characters
  as Unicode escapes, so they compile the same under any `-encoding`.
- **`ServiceFiles`**: `context.services().add(service, implementation)` collects `META-INF/services` entries over
  all rounds; they are checked, merged with the file already in the class output (minus classes that no longer
  exist), and written after the last round. `skipModuleCheck(service)` silences the warning about a missing
  `provides` directive, for processors that add it after compilation; `discard(service)` withdraws a service's
  entries so that its file isn't written.
- **`ProcessorOption`**: typed processor options (`-Aname=value`) with defaults: text, boolean, integer and enum.
  Declare them in `StepProcessor.options()` and read them with `context.option(...)`. An invalid value is reported
  as an error.
- **`MoreTypes`**, **`MoreElements`** and **`TypeKey`**: check a type against a class or a name, navigate to an
  element's package or enclosing type, cast an element with a clear error, tell inner classes from static nested
  ones, tell whether generated code can access an element (its `Visibility`, protected access from a subclass,
  module exports), and use types as keys in sets and maps. `context.type(List.class, String.class)` builds
  `List<String>`, `context.isSameType(...)` compares types ignoring type annotations such as `@Nullable`, and
  `context.isFromSource(element)` tells code compiled from this compilation's sources, generated ones included, from
  code compiled before.
  `context.isSubtype(type, List.class)` checks subtypes, ignoring type arguments, and
  `context.asSupertype(type, List.class)` gives the supertype with the type arguments the hierarchy gives it, such as
  `List<String>`. `context.methods(type)` lists a type's methods as the compiler sees them, inherited ones included,
  and `context.abstractMethods(type)` the ones an implementation still has to write.
  `MoreElements.firstUnresolvedType(element)` names a type javac could not resolve in an element's signature.
  `MoreTypes.isCastFromObjectUnchecked(type)` tells whether generated code casting to a type needs a reason it is safe.
  `context.superclass(type)` gives the direct superclass with its type arguments, leaving out `Object`.
  `context.asMemberOf(type, variable)` gives a field or a parameter the type it has as a member of a type.

```java
Type point = Type.newRecordBuilder("Point")
        .addModifiers(Modifier.PUBLIC)
        .addRecordComponent(int.class, "x")
        .addRecordComponent(int.class, "y")
        .addMethod(Method.newCompactConstructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .beginControlFlow("if (x < 0 || y < 0)")
                .addStatement("throw new $T($S)", IllegalArgumentException.class, "negative")
                .endControlFlow()
                .build())
        .build();
context.sources().write(JavaFile.newBuilder("com.example", point).build(), originatingElement);
```

```java
public final class MapperProcessor extends StepProcessor {
    @Override
    protected List<Step> steps(ProcessingContext context) {
        return List.of(new MapperStep(context));
    }
}

final class MapperStep implements Step {
    private final ProcessingContext context;

    MapperStep(ProcessingContext context) {
        this.context = context;
    }

    @Override
    public Set<String> annotations() {
        return Set.of("com.example.Mapper");
    }

    @Override
    public Set<? extends Element> process(AnnotatedElements elements) {
        for (Element mapper : elements.annotatedWith("com.example.Mapper")) {
            AnnotationReader annotation = context.annotation(mapper, "com.example.Mapper").orElseThrow();
            TypeMirror target = annotation.type("target");
            if (target.getKind() != TypeKind.DECLARED) {
                context.diagnostics().on(mapper).annotation("com.example.Mapper").attribute("target")
                        .error("must be a class or an interface");
            }
            // generate the mapper
        }
        return Set.of();
    }
}
```

### `gaia-processing-testing`

- **`TestCompiler`**: compiles in-memory sources with javac, running the given annotation processors, and returns
  a `Compilation` with the diagnostics and the generated files.
- **`CompilationAssert`**: AssertJ assertions on a `Compilation`, such as
  `assertThat(compilation).succeeded().hadNoWarnings()` or
  `assertThat(compilation).hadErrorContaining("...").inFile("example/Foo.java").onLine(4)`.
- **`ProcessorAssert`**: `assertThat(MyProcessor.class).isRegistered()` checks that javac can find the processor,
  through `META-INF/services` or `module-info.java`.
- **`CompilationExtension`**: a JUnit extension that gives tests the `Elements` and `Types` of a running
  compilation. Add `@SharedCompilation` to the test class to share one compilation between its tests.

## Using gaia

Import the BOM, then declare the libraries you use without versions:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>be.imgn.gaia</groupId>
      <artifactId>gaia-bom</artifactId>
      <version>${gaia.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>be.imgn.gaia</groupId>
    <artifactId>gaia-platform</artifactId>
  </dependency>
  <dependency>
    <groupId>be.imgn.gaia</groupId>
    <artifactId>gaia-platform-testing</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

The BOM manages gaia's own artifacts only, so importing it doesn't change the versions of your other
dependencies.

On the module path, require the modules you use, for example `requires be.imgn.gaia.platform;`.

`MoreGatherers` exists only on Java 24 and later: `gaia-core` is a multi-release jar that adds it there. Tools that
validate multi-release jars strictly, such as `jar --validate`, report it as a public class missing from the
Java 21 version. That doesn't affect compiling or running.

### Annotation processors

`gaia-processing` is for writing annotation processors. A processor depends on it like on any library:

```xml
<dependency>
  <groupId>be.imgn.gaia</groupId>
  <artifactId>gaia-processing</artifactId>
</dependency>
```

and registers itself, so that javac finds it: list the processor class in
`META-INF/services/javax.annotation.processing.Processor`, and on the module path also declare
`provides javax.annotation.processing.Processor with com.example.MapperProcessor;` in `module-info.java`.

The applications that use the processor put it on their annotation processor path, not their class path, so
`gaia-processing` runs inside javac and never ships with them. With Maven:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <annotationProcessorPaths>
      <path>
        <groupId>com.example</groupId>
        <artifactId>example-processor</artifactId>
        <version>1.0.0</version>
      </path>
    </annotationProcessorPaths>
  </configuration>
</plugin>
```

Since `maven-compiler-plugin` 3.12.0, the version can come from `dependencyManagement`, such as the processor's
BOM, instead of being repeated: add `<annotationProcessorPathsUseDepMgmt>true</annotationProcessorPathsUseDepMgmt>`
to the configuration and leave out `<version>`.

With Gradle:

```kotlin
dependencies {
    annotationProcessor("com.example:example-processor:1.0.0")
}
```

Declare the processor path explicitly, as above, rather than relying on processors found on the class path: since
JDK 23, javac no longer runs those unless asked to with `-proc:full`.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
