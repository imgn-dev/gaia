package be.imgn.gaia.core.random;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.random.DiceNode.Condition;
import be.imgn.gaia.core.random.DiceNode.Keep;
import be.imgn.gaia.core.random.DiceNode.Operator;
import be.imgn.gaia.core.random.DiceNode.Relation;
import be.imgn.gaia.core.random.DiceNode.Tag;
import java.util.ArrayList;
import org.jspecify.annotations.Nullable;

/** A recursive descent parser of dice expressions. */
final class DiceParser {

    private static final char END = '\0';
    private static final int PERCENT_SIDES = 100;
    private static final int RADIX = 10;

    private final String text;
    private int index;

    DiceParser(String text) {
        this.text = requireNonNull(text, "text");
    }

    DiceExpression parse() {
        var expression = expression();
        skipSpaces();
        if (index < text.length()) {
            throw error("Unexpected '" + peek() + "'");
        }
        return expression;
    }

    private DiceExpression expression() {
        var left = sum();
        var relation = relation();
        return relation == null ? left : DiceExpression.of(new DiceNode.Comparison<>(relation, left, sum()));
    }

    private @Nullable Relation relation() {
        skipSpaces();
        char next = index + 1 < text.length() ? text.charAt(index + 1) : END;
        Relation relation = switch (peek()) {
            case '=' -> Relation.EQUAL;
            case '!' -> next == '=' ? Relation.NOT_EQUAL : null;
            case '<' -> next == '=' ? Relation.LESS_OR_EQUAL : Relation.LESS;
            case '>' -> next == '=' ? Relation.GREATER_OR_EQUAL : Relation.GREATER;
            default -> null;
        };
        if (relation != null) {
            index += relation.symbol().length();
        }
        return relation;
    }

    private DiceExpression sum() {
        var left = product();
        while (true) {
            skipSpaces();
            Operator operator;
            if (peek() == '+') {
                operator = Operator.ADD;
            } else if (peek() == '-') {
                operator = Operator.SUBTRACT;
            } else {
                return left;
            }
            index++;
            left = DiceExpression.of(new DiceNode.Arithmetic<>(operator, left, product()));
        }
    }

    private DiceExpression product() {
        var left = unary();
        while (true) {
            skipSpaces();
            Operator operator;
            if (peek() == '*') {
                operator = Operator.MULTIPLY;
            } else if (peek() == '/') {
                operator = Operator.DIVIDE;
            } else {
                return left;
            }
            index++;
            left = DiceExpression.of(new DiceNode.Arithmetic<>(operator, left, unary()));
        }
    }

    private DiceExpression unary() {
        skipSpaces();
        if (peek() == '-') {
            index++;
            return DiceExpression.of(new DiceNode.Negate<>(unary()));
        }
        return dice();
    }

    private DiceExpression dice() {
        var count = peek() == 'd' ? constant(1) : primary();
        if (peek() != 'd') {
            return count;
        }
        index++;
        DiceExpression sides;
        if (peek() == '%') {
            index++;
            sides = constant(PERCENT_SIDES);
        } else {
            sides = primary();
        }
        return DiceExpression.of(new DiceNode.Dice<>(count, sides, keep()));
    }

    private @Nullable Keep keep() {
        char end = index + 1 < text.length() ? text.charAt(index + 1) : END;
        Keep.Mode mode;
        if (peek() == 'k') {
            mode = end == 'h' ? Keep.Mode.KEEP_HIGHEST : end == 'l' ? Keep.Mode.KEEP_LOWEST : null;
        } else if (peek() == 'd') {
            mode = end == 'h' ? Keep.Mode.DROP_HIGHEST : end == 'l' ? Keep.Mode.DROP_LOWEST : null;
        } else {
            return null;
        }
        if (mode == null) {
            throw error("Expected kh, kl, dh or dl");
        }
        index += 2;
        if (!isDigit(peek())) {
            throw error("Expected a number");
        }
        return new Keep(mode, number());
    }

    private DiceExpression primary() {
        char c = peek();
        if (isDigit(c)) {
            return constant(number());
        }
        if (isUpperCase(c) || c == '_') {
            int start = index;
            while (isUpperCase(peek()) || isDigit(peek()) || peek() == '_') {
                index++;
            }
            return DiceExpression.of(new DiceNode.Variable<>(text.substring(start, index)));
        }
        if (c == '(') {
            return group();
        }
        throw error(c == END ? "Unexpected end" : "Unexpected '" + c + "'");
    }

    private DiceExpression group() {
        index++;
        var inner = expression();
        var tags = new ArrayList<Tag<DiceExpression>>();
        skipSpaces();
        while (peek() == ':') {
            index++;
            skipSpaces();
            int start = index;
            if (!isDigit(peek())) {
                while (isUpperCase(peek()) || isLowerCase(peek()) || isDigit(peek()) || peek() == '_') {
                    index++;
                }
            }
            if (index == start) {
                throw error("Expected a tag name");
            }
            var name = text.substring(start, index);
            var relation = relation();
            tags.add(new Tag<>(name, relation == null ? null : new Condition<>(relation, unary())));
            skipSpaces();
        }
        if (peek() != ')') {
            throw error(peek() == END ? "Missing ')'" : "Unexpected '" + peek() + "'");
        }
        index++;
        return DiceExpression.of(new DiceNode.Group<>(inner, tags));
    }

    private int number() {
        int start = index;
        long value = 0;
        while (isDigit(peek())) {
            value = value * RADIX + (peek() - '0');
            if (value > Integer.MAX_VALUE) {
                throw new DiceParseException("Number too large", text, start);
            }
            index++;
        }
        return (int) value;
    }

    private static DiceExpression constant(int value) {
        return DiceExpression.of(new DiceNode.Constant<>(value));
    }

    private char peek() {
        return index < text.length() ? text.charAt(index) : END;
    }

    private void skipSpaces() {
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
    }

    private DiceParseException error(String message) {
        return new DiceParseException(message, text, index);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isUpperCase(char c) {
        return c >= 'A' && c <= 'Z';
    }

    private static boolean isLowerCase(char c) {
        return c >= 'a' && c <= 'z';
    }
}
