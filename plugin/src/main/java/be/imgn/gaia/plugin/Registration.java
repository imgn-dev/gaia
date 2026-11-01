package be.imgn.gaia.plugin;

/**
 * What was registered, such as an extension or a listener of extensions, until it is closed.
 *
 * @since 0.1.3
 */
public interface Registration extends AutoCloseable {

    /** Undoes the registration. Closing it again does nothing. */
    @Override
    void close();
}
