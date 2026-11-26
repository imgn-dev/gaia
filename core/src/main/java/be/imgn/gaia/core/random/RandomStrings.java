package be.imgn.gaia.core.random;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.math.BigInteger;
import java.util.random.RandomGenerator;

/**
 * Random strings: identifiers, tokens and codes made of characters taken at random in an alphabet.
 *
 * <pre>{@code
 * var secure = new SecureRandom();
 * RandomStrings.token(128, secure);                               // "pP3X2nq0GkTzvB-fL8_hJw", 128 bits
 * RandomStrings.string(8, RandomStrings.UNAMBIGUOUS, secure);     // "K7WQ4MZX", a code to read aloud
 * RandomStrings.string(6, RandomStrings.DIGITS, secure);          // "042917"
 * }</pre>
 *
 * <p>A string that must not be guessed, such as a session identifier or a password reset token, needs a source of
 * randomness that cannot be predicted: a {@link java.security.SecureRandom}, not a seeded generator.
 *
 * <p>Each character calls {@link RandomGenerator#nextInt(int)} once with the size of the alphabet, and is the character
 * at that index, so that every character of the alphabet has the same chance.
 *
 * @since 0.2.0
 */
public final class RandomStrings {

    /** The ten digits. */
    public static final String DIGITS = "0123456789";

    /** The sixteen hexadecimal digits, in lower case. */
    public static final String HEXADECIMAL = "0123456789abcdef";

    /** The digits and the ASCII letters of both cases: 62 characters. */
    public static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /** The 64 characters that need no escaping in a URL: {@link #ALPHANUMERIC}, the hyphen and the underscore. */
    public static final String URL_SAFE = ALPHANUMERIC + "-_";

    /**
     * Digits and upper-case letters that aren't mistaken for one another when read or typed: 32 characters, without
     * {@code 0}, {@code O}, {@code 1} and {@code I}.
     */
    public static final String UNAMBIGUOUS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private RandomStrings() {}

    /**
     * Returns a random string made of the characters of {@link #URL_SAFE}, long enough to be one among at least
     * {@code 2^bits} equally likely ones.
     *
     * @param bits the least number of bits of randomness: 128 or more for a string that must not be guessed
     * @param random the source of randomness
     * @return a string of {@code length(bits, URL_SAFE)} characters
     * @throws IllegalArgumentException if {@code bits} is negative
     */
    public static String token(int bits, RandomGenerator random) {
        return string(length(bits, URL_SAFE), URL_SAFE, random);
    }

    /**
     * Returns a random string of a given length, made of characters of an alphabet.
     *
     * @param length the number of characters
     * @param alphabet the characters to take from
     * @param random the source of randomness
     * @return the string
     * @throws IllegalArgumentException if {@code length} is negative, or if {@code alphabet} is empty, has a character
     *     twice, which would be taken twice as often, or has a surrogate
     */
    public static String string(int length, String alphabet, RandomGenerator random) {
        requireNonNull(random, "random");
        checkArgument(length >= 0, "length must not be negative: %d", length);
        checkAlphabet(alphabet);
        var chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = alphabet.charAt(random.nextInt(alphabet.length()));
        }
        return new String(chars);
    }

    /**
     * Returns how many characters of an alphabet a random string needs, to be one among at least {@code 2^bits} equally
     * likely ones.
     *
     * @param bits the least number of bits of randomness
     * @param alphabet the characters to take from
     * @return the least length
     * @throws IllegalArgumentException if {@code bits} is negative, or if {@code alphabet} has fewer than two
     *     characters, has a character twice, or has a surrogate
     */
    public static int length(int bits, String alphabet) {
        checkArgument(bits >= 0, "bits must not be negative: %d", bits);
        checkAlphabet(alphabet);
        checkArgument(alphabet.length() > 1, "An alphabet of one character has no randomness");
        // An estimate that rounding may leave one too low or too high, then the exact answer on whole numbers.
        int length = Math.max((int) (bits / (Math.log(alphabet.length()) / Math.log(2))) - 1, 0);
        var size = BigInteger.valueOf(alphabet.length());
        while (size.pow(length).bitLength() <= bits) {
            length++;
        }
        return length;
    }

    private static void checkAlphabet(String alphabet) {
        checkArgument(!alphabet.isEmpty(), "The alphabet is empty");
        for (int i = 0; i < alphabet.length(); i++) {
            char c = alphabet.charAt(i);
            checkArgument(!Character.isSurrogate(c), "The alphabet has a surrogate at index %d", i);
            checkArgument(alphabet.indexOf(c) == i, "The alphabet has '%s' twice", c);
        }
    }
}
