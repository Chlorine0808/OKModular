package ruiseki.okmodular.selftest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.message.Message;

/**
 * Copies WARN-and-above log lines from log4j2 (root plus the non-additive vanilla loggers) so scenarios can check them.
 */
public final class LogCapture {

    private static final int MAX_LINES = 20000;
    private static final List<String> LINES = new ArrayList<>();
    private static boolean installed;

    private LogCapture() {}

    public static synchronized void install() {
        if (installed) return;
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        CopyAppender appender = new CopyAppender();
        appender.start();
        // FML's log4j2.xml makes net.minecraft and com.mojang non-additive, so the root alone misses vanilla.
        Set<LoggerConfig> configs = Collections.newSetFromMap(new IdentityHashMap<LoggerConfig, Boolean>());
        for (String name : new String[] { LogManager.ROOT_LOGGER_NAME, "net.minecraft", "com.mojang" }) {
            configs.add(
                ctx.getConfiguration()
                    .getLoggerConfig(name));
        }
        for (LoggerConfig c : configs) c.addAppender(appender, Level.WARN, null);
        ctx.updateLoggers();
        installed = true;
    }

    public static List<String> lines() {
        synchronized (LINES) {
            return new ArrayList<>(LINES);
        }
    }

    private static void add(String line) {
        synchronized (LINES) {
            if (LINES.size() < MAX_LINES) LINES.add(line);
        }
    }

    private static final class CopyAppender extends AbstractAppender {

        CopyAppender() {
            super("OKModularSelfTestCapture", null, null, true);
        }

        @Override
        public void append(LogEvent event) {
            Message msg = event.getMessage();
            StringBuilder sb = new StringBuilder().append('[')
                .append(event.getLevel())
                .append("] [")
                .append(event.getLoggerName())
                .append("] ")
                .append(msg == null ? "" : msg.getFormattedMessage());
            Throwable thrown = event.getThrown();
            if (thrown != null) sb.append(" | ")
                .append(thrown);
            add(sb.toString());
        }
    }
}
