# Won't do

Features that were considered for gaia and rejected, with the reason and what to use instead.

## Atomic sinks

A `ByteSink` or `CharSink` that writes a file atomically, such as `CharSink.atomic(file, UTF_8)` built on
`AtomicFiles`.

A sink hands out its writer through `openWriter()`, and the caller decides when to close it. Code that writes to
the writer inside a try-with-resources block closes it when it fails too, so a sink that commits the file on
`close()` would publish a partial file. The writer cannot tell a failure apart from the end of the content: the
failure is usually in the caller's code between two writes, such as while formatting what to write.

Instead, use `AtomicFiles.write(file, out -> ...)`, which commits the file only if the callback returns normally.
A library that writes a format fits inside that callback when it can write to an `Appendable` or an
`OutputStream`:

```java
AtomicFiles.write(file, out -> {
    var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
    Toml.write(table, writer);
    writer.flush();
});
```

## Plugin system

A plugin manager that loads modules from a directory at runtime, each in a `ModuleLayer` of its own, with a
lifecycle, dependencies between plugins, enable/disable state, hot reload and extension points, plus a launcher
that runs the application in a layer of its own so plugins cannot reach it.

That is a framework, not a library: it owns how the application starts, which layers and class loaders its code
runs in, and when plugin code is called. gaia holds libraries that an application calls, so the plugin system
will be a separate project.

## Public segmented buffer

A public buffer type for `ByteReader` and `ByteWriter`, made of segments that move from one buffer to another
without copying, with a pool of segments to reuse.

It would about double the API of the readers and writers, and moving segments pays off mainly in proxies and
pipelines that pass bytes along without looking at them; a pool gains little on current JVMs, which allocate short-lived
arrays cheaply. Each reader and writer has a private buffer instead, which can become segmented later without changing
the API.

## Timeouts and non-blocking streams

Deadlines and timeouts on reads and writes, or readers and writers that don't block.

Socket timeouts (`Socket.setSoTimeout`), `Future.get` with a timeout, and virtual threads, which make blocking cheap,
already cover them.

## Compressing, hashing and encrypting streams

Readers and writers that compress, hash or encrypt what goes through them.

The JDK has them as streams: `GZIPInputStream`, `DeflaterOutputStream`, `DigestInputStream`, `CipherOutputStream`
and others. `ByteReader.asInputStream()` and `ByteWriter.asOutputStream()` plug them in.

## File system abstraction

An interface over files and directories, with an in-memory implementation for tests.

`java.nio.file` is one already, and `gaia-memfs` provides an in-memory `FileSystem` for it.
