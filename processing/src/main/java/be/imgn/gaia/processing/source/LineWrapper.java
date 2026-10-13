package be.imgn.gaia.processing.source;

/**
 * Writes text, breaking lines at wrap points when the text that follows one would go past the column limit. The text
 * after a wrap point is held until it is known whether it fits: up to the next wrap point, line break or the end.
 */
final class LineWrapper {

    private final StringBuilder out;
    private final int columnLimit;
    private final StringBuilder held = new StringBuilder();
    private int column;
    private boolean pending;
    private boolean space;
    private String nextLineIndentation = "";

    LineWrapper(StringBuilder out, int columnLimit) {
        this.out = out;
        this.columnLimit = columnLimit;
    }

    void append(CharSequence text) {
        if (!pending) {
            write(text);
            return;
        }
        int newLine = text.toString().indexOf('\n');
        int head = newLine < 0 ? text.length() : newLine;
        if (column + width() + held.length() + head > columnLimit) {
            resolve(true);
            write(text);
        } else if (newLine < 0) {
            held.append(text);
        } else {
            resolve(false);
            write(text);
        }
    }

    // A space, or a line break followed by the indentation, whichever keeps the next text within the limit.
    void wrappingSpace(String indentation) {
        point(true, indentation);
    }

    // Nothing, or a line break followed by the indentation, whichever keeps the next text within the limit. Nothing at
    // the start of a line, where breaking would not help.
    void zeroWidthSpace(String indentation) {
        if (column > 0 || pending) {
            point(false, indentation);
        }
    }

    void close() {
        if (pending) {
            resolve(false);
        }
    }

    private void point(boolean isSpace, String indentation) {
        if (pending) {
            resolve(false);
        }
        pending = true;
        space = isSpace;
        nextLineIndentation = indentation;
    }

    private int width() {
        return space ? 1 : 0;
    }

    private void resolve(boolean wrap) {
        if (wrap) {
            out.append('\n').append(nextLineIndentation);
            column = nextLineIndentation.length();
        } else if (space) {
            out.append(' ');
            column++;
        }
        out.append(held);
        column += held.length();
        held.setLength(0);
        pending = false;
    }

    private void write(CharSequence text) {
        out.append(text);
        int lastNewLine = text.toString().lastIndexOf('\n');
        column = lastNewLine < 0 ? column + text.length() : text.length() - lastNewLine - 1;
    }
}
