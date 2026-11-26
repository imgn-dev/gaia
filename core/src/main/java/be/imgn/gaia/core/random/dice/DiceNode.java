package be.imgn.gaia.core.random.dice;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * One node of a {@link DiceExpression}, whose children are of type {@code T}.
 *
 * <p>In {@link DiceExpression#node()}, the children are expressions themselves. In the function passed to
 * {@link DiceExpression#fold(Function)}, they are what that function returned for them. Either way, a {@code switch}
 * over the permitted records tells the nodes apart:
 *
 * <pre>{@code
 * int depth = expression.<Integer>fold(node -> switch (node) {
 *     case DiceNode.Constant<Integer> constant -> 1;
 *     case DiceNode.Variable<Integer> variable -> 1;
 *     default -> 1 + Collections.max(node.children());
 * });
 * }</pre>
 *
 * @param <T> the type of the children
 * @since 0.2.0
 */
public sealed interface DiceNode<T> {

    /**
     * Returns the children of this node, in the order they are written: none for a constant or a variable.
     *
     * @return the children of this node, in the order they are written: none for a constant or a variable
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
    <U> DiceNode<U> map(Function<? super T, ? extends U> mapper);

    private static void checkVariableName(String name) {
        checkArgument(isName(name, true), "Not a variable name: %s", name);
    }

    private static boolean isName(String name, boolean upperCaseOnly) {
        if (name.isEmpty() || isDigit(name.charAt(0))) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean valid =
                    c == '_' || isDigit(c) || (c >= 'A' && c <= 'Z') || (!upperCaseOnly && c >= 'a' && c <= 'z');
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * A whole number, such as {@code 20}.
     *
     * @param value the number
     * @param <T> the type of the children
     */
    record Constant<T>(int value) implements DiceNode<T> {

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
     * A variable that has no value yet, such as {@code STR}.
     *
     * @param name the name: upper-case ASCII letters, digits and underscores, not starting with a digit
     * @param <T> the type of the children
     */
    record Variable<T>(String name) implements DiceNode<T> {

        /**
         * Creates a variable.
         *
         * @param name the name: upper-case ASCII letters, digits and underscores, not starting with a digit
         * @throws IllegalArgumentException if {@code name} isn't a variable name
         */
        public Variable {
            checkVariableName(name);
        }

        @Override
        public List<T> children() {
            return List.of();
        }

        @Override
        public <U> Variable<U> map(Function<? super T, ? extends U> mapper) {
            requireNonNull(mapper, "mapper");
            return new Variable<>(name);
        }
    }

    /**
     * A variable that was given a value. It's written as its name and evaluates to its value.
     *
     * @param name the name of the variable
     * @param value what the variable stands for
     * @param <T> the type of the children
     */
    record Bound<T>(String name, T value) implements DiceNode<T> {

        /**
         * Creates a bound variable.
         *
         * @param name the name of the variable
         * @param value what the variable stands for
         * @throws IllegalArgumentException if {@code name} isn't a variable name
         */
        public Bound {
            checkVariableName(name);
            requireNonNull(value, "value");
        }

        @Override
        public List<T> children() {
            return List.of(value);
        }

        @Override
        public <U> Bound<U> map(Function<? super T, ? extends U> mapper) {
            return new Bound<>(name, mapper.apply(value));
        }
    }

    /**
     * Dice that are rolled and summed, such as {@code 4d6dl1}.
     *
     * @param count how many dice are rolled
     * @param sides how many sides each die has, numbered from one
     * @param keep which dice count in the sum
     * @param <T> the type of the children
     */
    record Dice<T>(T count, T sides, Keep keep) implements DiceNode<T> {

        /**
         * Creates dice.
         *
         * @param count how many dice are rolled
         * @param sides how many sides each die has, numbered from one
         * @param keep which dice count in the sum
         */
        public Dice {
            requireNonNull(count, "count");
            requireNonNull(sides, "sides");
            requireNonNull(keep, "keep");
        }

        @Override
        public List<T> children() {
            return List.of(count, sides);
        }

        @Override
        public <U> Dice<U> map(Function<? super T, ? extends U> mapper) {
            U mappedCount = mapper.apply(count);
            return new Dice<>(mappedCount, mapper.apply(sides), keep);
        }
    }

    /**
     * The opposite of a value, such as {@code -STR}.
     *
     * @param operand the value to negate
     * @param <T> the type of the children
     */
    record Negate<T>(T operand) implements DiceNode<T> {

        /**
         * Creates a negation.
         *
         * @param operand the value to negate
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
            return new Negate<>(mapper.apply(operand));
        }
    }

    /**
     * An arithmetic operation on two values, such as {@code 1d8+3}.
     *
     * @param operator the operation
     * @param left the left operand
     * @param right the right operand
     * @param <T> the type of the children
     */
    record Arithmetic<T>(Operator operator, T left, T right) implements DiceNode<T> {

        /**
         * Creates an arithmetic operation.
         *
         * @param operator the operation
         * @param left the left operand
         * @param right the right operand
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
            return new Arithmetic<>(operator, mappedLeft, mapper.apply(right));
        }
    }

    /**
     * A comparison of two values, such as {@code 1d20+5>=AC}, which evaluates to 1 if it holds and to 0 if not.
     *
     * @param relation how the operands are compared
     * @param left the left operand
     * @param right the right operand
     * @param <T> the type of the children
     */
    record Comparison<T>(Relation relation, T left, T right) implements DiceNode<T> {

        /**
         * Creates a comparison.
         *
         * @param relation how the operands are compared
         * @param left the left operand
         * @param right the right operand
         */
        public Comparison {
            requireNonNull(relation, "relation");
            requireNonNull(left, "left");
            requireNonNull(right, "right");
        }

        @Override
        public List<T> children() {
            return List.of(left, right);
        }

        @Override
        public <U> Comparison<U> map(Function<? super T, ? extends U> mapper) {
            U mappedLeft = mapper.apply(left);
            return new Comparison<>(relation, mappedLeft, mapper.apply(right));
        }
    }

    /**
     * A value between parentheses, with the tags it's given, such as {@code (1d20:critical>=20:miss=1)}.
     *
     * @param inner the value
     * @param tags the tags, in the order they are written
     * @param <T> the type of the children
     */
    record Group<T>(T inner, List<Tag<T>> tags) implements DiceNode<T> {

        /**
         * Creates a group.
         *
         * @param inner the value
         * @param tags the tags, in the order they are written
         */
        public Group {
            requireNonNull(inner, "inner");
            tags = List.copyOf(tags);
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
                mappedTags.add(new Tag<>(tag.name(), conditions));
            }
            return new Group<>(mappedInner, mappedTags);
        }
    }

    /**
     * A tag on a {@link Group}, such as {@code :critical>=20} or {@code :tier2>=12<=16}.
     *
     * @param name the name: ASCII letters, digits and underscores, not starting with a digit
     * @param conditions what the value of the group must satisfy, all at once, for the tag to apply: none if it always
     *     applies
     * @param <T> the type of the children
     */
    record Tag<T>(String name, List<Condition<T>> conditions) {

        /**
         * Creates a tag.
         *
         * @param name the name: ASCII letters, digits and underscores, not starting with a digit
         * @param conditions what the value of the group must satisfy, all at once, for the tag to apply: none if it
         *     always applies
         * @throws IllegalArgumentException if {@code name} isn't a tag name
         */
        public Tag {
            checkArgument(isName(name, false), "Not a tag name: %s", name);
            conditions = List.copyOf(conditions);
        }
    }

    /**
     * One condition of a {@link Tag}: the value of the group, compared to an operand.
     *
     * @param relation how the value of the group is compared to the operand
     * @param operand the right operand of the comparison
     * @param <T> the type of the children
     */
    record Condition<T>(Relation relation, T operand) {

        /**
         * Creates a condition.
         *
         * @param relation how the value of the group is compared to the operand
         * @param operand the right operand of the comparison
         */
        public Condition {
            requireNonNull(relation, "relation");
            requireNonNull(operand, "operand");
        }
    }

    /**
     * Which of the rolled dice count in the sum: {@link #ALL} of them, or the highest or the lowest ones.
     *
     * @param mode whether dice are kept or dropped, and from which end
     * @param count how many dice are kept or dropped, which is zero if they all count
     */
    record Keep(Mode mode, int count) {

        /** Every die counts: nothing is written after the dice. */
        public static final Keep ALL = new Keep(Mode.ALL, 0);

        /**
         * Creates a rule.
         *
         * @param mode whether dice are kept or dropped, and from which end
         * @param count how many dice are kept or dropped, which is zero if they all count
         * @throws IllegalArgumentException if {@code count} is negative, or if it isn't zero while {@code mode} is
         *     {@link Mode#ALL}
         */
        public Keep {
            requireNonNull(mode, "mode");
            checkArgument(count >= 0, "count must not be negative: %d", count);
            checkArgument(mode != Mode.ALL || count == 0, "count must be zero when every die counts: %d", count);
        }

        /**
         * Returns how many dice count in the sum.
         *
         * @param rolled how many dice are rolled
         * @return how many dice count in the sum
         */
        public int kept(int rolled) {
            return switch (mode) {
                case ALL -> rolled;
                case KEEP_HIGHEST, KEEP_LOWEST -> Math.min(count, rolled);
                case DROP_HIGHEST, DROP_LOWEST -> Math.max(rolled - count, 0);
            };
        }

        /**
         * Returns whether the dice that count are the highest ones, rather than the lowest ones. It's true when they
         * all count.
         *
         * @return whether the dice that count are the highest ones, rather than the lowest ones
         */
        public boolean highest() {
            return mode != Mode.KEEP_LOWEST && mode != Mode.DROP_HIGHEST;
        }

        /**
         * Returns this rule as it's written, such as {@code kh1}, which is empty when every die counts.
         *
         * @return this rule as it's written, such as {@code kh1}
         */
        @Override
        public String toString() {
            return mode == Mode.ALL ? "" : mode.symbol + count;
        }

        /** Whether dice are kept or dropped, and from which end. */
        public enum Mode {
            /** Keeps every die: nothing is written. */
            ALL(""),
            /** Keeps the highest dice: {@code kh}. */
            KEEP_HIGHEST("kh"),
            /** Keeps the lowest dice: {@code kl}. */
            KEEP_LOWEST("kl"),
            /** Drops the highest dice: {@code dh}. */
            DROP_HIGHEST("dh"),
            /** Drops the lowest dice: {@code dl}. */
            DROP_LOWEST("dl");

            private final String symbol;

            Mode(String symbol) {
                this.symbol = symbol;
            }

            /**
             * Returns how this mode is written.
             *
             * @return how this mode is written
             */
            public String symbol() {
                return symbol;
            }
        }
    }

    /** An arithmetic operation. */
    enum Operator {
        /** Addition: {@code +}. */
        ADD("+"),
        /** Subtraction: {@code -}. */
        SUBTRACT("-"),
        /** Multiplication: {@code *}. */
        MULTIPLY("*"),
        /** Division, rounded down: {@code /}. */
        DIVIDE("/");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        /**
         * Returns how this operator is written.
         *
         * @return how this operator is written
         */
        public String symbol() {
            return symbol;
        }

        /**
         * Applies this operation.
         *
         * @param left the left operand
         * @param right the right operand
         * @return the result
         * @throws ArithmeticException if the result overflows an {@code int}, or on a division by zero
         */
        public int apply(int left, int right) {
            return switch (this) {
                case ADD -> Math.addExact(left, right);
                case SUBTRACT -> Math.subtractExact(left, right);
                case MULTIPLY -> Math.multiplyExact(left, right);
                case DIVIDE -> Math.floorDivExact(left, right);
            };
        }
    }

    /** How two values are compared. */
    enum Relation {
        /** Equal: {@code =}. */
        EQUAL("="),
        /** Not equal: {@code !=}. */
        NOT_EQUAL("!="),
        /** Less than: {@code <}. */
        LESS("<"),
        /** Less than or equal: {@code <=}. */
        LESS_OR_EQUAL("<="),
        /** Greater than: {@code >}. */
        GREATER(">"),
        /** Greater than or equal: {@code >=}. */
        GREATER_OR_EQUAL(">=");

        private final String symbol;

        Relation(String symbol) {
            this.symbol = symbol;
        }

        /**
         * Returns how this relation is written.
         *
         * @return how this relation is written
         */
        public String symbol() {
            return symbol;
        }

        /**
         * Returns whether this relation holds between two values.
         *
         * @param left the left operand
         * @param right the right operand
         * @return whether this relation holds between two values
         */
        public boolean test(int left, int right) {
            return switch (this) {
                case EQUAL -> left == right;
                case NOT_EQUAL -> left != right;
                case LESS -> left < right;
                case LESS_OR_EQUAL -> left <= right;
                case GREATER -> left > right;
                case GREATER_OR_EQUAL -> left >= right;
            };
        }
    }
}
