package ruiseki.okmodular.selftest.judge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import ruiseki.okmodular.selftest.grid.BlockGrid;
import ruiseki.okmodular.selftest.scenario.Scenario;

/** Verdict, checks and figures of one scenario run. */
public final class ScenarioResult {

    public final String id, section, title, source;
    public final Verdict verdict;
    public final List<CheckResult> checks;
    public final Map<String, BlockGrid> figures;
    /** Null unless the scenario could not run or judge. */
    public final String error;

    private ScenarioResult(Scenario s, Verdict verdict, List<CheckResult> checks, Map<String, BlockGrid> figures,
        String error) {
        this.id = s.id;
        this.section = s.section;
        this.title = s.title;
        this.source = s.source;
        this.verdict = verdict;
        this.checks = Collections.unmodifiableList(new ArrayList<>(checks));
        this.figures = figures;
        this.error = error;
    }

    public static ScenarioResult of(Scenario s, List<CheckResult> checks, Map<String, BlockGrid> figures) {
        if (s.parseError != null) {
            return failed(s, "scenario: " + s.parseError, Collections.<String, BlockGrid>emptyMap());
        }
        boolean anyFailed = false;
        for (CheckResult c : checks) if (!c.passed) anyFailed = true;
        Verdict v = anyFailed ? Verdict.FAIL : (s.visual ? Verdict.VISUAL : Verdict.PASS);
        return new ScenarioResult(s, v, checks, figures, null);
    }

    public static ScenarioResult failed(Scenario s, String error, Map<String, BlockGrid> figures) {
        return new ScenarioResult(s, Verdict.FAIL, Collections.<CheckResult>emptyList(), figures, error);
    }
}
