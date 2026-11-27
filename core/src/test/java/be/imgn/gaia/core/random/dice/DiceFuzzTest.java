package be.imgn.gaia.core.random.dice;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.core.random.dice.DiceNode.Explode;
import be.imgn.gaia.core.random.dice.DiceNode.Extreme;
import be.imgn.gaia.core.random.dice.DiceNode.Keep;
import be.imgn.gaia.core.random.dice.DiceNode.Operator;
import be.imgn.gaia.core.random.dice.DiceNode.Relation;
import be.imgn.gaia.core.random.dice.DiceNode.Reroll;
import be.imgn.gaia.core.random.dice.DiceNode.Sides;
import be.imgn.gaia.core.random.dice.DiceNode.Tally;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Random expressions, to find the texts and the trees that aren't written back as they were read. */
class DiceFuzzTest {

    private static final int EXPRESSIONS = 20_000;

    @Test
    void aParsedTextIsWrittenBackAsTheSameTree() {
        int parsedTexts = 0;
        for (int seed = 0; seed < EXPRESSIONS; seed++) {
            var text = new Texts(new SplittableRandom(seed)).expression(4);
            DiceExpression parsed;
            try {
                parsed = DiceExpression.parse(text);
            } catch (DiceParseException e) {
                // A comparison right after a bare rule, which takes parentheses, or faces that go down.
                continue;
            }
            parsedTexts++;
            var written = parsed.toString();

            var read = DiceExpression.parse(written);
            assertThat(read)
                    .as("%s, written back as %s, differ in %s", text, written, difference(parsed, read))
                    .isEqualTo(parsed);
            assertThat(DiceExpression.parse(written)).as("%s", text).hasToString(written);
        }
        assertThat(parsedTexts).isGreaterThan(EXPRESSIONS / 2);
    }

    @Test
    void aBuiltTreeIsWrittenBackWithTheSameMeaning() {
        for (int seed = 0; seed < EXPRESSIONS; seed++) {
            var built = new Trees(new SplittableRandom(seed)).expression(4);
            var written = built.toString();

            var parsed = DiceExpression.parse(written);

            assertThat(roll(parsed, seed)).as("%s", written).isEqualTo(roll(built, seed));
            assertThat(parsed).as("%s", written).hasToString(written);
        }
    }

    @Test
    void theGeneratorsCoverEveryConstruct() {
        var texts = new ArrayList<String>();
        for (int seed = 0; seed < 2_000; seed++) {
            texts.add(new Texts(new SplittableRandom(seed)).expression(4));
            texts.add(new Trees(new SplittableRandom(seed)).expression(4).toString());
        }
        var all = String.join("\n", texts);

        assertThat(List.of("d[", "..", "rr", "x", "kh", "dl", "c>", "max(", "min(", ":", "%", "!=", "STR"))
                .allMatch(all::contains);
    }

    /** The smallest parts of two expressions that differ, or an empty text if the expressions are equal. */
    private static String difference(DiceExpression left, DiceExpression right) {
        if (left.equals(right)) {
            return "";
        }
        var leftChildren = left.node().children();
        var rightChildren = right.node().children();
        if (leftChildren.size() == rightChildren.size()) {
            for (int i = 0; i < leftChildren.size(); i++) {
                var difference = difference(leftChildren.get(i), rightChildren.get(i));
                if (!difference.isEmpty()) {
                    return difference;
                }
            }
        }
        return left.node() + " and " + right.node();
    }

    /** The value of a roll, or null if the expression cannot be evaluated. */
    private static @Nullable Integer roll(DiceExpression expression, long seed) {
        try {
            return expression.roll(new SplittableRandom(seed)).value();
        } catch (ArithmeticException e) {
            return null;
        }
    }

    /** Writes random texts that follow the grammar, with spaces anywhere they are allowed. */
    private static final class Texts {

        private static final String[] RELATIONS = {"=", "!=", "<", "<=", ">", ">="};

        private final RandomGenerator random;

        Texts(RandomGenerator random) {
            this.random = random;
        }

        String expression(int depth) {
            var text = sum(depth);
            return chance(4) ? text + space() + relation() + space() + sum(depth) : text;
        }

        private String sum(int depth) {
            var text = new StringBuilder(product(depth));
            while (chance(4)) {
                text.append(space())
                        .append(chance(2) ? '+' : '-')
                        .append(space())
                        .append(product(depth));
            }
            return text.toString();
        }

        private String product(int depth) {
            var text = new StringBuilder(unary(depth));
            while (chance(6)) {
                text.append(space())
                        .append(chance(2) ? '*' : '/')
                        .append(space())
                        .append(unary(depth));
            }
            return text.toString();
        }

        private String unary(int depth) {
            if (depth > 0 && chance(10)) {
                return "-" + space() + unary(depth - 1);
            }
            if (depth > 0 && chance(10)) {
                return primary(depth - 1) + space() + "x" + space() + unary(depth - 1);
            }
            return chance(2) ? dice(depth) : primary(depth);
        }

        private String dice(int depth) {
            var text = new StringBuilder(chance(4) ? "" : primary(depth - 1) + space());
            text.append('d').append(space()).append(sides(depth));
            if (chance(4)) {
                text.append(space()).append(chance(2) ? "r" : "rr").append(condition(2));
            }
            if (chance(4)) {
                text.append(space()).append('x').append(condition(2));
            }
            if (chance(4)) {
                text.append(space())
                        .append(new String[] {"kh", "kl", "dh", "dl"}[random.nextInt(4)])
                        .append(space())
                        .append(random.nextInt(4));
            }
            if (chance(6)) {
                text.append(space()).append('c').append(condition(1));
            }
            return text.toString();
        }

        private String sides(int depth) {
            return switch (random.nextInt(6)) {
                case 0 -> "%";
                case 1 -> {
                    var faces = new StringBuilder("[");
                    int face = random.nextInt(-3, 3);
                    for (int count = random.nextInt(1, 5); count > 0; count--) {
                        faces.append(space()).append(face);
                        if (chance(3)) {
                            int last = face + random.nextInt(4);
                            faces.append(space()).append("..").append(space()).append(last);
                            face = last;
                        }
                        faces.append(space()).append(count > 1 ? "," : "");
                        face += random.nextInt(3);
                    }
                    yield faces.append(']').toString();
                }
                default -> primary(depth - 1);
            };
        }

        /** A condition, always when {@code optional} is one, or one time in that number. */
        private String condition(int optional) {
            return optional > 1 && chance(optional)
                    ? ""
                    : space() + relation() + space() + (chance(5) ? "-" + space() : "") + random.nextInt(8);
        }

        private String primary(int depth) {
            if (depth <= 0) {
                return chance(4) ? variable() : Integer.toString(random.nextInt(12));
            }
            return switch (random.nextInt(6)) {
                case 0 -> variable();
                case 1 -> group(depth - 1);
                case 2 -> {
                    var text = new StringBuilder(chance(2) ? "min" : "max")
                            .append(space())
                            .append('(');
                    for (int count = random.nextInt(1, 4); count > 0; count--) {
                        text.append(space())
                                .append(expression(depth - 1))
                                .append(space())
                                .append(count > 1 ? "," : "");
                    }
                    yield text.append(')').toString();
                }
                default -> Integer.toString(random.nextInt(12));
            };
        }

        private String group(int depth) {
            var text = new StringBuilder("(").append(space()).append(expression(depth));
            for (int tags = chance(3) ? random.nextInt(1, 3) : 0; tags > 0; tags--) {
                text.append(space()).append(':').append(space()).append(chance(2) ? "tag" : "Crit_2");
                for (int conditions = random.nextInt(3); conditions > 0; conditions--) {
                    text.append(space()).append(relation()).append(space()).append(sum(depth - 1));
                }
            }
            return text.append(space()).append(')').toString();
        }

        private String variable() {
            return new String[] {"STR", "AC", "_X1", "BONUS"}[random.nextInt(4)];
        }

        private String relation() {
            return RELATIONS[random.nextInt(RELATIONS.length)];
        }

        private String space() {
            return chance(4) ? " " : "";
        }

        private boolean chance(int oneIn) {
            return random.nextInt(oneIn) == 0;
        }
    }

    /** Builds random trees with no group, so that writing them takes parentheses of its own. */
    private static final class Trees {

        private static final List<Keep.Mode> KEEPS =
                List.of(Keep.Mode.KEEP_HIGHEST, Keep.Mode.KEEP_LOWEST, Keep.Mode.DROP_HIGHEST, Keep.Mode.DROP_LOWEST);

        private final RandomGenerator random;

        Trees(RandomGenerator random) {
            this.random = random;
        }

        DiceExpression expression(int depth) {
            if (depth <= 0) {
                return constant(random.nextInt(-2, 7));
            }
            DiceNode<DiceExpression> node = switch (random.nextInt(9)) {
                case 0 -> new DiceNode.Negate<>(expression(depth - 1));
                case 1, 2 ->
                    new DiceNode.Arithmetic<>(pick(Operator.values()), expression(depth - 1), expression(depth - 1));
                case 3 ->
                    new DiceNode.Comparison<>(pick(Relation.values()), expression(depth - 1), expression(depth - 1));
                case 4 -> {
                    var operands = new ArrayList<DiceExpression>();
                    for (int count = random.nextInt(1, 4); count > 0; count--) {
                        operands.add(expression(depth - 1));
                    }
                    yield new DiceNode.Extremum<>(pick(Extreme.values()), operands);
                }
                case 5 -> new DiceNode.Repeat<>(expression(depth - 1), expression(depth - 1));
                case 6, 7 -> dice(depth);
                default -> new DiceNode.Constant<>(random.nextInt(-2, 7));
            };
            return DiceExpression.of(node);
        }

        private DiceNode<DiceExpression> dice(int depth) {
            Sides<DiceExpression> sides;
            if (random.nextInt(4) == 0) {
                var faces = new ArrayList<Integer>();
                int face = random.nextInt(-3, 3);
                for (int count = random.nextInt(1, 6); count > 0; count--) {
                    faces.add(face);
                    face += random.nextInt(3);
                }
                sides = new Sides.Custom<>(faces);
            } else {
                sides = new Sides.Standard<>(
                        random.nextInt(4) == 0 ? expression(depth - 1) : constant(random.nextInt(1, 9)));
            }
            var reroll = switch (random.nextInt(6)) {
                case 0 -> new Reroll(Reroll.Times.ONCE, Reroll.Target.LOWEST, Relation.EQUAL, 0);
                case 1 -> new Reroll(Reroll.Times.ALWAYS, Reroll.Target.MATCHING, relation(), random.nextInt(-1, 4));
                default -> Reroll.NONE;
            };
            var explode = switch (random.nextInt(6)) {
                case 0 -> Explode.HIGHEST;
                case 1 -> new Explode(Explode.Faces.MATCHING, relation(), random.nextInt(-1, 7));
                default -> Explode.NONE;
            };
            var keep = random.nextInt(4) == 0
                    ? new Keep(KEEPS.get(random.nextInt(KEEPS.size())), random.nextInt(3))
                    : Keep.ALL;
            var tally = random.nextInt(6) == 0 ? new Tally(true, relation(), random.nextInt(-1, 7)) : Tally.SUM;
            var count = random.nextInt(4) == 0 ? expression(depth - 1) : constant(random.nextInt(4));
            return new DiceNode.Dice<>(count, sides, reroll, explode, keep, tally);
        }

        private <E> E pick(E[] values) {
            return values[random.nextInt(values.length)];
        }

        private Relation relation() {
            return pick(Relation.values());
        }

        private static DiceExpression constant(int value) {
            return DiceExpression.of(new DiceNode.Constant<>(value));
        }
    }
}
