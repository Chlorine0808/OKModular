package ruiseki.okmodular.selftest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ruiseki.okmodular.selftest.grid.BlockGrid;
import ruiseki.okmodular.selftest.judge.CheckResult;
import ruiseki.okmodular.selftest.judge.ScenarioResult;
import ruiseki.okmodular.selftest.scenario.Scenario;

/** Runs scenarios one at a time, one driver step per tick(); always terminates (exceptions contained, deadline). */
public final class RunSchedule {

    public static final long DEFAULT_DEADLINE = 36000;

    private final List<Scenario> scenarios;
    private final ScenarioDriver driver;
    private final long deadlineTicks;
    private final List<ScenarioResult> results = new ArrayList<>();
    private int index = 0;
    private Scenario current;
    private int t;
    private long elapsed;
    private boolean deadlineHit;

    public RunSchedule(List<Scenario> scenarios, ScenarioDriver driver, long deadlineTicks) {
        this.scenarios = scenarios;
        this.driver = driver;
        this.deadlineTicks = deadlineTicks;
    }

    /** Advances by one step; returns false when every scenario has a result. */
    public boolean tick() {
        if (isDone()) return false;
        if (elapsed >= deadlineTicks) {
            hitDeadline();
            return false;
        }
        elapsed++;
        try {
            step();
        } catch (Throwable ex) {
            fail(current, describe(ex));
        }
        return !isDone();
    }

    public List<ScenarioResult> results() {
        return Collections.unmodifiableList(results);
    }

    public boolean deadlineHit() {
        return deadlineHit;
    }

    public long elapsed() {
        return elapsed;
    }

    private boolean isDone() {
        return current == null && index >= scenarios.size();
    }

    private void step() throws Exception {
        if (current == null) {
            current = nextRunnable();
            if (current == null) return;
            t = 0;
            driver.setUp(current);
        }
        if (t <= current.ticks) {
            driver.tick(current, t);
            t++;
            return;
        }
        ScenarioResult r = driver.finish(current);
        if (r == null) throw new IllegalStateException("driver returned no result");
        Scenario done = current;
        current = null;
        results.add(r);
        tearDownQuietly(done);
    }

    /** Fails invalid scenarios without calling the driver; returns the next valid one or null. */
    private Scenario nextRunnable() {
        while (index < scenarios.size()) {
            Scenario s = scenarios.get(index++);
            if (s.parseError == null) return s;
            results.add(ScenarioResult.of(s, Collections.<CheckResult>emptyList(), noFigures()));
        }
        return null;
    }

    private void fail(Scenario s, String error) {
        current = null;
        results.add(ScenarioResult.failed(s, error, noFigures()));
        tearDownQuietly(s);
    }

    private void hitDeadline() {
        deadlineHit = true;
        if (current != null) {
            Scenario s = current;
            current = null;
            results.add(notRun(s));
            tearDownQuietly(s);
        }
        while (index < scenarios.size()) results.add(notRun(scenarios.get(index++)));
    }

    private static ScenarioResult notRun(Scenario s) {
        return ScenarioResult.failed(s, "not run: deadline", noFigures());
    }

    private static java.util.Map<String, BlockGrid> noFigures() {
        return Collections.<String, BlockGrid>emptyMap();
    }

    private void tearDownQuietly(Scenario s) {
        try {
            driver.tearDown(s);
        } catch (Throwable ignored) {
            // A failing tearDown must never stop the schedule.
        }
    }

    private static String describe(Throwable ex) {
        StackTraceElement[] st = ex.getStackTrace();
        return ex + (st.length > 0 ? " at " + st[0] : "");
    }
}
