package be.imgn.gaia.core.tests.event;

import java.text.MessageFormat;
import java.util.List;
import java.util.Queue;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jspecify.annotations.Nullable;

/** Records what is logged with {@link System.Logger}, for the tests of what the event bus logs. */
public final class RecordingLoggerFinder extends System.LoggerFinder {

    private static final Queue<String> RECORDS = new ConcurrentLinkedQueue<>();

    /** Creates the finder, as {@link System.LoggerFinder#getLoggerFinder()} does. */
    public RecordingLoggerFinder() {}

    /**
     * Returns what was logged so far by a logger, as its level, then its message, then the message of its exception.
     *
     * @param name the name of the logger
     * @return the records, oldest first
     */
    static List<String> records(String name) {
        return RECORDS.stream()
                .filter(record -> record.startsWith(name + " "))
                .map(record -> record.substring(name.length() + 1))
                .toList();
    }

    @Override
    public System.Logger getLogger(String name, Module module) {
        return new System.Logger() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public boolean isLoggable(Level level) {
                return true;
            }

            @Override
            public void log(
                    Level level,
                    @Nullable ResourceBundle bundle,
                    @Nullable String message,
                    @Nullable Throwable thrown) {
                RECORDS.add(name + " " + level + " " + message + (thrown == null ? "" : ": " + thrown.getMessage()));
            }

            @Override
            public void log(
                    Level level, @Nullable ResourceBundle bundle, @Nullable String format, @Nullable Object... params) {
                log(level, bundle, format == null ? null : MessageFormat.format(format, params), (Throwable) null);
            }
        };
    }
}
