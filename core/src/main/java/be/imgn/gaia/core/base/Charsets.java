package be.imgn.gaia.core.base;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Charsets that gaia relies on.
 *
 * @since 0.1.2
 */
public final class Charsets {

    /**
     * The charset that the methods of gaia use when they are not given one, which currently is UTF-8. Unlike
     * {@link Charset#defaultCharset()}, it does not depend on the platform or on the {@code file.encoding} system
     * property.
     */
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    private Charsets() {}
}
