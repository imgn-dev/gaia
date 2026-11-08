package be.imgn.gaia.core.io;

import static be.imgn.gaia.core.io.TestStreams.randomBytes;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class BytesTest {

    private static Bytes bytes(int... values) {
        var bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return Bytes.of(bytes);
    }

    @Test
    void copiesTheArraysItIsGiven() {
        var array = new byte[] {1, 2, 3};
        var bytes = Bytes.of(array);
        var part = Bytes.copyOf(array, 1, 2);

        array[1] = 9;

        assertThat(bytes.toByteArray()).containsExactly(1, 2, 3);
        assertThat(part.toByteArray()).containsExactly(2, 3);
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> Bytes.copyOf(array, 2, 2));
    }

    @Test
    void copiesTheArraysItHandsOut() {
        var bytes = bytes(1, 2, 3);

        bytes.toByteArray()[0] = 9;

        assertThat(bytes.get(0)).isEqualTo((byte) 1);
        assertThat(bytes.toByteArray()).isNotSameAs(bytes.toByteArray());
    }

    @Test
    void copiesTheRemainingBytesOfABuffer() {
        var buffer = ByteBuffer.wrap(new byte[] {1, 2, 3, 4});
        buffer.position(1).limit(3);

        assertThat(Bytes.copyOf(buffer)).isEqualTo(bytes(2, 3));
        assertThat(buffer.position()).isOne();
    }

    @Test
    void hasNoBytesWhenEmpty() {
        assertThat(Bytes.empty().size()).isZero();
        assertThat(Bytes.empty().isEmpty()).isTrue();
        assertThat(Bytes.of()).isEqualTo(Bytes.empty());
        assertThat(bytes(1).isEmpty()).isFalse();
    }

    @Test
    void encodesAndDecodesText() {
        var text = "héllo 😀";

        assertThat(Bytes.encode(text).toByteArray()).isEqualTo(text.getBytes(UTF_8));
        assertThat(Bytes.encode(CharBuffer.wrap(text), ISO_8859_1).toByteArray())
                .isEqualTo(text.getBytes(ISO_8859_1));
        assertThat(Bytes.encode(text).decode()).isEqualTo(text);
        assertThat(Bytes.encode("héllo", ISO_8859_1).decode(ISO_8859_1)).isEqualTo("héllo");
        assertThat(bytes('a', 0xFF).decode()).isEqualTo("a�");
    }

    @Test
    void convertsToAndFromHex() {
        var random = randomBytes(1, 1_000);
        var bytes = Bytes.of(random);

        assertThat(bytes.toHex()).isEqualTo(HexFormat.of().formatHex(random));
        assertThat(Bytes.fromHex(bytes.toHex())).isEqualTo(bytes);
        assertThat(Bytes.fromHex("CAfeBA")).isEqualTo(bytes(0xCA, 0xFE, 0xBA));
        assertThat(bytes.slice(1, 3).toHex()).isEqualTo(HexFormat.of().formatHex(random, 1, 3));
        assertThatIllegalArgumentException().isThrownBy(() -> Bytes.fromHex("abc"));
        assertThatIllegalArgumentException().isThrownBy(() -> Bytes.fromHex("zz"));
    }

    @Test
    void convertsToAndFromBase64() {
        var random = randomBytes(2, 1_000);
        var bytes = Bytes.of(random);

        assertThat(bytes.toBase64()).isEqualTo(Base64.getEncoder().encodeToString(random));
        assertThat(bytes.toBase64Url()).isEqualTo(Base64.getUrlEncoder().encodeToString(random));
        assertThat(Bytes.fromBase64(bytes.toBase64())).isEqualTo(bytes);
        assertThat(Bytes.fromBase64Url(bytes.toBase64Url())).isEqualTo(bytes);
        assertThat(bytes.slice(1, 3).toBase64())
                .isEqualTo(Base64.getEncoder().encodeToString(Arrays.copyOfRange(random, 1, 3)));
        assertThat(Bytes.fromBase64("AQ")).isEqualTo(bytes(1));
        assertThat(Bytes.fromBase64("AQ==")).isEqualTo(bytes(1));
        assertThat(Bytes.fromBase64Url("-_8")).isEqualTo(bytes(0xFB, 0xFF));
        assertThatIllegalArgumentException().isThrownBy(() -> Bytes.fromBase64("-_8"));
        assertThatIllegalArgumentException().isThrownBy(() -> Bytes.fromBase64Url("+/8"));
    }

    @Test
    void concatenates() {
        assertThat(Bytes.concat(bytes(1, 2), Bytes.empty(), bytes(1, 2, 3).slice(2, 3)))
                .isEqualTo(bytes(1, 2, 3));
        assertThat(Bytes.concat()).isEqualTo(Bytes.empty());
    }

    @Test
    void buildsWithAWriter() {
        assertThat(Bytes.build(out -> out.writeShort(1).writeIntLe(2))).isEqualTo(bytes(0, 1, 2, 0, 0, 0));
        assertThat(Bytes.build(out -> out.writeByte(1).close())).isEqualTo(bytes(1));
        var failure = new IOException("failure");
        assertThatExceptionOfType(UncheckedIOException.class)
                .isThrownBy(() -> Bytes.build(out -> {
                    throw failure;
                }))
                .withCause(failure);
    }

    @Test
    void getsBytesByIndex() {
        var bytes = bytes(1, 2, 3).slice(1, 3);

        assertThat(bytes.get(0)).isEqualTo((byte) 2);
        assertThat(bytes.get(1)).isEqualTo((byte) 3);
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> bytes.get(2));
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> bytes.get(-1));
    }

    @Test
    void slices() {
        var bytes = bytes(1, 2, 3, 4);

        assertThat(bytes.slice(1, 3)).isEqualTo(bytes(2, 3));
        assertThat(bytes.slice(1, 3).slice(1, 2)).isEqualTo(bytes(3));
        assertThat(bytes.slice(0, 4)).isSameAs(bytes);
        assertThat(bytes.slice(2, 2)).isEqualTo(Bytes.empty());
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> bytes.slice(3, 2));
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> bytes.slice(0, 5));
    }

    @Test
    void searchesAsStringsDo() {
        var random = new SplittableRandom(3);
        for (int i = 0; i < 10_000; i++) {
            var text = randomText(random, 20);
            var target = randomText(random, 3);
            var bytes = Bytes.encode(text, ISO_8859_1);
            var targetBytes = Bytes.encode(target, ISO_8859_1);

            assertThat(bytes.indexOf(targetBytes)).as(text + " / " + target).isEqualTo(text.indexOf(target));
            assertThat(bytes.startsWith(targetBytes)).as(text + " / " + target).isEqualTo(text.startsWith(target));
            assertThat(bytes.endsWith(targetBytes)).as(text + " / " + target).isEqualTo(text.endsWith(target));
            if (!target.isEmpty()) {
                assertThat(bytes.indexOf((byte) target.charAt(0)))
                        .as(text + " / " + target)
                        .isEqualTo(text.indexOf(target.charAt(0)));
            }
        }
    }

    @Test
    void handsOutAReadOnlyBuffer() {
        var buffer = bytes(1, 2, 3).slice(1, 3).asByteBuffer();

        assertThat(buffer.position()).isZero();
        assertThat(buffer.remaining()).isEqualTo(2);
        assertThat(buffer.get(0)).isEqualTo((byte) 2);
        assertThatExceptionOfType(ReadOnlyBufferException.class).isThrownBy(() -> buffer.put((byte) 0));
    }

    @Test
    void writesToAStream() throws IOException {
        var out = new ByteArrayOutputStream();

        bytes(1, 2, 3).slice(1, 3).writeTo(out);

        assertThat(out.toByteArray()).containsExactly(2, 3);
    }

    @Test
    void computesDigests() {
        assertThat(Bytes.encode("abc").digest("SHA-256").toHex())
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(Bytes.encode("xabcx").slice(1, 4).digest("SHA-256"))
                .isEqualTo(Bytes.encode("abc").digest("SHA-256"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Bytes.empty().digest("NOPE"))
                .withMessage("No such digest algorithm: NOPE");
    }

    @Test
    void isEqualToTheSameBytes() {
        var bytes = bytes(9, 1, 2);

        assertThat(bytes.slice(1, 3)).isEqualTo(bytes(1, 2)).hasSameHashCodeAs(bytes(1, 2));
        assertThat(bytes(1, 2).hashCode()).isEqualTo(Arrays.hashCode(new byte[] {1, 2}));
        assertThat(Bytes.empty().hashCode()).isEqualTo(Arrays.hashCode(new byte[0]));
        assertThat(bytes(1, 2)).isNotEqualTo(bytes(1, 3)).isNotEqualTo(bytes(1)).isNotEqualTo("12");
    }

    @Test
    void comparesBytesAsUnsigned() {
        var random = new SplittableRandom(4);
        for (int i = 0; i < 10_000; i++) {
            var a = randomBytes(random.nextLong(), random.nextInt(4));
            var b = randomBytes(random.nextLong(), random.nextInt(4));

            assertThat(Integer.signum(Bytes.of(a).compareTo(Bytes.of(b))))
                    .isEqualTo(Integer.signum(Arrays.compareUnsigned(a, b)));
        }
        assertThat(bytes(0xFF)).isGreaterThan(bytes(1));
        assertThat(bytes(1, 2)).isGreaterThan(bytes(1));
    }

    @Test
    void describesTheFirstBytes() {
        assertThat(bytes(0xCA, 0xFE, 0xBA)).hasToString("Bytes[size=3, hex=cafeba]");
        assertThat(Bytes.of(new byte[33])).hasToString("Bytes[size=33, hex=" + "00".repeat(32) + "...]");
    }

    // Passing null is the point.
    @Test
    @SuppressWarnings("NullAway")
    void rejectsNulls() {
        var bytes = bytes(1);

        assertThatNullPointerException()
                .isThrownBy(() -> Bytes.of((byte[]) null))
                .withMessage("bytes");
        assertThatNullPointerException()
                .isThrownBy(() -> Bytes.copyOf(null, 0, 0))
                .withMessage("bytes");
        assertThatNullPointerException().isThrownBy(() -> Bytes.copyOf(null)).withMessage("buffer");
        assertThatNullPointerException().isThrownBy(() -> Bytes.encode(null)).withMessage("chars");
        assertThatNullPointerException()
                .isThrownBy(() -> Bytes.encode("a", null))
                .withMessage("charset");
        assertThatNullPointerException().isThrownBy(() -> Bytes.fromHex(null)).withMessage("hex");
        assertThatNullPointerException()
                .isThrownBy(() -> Bytes.fromBase64(null))
                .withMessage("base64");
        assertThatNullPointerException()
                .isThrownBy(() -> Bytes.fromBase64Url(null))
                .withMessage("base64");
        assertThatNullPointerException()
                .isThrownBy(() -> Bytes.concat((Bytes[]) null))
                .withMessage("values");
        assertThatNullPointerException()
                .isThrownBy(() -> Bytes.concat(bytes, null))
                .withMessage("values contains null");
        assertThatNullPointerException().isThrownBy(() -> Bytes.build(null)).withMessage("content");
        assertThatNullPointerException()
                .isThrownBy(() -> bytes.startsWith(null))
                .withMessage("prefix");
        assertThatNullPointerException().isThrownBy(() -> bytes.endsWith(null)).withMessage("suffix");
        assertThatNullPointerException().isThrownBy(() -> bytes.indexOf(null)).withMessage("target");
        assertThatNullPointerException().isThrownBy(() -> bytes.writeTo(null)).withMessage("out");
        assertThatNullPointerException().isThrownBy(() -> bytes.decode(null)).withMessage("charset");
        assertThatNullPointerException().isThrownBy(() -> bytes.digest(null)).withMessage("algorithm");
        assertThatNullPointerException().isThrownBy(() -> bytes.compareTo(null)).withMessage("other");
    }

    private static String randomText(SplittableRandom random, int maxLength) {
        var chars = new char[random.nextInt(maxLength + 1)];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = "abÿ".charAt(random.nextInt(3));
        }
        return new String(chars);
    }
}
