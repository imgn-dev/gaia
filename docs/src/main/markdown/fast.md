# Fast collections

`gaia-fast` holds collections of primitives that store them as they are, without boxing: no `Integer` is created to
put, get or walk their entries.

```xml
<dependency>
  <groupId>be.imgn.gaia</groupId>
  <artifactId>gaia-fast</artifactId>
</dependency>
```

## `IntIntMap`

[`IntIntMap`](api:be.imgn.gaia.fast.collect.IntIntMap) maps `int` keys to `int` values. Every key is allowed, `0`
included.

```java
var counts = new IntIntMap();
counts.put(7, 1);
counts.put(7, counts.get(7) + 1);
int count = counts.get(7);   // 2
int none = counts.get(8);    // 0, the missing value
```

Where a `Map` returns `null`, an `IntIntMap` returns its *missing value*: `get` for an absent key, and `put` and
`remove` when the key had no value before. It is `0` unless the map is created with another one, such as `-1` when
`0` is a meaningful value. `containsKey` tells a stored value apart from the missing one.

```java
var positions = new IntIntMap(1_000, -1);   // room for 1,000 entries, -1 for absent keys
```

### Walking the entries

An iterator would box each entry, so the map has a [`Cursor`](api:be.imgn.gaia.fast.collect.IntIntMap.Cursor)
instead. It starts before the first entry, and `next()` moves it to the next one until it returns `false`. The cursor
can change the value of its entry, and remove it without skipping any other:

```java
for (var cursor = map.cursor(); cursor.next(); ) {
    if (cursor.value() == 0) {
        cursor.remove();
    } else {
        cursor.setValue(cursor.value() * 2);
    }
}
```

`forEach` and `removeIf` do the same with an
[`IntIntConsumer`](api:be.imgn.gaia.fast.collect.IntIntConsumer) and an
[`IntIntPredicate`](api:be.imgn.gaia.fast.collect.IntIntPredicate). The order of the entries is unspecified. Changing
the map while walking it, other than through the cursor, has unspecified results: there is no check for it.

### How it is stored

Each entry is kept in one `long` of an array, its key and its value together, so a lookup reads both at once. A
multiplicative hash of the key picks its slot, and a key that finds it taken goes to the next free one. Removing an
entry moves back the entries after it rather than leaving a marker, so lookups don't slow down as entries come and go.

The map doubles its capacity when its entries would fill more than its load factor of the slots: `0.5` by default,
which keeps lookups short at the cost of memory. A higher load factor, up to just below `1`, saves memory and makes
lookups longer:

```java
var compact = new IntIntMap(1_000, 0, 0.75f);
```

The map never shrinks; `clear()` keeps its capacity. It is not thread-safe.
