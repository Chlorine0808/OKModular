package ruiseki.okmodular.selftest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.selftest.grid.BlockGrid;
import ruiseki.okmodular.selftest.judge.CheckResult;
import ruiseki.okmodular.selftest.judge.ScenarioResult;
import ruiseki.okmodular.selftest.judge.Verdict;
import ruiseki.okmodular.selftest.scenario.Expectation;
import ruiseki.okmodular.selftest.scenario.InputSpec;
import ruiseki.okmodular.selftest.scenario.Region;
import ruiseki.okmodular.selftest.scenario.Scenario;
import ruiseki.okmodular.selftest.scenario.Step;

// The schedule must always terminate: a throwing driver (even a throwing tearDown) fails only
// its own scenario, and the deadline fails everything left. One tick() call is one driver step.
class RunScheduleTest {

    private static Scenario sc(String id, int ticks) {
        return new Scenario(
            id,
            "s",
            id,
            "src",
            0,
            64,
            false,
            false,
            null,
            Collections.<InputSpec>emptyList(),
            Collections.<Step>emptyList(),
            ticks,
            Collections.<String, Region>emptyMap(),
            Collections.<String, Integer>emptyMap(),
            Collections.<Expectation>emptyList(),
            null);
    }

    private static class Fake implements ScenarioDriver {

        final List<String> log = new ArrayList<>();
        String throwInTick, throwInSetUp;
        boolean throwInTearDown;

        public void setUp(Scenario s) {
            log.add("setUp " + s.id);
            if (s.id.equals(throwInSetUp)) throw new IllegalStateException("boom setUp");
        }

        public void tick(Scenario s, int t) {
            log.add("tick " + s.id + " " + t);
            if (s.id.equals(throwInTick)) throw new IllegalStateException("boom");
        }

        public ScenarioResult finish(Scenario s) {
            log.add("finish " + s.id);
            return ScenarioResult
                .of(s, Collections.<CheckResult>emptyList(), Collections.<String, BlockGrid>emptyMap());
        }

        public void tearDown(Scenario s) {
            log.add("tearDown " + s.id);
            if (throwInTearDown) throw new IllegalStateException("boom tearDown");
        }
    }

    private static RunSchedule run(Fake d, long deadline, Scenario... ss) {
        RunSchedule r = new RunSchedule(Arrays.asList(ss), d, deadline);
        int guard = 0;
        while (r.tick()) assertTrue(++guard < 10000, "schedule did not terminate");
        return r;
    }

    @Test
    @DisplayName("each scenario runs ticks 0..ticks, then finish and tearDown")
    void runsEachScenarioForItsTicksThenFinishes() {
        Fake d = new Fake();
        RunSchedule r = run(d, 100, sc("a", 3));
        assertEquals(
            Arrays.asList("setUp a", "tick a 0", "tick a 1", "tick a 2", "tick a 3", "finish a", "tearDown a"),
            d.log);
        assertEquals(
            Verdict.PASS,
            r.results()
                .get(0).verdict);
        assertFalse(r.deadlineHit());
    }

    @Test
    @DisplayName("an exception fails only that scenario")
    void exceptionInTickFailsOnlyThatScenario() {
        Fake d = new Fake();
        d.throwInTick = "b";
        RunSchedule r = run(d, 100, sc("a", 1), sc("b", 1), sc("c", 1));
        assertEquals(
            Verdict.PASS,
            r.results()
                .get(0).verdict);
        ScenarioResult b = r.results()
            .get(1);
        assertEquals(Verdict.FAIL, b.verdict);
        assertTrue(b.error.contains("boom") && b.error.contains(" at "), b.error);
        assertEquals(
            Verdict.PASS,
            r.results()
                .get(2).verdict);
    }

    @Test
    @DisplayName("tearDown runs even after an exception (setUp or tick)")
    void tearDownRunsEvenAfterAnException() {
        Fake d = new Fake();
        d.throwInTick = "a";
        run(d, 100, sc("a", 2));
        assertEquals("tearDown a", d.log.get(d.log.size() - 1));
        Fake d2 = new Fake();
        d2.throwInSetUp = "a";
        run(d2, 100, sc("a", 2));
        assertTrue(d2.log.contains("tearDown a"));
    }

    @Test
    @DisplayName("an exception thrown by tearDown cannot stop the schedule")
    void tearDownExceptionIsContained() {
        Fake d = new Fake();
        d.throwInTearDown = true;
        RunSchedule r = run(d, 100, sc("a", 1), sc("b", 1));
        assertEquals(
            2,
            r.results()
                .size());
        assertEquals(
            Verdict.PASS,
            r.results()
                .get(1).verdict);
    }

    @Test
    @DisplayName("an invalid scenario fails without calling the driver")
    void invalidScenarioSkipsTheDriver() {
        Fake d = new Fake();
        RunSchedule r = run(d, 100, Scenario.invalid("bad", "s", "src", "oops"), sc("a", 0));
        assertEquals(
            Verdict.FAIL,
            r.results()
                .get(0).verdict);
        assertFalse(d.log.contains("setUp bad"));
        assertEquals(
            Verdict.PASS,
            r.results()
                .get(1).verdict);
    }

    @Test
    @DisplayName("the deadline fails the rest and stops")
    void deadlineFailsTheRestAndStops() {
        Fake d = new Fake();
        RunSchedule r = run(d, 5, sc("a", 4), sc("b", 4), sc("c", 4));
        assertTrue(r.deadlineHit());
        assertEquals(
            3,
            r.results()
                .size());
        ScenarioResult last = r.results()
            .get(2);
        assertEquals(Verdict.FAIL, last.verdict);
        assertEquals("not run: deadline", last.error);
        assertTrue(d.log.contains("tearDown a"), "the running scenario is torn down");
        assertEquals(5, r.elapsed());
    }

    @Test
    @DisplayName("results keep the scenario order")
    void resultsKeepScenarioOrder() {
        RunSchedule r = run(new Fake(), 100, sc("a", 0), Scenario.invalid("b", "s", "src", "x"), sc("c", 0));
        assertEquals(
            "a",
            r.results()
                .get(0).id);
        assertEquals(
            "b",
            r.results()
                .get(1).id);
        assertEquals(
            "c",
            r.results()
                .get(2).id);
    }
}
