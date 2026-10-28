package ruiseki.okmodular.selftest.scenario;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** One selftest scenario; an unparseable one carries parseError instead of expectations. */
public final class Scenario {

    public final String id;
    public final String section;
    public final String title;
    public final String source;
    public final int dimension;
    public final int y;
    public final boolean visual;
    public final boolean exclusive;
    /** Nullable. */
    public final MachineSpec machine;
    public final List<InputSpec> inputs;
    /** Sorted by tick. */
    public final List<Step> steps;
    public final int ticks;
    /** Snapshot name to region. */
    public final Map<String, Region> snapshots;
    /** Snapshot name to the tick it is taken at (defaults to ticks). */
    public final Map<String, Integer> snapshotTicks;
    public final List<Expectation> expectations;
    /** Null when valid. */
    public final String parseError;

    public Scenario(String id, String section, String title, String source, int dimension, int y, boolean visual,
        boolean exclusive, MachineSpec machine, List<InputSpec> inputs, List<Step> steps, int ticks,
        Map<String, Region> snapshots, Map<String, Integer> snapshotTicks, List<Expectation> expectations,
        String parseError) {
        this.id = id;
        this.section = section;
        this.title = title;
        this.source = source;
        this.dimension = dimension;
        this.y = y;
        this.visual = visual;
        this.exclusive = exclusive;
        this.machine = machine;
        this.inputs = inputs;
        this.steps = steps;
        this.ticks = ticks;
        this.snapshots = snapshots;
        this.snapshotTicks = snapshotTicks;
        this.expectations = expectations;
        this.parseError = parseError;
    }

    public static Scenario invalid(String id, String section, String source, String message) {
        return new Scenario(
            id,
            section,
            id,
            source,
            0,
            64,
            false,
            false,
            null,
            Collections.<InputSpec>emptyList(),
            Collections.<Step>emptyList(),
            0,
            Collections.<String, Region>emptyMap(),
            Collections.<String, Integer>emptyMap(),
            Collections.<Expectation>emptyList(),
            message != null ? message : "invalid scenario");
    }
}
