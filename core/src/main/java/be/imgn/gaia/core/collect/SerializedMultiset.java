package be.imgn.gaia.core.collect;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.function.ObjIntConsumer;
import java.util.stream.IntStream;

/**
 * The serialized form of the multisets of this package: each distinct element with its count. Deserializing adds them
 * to a new multiset, so that a tampered stream cannot produce one with a {@code null} element or a count that is not
 * positive.
 */
abstract class SerializedMultiset implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private transient Object[] elements;
    private transient int[] counts;

    SerializedMultiset(Multiset<?> multiset) {
        var entries = multiset.entrySet();
        elements = new Object[entries.size()];
        counts = new int[entries.size()];
        var i = 0;
        for (var entry : entries) {
            elements[i] = entry.element();
            counts[i] = entry.count();
            i++;
        }
    }

    /**
     * Calls an action with each element read and its count.
     *
     * @param action the action, which adds the element to a new multiset
     * @throws InvalidObjectException if the action rejects an element, as when it occurs more than
     *     {@link Integer#MAX_VALUE} times
     */
    final void forEachEntry(ObjIntConsumer<Object> action) throws InvalidObjectException {
        for (var i = 0; i < elements.length; i++) {
            try {
                action.accept(elements[i], counts[i]);
            } catch (IllegalArgumentException e) {
                var invalid = new InvalidObjectException(e.getMessage());
                invalid.initCause(e);
                throw invalid;
            }
        }
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeInt(elements.length);
        for (var i = 0; i < elements.length; i++) {
            out.writeObject(elements[i]);
            out.writeInt(counts[i]);
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        int length = in.readInt();
        if (length < 0) {
            throw new InvalidObjectException("negative length: " + length);
        }
        // Growing with what the stream actually holds, rather than allocating the length it claims, keeps a tampered
        // length from exhausting the heap: the stream runs out of elements first.
        var readElements = new ArrayList<Object>();
        var readCounts = IntStream.builder();
        for (var i = 0; i < length; i++) {
            Object element = in.readObject();
            if (element == null) {
                throw new InvalidObjectException("null element");
            }
            int count = in.readInt();
            if (count <= 0) {
                throw new InvalidObjectException("count of " + element + " is not positive: " + count);
            }
            readElements.add(element);
            readCounts.add(count);
        }
        elements = readElements.toArray();
        counts = readCounts.build().toArray();
    }
}
