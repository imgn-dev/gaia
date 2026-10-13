package be.imgn.gaia.processing.source;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

/**
 * A fragment of code: statements, an expression, or Javadoc text, with placeholders for the types and names it refers
 * to.
 *
 * <pre>{@code
 * CodeBlock block = CodeBlock.newBuilder()
 *         .addStatement("$T<$T> names = new $T<>()", List.class, String.class, ArrayList.class)
 *         .beginControlFlow("for ($T user : users)", User.class)
 *         .addStatement("names.add(user.name())")
 *         .endControlFlow()
 *         .build();
 * }</pre>
 *
 * <p>The placeholders are {@code $T} for a type ({@link TypeName}, {@link Class}, {@link TypeMirror} or
 * {@link TypeElement}), imported as needed; {@code $N} for a name (a {@code String}, or a {@link Parameter},
 * {@link Field}, {@link Method} or {@link Type}); {@code $S} for a string literal, escaped, or {@code null}; {@code $L}
 * for anything written as is, such as a number, another code block, an annotation or an anonymous class; {@code $$} for
 * a dollar sign; {@code $>} and {@code $<} to indent and unindent the lines that follow; {@code $[} and {@code $]} to
 * start and end a statement, whose lines after the first are indented twice more; {@code $W} for a space, and
 * {@code $Z} for nothing, where the line breaks if what follows would go past column 100. Arguments are taken in order,
 * or by position with {@code $1T}, {@code $2N} and so on, but not both in one format; or by name with {@code $type:T},
 * from a map given to {@link Builder#addNamed(String, Map)}.
 *
 * <p>Code blocks are values: two blocks with the same text and arguments are equal.
 */
public final class CodeBlock {

    private static final CodeBlock EMPTY = new CodeBlock(List.of());

    private final List<Part> parts;

    private CodeBlock(List<Part> parts) {
        this.parts = List.copyOf(parts);
    }

    /**
     * Returns a code block.
     *
     * @param format the code, with placeholders
     * @param args the arguments of the placeholders
     * @return the code block
     * @throws IllegalArgumentException if the format and the arguments do not match
     */
    public static CodeBlock of(String format, @Nullable Object... args) {
        return newBuilder().add(format, args).build();
    }

    /**
     * Returns the empty code block.
     *
     * @return the empty code block
     */
    public static CodeBlock empty() {
        return EMPTY;
    }

    /**
     * Joins code blocks with a separator, such as {@code ", "}.
     *
     * @param blocks the code blocks
     * @param separator the text between two blocks
     * @return the joined code block
     */
    public static CodeBlock join(Iterable<CodeBlock> blocks, String separator) {
        return join(blocks, separator, "", "");
    }

    private static CodeBlock join(Iterable<CodeBlock> blocks, String separator, String prefix, String suffix) {
        Builder builder = newBuilder();
        builder.text(prefix);
        Iterator<CodeBlock> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            builder.add(iterator.next());
            if (iterator.hasNext()) {
                builder.text(separator);
            }
        }
        return builder.text(suffix).build();
    }

    /**
     * Returns a collector that joins code blocks with a separator, such as {@code ", "}.
     *
     * @param separator the text between two blocks, written as is
     * @return the collector
     */
    public static Collector<CodeBlock, ?, CodeBlock> joining(String separator) {
        return joining(separator, "", "");
    }

    /**
     * Returns a collector that joins code blocks with a separator, between a prefix and a suffix, such as {@code "("}
     * and {@code ")"}. The prefix and the suffix are written even when there is no block.
     *
     * @param separator the text between two blocks, written as is
     * @param prefix the text before the first block, written as is
     * @param suffix the text after the last block, written as is
     * @return the collector
     */
    public static Collector<CodeBlock, ?, CodeBlock> joining(String separator, String prefix, String suffix) {
        return Collector.<CodeBlock, List<CodeBlock>, CodeBlock>of(
                ArrayList::new,
                List::add,
                (left, right) -> {
                    left.addAll(right);
                    return left;
                },
                blocks -> join(blocks, separator, prefix, suffix));
    }

    /**
     * Returns a builder of code blocks.
     *
     * @return a new, empty builder
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Returns whether this code block is empty.
     *
     * @return {@code true} if it has no code
     */
    public boolean isEmpty() {
        return parts.isEmpty();
    }

    /**
     * Returns a builder that starts with this code block.
     *
     * @return a new builder
     */
    public Builder toBuilder() {
        Builder builder = newBuilder();
        builder.parts.addAll(parts);
        return builder;
    }

    List<Part> parts() {
        return parts;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof CodeBlock block && parts.equals(block.parts);
    }

    @Override
    public int hashCode() {
        return parts.hashCode();
    }

    @Override
    public String toString() {
        return CodeWriter.standalone(this);
    }

    /** A builder of {@link CodeBlock}s. */
    public static final class Builder {

        private final List<Part> parts = new ArrayList<>();

        private Builder() {}

        /**
         * Adds code.
         *
         * @param format the code, with placeholders
         * @param args the arguments of the placeholders
         * @return this builder
         * @throws IllegalArgumentException if the format and the arguments do not match
         */
        public Builder add(String format, @Nullable Object... args) {
            parts.addAll(new Parser(format, args.clone(), null).parse());
            return this;
        }

        /**
         * Adds a code block.
         *
         * @param block the code block
         * @return this builder
         */
        public Builder add(CodeBlock block) {
            parts.addAll(block.parts);
            return this;
        }

        /**
         * Adds code whose placeholders name their arguments, as in {@code $type:T} or {@code $count:L}. Names start
         * with a lower-case letter, followed by letters, digits and underscores. Arguments the format does not use are
         * ignored.
         *
         * <pre>{@code
         * builder.addNamed("$list:T<$item:T> $name:N = new $array:T<>()", Map.of(
         *         "list", List.class, "item", String.class, "name", "names", "array", ArrayList.class));
         * }</pre>
         *
         * @param format the code, with named placeholders
         * @param arguments the arguments, by name
         * @return this builder
         * @throws IllegalArgumentException if a name is not valid, a placeholder has no argument, or the format has a
         *     placeholder without a name
         */
        public Builder addNamed(String format, Map<String, ?> arguments) {
            for (String name : arguments.keySet()) {
                if (!isArgumentName(name)) {
                    throw new IllegalArgumentException(
                            "An argument name starts with a lower-case letter, followed by letters, digits and"
                                    + " underscores: " + name);
                }
            }
            parts.addAll(new Parser(format, new Object[0], arguments).parse());
            return this;
        }

        /**
         * Adds a statement: the code, a semicolon and a new line.
         *
         * @param format the statement, without its semicolon, with placeholders
         * @param args the arguments of the placeholders
         * @return this builder
         * @throws IllegalArgumentException if the format and the arguments do not match
         */
        public Builder addStatement(String format, @Nullable Object... args) {
            parts.add(new Statement(true));
            add(format, args);
            parts.add(new Text(";\n"));
            parts.add(new Statement(false));
            return this;
        }

        /**
         * Adds a statement: the code block, a semicolon and a new line.
         *
         * @param statement the statement, without its semicolon
         * @return this builder
         */
        public Builder addStatement(CodeBlock statement) {
            parts.add(new Statement(true));
            add(statement);
            parts.add(new Text(";\n"));
            parts.add(new Statement(false));
            return this;
        }

        /**
         * Adds a line comment.
         *
         * @param comment the comment, without {@code //}
         * @return this builder
         */
        public Builder addComment(String comment) {
            parts.add(new Text("// " + comment + "\n"));
            return this;
        }

        /**
         * Opens a block, such as {@code if (ready)} or {@code for (String name : names)}, and indents what follows.
         *
         * @param controlFlow the code before the opening brace, with placeholders
         * @param args the arguments of the placeholders
         * @return this builder
         * @throws IllegalArgumentException if the format and the arguments do not match
         */
        public Builder beginControlFlow(String controlFlow, @Nullable Object... args) {
            return beginControlFlow(CodeBlock.of(controlFlow, args));
        }

        /**
         * Opens a block, such as {@code if (ready)} or {@code for (String name : names)}, and indents what follows.
         *
         * @param controlFlow the code before the opening brace
         * @return this builder
         */
        public Builder beginControlFlow(CodeBlock controlFlow) {
            add(controlFlow);
            parts.add(new Text(" {\n"));
            return indent();
        }

        /**
         * Closes a block and opens the next one, such as {@code else if (other)} or {@code catch (IOException e)}.
         *
         * @param controlFlow the code between the braces, with placeholders
         * @param args the arguments of the placeholders
         * @return this builder
         * @throws IllegalArgumentException if the format and the arguments do not match
         */
        public Builder nextControlFlow(String controlFlow, @Nullable Object... args) {
            return nextControlFlow(CodeBlock.of(controlFlow, args));
        }

        /**
         * Closes a block and opens the next one, such as {@code else if (other)} or {@code catch (IOException e)}.
         *
         * @param controlFlow the code between the braces
         * @return this builder
         */
        public Builder nextControlFlow(CodeBlock controlFlow) {
            unindent();
            parts.add(new Text("} "));
            add(controlFlow);
            parts.add(new Text(" {\n"));
            return indent();
        }

        /**
         * Closes a block.
         *
         * @return this builder
         */
        public Builder endControlFlow() {
            unindent();
            parts.add(new Text("}\n"));
            return this;
        }

        /**
         * Closes a block with code after its closing brace, such as {@code while (more)} for a {@code do} loop.
         *
         * @param controlFlow the code after the closing brace, without its semicolon, with placeholders
         * @param args the arguments of the placeholders
         * @return this builder
         * @throws IllegalArgumentException if the format and the arguments do not match
         */
        public Builder endControlFlow(String controlFlow, @Nullable Object... args) {
            return endControlFlow(CodeBlock.of(controlFlow, args));
        }

        /**
         * Closes a block with code after its closing brace, such as {@code while (more)} for a {@code do} loop.
         *
         * @param controlFlow the code after the closing brace, without its semicolon
         * @return this builder
         */
        public Builder endControlFlow(CodeBlock controlFlow) {
            unindent();
            parts.add(new Text("} "));
            add(controlFlow);
            parts.add(new Text(";\n"));
            return this;
        }

        /**
         * Indents the lines that follow.
         *
         * @return this builder
         */
        public Builder indent() {
            parts.add(new Indent(1));
            return this;
        }

        /**
         * Unindents the lines that follow.
         *
         * @return this builder
         */
        public Builder unindent() {
            parts.add(new Indent(-1));
            return this;
        }

        /**
         * Removes everything added so far.
         *
         * @return this builder, empty
         */
        public Builder clear() {
            parts.clear();
            return this;
        }

        /**
         * Returns whether nothing was added yet.
         *
         * @return {@code true} if the code block would be empty
         */
        public boolean isEmpty() {
            return parts.isEmpty();
        }

        /**
         * Builds the code block.
         *
         * @return the code block
         */
        public CodeBlock build() {
            return parts.isEmpty() ? EMPTY : new CodeBlock(parts);
        }

        private Builder text(String text) {
            if (!text.isEmpty()) {
                parts.add(new Text(text));
            }
            return this;
        }

        private static boolean isArgumentName(String name) {
            if (name.isEmpty() || !Character.isLowerCase(name.charAt(0))) {
                return false;
            }
            return name.chars().allMatch(Parser::isArgumentNamePart);
        }
    }

    /**
     * A part of a code block: text, an argument, a change of indentation, the start or end of a statement, or a place
     * where the line may break.
     */
    sealed interface Part permits Text, Argument, Indent, Statement, Wrap {}

    /**
     * Text written as is.
     *
     * @param text the text
     */
    record Text(String text) implements Part {}

    /**
     * The argument of a placeholder.
     *
     * @param kind the kind of placeholder: {@code L}, {@code N}, {@code S} or {@code T}
     * @param value the argument, already converted: a {@link TypeName} for {@code T}, a {@code String} for {@code N}
     */
    record Argument(char kind, @Nullable Object value) implements Part {}

    /**
     * A change of indentation.
     *
     * @param delta {@code 1} to indent, {@code -1} to unindent
     */
    record Indent(int delta) implements Part {}

    /**
     * The start or the end of a statement, whose lines after the first are indented twice more.
     *
     * @param start {@code true} at the start, {@code false} at the end
     */
    record Statement(boolean start) implements Part {}

    /**
     * A place where the line may break, if what follows would go past the column limit.
     *
     * @param space {@code true} for a space when the line does not break, {@code false} for nothing
     */
    record Wrap(boolean space) implements Part {}

    /** Splits a format into parts, matching placeholders to arguments, by position or by name. */
    private static final class Parser {

        private final String format;
        private final @Nullable Object[] args;
        private final @Nullable Map<String, ?> named;
        private final boolean[] used;
        private final List<Part> parts = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();
        private int relative;
        private boolean indexed;

        Parser(String format, @Nullable Object[] args, @Nullable Map<String, ?> named) {
            this.format = format;
            this.args = args;
            this.named = named;
            this.used = new boolean[args.length];
        }

        static boolean isArgumentNamePart(int c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
        }

        List<Part> parse() {
            int i = 0;
            while (i < format.length()) {
                char c = format.charAt(i);
                if (c == '$') {
                    i = placeholder(i);
                } else {
                    text.append(c);
                    i++;
                }
            }
            flush();
            if (indexed && relative > 0) {
                throw new IllegalArgumentException("Cannot mix $1T-style and $T-style placeholders in: " + format);
            }
            for (int index = 0; index < used.length; index++) {
                if (!used[index]) {
                    throw new IllegalArgumentException(
                            "Argument " + (index + 1) + " of " + used.length + " is not used in: " + format);
                }
            }
            return parts;
        }

        private int placeholder(int dollar) {
            if (dollar + 1 >= format.length()) {
                throw new IllegalArgumentException("Dangling $ at the end of: " + format);
            }
            if (control(format.charAt(dollar + 1))) {
                return dollar + 2;
            }
            return named == null ? positional(dollar) : named(dollar, named);
        }

        private boolean control(char next) {
            switch (next) {
                case '$' -> text.append('$');
                case '>', '<' -> {
                    flush();
                    parts.add(new Indent(next == '>' ? 1 : -1));
                }
                case '[', ']' -> {
                    flush();
                    parts.add(new Statement(next == '['));
                }
                case 'W', 'Z' -> {
                    flush();
                    parts.add(new Wrap(next == 'W'));
                }
                default -> {
                    return false;
                }
            }
            return true;
        }

        private int positional(int dollar) {
            int end = dollar + 1;
            while (end < format.length() && Character.isDigit(format.charAt(end))) {
                end++;
            }
            if (end >= format.length()) {
                throw new IllegalArgumentException("Dangling placeholder at the end of: " + format);
            }
            int index = index(format.substring(dollar + 1, end));
            flush();
            parts.add(new Argument(format.charAt(end), convert(format.charAt(end), args[index])));
            return end + 1;
        }

        private int named(int dollar, Map<String, ?> arguments) {
            int end = dollar + 1;
            while (end < format.length() && isArgumentNamePart(format.charAt(end))) {
                end++;
            }
            String name = format.substring(dollar + 1, end);
            if (name.isEmpty() || end + 1 >= format.length() || format.charAt(end) != ':') {
                throw new IllegalArgumentException(
                        "Expected a named placeholder such as $name:T at " + dollar + " in: " + format);
            }
            if (!arguments.containsKey(name)) {
                throw new IllegalArgumentException("Missing named argument for $" + name + " in: " + format);
            }
            char kind = format.charAt(end + 1);
            flush();
            parts.add(new Argument(kind, convert(kind, arguments.get(name))));
            return end + 2;
        }

        private int index(String digits) {
            int index;
            if (digits.isEmpty()) {
                index = relative++;
            } else {
                indexed = true;
                index = Integer.parseInt(digits) - 1;
            }
            if (index < 0 || index >= args.length) {
                throw new IllegalArgumentException("No argument " + (index + 1) + " for: " + format + " (" + args.length
                        + " given: " + Arrays.toString(args) + ")");
            }
            used[index] = true;
            return index;
        }

        private @Nullable Object convert(char kind, @Nullable Object value) {
            return switch (kind) {
                case 'T' -> type(value);
                case 'N' -> name(value);
                case 'S' -> value == null ? null : value.toString();
                case 'L' -> value;
                default -> throw new IllegalArgumentException("Unknown placeholder $" + kind + " in: " + format);
            };
        }

        private TypeName type(@Nullable Object value) {
            return switch (value) {
                case TypeName name -> name;
                case Class<?> type -> TypeName.of(type);
                case TypeMirror type -> TypeName.of(type);
                case TypeElement type -> ClassName.of(type);
                case null, default ->
                    throw new IllegalArgumentException("$T expects a type, not " + value + " in: " + format);
            };
        }

        private String name(@Nullable Object value) {
            return switch (value) {
                case CharSequence name -> name.toString();
                case Parameter parameter -> parameter.name();
                case Field field -> field.name();
                case Method method -> method.name();
                case Type type when type.name() != null -> type.name();
                case null, default ->
                    throw new IllegalArgumentException("$N expects a name, not " + value + " in: " + format);
            };
        }

        private void flush() {
            if (!text.isEmpty()) {
                parts.add(new Text(text.toString()));
                text.setLength(0);
            }
        }
    }
}
