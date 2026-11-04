package ruiseki.okmodular.selftest.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import ruiseki.okmodular.selftest.grid.BlockGrid;
import ruiseki.okmodular.selftest.scenario.Expectation;
import ruiseki.okmodular.selftest.scenario.InputSpec;
import ruiseki.okmodular.selftest.scenario.Region;
import ruiseki.okmodular.selftest.scenario.Scenario;
import ruiseki.okmodular.selftest.scenario.Step;

class ScenarioResultTest {

    private static Scenario scenario(boolean visual) {
        return new Scenario(
            "id1",
            "sec",
            "title",
            "docs/x.md",
            0,
            64,
            visual,
            false,
            null,
            Collections.<InputSpec>emptyList(),
            Collections.<Step>emptyList(),
            10,
            Collections.<String, Region>emptyMap(),
            Collections.<String, Integer>emptyMap(),
            Collections.<Expectation>emptyList(),
            null);
    }

    private static final CheckResult OK = new CheckResult("formed", true, "e", "a");
    private static final CheckResult NG = new CheckResult("formed", false, "e", "a");

    @Test
    void allPassingChecksPass() {
        ScenarioResult r = ScenarioResult
            .of(scenario(false), Arrays.asList(OK, OK), Collections.<String, BlockGrid>emptyMap());
        assertEquals(Verdict.PASS, r.verdict);
        assertEquals("id1", r.id);
        assertEquals("sec", r.section);
        assertEquals("title", r.title);
        assertEquals("docs/x.md", r.source);
        assertNull(r.error);
    }

    @Test
    void anyFailedCheckFailsTheScenario() {
        List<CheckResult> checks = Arrays.asList(OK, NG, OK);
        assertEquals(
            Verdict.FAIL,
            ScenarioResult.of(scenario(false), checks, Collections.<String, BlockGrid>emptyMap()).verdict);
        // a failure outranks the visual flag
        assertEquals(
            Verdict.FAIL,
            ScenarioResult.of(scenario(true), checks, Collections.<String, BlockGrid>emptyMap()).verdict);
    }

    @Test
    void visualScenarioWithPassingChecksIsVisual() {
        ScenarioResult r = ScenarioResult
            .of(scenario(true), Collections.singletonList(OK), Collections.<String, BlockGrid>emptyMap());
        assertEquals(Verdict.VISUAL, r.verdict);
        assertEquals("要目視", r.verdict.label);
    }

    @Test
    void invalidScenarioIsReportedAsFailed() {
        Scenario bad = Scenario.invalid("bad", "sec", "docs/x.md", "no ticks");
        ScenarioResult r = ScenarioResult
            .of(bad, Collections.singletonList(OK), Collections.<String, BlockGrid>emptyMap());
        assertEquals(Verdict.FAIL, r.verdict);
        assertEquals("scenario: no ticks", r.error);
        assertTrue(r.checks.isEmpty());
        assertTrue(r.figures.isEmpty());
    }

    @Test
    void failedKeepsTheFigures() {
        BlockGrid g = new BlockGrid(new int[] { 0, 0, 0 }, new int[] { 0, 0, 0 });
        ScenarioResult r = ScenarioResult.failed(scenario(false), "boom", Collections.singletonMap("s", g));
        assertEquals(Verdict.FAIL, r.verdict);
        assertEquals("boom", r.error);
        assertEquals(g, r.figures.get("s"));
    }
}
