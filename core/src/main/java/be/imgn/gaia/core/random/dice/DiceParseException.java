package be.imgn.gaia.core.random.dice;

/**
 * Thrown when a text isn't a dice expression.
 *
 * @since 0.2.0
 * @see DiceExpression#parse(String)
 */
public final class DiceParseException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final int position;

    DiceParseException(String message, String text, int position) {
        super(message + " at index " + position + ": " + text);
        this.position = position;
    }

    /**
     * Returns the index in the text, from zero, at which parsing failed.
     *
     * @return the index in the text, from zero, at which parsing failed
     */
    public int position() {
        return position;
    }
}
