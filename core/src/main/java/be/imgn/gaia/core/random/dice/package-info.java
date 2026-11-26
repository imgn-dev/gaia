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
 */
@NullMarked
package be.imgn.gaia.core.random.dice;

import org.jspecify.annotations.NullMarked;
