package ruiseki.okmodular.selftest;

import ruiseki.okmodular.selftest.judge.ScenarioResult;
import ruiseki.okmodular.selftest.scenario.Scenario;

/** Runs one scenario against the real world; RunSchedule calls it in order and contains its exceptions. */
public interface ScenarioDriver {

    void setUp(Scenario s) throws Exception;

    void tick(Scenario s, int t) throws Exception;

    ScenarioResult finish(Scenario s) throws Exception;

    void tearDown(Scenario s);
}
