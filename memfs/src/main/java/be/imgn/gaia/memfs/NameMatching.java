package be.imgn.gaia.memfs;

/**
 * How the names of the files of a directory are matched when they are looked up. The names are always shown as they
 * were created.
 *
 * @since 0.1.1
 */
public enum NameMatching {

    /** Names match only when they have the same characters, as on Linux. */
    EXACT,

    /** Names match ignoring the case of ASCII letters: {@code A} and {@code a}, but not {@code É} and {@code é}. */
    IGNORE_CASE_ASCII,

    /** Names match ignoring the case of any letter, as on macOS and Windows. */
    IGNORE_CASE_UNICODE
}
