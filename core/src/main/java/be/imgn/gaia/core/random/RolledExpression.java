package be.imgn.gaia.core.random;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * What a {@link DiceExpression} evaluated to: a tree of {@link RolledNode}s with the same shape as the expression, in
 * which each node has a value, each die a face, and each tag applies or not.
 *
 * <pre>{@code
 * var rolled = DiceExpression.parse("(4d6dl1:high>=15)+2").roll(random);
 * rolled.value();     // 16
 * rolled.tags();      // []
 * rolled.toString();  // "(4d6dl1[6, ~1~, 4, 4])+2 = 16"
 * }</pre>
 *
 * <p>It's inspected like the expression: from the top with {@link #node()}, or from the bottom with
 * {@link #fold(Function)}.
 *
 * @since 0.2.0
 * @see DiceExpression#roll(java.util.random.RandomGenerator)
 */
public final class RolledExpression {

    private final RolledNode<RolledExpression> node;

    RolledExpression(RolledNode<RolledExpression> node) {
        this.node = requireNonNull(node, "node");
    }

    /**
     * Returns the root of this rolled expression, whose children are rolled expressions.
     *
     * @return the root of this rolled expression, whose children are rolled expressions
     */
    public RolledNode<RolledExpression> node() {
        return node;
    }

    /**
     * Returns what the expression evaluated to.
     *
     * @return what the expression evaluated to
     */
    public int value() {
        return node.value();
    }

    /**
     * Returns the names of the tags that apply anywhere in this rolled expression, in the order they are written.
     *
     * @return the names of the tags that apply anywhere in this rolled expression, in the order they are written
     */
    public Set<String> tags() {
        Set<String> tags = this.<Set<String>>fold(node -> {
            var names = new LinkedHashSet<String>();
            node.children().forEach(names::addAll);
            if (node instanceof RolledNode.Group<Set<String>> group) {
                names.addAll(group.appliedTags());
            }
            return names;
        });
        return Collections.unmodifiableSet(tags);
    }

    /**
     * Reduces this rolled expression to one value, from the leaves to the root: the function is applied to each node,
     * whose children are what the function returned for them.
     *
     * @param function the function to apply to each node
     * @param <R> the type of the result
     * @return what the function returned for the root
     */
    public <R> R fold(Function<? super RolledNode<R>, ? extends R> function) {
        requireNonNull(function, "function");
        return function.apply(node.<R>map(child -> child.fold(function)));
    }

    @Override
    public boolean equals(@Nullable Object object) {
        return object instanceof RolledExpression other && node.equals(other.node);
    }

    @Override
    public int hashCode() {
        return node.hashCode();
    }

    /**
     * Returns the expression with its values, then {@code " = "} and the result. Each die is written in brackets after
     * its dice, between tildes if it doesn't count; each variable is followed by its value in brackets; and only the
     * tags that apply are written.
     *
     * @return the expression with its values, then {@code " = "} and the result
     */
    @Override
    public String toString() {
        return this.<Rendering.Rendered>fold(Rendering::render).text() + " = " + value();
    }
}
