package be.imgn.gaia.core.random;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.random.RandomGenerator;

/**
 * A bag to draw elements from at random, without putting them back: a deck of cards, or a list of options that must
 * each come up once before any comes up again.
 *
 * <pre>{@code
 * var deck = RandomBag.of(cards);
 * var hand = deck.draw(5, random);      // five different cards
 * deck.size();                          // 47
 * deck.refill();                        // the hand is back in the deck
 *
 * var track = RandomBag.of(playlist).refilling();
 * track.next(random);                   // every track once, in a random order, then all of them again
 * }</pre>
 *
 * <p>An element that is in the bag several times may be drawn as many times. A bag remembers the elements that were
 * drawn, so that {@link #refill()} puts them back at no cost.
 *
 * <p>Each draw calls {@link RandomGenerator#nextInt(int) nextInt(size)} once, even when one element is left. Which
 * element a number draws depends on the previous draws.
 *
 * <p>Bags are mutable, and aren't safe to use from several threads at once.
 *
 * @param <T> the type of the elements
 * @since 0.2.0
 */
public final class RandomBag<T> {

    // The elements in the bag, then the drawn ones.
    private Object[] elements;
    private int size;
    private int total;

    private RandomBag(Object[] elements) {
        this.elements = elements;
        this.size = elements.length;
        this.total = elements.length;
    }

    /**
     * Returns a bag of the elements of a collection.
     *
     * @param <T> the type of the elements
     * @param elements the elements to put in the bag
     * @return a new bag
     */
    public static <T> RandomBag<T> of(Collection<? extends T> elements) {
        return new RandomBag<>(List.copyOf(elements).toArray());
    }

    /**
     * Returns how many elements are in this bag.
     *
     * @return the number of elements that may still be drawn
     */
    public int size() {
        return size;
    }

    /**
     * Returns whether this bag has no element left to draw.
     *
     * @return whether this bag is empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Draws an element, which is no longer in this bag afterwards.
     *
     * @param random the source of randomness
     * @return the drawn element
     * @throws NoSuchElementException if this bag is empty
     */
    public T draw(RandomGenerator random) {
        requireNonNull(random, "random");
        if (size == 0) {
            throw new NoSuchElementException("The bag is empty");
        }
        int index = random.nextInt(size);
        @SuppressWarnings("unchecked")
        T drawn = (T) elements[index];
        size--;
        elements[index] = elements[size];
        elements[size] = drawn;
        return drawn;
    }

    /**
     * Draws several elements, which are no longer in this bag afterwards.
     *
     * @param count how many elements to draw
     * @param random the source of randomness
     * @return the drawn elements, in the order they were drawn
     * @throws IllegalArgumentException if {@code count} is negative or more than {@link #size()}
     */
    public List<T> draw(int count, RandomGenerator random) {
        requireNonNull(random, "random");
        checkArgument(count >= 0 && count <= size, "Cannot draw %d of %d elements", count, size);
        var drawn = new ArrayList<T>(count);
        for (int i = 0; i < count; i++) {
            drawn.add(draw(random));
        }
        return List.copyOf(drawn);
    }

    /**
     * Adds an element to this bag, which {@link #refill()} puts back like the other ones.
     *
     * @param element the element to add
     */
    public void add(T element) {
        requireNonNull(element, "element");
        if (total == elements.length) {
            elements = Arrays.copyOf(elements, Math.max(2 * total, 1));
        }
        // The first drawn element makes room at the end of the ones in the bag.
        elements[total] = elements[size];
        elements[size] = element;
        size++;
        total++;
    }

    /** Puts every drawn element back in this bag. */
    public void refill() {
        size = total;
    }

    /**
     * Returns a selector that draws from this bag, and refills it first when it's empty: every element is selected once
     * before any is selected again. The selector and this bag share their elements.
     *
     * @return a selector that never runs out
     * @throws IllegalStateException if this bag has no element at all, drawn or not
     */
    public RandomSelector<T> refilling() {
        if (total == 0) {
            throw new IllegalStateException("The bag has no element");
        }
        return random -> {
            if (size == 0) {
                refill();
            }
            return draw(random);
        };
    }
}
