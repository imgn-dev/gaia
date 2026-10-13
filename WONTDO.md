# Won't do

Features that were considered for gaia and rejected, with the reason and what to use instead.

## Atomic sinks

A `ByteSink` or `CharSink` that writes a file atomically, such as `CharSink.atomic(file, UTF_8)` built on
`AtomicFiles`.

A sink hands out its stream through `openStream()`, and the caller decides when to close it. Code that writes to
the stream inside a try-with-resources block closes it when it fails too, so a sink that commits the file on
`close()` would publish a partial file. The stream cannot tell a failure apart from the end of the content: the
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
