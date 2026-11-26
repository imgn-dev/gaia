package be.imgn.gaia.core.random;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static be.imgn.gaia.core.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.collect.HashMultiset;
import be.imgn.gaia.core.collect.Multiset;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;

/**
 * A table to draw from at random: each draw gives a multiset of elements, made of the entries that are always given and
 * of entries selected by weight, each of which is an element in some quantity, another table, or nothing.
 *
 * <pre>{@code
 * var gems = RandomTable.<Item>builder()
 *         .add(3, ruby)
 *         .add(1, diamond)
 *         .build();
 * var chest = RandomTable.<Item>builder()
 *         .always(key)
 *         .add(60, gold, random -> random.nextInt(10, 101))
 *         .addTable(30, gems)
 *         .addNothing(10)
 *         .draws(random -> random.nextInt(1, 4))
 *         .build();
 *
 * Multiset<Item> drawn = chest.draw(random);    // such as [key, gold x 57, ruby]
 * }</pre>
 *
 * <p>A quantity or a number of draws is either fixed or a {@link RandomSelector} of integers, which any function of a
 * {@link RandomGenerator} is.
 *
 * <p>Tables are immutable. A table only refers to tables that were built before it, so tables never refer to each other
 * in a circle.
 *
 * @param <T> the type of the elements
 * @since 0.2.0
 * @see RandomSelector
 */
public final class RandomTable<T> {

    private static final RandomSelector<Integer> ONCE = RandomSelector.constant(1);

    private final List<Entry<T>> always;
    private final @Nullable RandomSelector<Entry<T>> selector;
    private final RandomSelector<Integer> draws;

    private RandomTable(
            List<Entry<T>> always, @Nullable RandomSelector<Entry<T>> selector, RandomSelector<Integer> draws) {
        this.always = always;
        this.selector = selector;
        this.draws = draws;
    }

    /**
     * Returns a builder of tables.
     *
     * @param <T> the type of the elements
     * @return a new builder
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Draws from this table: first the entries that are always given, in the order they were added, then as many
     * weighted entries as the number of draws, which is selected before them.
     *
     * @param random the source of randomness
     * @return the drawn elements with their quantities, in a new multiset that the caller may modify
     * @throws IllegalStateException if a selector gives a negative quantity or number of draws
     * @throws IllegalArgumentException if an element is drawn more than {@link Integer#MAX_VALUE} times
     */
    public Multiset<T> draw(RandomGenerator random) {
        requireNonNull(random, "random");
        Multiset<T> drawn = HashMultiset.create();
        drawInto(drawn, random);
        return drawn;
    }

    private void drawInto(Multiset<? super T> drawn, RandomGenerator random) {
        for (var entry : always) {
            add(entry, drawn, random);
        }
        if (selector != null) {
            int count = count(draws, random, "number of draws");
            for (int i = 0; i < count; i++) {
                add(selector.next(random), drawn, random);
            }
        }
    }

    private static <T> void add(Entry<T> entry, Multiset<? super T> drawn, RandomGenerator random) {
        switch (entry) {
            case Entry.Element<T> element ->
                drawn.add(element.element(), count(element.quantity(), random, "quantity"));
            case Entry.Table<T> table -> table.table().drawInto(drawn, random);
            case Entry.Nothing<T> nothing -> {
                // Gives nothing.
            }
        }
    }

    private static int count(RandomSelector<Integer> selector, RandomGenerator random, String name) {
        int count = selector.next(random);
        checkState(count >= 0, "Negative %s: %d", name, count);
        return count;
    }

    /**
     * Builds a {@link RandomTable}.
     *
     * @param <T> the type of the elements
     * @since 0.2.0
     */
    public static final class Builder<T> {

        private final List<Entry<T>> always = new ArrayList<>();
        private final List<Entry<T>> entries = new ArrayList<>();
        private final List<Double> weights = new ArrayList<>();
        private RandomSelector<Integer> draws = ONCE;

        private Builder() {}

        /**
         * Adds an element that every draw gives once.
         *
         * @param element the element
         * @return this builder
         */
        public Builder<T> always(T element) {
            return always(element, ONCE);
        }

        /**
         * Adds an element that every draw gives in a fixed quantity.
         *
         * @param element the element
         * @param quantity how many times the element is given
         * @return this builder
         * @throws IllegalArgumentException if {@code quantity} is negative
         */
        public Builder<T> always(T element, int quantity) {
            return always(element, fixed(quantity, "quantity"));
        }

        /**
         * Adds an element that every draw gives in a quantity selected for each draw.
         *
         * @param element the element
         * @param quantity the selector of how many times the element is given
         * @return this builder
         */
        public Builder<T> always(T element, RandomSelector<Integer> quantity) {
            always.add(new Entry.Element<>(requireNonNull(element, "element"), requireNonNull(quantity, "quantity")));
            return this;
        }

        /**
         * Adds a table that every draw draws from once.
         *
         * @param table the table to draw from
         * @return this builder
         */
        public Builder<T> alwaysTable(RandomTable<? extends T> table) {
            always.add(new Entry.Table<>(requireNonNull(table, "table")));
            return this;
        }

        /**
         * Adds a weighted entry that gives an element once.
         *
         * @param weight the weight of the entry
         * @param element the element
         * @return this builder
         * @throws IllegalArgumentException if {@code weight} is negative, infinite or not a number
         */
        public Builder<T> add(double weight, T element) {
            return add(weight, element, ONCE);
        }

        /**
         * Adds a weighted entry that gives an element in a fixed quantity.
         *
         * @param weight the weight of the entry
         * @param element the element
         * @param quantity how many times the element is given
         * @return this builder
         * @throws IllegalArgumentException if {@code weight} is negative, infinite or not a number, or if
         *     {@code quantity} is negative
         */
        public Builder<T> add(double weight, T element, int quantity) {
            return add(weight, element, fixed(quantity, "quantity"));
        }

        /**
         * Adds a weighted entry that gives an element in a quantity selected each time the entry is.
         *
         * @param weight the weight of the entry
         * @param element the element
         * @param quantity the selector of how many times the element is given
         * @return this builder
         * @throws IllegalArgumentException if {@code weight} is negative, infinite or not a number
         */
        public Builder<T> add(double weight, T element, RandomSelector<Integer> quantity) {
            return addEntry(
                    weight,
                    new Entry.Element<>(requireNonNull(element, "element"), requireNonNull(quantity, "quantity")));
        }

        /**
         * Adds a weighted entry that draws from another table.
         *
         * @param weight the weight of the entry
         * @param table the table to draw from
         * @return this builder
         * @throws IllegalArgumentException if {@code weight} is negative, infinite or not a number
         */
        public Builder<T> addTable(double weight, RandomTable<? extends T> table) {
            return addEntry(weight, new Entry.Table<>(requireNonNull(table, "table")));
        }

        /**
         * Adds a weighted entry that gives nothing.
         *
         * @param weight the weight of the entry
         * @return this builder
         * @throws IllegalArgumentException if {@code weight} is negative, infinite or not a number
         */
        public Builder<T> addNothing(double weight) {
            return addEntry(weight, new Entry.Nothing<>());
        }

        /**
         * Sets how many weighted entries a draw selects, which is one by default. An entry may be selected several
         * times.
         *
         * @param count the number of entries
         * @return this builder
         * @throws IllegalArgumentException if {@code count} is negative
         */
        public Builder<T> draws(int count) {
            return draws(fixed(count, "count"));
        }

        /**
         * Sets how many weighted entries a draw selects, with a number selected for each draw.
         *
         * @param count the selector of the number of entries
         * @return this builder
         */
        public Builder<T> draws(RandomSelector<Integer> count) {
            draws = requireNonNull(count, "count");
            return this;
        }

        /**
         * Builds the table. Entries added to this builder afterwards don't change it.
         *
         * @return the table
         * @throws IllegalArgumentException if weighted entries were added but none has a positive weight
         */
        public RandomTable<T> build() {
            var selector = entries.isEmpty()
                    ? null
                    : Selectors.weighted(
                            List.copyOf(entries),
                            weights.stream().mapToDouble(Double::doubleValue).toArray());
            return new RandomTable<>(List.copyOf(always), selector, draws);
        }

        private Builder<T> addEntry(double weight, Entry<T> entry) {
            checkArgument(weight >= 0 && Double.isFinite(weight), "Not a weight: %s", weight);
            entries.add(entry);
            weights.add(weight);
            return this;
        }

        private static RandomSelector<Integer> fixed(int value, String name) {
            checkArgument(value >= 0, "%s must not be negative: %d", name, value);
            return value == 1 ? ONCE : RandomSelector.constant(value);
        }
    }

    /** What a table gives when an entry is drawn. */
    private sealed interface Entry<T> {

        record Element<T>(T element, RandomSelector<Integer> quantity) implements Entry<T> {}

        record Table<T>(RandomTable<? extends T> table) implements Entry<T> {}

        record Nothing<T>() implements Entry<T> {}
    }
}
