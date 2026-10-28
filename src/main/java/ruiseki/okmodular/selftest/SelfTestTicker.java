package ruiseki.okmodular.selftest;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.MinecraftServer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import ruiseki.okmodular.selftest.judge.ScenarioResult;
import ruiseki.okmodular.selftest.report.Annotations;
import ruiseki.okmodular.selftest.report.ReportHtml;
import ruiseki.okmodular.selftest.report.RunInfo;

/** Advances the RunSchedule once per server tick; when done it writes the report and stops the server. */
public final class SelfTestTicker {

    private final MinecraftServer server;
    private final RunSchedule schedule;
    private final Annotations annotations;
    private final File reportDir;
    private final String startedAt, branch, commit;
    private final List<String> notes;
    private boolean finished;

    SelfTestTicker(MinecraftServer server, RunSchedule schedule, Annotations annotations, File reportDir,
        String startedAt, String branch, String commit, List<String> notes) {
        this.server = server;
        this.schedule = schedule;
        this.annotations = annotations;
        this.reportDir = reportDir;
        this.startedAt = startedAt;
        this.branch = branch;
        this.commit = commit;
        this.notes = new ArrayList<>(notes);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (finished || e.phase != TickEvent.Phase.END) return;
        boolean more = false;
        try {
            more = schedule.tick();
        } catch (Throwable t) {
            // RunSchedule contains driver exceptions; reaching here is a bug in the schedule itself.
            SelfTest.LOG.error("selftest schedule crashed", t);
            notes.add("schedule crashed: " + t);
        } finally {
            if (!more) finish();
        }
    }

    private void finish() {
        finished = true;
        try {
            List<ScenarioResult> results = schedule.results();
            int pass = 0, fail = 0, visual = 0;
            for (ScenarioResult r : results) {
                switch (r.verdict) {
                    case PASS:
                        pass++;
                        break;
                    case VISUAL:
                        visual++;
                        break;
                    default:
                        fail++;
                }
            }
            try {
                RunInfo info = new RunInfo(
                    startedAt,
                    branch,
                    commit,
                    schedule.elapsed(),
                    schedule.deadlineHit(),
                    notes);
                String html = ReportHtml.render(results, annotations, info);
                reportDir.mkdirs();
                File index = new File(reportDir, "index.html");
                Files.write(index.toPath(), html.getBytes(StandardCharsets.UTF_8));
                SelfTest.LOG.info("selftest report: {}", index.getAbsolutePath());
            } catch (Throwable t) {
                SelfTest.LOG.error("selftest report could not be written", t);
                writeError(reportDir, "report could not be written", t);
            }
            SelfTest.LOG.info("SELFTEST DONE pass={} fail={} visual={}", pass, fail, visual);
        } catch (Throwable t) {
            SelfTest.LOG.error("selftest finish failed", t);
            writeError(reportDir, "finish failed", t);
        } finally {
            server.initiateShutdown();
        }
    }

    /** Best effort: leaves report-error.txt with the stack trace; never throws. */
    static void writeError(File reportDir, String what, Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            sw.append(what)
                .append('\n');
            t.printStackTrace(new PrintWriter(sw));
            reportDir.mkdirs();
            Files.write(
                new File(reportDir, "report-error.txt").toPath(),
                sw.toString()
                    .getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            // Nothing left to report to; the caller still shuts the server down.
        }
    }
}
