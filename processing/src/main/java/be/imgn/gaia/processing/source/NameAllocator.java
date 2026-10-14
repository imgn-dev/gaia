package be.imgn.gaia.processing.source;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.SourceVersion;

/**
 * Hands out Java identifiers that do not clash: each new name is the suggestion, made a valid identifier, with as many
 * underscores appended as needed to differ from the keywords and the names already handed out.
 *
 * <pre>{@code
 * NameAllocator names = new NameAllocator();
 * for (Property property : properties) {
 *     names.newName(property.name(), property);
 * }
 * // Later, the same name for the same property.
 * builder.addStatement("this.$N = $N", names.get(property), names.get(property));
 * }</pre>
 *
 * <p>An allocator suits one scope; {@link #copy()} gives a nested scope that sees the names of the enclosing one.
 */
public final class NameAllocator {

    private final Set<String> allocated;
    private final Map<Object, String> byTag;

    /** Creates an allocator that has handed out no name yet. */
    public NameAllocator() {
        this(Set.of(), Map.of());
    }

    private NameAllocator(Set<String> allocated, Map<Object, String> byTag) {
        this.allocated = new HashSet<>(allocated);
        this.byTag = new HashMap<>(byTag);
    }

    /**
     * Returns a valid Java identifier close to a suggestion: each character that cannot be in an identifier is replaced
     * by an underscore, and an underscore goes first if the first character cannot start one. The result may still be a
     * keyword.
     *
     * @param suggestion the suggestion, such as {@code "first-name"}
     * @return the identifier, such as {@code first_name}
     */
    public static String toJavaIdentifier(String suggestion) {
        Objects.requireNonNull(suggestion, "suggestion");
        StringBuilder identifier = new StringBuilder();
        suggestion.codePoints().forEach(codePoint -> {
            if (identifier.isEmpty()
                    && !Character.isJavaIdentifierStart(codePoint)
                    && Character.isJavaIdentifierPart(codePoint)) {
                identifier.append('_');
            }
            if (Character.isJavaIdentifierPart(codePoint)) {
                identifier.appendCodePoint(codePoint);
            } else {
                identifier.append('_');
            }
        });
        return identifier.isEmpty() ? "_" : identifier.toString();
    }

    /**
     * Returns a new name, not attached to any tag.
     *
     * @param suggestion the name to start from
     * @return a valid identifier, different from the keywords and every name handed out so far
     */
    public String newName(String suggestion) {
        Objects.requireNonNull(suggestion, "suggestion");
        String name = toJavaIdentifier(suggestion);
        while (SourceVersion.isKeyword(name) || allocated.contains(name)) {
            name += "_";
        }
        allocated.add(name);
        return name;
    }

    /**
     * Returns a new name, attached to a tag to {@link #get(Object) get} it again later.
     *
     * @param suggestion the name to start from
     * @param tag what the name is for, such as the element or property it names
     * @return a valid identifier, different from the keywords and every name handed out so far
     * @throws IllegalArgumentException if the tag already has a name
     */
    public String newName(String suggestion, Object tag) {
        Objects.requireNonNull(suggestion, "suggestion");
        Objects.requireNonNull(tag, "tag");
        String existing = byTag.get(tag);
        if (existing != null) {
            throw new IllegalArgumentException(
                    "The tag " + tag + " already names " + existing + ", it cannot name " + suggestion + " too");
        }
        String name = newName(suggestion);
        byTag.put(tag, name);
        return name;
    }

    /**
     * Returns the name handed out for a tag.
     *
     * @param tag the tag given to {@link #newName(String, Object)}
     * @return the name
     * @throws IllegalArgumentException if no name was handed out for the tag
     */
    public String get(Object tag) {
        Objects.requireNonNull(tag, "tag");
        String name = byTag.get(tag);
        if (name == null) {
            throw new IllegalArgumentException("No name for the tag " + tag);
        }
        return name;
    }

    /**
     * Returns an allocator for a nested scope: it knows the names and tags of this one, and the names it hands out are
     * not known to this one.
     *
     * @return a new allocator
     */
    public NameAllocator copy() {
        return new NameAllocator(allocated, byTag);
    }
}
