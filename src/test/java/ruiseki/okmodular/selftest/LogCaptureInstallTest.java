package ruiseki.okmodular.selftest;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// log4j keeps one LoggerContext per class loader. In game, FMLLog.log(channel, ...) logs from
// FMLRelaunchLog, which the system class loader holds, while the mod (and LogCapture) live in
// LaunchClassLoader. Installing only into LogCapture's own context missed every FMLLog channel
// line, including OKModular's own Logger.warn (s3.6-iron-no-level: "none of 49 lines" while
// fml-server-latest.log had the warning). A context made for a detached loader stands in for
// the system loader's here.
class LogCaptureInstallTest {

    @Test
    @DisplayName("別のクラスローダーのログ文脈に出た WARN も拾う")
    void capturesWarningsFromAnotherLoadersContext() throws Exception {
        try (URLClassLoader other = new URLClassLoader(new URL[0], null)) {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(other, false);
            LogCapture.install();
            ctx.getLogger("LogCaptureInstallTest")
                .log(Level.WARN, "a warning from another context");
            assertTrue(
                LogCapture.lines()
                    .stream()
                    .anyMatch(l -> l.contains("a warning from another context")),
                LogCapture.lines()
                    .toString());
        }
    }
}
