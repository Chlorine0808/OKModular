package ruiseki.okmodular.selftest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import ruiseki.okmodular.selftest.judge.CheckResult;
import ruiseki.okmodular.selftest.judge.ExpectationJudge;
import ruiseki.okmodular.selftest.judge.Observation;
import ruiseki.okmodular.selftest.judge.ScenarioResult;
import ruiseki.okmodular.selftest.scenario.Expectation;
import ruiseki.okmodular.selftest.scenario.Scenario;
import ruiseki.okmodular.selftest.scenario.Step;

/** Runs each scenario in the live server world: its own site and machine, steps on their ticks, then the judge. */
final class LiveDriver implements ScenarioDriver {

    private static final long NOON = 6000;

    private final TestSites sites = new TestSites();
    private TestSites.Site site;
    private MachineRig rig;
    private Observation obs;
    private int nextStep;
    /** Snapshots a step takes; the schedule leaves those alone. */
    private Set<String> stepSnapshots;

    @Override
    public void setUp(Scenario s) {
        obs = new Observation();
        nextStep = 0;
        stepSnapshots = new HashSet<>();
        for (Step step : s.steps) {
            if (step.action.equals("snapshot") && step.args.has("name")) {
                stepSnapshots.add(
                    step.args.get("name")
                        .getAsString());
            }
        }
        // Every scenario starts at noon in clear weather, whatever the previous one left behind.
        for (WorldServer w : DimensionManager.getWorlds()) {
            w.getGameRules()
                .setOrCreateGameRule("doDaylightCycle", "false");
            w.setWorldTime(NOON);
            WorldActions.weather(w, "clear");
        }
        site = sites.allocate(s);
        rig = s.machine != null ? MachineRig.build(site, s.machine) : null;
        if (!s.inputs.isEmpty()) {
            if (rig == null) throw new IllegalStateException("inputs need a machine");
            rig.insert(s.inputs);
        }
    }

    @Override
    public void tick(Scenario s, int t) {
        while (nextStep < s.steps.size() && s.steps.get(nextStep).at <= t) {
            WorldActions.apply(s.steps.get(nextStep++), site, rig, obs);
        }
        if (rig != null) obs.samples.add(Observer.sample(rig, t));
        for (Map.Entry<String, Integer> e : s.snapshotTicks.entrySet()) {
            if (e.getValue() == t && !stepSnapshots.contains(e.getKey())) {
                obs.snapshots.put(e.getKey(), Observer.snapshot(site, s.snapshots.get(e.getKey()), rig));
            }
        }
    }

    @Override
    public ScenarioResult finish(Scenario s) {
        Observer.finish(rig, site, obs);
        List<CheckResult> checks = new ArrayList<>();
        for (Expectation e : s.expectations) checks.add(ExpectationJudge.judge(e, obs));
        return ScenarioResult.of(s, checks, obs.snapshots);
    }

    @Override
    public void tearDown(Scenario s) {
        if (site != null) sites.release(site);
        site = null;
        rig = null;
        obs = null;
    }
}
