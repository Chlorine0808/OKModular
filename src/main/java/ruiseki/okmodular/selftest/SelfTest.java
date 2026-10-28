package ruiseki.okmodular.selftest;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.FMLCommonHandler;
import ruiseki.okmodular.OKModular;
import ruiseki.okmodular.selftest.grid.BlockGrid;
import ruiseki.okmodular.selftest.judge.CheckResult;
import ruiseki.okmodular.selftest.judge.ExpectationJudge;
import ruiseki.okmodular.selftest.judge.Observation;
import ruiseki.okmodular.selftest.judge.ScenarioResult;
import ruiseki.okmodular.selftest.report.Annotations;
import ruiseki.okmodular.selftest.scenario.Expectation;
import ruiseki.okmodular.selftest.scenario.Scenario;
import ruiseki.okmodular.selftest.scenario.ScenarioParser;

/** Entry point of the in-game selftest; both hooks return at once unless SelfTestTrigger says so. */
public final class SelfTest {

    static final Logger LOG = LogManager.getLogger("OKModular-selftest");

    private SelfTest() {}

    public static void onPreInit() {
        if (!requested()) return;
        LogCapture.install();
        LOG.info("selftest requested; capturing WARN+ log lines");
    }

    public static void onServerStarted(MinecraftServer server) {
        if (!requested()) return;
        File reportDir = new File(System.getProperty("okmodular.selftest.report", "selftest-report"));
        try {
            List<String> notes = new ArrayList<>();
            List<Scenario> scenarios = select(
                loadScenarios(System.getProperty("okmodular.selftest.scenarios"), notes),
                System.getProperty("okmodular.selftest.only"),
                notes);
            Annotations annotations = loadAnnotations(System.getProperty("okmodular.selftest.annotations"), notes);
            ForgeChunkManager.setForcedChunkLoadingCallback(OKModular.instance, new ReleaseTickets());
            String repo = System.getProperty("okmodular.selftest.repo");
            SelfTestTicker ticker = new SelfTestTicker(
                server,
                new RunSchedule(scenarios, new NoWorldDriver(), RunSchedule.DEFAULT_DEADLINE),
                annotations,
                reportDir,
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()),
                git(repo, "rev-parse", "--abbrev-ref", "HEAD"),
                git(repo, "rev-parse", "--short", "HEAD"),
                notes);
            FMLCommonHandler.instance()
                .bus()
                .register(ticker);
            LOG.info("selftest scheduled: {} scenario(s)", scenarios.size());
        } catch (Throwable t) {
            // Never leave a server running unattended: record why and stop.
            LOG.error("selftest could not start", t);
            SelfTestTicker.writeError(reportDir, "selftest could not start", t);
            server.initiateShutdown();
        }
    }

    private static boolean requested() {
        Map<String, Object> blackboard;
        try {
            blackboard = Launch.blackboard;
        } catch (Throwable t) {
            return false;
        }
        return SelfTestTrigger.isRequested(System.getProperties(), blackboard);
    }

    /** Parses every *.json in the folder (sorted); an unreadable file becomes one failed scenario. */
    static List<Scenario> loadScenarios(String dirPath, List<String> notes) {
        List<Scenario> out = new ArrayList<>();
        if (dirPath == null) {
            notes.add("okmodular.selftest.scenarios is not set; nothing to run");
            return out;
        }
        File[] files = new File(dirPath).listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) {
            notes.add("scenario folder not found: " + dirPath);
            return out;
        }
        Arrays.sort(files);
        ScenarioParser parser = new ScenarioParser(ExpectationJudge.KINDS);
        for (File f : files) {
            try (InputStream in = new FileInputStream(f);
                Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                out.addAll(parser.parseFile(f.getName(), r));
            } catch (Exception e) {
                out.add(Scenario.invalid(f.getName(), null, f.getName(), String.valueOf(e.getMessage())));
            }
        }
        return out;
    }

    /**
     * Keeps scenarios whose id or section starts with one of the comma-separated prefixes. Whole-file
     * failures (no section) are always kept so a broken file is never filtered out of sight.
     */
    static List<Scenario> select(List<Scenario> all, String only, List<String> notes) {
        if (only == null || only.trim()
            .isEmpty()) return all;
        List<String> prefixes = new ArrayList<>();
        for (String p : only.split(",")) if (!p.trim()
            .isEmpty()) prefixes.add(p.trim());
        notes.add("only: " + String.join(", ", prefixes));
        List<Scenario> out = new ArrayList<>();
        for (Scenario s : all) {
            boolean keep = s.parseError != null && s.section == null;
            for (String p : prefixes) {
                if (s.id.startsWith(p) || (s.section != null && s.section.startsWith(p))) keep = true;
            }
            if (keep) out.add(s);
        }
        return out;
    }

    private static Annotations loadAnnotations(String path, List<String> notes) {
        if (path == null || !new File(path).isFile()) return Annotations.empty();
        try (InputStream in = new FileInputStream(path); Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return Annotations.parse(r);
        } catch (Exception e) {
            notes.add("annotations.json could not be read: " + e);
            return Annotations.empty();
        }
    }

    /** Runs a git command in the repo folder with a timeout; any failure yields "unknown". */
    private static String git(String repo, String... args) {
        if (repo == null) return "unknown";
        File out = null;
        try {
            out = File.createTempFile("okmodular-selftest-git", ".txt");
            List<String> cmd = new ArrayList<>();
            cmd.add("git");
            cmd.addAll(Arrays.asList(args));
            Process p = new ProcessBuilder(cmd).directory(new File(repo))
                .redirectErrorStream(true)
                .redirectOutput(out)
                .start();
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "unknown";
            }
            if (p.exitValue() != 0) return "unknown";
            String s = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8).trim();
            return s.isEmpty() ? "unknown" : s;
        } catch (Throwable t) {
            return "unknown";
        } finally {
            if (out != null) out.delete();
        }
    }

    /** Drops any forced-chunk tickets left from an earlier run; the selftest requests fresh ones. */
    private static final class ReleaseTickets implements ForgeChunkManager.LoadingCallback {

        @Override
        public void ticketsLoaded(List<ForgeChunkManager.Ticket> tickets, World world) {
            for (ForgeChunkManager.Ticket t : tickets) ForgeChunkManager.releaseTicket(t);
        }
    }

    /** Placeholder driver until the live world driver exists: judges every expectation on an empty observation. */
    private static final class NoWorldDriver implements ScenarioDriver {

        @Override
        public void setUp(Scenario s) {}

        @Override
        public void tick(Scenario s, int t) {}

        @Override
        public ScenarioResult finish(Scenario s) {
            Observation o = new Observation();
            List<CheckResult> checks = new ArrayList<>();
            for (Expectation e : s.expectations) checks.add(ExpectationJudge.judge(e, o));
            return ScenarioResult.of(s, checks, Collections.<String, BlockGrid>emptyMap());
        }

        @Override
        public void tearDown(Scenario s) {}
    }
}
