# gaia

A family of Java libraries: utilities the JDK doesn't provide, a cross-platform API, and helpers for writing
annotation processors. `gaia-core` and `gaia-processing` each have a matching testing library, for testing the code
that uses them.

The libraries require Java 25 or later and are released together under one version.

## Documentation

- [Guide](https://imgn-dev.github.io/gaia/): what each library does and how to set it up and use it, with examples.
- [API documentation](https://imgn-dev.github.io/gaia/api/latest/): the Javadoc of the latest release, or of
  [every released version](https://imgn-dev.github.io/gaia/api/).

## Benchmarks

JMH benchmarks of `gaia-core`'s I/O against the JDK's: reading files whole and by lines, and copying them; encoding
and decoding UTF-8, for ASCII and for mixed text, of 1 KB, 64 KB and 8 MB; and reading and writing 64 KB of binary
data an `int` or a byte at a time:

```sh
./mvnw -pl benchmarks -am package exec:exec -DskipTests -Djmh.args="Utf8Benchmark -prof gc"
```

`CharSource.read()` on bytes decodes them at once up to a size, `WHOLE_LIMIT` in `DecodedCharSource`, and as a stream
past it, which is faster once the bytes outgrow the processor's caches. `TextReadBenchmark` measures both from 1 to
16 MiB, for ASCII and for mixed text, to check the limit on another processor: it fits where `decodedAtOnce` stops
being faster than `decodedAsAStream`.

```sh
./mvnw -pl benchmarks -am package exec:exec -DskipTests -Djmh.args="TextReadBenchmark"
```

The same module measures the `gaia-fast` collections: `IntIntMapBenchmark` runs the operations of `IntIntMap`, and
`HashMapBenchmark` the same ones on a `HashMap<Integer, Integer>`:

```sh
./mvnw -pl benchmarks -am package exec:exec -DskipTests -Djmh.args="IntIntMapBenchmark|HashMapBenchmark"
```

`RandomSelectorBenchmark` measures uniform and weighted selections of `RandomSelector`, among 4, 1,000 and 1,000,000
elements:

```sh
./mvnw -pl benchmarks -am package exec:exec -DskipTests -Djmh.args="RandomSelectorBenchmark"
```

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
