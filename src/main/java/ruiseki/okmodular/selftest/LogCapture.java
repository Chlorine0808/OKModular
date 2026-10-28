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
import org.apache.logging.log4j.core.impl.Log4jContextFactory;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.spi.LoggerContextFactory;

/**
 * Copies WARN-and-above log lines from every log4j2 context (root plus the non-additive vanilla loggers) so scenarios
 * can check them.
 */
public final class LogCapture {

    private static final int MAX_LINES = 20000;
    private static final List<String> LINES = new ArrayList<>();
    private static boolean installed;

    private LogCapture() {}

    public static synchronized void install() {
        if (installed) return;
        CopyAppender appender = new CopyAppender();
        appender.start();
        for (LoggerContext ctx : contexts()) {
            // FML's log4j2.xml makes net.minecraft and com.mojang non-additive, so the root alone misses vanilla.
            Set<LoggerConfig> configs = Collections.newSetFromMap(new IdentityHashMap<LoggerConfig, Boolean>());
            for (String name : new String[] { LogManager.ROOT_LOGGER_NAME, "net.minecraft", "com.mojang" }) {
                configs.add(
                    ctx.getConfiguration()
                        .getLoggerConfig(name));
            }
            for (LoggerConfig c : configs) c.addAppender(appender, Level.WARN, null);
            ctx.updateLoggers();
        }
        installed = true;
    }

    /**
     * Every live LoggerContext, not just this class's: log4j keeps one per class loader, and FMLLog's channels
     * (OKModular's own Logger among them) log from the system loader's context, not LaunchClassLoader's.
     */
    private static Set<LoggerContext> contexts() {
        Set<LoggerContext> out = Collections.newSetFromMap(new IdentityHashMap<LoggerContext, Boolean>());
        out.add((LoggerContext) LogManager.getContext(false));
        LoggerContextFactory factory = LogManager.getFactory();
        if (factory instanceof Log4jContextFactory) {
            out.addAll(
                ((Log4jContextFactory) factory).getSelector()
                    .getLoggerContexts());
        }
        return out;
    }

    public static List<String> lines() {
        synchronized (LINES) {
            return new ArrayList<>(LINES);
        }
    }

    public static int size() {
        synchronized (LINES) {
            return LINES.size();
        }
    }

    /** Lines before loadTimeEnd (logged while the server loaded) plus the lines from since onwards. */
    static List<String> slice(List<String> all, int loadTimeEnd, int since) {
        int n = all.size();
        int le = Math.max(0, Math.min(loadTimeEnd, n));
        int st = Math.max(le, Math.min(since, n));
        List<String> out = new ArrayList<>(all.subList(0, le));
        out.addAll(all.subList(st, n));
        return out;
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
