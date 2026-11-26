package be.imgn.gaia.core.random.dice;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.random.dice.DiceNode.Condition;
import be.imgn.gaia.core.random.dice.DiceNode.Explode;
import be.imgn.gaia.core.random.dice.DiceNode.Keep;
import be.imgn.gaia.core.random.dice.DiceNode.Operator;
import be.imgn.gaia.core.random.dice.DiceNode.Relation;
import be.imgn.gaia.core.random.dice.DiceNode.Reroll;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * One node of a {@link RolledExpression}, whose children are of type {@code T}: a {@link DiceNode} together with what
 * it evaluated to.
 *
 * <p>In {@link RolledExpression#node()}, the children are rolled expressions themselves. In the function passed to
 * {@link RolledExpression#fold(Function)}, they are what that function returned for them.
 *
 * @param <T> the type of the children
 * @since 0.2.0
 */
public sealed interface RolledNode<T> {

    /**
     * Returns what this node evaluated to.
     *
     * @return what this node evaluated to
     */
    int value();

    /**
     * Returns the children of this node, in the order they are written: none for a constant.
     *
     * @return the children of this node, in the order they are written: none for a constant
     */
    List<T> children();

    /**
     * Returns this node with each child replaced by what a function returns for it. The function is called on the
     * children in the order they are written.
     *
     * @param mapper the function to apply to each child
     * @param <U> the type of the new children
     * @return this node with each child replaced by what a function returns for it
     */
    <U> RolledNode<U> map(Function<? super T, ? extends U> mapper);

    /**
     * A whole number.
     *
     * @param value the number
     * @param <T> the type of the children
     */
    record Constant<T>(int value) implements RolledNode<T> {

        @Override
        public List<T> children() {
            return List.of();
        }

        @Override
        public <U> Constant<U> map(Function<? super T, ? extends U> mapper) {
            requireNonNull(mapper, "mapper");
            return new Constant<>(value);
        }
    }

    /**
     * A variable and the value it was given.
     *
     * @param name the name of the variable
     * @param bound what the variable stands for
     * @param value what the variable evaluated to
     * @param <T> the type of the children
     */
    record Bound<T>(String name, T bound, int value) implements RolledNode<T> {

        /**
         * Creates a rolled variable.
         *
         * @param name the name of the variable
         * @param bound what the variable stands for
         * @param value what the variable evaluated to
         */
        public Bound {
            requireNonNull(name, "name");
            requireNonNull(bound, "bound");
        }

        @Override
        public List<T> children() {
            return List.of(bound);
        }

        @Override
        public <U> Bound<U> map(Function<? super T, ? extends U> mapper) {
            return new Bound<>(name, mapper.apply(bound), value);
        }
    }

    /**
     * Dice that were rolled.
     *
     * @param count how many dice were rolled at first
     * @param sides how many sides each die has
     * @param reroll which dice were rolled again
     * @param explode which dice added another die
     * @param keep which dice count in the sum
     * @param dice every die that was rolled, in order: a die that was rerolled is followed by the one that replaces it,
     *     and a die that exploded by the one it adds
     * @param value the sum of the dice that were kept
     * @param <T> the type of the children
     */
    record Dice<T>(T count, T sides, Reroll reroll, Explode explode, Keep keep, List<Die> dice, int value)
            implements RolledNode<T> {

        /**
         * Creates rolled dice.
         *
         * @param count how many dice were rolled at first
         * @param sides how many sides each die has
         * @param reroll which dice were rolled again
         * @param explode which dice added another die
         * @param keep which dice count in the sum
         * @param dice every die that was rolled, in order
         * @param value the sum of the dice that were kept
         */
        public Dice {
            requireNonNull(count, "count");
            requireNonNull(sides, "sides");
            requireNonNull(reroll, "reroll");
            requireNonNull(explode, "explode");
            requireNonNull(keep, "keep");
            dice = List.copyOf(dice);
        }

        @Override
        public List<T> children() {
            return List.of(count, sides);
        }

        @Override
        public <U> Dice<U> map(Function<? super T, ? extends U> mapper) {
            U mappedCount = mapper.apply(count);
            return new Dice<>(mappedCount, mapper.apply(sides), reroll, explode, keep, dice, value);
        }
    }

    /**
     * The opposite of a value.
     *
     * @param operand the value that was negated
     * @param value the result
     * @param <T> the type of the children
     */
    record Negate<T>(T operand, int value) implements RolledNode<T> {

        /**
         * Creates a rolled negation.
         *
         * @param operand the value that was negated
         * @param value the result
         */
        public Negate {
            requireNonNull(operand, "operand");
        }

        @Override
        public List<T> children() {
            return List.of(operand);
        }

        @Override
        public <U> Negate<U> map(Function<? super T, ? extends U> mapper) {
            return new Negate<>(mapper.apply(operand), value);
        }
    }

    /**
     * An arithmetic operation on two values.
     *
     * @param operator the operation
     * @param left the left operand
     * @param right the right operand
     * @param value the result
     * @param <T> the type of the children
     */
    record Arithmetic<T>(Operator operator, T left, T right, int value) implements RolledNode<T> {

        /**
         * Creates a rolled arithmetic operation.
         *
         * @param operator the operation
         * @param left the left operand
         * @param right the right operand
         * @param value the result
         */
        public Arithmetic {
            requireNonNull(operator, "operator");
            requireNonNull(left, "left");
            requireNonNull(right, "right");
        }

        @Override
        public List<T> children() {
            return List.of(left, right);
        }

        @Override
        public <U> Arithmetic<U> map(Function<? super T, ? extends U> mapper) {
            U mappedLeft = mapper.apply(left);
            return new Arithmetic<>(operator, mappedLeft, mapper.apply(right), value);
        }
    }

    /**
     * A comparison of two values.
     *
     * @param relation how the operands were compared
     * @param left the left operand
     * @param right the right operand
     * @param value 1 if the comparison holds, 0 if not
     * @param <T> the type of the children
     */
    record Comparison<T>(Relation relation, T left, T right, int value) implements RolledNode<T> {

        /**
         * Creates a rolled comparison.
         *
         * @param relation how the operands were compared
         * @param left the left operand
         * @param right the right operand
         * @param value 1 if the comparison holds, 0 if not
         */
        public Comparison {
            requireNonNull(relation, "relation");
            requireNonNull(left, "left");
            requireNonNull(right, "right");
        }

        /**
         * Returns whether the comparison holds.
         *
         * @return whether the comparison holds
         */
        public boolean holds() {
            return value != 0;
        }

        @Override
        public List<T> children() {
            return List.of(left, right);
        }

        @Override
        public <U> Comparison<U> map(Function<? super T, ? extends U> mapper) {
            U mappedLeft = mapper.apply(left);
            return new Comparison<>(relation, mappedLeft, mapper.apply(right), value);
        }
    }

    /**
     * A value between parentheses, with its tags.
     *
     * @param inner the value
     * @param tags the tags, in the order they are written, whether they apply or not
     * @param value the value of {@code inner}
     * @param <T> the type of the children
     */
    record Group<T>(T inner, List<Tag<T>> tags, int value) implements RolledNode<T> {

        /**
         * Creates a rolled group.
         *
         * @param inner the value
         * @param tags the tags, in the order they are written, whether they apply or not
         * @param value the value of {@code inner}
         */
        public Group {
            requireNonNull(inner, "inner");
            tags = List.copyOf(tags);
        }

        /**
         * Returns the names of the tags that apply, in the order they are written.
         *
         * @return the names of the tags that apply, in the order they are written
         */
        public List<String> appliedTags() {
            return tags.stream().filter(Tag::applied).map(Tag::name).toList();
        }

        /**
         * Returns the inner value, then the operand of each condition of each tag.
         *
         * @return the inner value, then the operand of each condition of each tag
         */
        @Override
        public List<T> children() {
            var children = new ArrayList<T>();
            children.add(inner);
            for (var tag : tags) {
                for (var condition : tag.conditions()) {
                    children.add(condition.operand());
                }
            }
            return List.copyOf(children);
        }

        @Override
        public <U> Group<U> map(Function<? super T, ? extends U> mapper) {
            U mappedInner = mapper.apply(inner);
            var mappedTags = new ArrayList<Tag<U>>();
            for (var tag : tags) {
                var conditions = new ArrayList<Condition<U>>();
                for (var condition : tag.conditions()) {
                    conditions.add(new Condition<>(condition.relation(), mapper.apply(condition.operand())));
                }
                mappedTags.add(new Tag<>(tag.name(), conditions, tag.applied()));
            }
            return new Group<>(mappedInner, mappedTags, value);
        }
    }

    /**
     * A tag on a {@link Group}, and whether it applies to this roll.
     *
     * @param name the name
     * @param conditions what the value of the group had to satisfy, all at once: none if the tag always applies
     * @param applied whether the tag applies
     * @param <T> the type of the children
     */
    record Tag<T>(String name, List<Condition<T>> conditions, boolean applied) {

        /**
         * Creates a rolled tag.
         *
         * @param name the name
         * @param conditions what the value of the group had to satisfy, all at once: none if the tag always applies
         * @param applied whether the tag applies
         */
        public Tag {
            requireNonNull(name, "name");
            conditions = List.copyOf(conditions);
        }
    }

    /**
     * One die that was rolled.
     *
     * @param face the face it shows
     * @param kept whether it counts in the sum: neither rerolled nor dropped
     * @param rerolled whether it was rolled again, so that the next die replaces it
     * @param exploded whether it exploded, so that the next die that isn't rerolled was added by it
     */
    record Die(int face, boolean kept, boolean rerolled, boolean exploded) {

        /**
         * Creates a die that was neither rerolled nor exploded.
         *
         * @param face the face it shows
         * @param kept whether it counts in the sum
         */
        public Die(int face, boolean kept) {
            this(face, kept, false, false);
        }
    }
}
