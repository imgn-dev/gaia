package be.imgn.gaia.processing;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.lang.model.SourceVersion;
import org.jspecify.annotations.Nullable;

/**
 * An option of an annotation processor, given to javac as {@code -Aname=value}, with its type and default value.
 *
 * <pre>{@code
 * static final ProcessorOption<Boolean> VERBOSE = ProcessorOption.ofBoolean("mapper.verbose", false);
 *
 * // in the processor
 * protected Set<ProcessorOption<?>> options() {
 *     return Set.of(VERBOSE);
 * }
 *
 * // in a step
 * if (context.option(VERBOSE)) { ... }
 * }</pre>
 *
 * @param <T> the type of the value
 */
public final class ProcessorOption<T> {

    private final String name;
    private final T defaultValue;
    private final String expected;
    private final Function<@Nullable String, Optional<T>> parser;

    private ProcessorOption(
            String name, T defaultValue, String expected, Function<@Nullable String, Optional<T>> parser) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.expected = expected;
        this.parser = parser;
    }

    // Javac only accepts the options of a processor whose names are identifiers separated by dots, and fails the
    // compilation for any other when it initializes the processor.
    private static void checkName(String name) {
        checkArgument(
                Arrays.stream(name.split("\\.", -1)).allMatch(SourceVersion::isIdentifier),
                "Invalid option name %s: it must be Java identifiers separated by dots, as javac requires",
                name);
    }

    /**
     * Returns a text option.
     *
     * @param name the name of the option, as in {@code -Aname=value}: Java identifiers separated by dots
     * @param defaultValue the value when the option is not given
     * @return the option
     * @throws IllegalArgumentException if {@code name} is not Java identifiers separated by dots
     */
    public static ProcessorOption<String> ofString(String name, String defaultValue) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(defaultValue, "defaultValue");
        checkName(name);
        return new ProcessorOption<>(name, defaultValue, "a value", Optional::ofNullable);
    }

    /**
     * Returns a boolean option. Given without a value, as {@code -Aname}, it is {@code true}.
     *
     * @param name the name of the option, as in {@code -Aname=true}: Java identifiers separated by dots
     * @param defaultValue the value when the option is not given
     * @return the option
     * @throws IllegalArgumentException if {@code name} is not Java identifiers separated by dots
     */
    public static ProcessorOption<Boolean> ofBoolean(String name, boolean defaultValue) {
        Objects.requireNonNull(name, "name");
        checkName(name);
        return new ProcessorOption<>(name, defaultValue, "true or false", value -> {
            if (value == null || value.equalsIgnoreCase("true")) {
                return Optional.of(true);
            }
            return value.equalsIgnoreCase("false") ? Optional.of(false) : Optional.empty();
        });
    }

    /**
     * Returns an integer option.
     *
     * @param name the name of the option, as in {@code -Aname=42}: Java identifiers separated by dots
     * @param defaultValue the value when the option is not given
     * @return the option
     * @throws IllegalArgumentException if {@code name} is not Java identifiers separated by dots
     */
    public static ProcessorOption<Integer> ofInt(String name, int defaultValue) {
        Objects.requireNonNull(name, "name");
        checkName(name);
        return new ProcessorOption<>(name, defaultValue, "an integer", value -> {
            if (value == null || !value.strip().matches("[+-]?\\d{1,10}")) {
                return Optional.empty();
            }
            long parsed = Long.parseLong(value.strip());
            return parsed == (int) parsed ? Optional.of((int) parsed) : Optional.empty();
        });
    }

    /**
     * Returns an enum option, whose values are the names of the constants, in any case.
     *
     * @param <E> the enum type
     * @param name the name of the option, as in {@code -Aname=constant}: Java identifiers separated by dots
     * @param type the enum class
     * @param defaultValue the value when the option is not given
     * @return the option
     * @throws IllegalArgumentException if {@code name} is not Java identifiers separated by dots, or {@code type} is
     *     not an enum class
     */
    public static <E extends Enum<E>> ProcessorOption<E> ofEnum(String name, Class<E> type, E defaultValue) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(defaultValue, "defaultValue");
        checkName(name);
        E[] constants = type.getEnumConstants();
        if (constants == null) {
            throw new IllegalArgumentException(type.getName() + " is not an enum class");
        }
        String expected = "one of "
                + Arrays.stream(constants)
                        .map(constant -> constant.name().toLowerCase(Locale.ROOT))
                        .collect(Collectors.joining(", "));
        return new ProcessorOption<>(
                name,
                defaultValue,
                expected,
                value -> Arrays.stream(constants)
                        .filter(constant -> constant.name().equalsIgnoreCase(value == null ? "" : value.strip()))
                        .findFirst());
    }

    /**
     * Returns the name of this option.
     *
     * @return the name, as in {@code -Aname=value}
     */
    public String name() {
        return name;
    }

    /**
     * Returns the value of this option when it is not given.
     *
     * @return the default value
     */
    public T defaultValue() {
        return defaultValue;
    }

    T read(Map<String, @Nullable String> options, Diagnostics diagnostics) {
        if (!options.containsKey(name)) {
            return defaultValue;
        }
        String value = options.get(name);
        Optional<T> parsed = parser.apply(value);
        if (parsed.isEmpty()) {
            diagnostics.error(
                    "Option " + name + " must be " + expected + ", but is " + (value == null ? "empty" : value));
            return defaultValue;
        }
        return parsed.get();
    }

    @Override
    public String toString() {
        return "-A" + name + " (default " + defaultValue + ")";
    }
}
