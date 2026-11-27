/**
 * Dice expressions that are parsed, inspected, rolled, and whose chances are computed exactly. See {@link
 * be.imgn.gaia.core.random.dice.DiceExpression}.
 *
 * <p>An expression only says which tags apply to a roll. What the tags mean in a game is left to the code that reads
 * them, as in the two examples below.
 *
 * <h2>The power roll of Draw Steel</h2>
 *
 * <p>Two ten-sided dice and a characteristic give a tier: the first one up to 11, the second one from 12 to 16, and
 * the third one from 17. The dice alone showing 19 or 20 is a critical hit. Each tier is a tag whose conditions cover
 * its range, so that every roll has exactly one of them:
 *
 * <pre>{@code
 * enum Tier {
 *     TIER1,
 *     TIER2,
 *     TIER3;
 *
 *     static final DiceExpression POWER_ROLL =
 *             DiceExpression.parse("((2d10:critical>=19)+CHARACTERISTIC:tier1<=11:tier2>=12<=16:tier3>=17)");
 *
 *     static Tier of(Set<String> tags) {
 *         return Arrays.stream(values())
 *                 .filter(tier -> tags.contains(tier.name().toLowerCase(Locale.ROOT)))
 *                 .findFirst()
 *                 .orElseThrow();
 *     }
 * }
 *
 * var roll = Tier.POWER_ROLL.bind("CHARACTERISTIC", 2);
 *
 * var rolled = roll.roll(random);
 * rolled.toString();                     // "((2d10[7, 4])+CHARACTERISTIC[2]:tier2) = 13"
 * Tier.of(rolled.tags());                // TIER2
 * rolled.tags().contains("critical");    // false
 *
 * var chances = roll.chances();
 * chances.where(outcome -> Tier.of(outcome.tags()) == Tier.TIER1);    // 9/25
 * chances.where(outcome -> Tier.of(outcome.tags()) == Tier.TIER2);    // 43/100
 * chances.where(outcome -> Tier.of(outcome.tags()) == Tier.TIER3);    // 21/100
 * chances.tagged("critical");                                         // 3/100
 * }</pre>
 *
 * <h2>The degrees of success of Pathfinder 2</h2>
 *
 * <p>A twenty-sided die and a modifier are compared to a difficulty class: the check succeeds from the difficulty
 * class, critically from ten above it, and fails critically from ten below it. The die alone then moves the result:
 * a 20 makes it one degree better, and a 1 one degree worse. The tags give the degree that the total reaches and
 * what the die shows, and the enum applies the rule:
 *
 * <pre>{@code
 * enum Degree {
 *     CRITICAL_FAILURE,
 *     FAILURE,
 *     SUCCESS,
 *     CRITICAL_SUCCESS;
 *
 *     static final DiceExpression CHECK = DiceExpression.parse("((1d20:natural20=20:natural1=1)+MODIFIER"
 *             + ":critical_failure<=DC-10:failure>DC-10<DC:success>=DC<DC+10:critical_success>=DC+10)");
 *
 *     static Degree of(Set<String> tags) {
 *         int degree = Arrays.stream(values())
 *                 .filter(reached -> tags.contains(reached.name().toLowerCase(Locale.ROOT)))
 *                 .findFirst()
 *                 .orElseThrow()
 *                 .ordinal();
 *         if (tags.contains("natural20")) {
 *             degree = Math.min(degree + 1, CRITICAL_SUCCESS.ordinal());
 *         }
 *         if (tags.contains("natural1")) {
 *             degree = Math.max(degree - 1, CRITICAL_FAILURE.ordinal());
 *         }
 *         return values()[degree];
 *     }
 * }
 *
 * var strike = Degree.CHECK.bind(Map.of("MODIFIER", 12, "DC", 25));
 *
 * var rolled = strike.roll(random);
 * rolled.toString();                     // "((1d20[18])+MODIFIER[12]:success) = 30"
 * Degree.of(rolled.tags());              // SUCCESS
 *
 * var chances = strike.chances();
 * chances.where(outcome -> Degree.of(outcome.tags()) == Degree.CRITICAL_FAILURE);    // 3/20
 * chances.where(outcome -> Degree.of(outcome.tags()) == Degree.FAILURE);             // 9/20
 * chances.where(outcome -> Degree.of(outcome.tags()) == Degree.SUCCESS);             // 7/20
 * chances.where(outcome -> Degree.of(outcome.tags()) == Degree.CRITICAL_SUCCESS);    // 1/20
 * }</pre>
 *
 * <p>With a modifier of 12, no total reaches 35: the only critical successes are the 20s on the die, which the
 * chances count because they keep each total together with the tags of the roll that gave it.
 *
 * <h2>Advantage and critical hits in Dungeons and Dragons 5</h2>
 *
 * <p>These rules change the dice of an expression rather than read its result, so they are functions from an
 * expression to another one. They walk down the tree with {@link be.imgn.gaia.core.random.dice.DiceExpression#node()}
 * and {@link be.imgn.gaia.core.random.dice.DiceNode#map(java.util.function.Function)}, which rebuilds a node around
 * its changed children.
 *
 * <p>With advantage, a twenty-sided die is rolled twice and the highest is kept; with disadvantage, the lowest is.
 * Both at once cancel out, however many times each applies. A tag on the die remembers what was applied to it, so
 * that the functions may be applied one after the other in any order:
 *
 * <pre>{@code
 * static DiceExpression advantage(DiceExpression expression) {
 *     return mark(expression, "advantage");
 * }
 *
 * static DiceExpression disadvantage(DiceExpression expression) {
 *     return mark(expression, "disadvantage");
 * }
 *
 * static DiceExpression mark(DiceExpression expression, String mark) {
 *     return switch (expression.node()) {
 *         // A d20 that was marked before: its tags say how.
 *         case DiceNode.Group<DiceExpression>(var inner, var tags) when isD20(inner) && isMarked(tags) ->
 *             d20(tags, mark);
 *         // A d20 that wasn't.
 *         case DiceNode.Dice<DiceExpression> dice when isD20(expression) -> d20(List.of(), mark);
 *         case DiceNode<DiceExpression> node -> DiceExpression.of(node.map(child -> mark(child, mark)));
 *     };
 * }
 *
 * static DiceExpression d20(List<Tag<DiceExpression>> tags, String mark) {
 *     var marks = new ArrayList<>(tags);
 *     if (tags.stream().noneMatch(tag -> tag.name().equals(mark))) {
 *         marks.add(new Tag<>(mark, List.of()));
 *     }
 *     boolean advantage = marks.stream().anyMatch(tag -> tag.name().equals("advantage"));
 *     boolean disadvantage = marks.stream().anyMatch(tag -> tag.name().equals("disadvantage"));
 *     var dice = DiceExpression.parse(advantage == disadvantage ? "1d20" : advantage ? "2d20kh1" : "2d20kl1");
 *     return DiceExpression.of(new DiceNode.Group<>(dice, marks));
 * }
 *
 * static boolean isMarked(List<Tag<DiceExpression>> tags) {
 *     return tags.stream().anyMatch(tag -> Set.of("advantage", "disadvantage").contains(tag.name()));
 * }
 *
 * static boolean isD20(DiceExpression expression) {
 *     return Set.of("1d20", "2d20kh1", "2d20kl1").contains(expression.toString());
 * }
 *
 * var attack = DiceExpression.parse("(1d20:critical>=20)+5>=15");
 * advantage(attack);                             // ((2d20kh1:advantage):critical>=20)+5>=15
 * advantage(disadvantage(attack));               // ((1d20:disadvantage:advantage):critical>=20)+5>=15
 * advantage(advantage(disadvantage(attack)));    // the same: still one die
 *
 * attack.chances().of(1);                        // 11/20
 * advantage(attack).chances().of(1);             // 319/400
 * disadvantage(attack).chances().of(1);          // 121/400
 * }</pre>
 *
 * <p>A critical hit rolls the dice of the damage twice, and adds the rest once. Dice that all count are rolled in
 * twice the number; dice of which some are dropped are rolled a second time:
 *
 * <pre>{@code
 * static DiceExpression critical(DiceExpression damage) {
 *     return switch (damage.node()) {
 *         case DiceNode.Dice<DiceExpression> dice when dice.keep().equals(Keep.ALL) ->
 *             DiceExpression.of(new DiceNode.Dice<>(
 *                     twice(dice.count()), dice.sides(), dice.reroll(), dice.explode(), dice.keep(), dice.tally()));
 *         case DiceNode.Dice<DiceExpression> dice ->
 *             DiceExpression.of(new DiceNode.Arithmetic<>(Operator.ADD, damage, damage));
 *         case DiceNode<DiceExpression> node -> DiceExpression.of(node.map(child -> critical(child)));
 *     };
 * }
 *
 * static DiceExpression twice(DiceExpression count) {
 *     var two = DiceExpression.parse("2");
 *     return count.node() instanceof DiceNode.Constant<DiceExpression>(var value)
 *             ? DiceExpression.of(new DiceNode.Constant<>(2 * value))
 *             : DiceExpression.of(new DiceNode.Arithmetic<>(Operator.MULTIPLY, count, two));
 * }
 *
 * critical(DiceExpression.parse("2d6+STR"));          // 4d6+STR
 * critical(DiceExpression.parse("1d8+SNEAKd6+3"));    // 2d8+(SNEAK*2)d6+3
 * critical(DiceExpression.parse("4d6dl1*2"));         // (4d6dl1+4d6dl1)*2
 * critical(DiceExpression.parse("2d6r<=2+3"));        // 4d6r<=2+3, as great weapon fighting rerolls
 *
 * var damage = DiceExpression.parse("2d6+STR").bind("STR", 3);
 * damage.chances().mean();                            // 10
 * critical(damage).chances().mean();                  // 17
 * }</pre>
 *
 * <p>The dice of a variable that stands for an expression are doubled too, since the function goes through the
 * variable: {@code 1d8+BONUS}, where {@code BONUS} is {@code 1d4}, is still written {@code 2d8+BONUS} but rolls two
 * {@code d4}.
 */
@NullMarked
package be.imgn.gaia.core.random.dice;

import org.jspecify.annotations.NullMarked;
