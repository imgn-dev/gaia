package be.imgn.gaia.core.random.dice;

import be.imgn.gaia.core.random.dice.DiceNode.Relation;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/** The faces of one die, once its number of sides is known: numbered from one, or listed. */
final class DieFaces {

    private final int size;

    // Null when the faces are numbered from one.
    private final int @Nullable [] listed;
    private final int lowest;
    private final int highest;

    private DieFaces(int size, int @Nullable [] listed, int lowest, int highest) {
        this.size = size;
        this.listed = listed;
        this.lowest = lowest;
        this.highest = highest;
    }

    static DieFaces numbered(int sides) {
        return new DieFaces(sides, null, 1, sides);
    }

    static DieFaces listed(List<Integer> faces) {
        var listed = faces.stream().mapToInt(Integer::intValue).toArray();
        int lowest = listed[0];
        int highest = listed[0];
        for (int face : listed) {
            lowest = Math.min(lowest, face);
            highest = Math.max(highest, face);
        }
        return new DieFaces(listed.length, listed, lowest, highest);
    }

    /** How many faces the die has, counting each as many times as it's listed. */
    int size() {
        return size;
    }

    /** The face at an index, from zero. */
    int face(int index) {
        return listed == null ? index + 1 : listed[index];
    }

    int lowest() {
        return lowest;
    }

    int highest() {
        return highest;
    }

    /** How many faces satisfy a condition. */
    int matching(Relation relation, int value) {
        if (listed != null) {
            int matching = 0;
            for (int face : listed) {
                matching += relation.test(face, value) ? 1 : 0;
            }
            return matching;
        }
        long below = Math.clamp((long) value - 1, 0, size);
        long upTo = Math.clamp((long) value, 0, size);
        return (int)
                switch (relation) {
                    case EQUAL -> upTo - below;
                    case NOT_EQUAL -> size - (upTo - below);
                    case LESS -> below;
                    case LESS_OR_EQUAL -> upTo;
                    case GREATER -> size - upTo;
                    case GREATER_OR_EQUAL -> size - below;
                };
    }

    /** Each different face with how many times the die has it, from the lowest face. */
    SortedMap<Integer, Integer> counts() {
        var counts = new TreeMap<Integer, Integer>();
        for (int index = 0; index < size; index++) {
            counts.merge(face(index), 1, Integer::sum);
        }
        return counts;
    }
}
