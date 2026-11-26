package be.imgn.gaia.core.random.dice;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.random.Chances;
import be.imgn.gaia.core.random.RandomSelector;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;

/**
 * A dice expression, such as {@code 4d6dl1} or {@code (1d20:critical>=20:miss=1)+STR+BONUS>=AC}: a tree of
 * {@link DiceNode}s that is parsed from a text, inspected, rolled, and whose chances are computed exactly.
 *
 * <pre>{@code
 * var attack = DiceExpression.parse("(1d20:critical>=20:miss=1)+STR+BONUS>=AC");
 * attack.variables();                           // [STR, BONUS, AC]
 *
 * var bound = attack.bind(Map.of("STR", 3, "BONUS", 2, "AC", 15));
 * var rolled = bound.roll(random);
 * rolled.value();                               // 1 if the attack hits, 0 if it misses
 * rolled.tags();                                // [critical] if the die shows 20
 *
 * var chances = bound.chances();
 * chances.of(1);                                // 11/20
 * chances.tagged("critical");                   // 1/20
 * }</pre>
 *
 * <h2>Syntax</h2>
 *
 * <p>From the lowest precedence to the highest:
 *
 * <ul>
 *   <li>one comparison, {@code = != < <= > >=}, which evaluates to 1 if it holds and to 0 if not;
 *   <li>{@code + -}, then {@code * /}, from left to right; a division is rounded down;
 *   <li>a unary {@code -};
 *   <li>dice, {@code 3d6}: a count, which is 1 if omitted, {@code d}, a number of sides or {@code %} for 100, then
 *       optionally, in this order:
 *       <ul>
 *         <li>which dice are rolled again: {@code r<=2} rerolls once a die that shows 2 or less, and keeps its new face
 *             whatever it is; {@code rr<=2} rerolls it until it shows more; {@code r} alone is {@code r=1};
 *         <li>which dice explode, that is, add another die that may explode too, up to ten times for one die: {@code x}
 *             on the highest face, {@code x>=5} on 5 or more;
 *         <li>which dice count in the sum: {@code kh2} keeps the two highest, {@code kl2} the two lowest, {@code dh2}
 *             drops the two highest and {@code dl2} the two lowest;
 *       </ul>
 *   <li>a whole number, a variable, whose name is made of upper-case letters, digits and underscores, or a group
 *       between parentheses.
 * </ul>
 *
 * <p>A group may end with tags, each made of a colon and a name, optionally followed by conditions, each made of a
 * comparison operator and a value: {@code (2d10:attack:tier1<=11:tier2>=12<=16:tier3>=17)}. A tag with no condition
 * always applies; the other ones apply when the value of the group satisfies all their conditions. Spaces are allowed
 * anywhere but inside dice.
 *
 * <p>As a grammar:
 *
 * <pre>{@code
 * expression := sum (relation sum)?
 * relation   := '=' | '!=' | '<' | '<=' | '>' | '>='
 * sum        := product (('+' | '-') product)*
 * product    := unary (('*' | '/') unary)*
 * unary      := '-' unary | dice
 * dice       := primary? 'd' (primary | '%') reroll? explode? keep? | primary
 * reroll     := ('r' | 'rr') (relation number)?
 * explode    := 'x' (relation number)?
 * keep       := ('kh' | 'kl' | 'dh' | 'dl') number
 * primary    := number | variable | group
 * group      := '(' expression tag* ')'
 * tag        := ':' name (relation sum)*
 * number     := [0-9]+
 * variable   := [A-Z_] [A-Z0-9_]*
 * name       := [A-Za-z_] [A-Za-z0-9_]*
 * }</pre>
 *
 * <p>A number is at most {@link Integer#MAX_VALUE}. The parts of {@code dice} follow each other with no space in
 * between, down to the condition of a reroll or of an explosion: {@code 1d6x>=5} explodes on 5 or more, while
 * {@code 1d6x >=5} compares dice that explode on a 6 with 5.
 *
 * <h2>Inspection</h2>
 *
 * <p>{@link #node()} is the root of the tree, to walk from the top with a {@code switch} over the records of
 * {@link DiceNode}. {@link #fold(Function)} walks it from the bottom instead: the function is given each node with its
 * children already replaced by what it returned for them. Everything else this class does is built on it.
 *
 * <pre>{@code
 * int dice = expression.<Integer>fold(node -> switch (node) {
 *     case DiceNode.Dice<Integer> d -> 1;
 *     default -> node.children().stream().mapToInt(Integer::intValue).sum();
 * });
 * }</pre>
 *
 * <p>Expressions are immutable, and equal when their trees are.
 *
 * @since 0.2.0
 */
public final class DiceExpression {

    private final DiceNode<DiceExpression> node;

    private DiceExpression(DiceNode<DiceExpression> node) {
        this.node = requireNonNull(node, "node");
    }

    /**
     * Parses a dice expression.
     *
     * @param text the text to parse
     * @return the expression
     * @throws DiceParseException if {@code text} isn't a dice expression
     */
    public static DiceExpression parse(String text) {
        return new DiceParser(text).parse();
    }

    /**
     * Returns the expression whose root is a given node.
     *
     * @param node the root of the expression
     * @return the expression whose root is a given node
     */
    public static DiceExpression of(DiceNode<DiceExpression> node) {
        return new DiceExpression(node);
    }

    /**
     * Returns the root of this expression, whose children are expressions.
     *
     * @return the root of this expression, whose children are expressions
     */
    public DiceNode<DiceExpression> node() {
        return node;
    }

    /**
     * Reduces this expression to one value, from the leaves to the root: the function is applied to each node, whose
     * children are what the function returned for them.
     *
     * @param function the function to apply to each node
     * @param <R> the type of the result
     * @return what the function returned for the root
     */
    public <R> R fold(Function<? super DiceNode<R>, ? extends R> function) {
        requireNonNull(function, "function");
        return function.apply(node.<R>map(child -> child.fold(function)));
    }

    /**
     * Returns the names of the variables that have no value, in the order they first appear.
     *
     * @return the names of the variables that have no value, in the order they first appear
     */
    public Set<String> variables() {
        Set<String> variables = this.<Set<String>>fold(node -> {
            var names = new LinkedHashSet<String>();
            if (node instanceof DiceNode.Variable<Set<String>> variable) {
                names.add(variable.name());
            }
            node.children().forEach(names::addAll);
            return names;
        });
        return Collections.unmodifiableSet(variables);
    }

    /**
     * Returns this expression with a value given to a variable. Other variables are left as they are, and so is this
     * expression if it doesn't have that variable.
     *
     * @param name the name of the variable
     * @param value the value of the variable
     * @return this expression with a value given to a variable
     */
    public DiceExpression bind(String name, int value) {
        return bind(name, of(new DiceNode.Constant<>(value)));
    }

    /**
     * Returns this expression with a variable standing for another expression, which is evaluated again for each
     * occurrence of the variable: dice in it are rolled once per occurrence.
     *
     * @param name the name of the variable
     * @param value what the variable stands for
     * @return this expression with a variable standing for another expression, which is evaluated again for each
     *     occurrence of the variable: dice in it are rolled once per occurrence
     */
    public DiceExpression bind(String name, DiceExpression value) {
        requireNonNull(name, "name");
        requireNonNull(value, "value");
        return bind(variable -> variable.equals(name) ? value : null);
    }

    /**
     * Returns this expression with values given to variables. Other variables are left as they are, and names that
     * aren't variables of this expression are ignored.
     *
     * @param values the values of the variables, by name
     * @return this expression with values given to variables
     */
    public DiceExpression bind(Map<String, Integer> values) {
        var copy = Map.copyOf(values);
        return bind(variable -> {
            var value = copy.get(variable);
            return value == null ? null : of(new DiceNode.Constant<>(value));
        });
    }

    /**
     * Returns this expression with each variable standing for what a function returns for its name, or left as it is if
     * the function returns null. The function is called once per occurrence of a variable, and what it returns is
     * evaluated again for each of them: dice in it are rolled once per occurrence.
     *
     * <pre>{@code
     * expression.bind(name -> switch (name) {
     *     case "STR" -> DiceExpression.parse("3");
     *     case "SNEAK" -> DiceExpression.parse("3d6");
     *     default -> null;
     * });
     * }</pre>
     *
     * @param values what each variable stands for, by name
     * @return the expression with the values
     */
    public DiceExpression bind(Function<? super String, ? extends @Nullable DiceExpression> values) {
        requireNonNull(values, "values");
        return this.<DiceExpression>fold(node -> {
            if (node instanceof DiceNode.Variable<DiceExpression> variable) {
                var value = values.apply(variable.name());
                if (value != null) {
                    return of(new DiceNode.Bound<>(variable.name(), value));
                }
            }
            return of(node);
        });
    }

    /**
     * Rolls the dice of this expression, from left to right, and evaluates it.
     *
     * <p>Each die calls {@link RandomGenerator#nextInt(int) nextInt(sides)} once, and shows that number plus one. A die
     * that is rerolled calls it again right away, and a die that explodes is followed by the one it adds. The dice of a
     * count and of a number of sides are rolled before the dice they count, and those of a group before those of the
     * conditions of its tags. Nothing else uses the source of randomness.
     *
     * @param random the source of randomness
     * @return the tree of what each node evaluated to
     * @throws UnresolvedVariableException if a variable has no value
     * @throws ArithmeticException if a value overflows an {@code int}, on a division by zero, or if a number of dice is
     *     negative or more than 10,000, or a number of sides less than one
     */
    public RolledExpression roll(RandomGenerator random) {
        requireNonNull(random, "random");
        checkResolved();
        return this.<RolledExpression>fold(node -> Rolls.roll(node, random));
    }

    /**
     * Computes the exact chances of each outcome of this expression, without rolling.
     *
     * <p>Dice that are kept or dropped aren't enumerated roll by roll: their faces are taken one at a time, counting
     * the ways for each number of dice to show that face. A die that is rerolled is counted as a die whose faces aren't
     * equally likely, and a die that explodes as the sum of the dice it may add.
     *
     * @return the chances of each outcome
     * @throws UnresolvedVariableException if a variable has no value
     * @throws ArithmeticException if a value overflows an {@code int}, on a division by zero, if a number of dice is
     *     negative or more than 10,000, or a number of sides less than one, or if some dice have more than 1,000,000
     *     sides in total
     * @throws UnsupportedOperationException if some dice both explode and have dice kept or dropped
     */
    public DiceChances chances() {
        checkResolved();
        return new DiceChances(this.<Distribution>fold(Distribution::of));
    }

    /**
     * Returns this expression as a selector of the values it evaluates to: each selection rolls the dice, and the
     * chances of the selector are those of {@link #chances()}. It's how a quantity of a
     * {@link be.imgn.gaia.core.random.RandomTable} is given as dice.
     *
     * @return a selector that rolls this expression
     * @throws UnresolvedVariableException if a variable has no value
     */
    public RandomSelector<Integer> selector() {
        checkResolved();
        return new RandomSelector<>() {
            @Override
            public Integer next(RandomGenerator random) {
                return roll(random).value();
            }

            @Override
            public Chances<Integer> chances() {
                return DiceExpression.this.chances().values();
            }
        };
    }

    private void checkResolved() {
        var variables = variables();
        if (!variables.isEmpty()) {
            throw new UnresolvedVariableException(variables);
        }
    }

    @Override
    public boolean equals(@Nullable Object object) {
        return object instanceof DiceExpression other && node.equals(other.node);
    }

    @Override
    public int hashCode() {
        return node.hashCode();
    }

    /**
     * Returns this expression as a text that {@link #parse(String)} reads back to an expression with the same meaning.
     * Spaces are lost, an omitted count is written as {@code 1}, {@code d%} as {@code d100}, and a variable that was
     * given a value is written as its name.
     *
     * @return this expression as a text that {@link #parse(String)} reads back to an expression with the same meaning
     */
    @Override
    public String toString() {
        return this.<Rendering.Rendered>fold(Rendering::render).text();
    }
}
