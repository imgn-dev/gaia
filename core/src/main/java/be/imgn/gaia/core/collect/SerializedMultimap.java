package be.imgn.gaia.core.collect;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The serialized form of the multimaps of this package: each key with its values, in order. Deserializing adds them to
 * a new multimap, so that a tampered stream cannot produce one with a {@code null} key or value, or a key without
 * values.
 */
abstract class SerializedMultimap implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private transient Object[] keys;
    private transient Object[][] values;

    SerializedMultimap(Multimap<?, ?> multimap) {
        var map = multimap.asMap();
        keys = new Object[map.size()];
        values = new Object[map.size()][];
        var i = 0;
        for (var entry : map.entrySet()) {
            keys[i] = entry.getKey();
            values[i] = entry.getValue().toArray();
            i++;
        }
    }

    /**
     * Calls an action with each key read and its values.
     *
     * @param action the action, which adds the key and its values to a new multimap
     */
    final void forEachKey(BiConsumer<Object, List<Object>> action) {
        for (var i = 0; i < keys.length; i++) {
            action.accept(keys[i], Arrays.asList(values[i]));
        }
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeInt(keys.length);
        for (var i = 0; i < keys.length; i++) {
            out.writeObject(keys[i]);
            out.writeInt(values[i].length);
            for (Object value : values[i]) {
                out.writeObject(value);
            }
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        int length = in.readInt();
        if (length < 0) {
            throw new InvalidObjectException("negative length: " + length);
        }
        // Growing with what the stream actually holds, rather than allocating the lengths it claims, keeps a tampered
        // length from exhausting the heap: the stream runs out of keys or values first.
        var readKeys = new ArrayList<Object>();
        var readValues = new ArrayList<Object[]>();
        for (var i = 0; i < length; i++) {
            Object key = in.readObject();
            if (key == null) {
                throw new InvalidObjectException("null key");
            }
            int count = in.readInt();
            if (count <= 0) {
                throw new InvalidObjectException("number of values of " + key + " is not positive: " + count);
            }
            var keyValues = new ArrayList<Object>();
            for (var j = 0; j < count; j++) {
                Object value = in.readObject();
                if (value == null) {
                    throw new InvalidObjectException("null value of " + key);
                }
                keyValues.add(value);
            }
            readKeys.add(key);
            readValues.add(keyValues.toArray());
        }
        keys = readKeys.toArray();
        values = readValues.toArray(Object[][]::new);
    }
}
