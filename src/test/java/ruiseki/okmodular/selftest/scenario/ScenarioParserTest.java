package ruiseki.okmodular.selftest.scenario;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Scenario files are data; one bad scenario must not hide the others, so every per-scenario problem
 * becomes Scenario.invalid instead of an exception.
 */
class ScenarioParserTest {

    private final ScenarioParser parser = new ScenarioParser(Set.of("formed", "blockCount"));

    private List<Scenario> parse(String json) {
        return parser.parseFile("test.json", new StringReader(json));
    }

    private static String scen(String id, String extra) {
        return "{\"id\":\"" + id
            + "\",\"section\":\"s3\",\"title\":\"t\",\"source\":\"TESTING.md s3\",\"ticks\":20,"
            + "\"expect\":[{\"kind\":\"formed\"}]"
            + extra
            + "}";
    }

    @Test
    @DisplayName("minimal scenario gets defaults")
    void parsesMinimalScenario() {
        Scenario s = parse("[" + scen("s3.5-weather-rain", "") + "]").get(0);
        assertEquals("s3.5-weather-rain", s.id);
        assertEquals(0, s.dimension);
        assertEquals(64, s.y);
        assertNull(s.machine);
        assertTrue(s.steps.isEmpty());
        assertNull(s.parseError);
        assertEquals(20, s.ticks);
        assertEquals(1, s.expectations.size());
    }

    @Test
    void stepsAreSortedByTick() {
        String steps = ",\"steps\":[{\"at\":30,\"action\":\"weather\"},{\"at\":0,\"action\":\"fill\"},"
            + "{\"at\":10,\"action\":\"biome\",\"x\":1}]";
        Scenario s = parse("[" + scen("a", steps) + "]").get(0);
        assertNull(s.parseError);
        assertEquals(0, s.steps.get(0).at);
        assertEquals(10, s.steps.get(1).at);
        assertEquals(30, s.steps.get(2).at);
        assertEquals(
            1,
            s.steps.get(1).args.get("x")
                .getAsInt());
        assertFalse(s.steps.get(1).args.has("action"));
    }

    @Test
    void cellsKeepAuthoredKeys() {
        String m = ",\"machine\":{\"structure\":\"x\",\"cells\":{\"C*\":\"mod:blk:2\",\"P#1\":\"mod:other\"}}";
        Scenario s = parse("[" + scen("a", m) + "]").get(0);
        assertNull(s.parseError);
        assertEquals("mod:blk:2", s.machine.cells.get("C*"));
        assertEquals("mod:other", s.machine.cells.get("P#1"));
        assertEquals("NORTH", s.machine.facing);
        assertEquals("NORMAL", s.machine.rotation);
        assertEquals("NONE", s.machine.flip);
    }

    @Test
    void unknownActionMakesScenarioInvalid() {
        Scenario s = parse("[" + scen("a", ",\"steps\":[{\"at\":1,\"action\":\"explode\"}]") + "]").get(0);
        assertTrue(s.parseError.contains("unknown action 'explode'"), s.parseError);
        assertTrue(s.expectations.isEmpty());
    }

    @Test
    void unknownKindMakesScenarioInvalid() {
        String json = "[{\"id\":\"a\",\"source\":\"x\",\"ticks\":1,\"expect\":[{\"kind\":\"formd\"}]}]";
        Scenario s = parse(json).get(0);
        assertTrue(s.parseError.contains("unknown kind 'formd'"), s.parseError);
    }

    @Test
    void missingSourceMakesScenarioInvalid() {
        Scenario s = parse("[{\"id\":\"a\",\"ticks\":1,\"expect\":[]}]").get(0);
        assertTrue(s.parseError.contains("source"), s.parseError);
        assertEquals("a", s.id);
    }

    @Test
    void snapshotReferencedButUndeclaredIsInvalid() {
        String json = "[{\"id\":\"a\",\"source\":\"x\",\"ticks\":1,"
            + "\"expect\":[{\"kind\":\"blockCount\",\"snapshot\":\"end\"}]}]";
        Scenario s = parse(json).get(0);
        assertTrue(s.parseError.contains("snapshot 'end'"), s.parseError);
    }

    @Test
    void declaredSnapshotsKeepRegionAndTick() {
        String snaps = ",\"snapshots\":{\"mid\":{\"from\":[0,0,0],\"to\":[2,1,3],\"at\":5},"
            + "\"end\":{\"from\":[-1,0,0],\"to\":[0,0,0]}}";
        String json = "[{\"id\":\"a\",\"source\":\"x\",\"ticks\":20" + snaps
            + ",\"expect\":[{\"kind\":\"blockCount\",\"snapshot\":\"end\"}]}]";
        Scenario s = parse(json).get(0);
        assertNull(s.parseError);
        assertEquals(
            3,
            s.snapshots.get("mid")
                .sizeX());
        assertEquals(
            2,
            s.snapshots.get("mid")
                .sizeY());
        assertEquals(
            4,
            s.snapshots.get("mid")
                .sizeZ());
        assertTrue(
            s.snapshots.get("end")
                .contains(-1, 0, 0));
        assertFalse(
            s.snapshots.get("end")
                .contains(1, 0, 0));
        assertEquals(5, s.snapshotTicks.get("mid"));
        assertEquals(20, s.snapshotTicks.get("end"));
    }

    @Test
    void oneBadScenarioDoesNotHideTheOthers() {
        String json = "[" + scen("a", "") + ",{\"id\":\"b\",\"ticks\":1,\"expect\":[]}," + scen("c", "") + "]";
        List<Scenario> r = parse(json);
        assertEquals(3, r.size());
        assertNull(r.get(0).parseError);
        assertNotNull(r.get(1).parseError);
        assertNull(r.get(2).parseError);
    }

    @Test
    void duplicateIdsAreInvalid() {
        List<Scenario> r = parse("[" + scen("a", "") + "," + scen("a", "") + "]");
        assertNull(r.get(0).parseError);
        assertTrue(r.get(1).parseError.contains("duplicate id 'a'"), r.get(1).parseError);
    }

    @Test
    void inputKinds() {
        String in = ",\"inputs\":[{\"item\":\"minecraft:stone\",\"amount\":2},{\"fluid\":\"water\",\"amount\":1000},"
            + "{\"energy\":5000}]";
        Scenario s = parse("[" + scen("a", in) + "]").get(0);
        assertNull(s.parseError);
        assertEquals(InputSpec.Kind.ITEM, s.inputs.get(0).kind);
        assertEquals("minecraft:stone", s.inputs.get(0).id);
        assertEquals(2, s.inputs.get(0).amount);
        assertEquals(InputSpec.Kind.FLUID, s.inputs.get(1).kind);
        assertEquals(1000, s.inputs.get(1).amount);
        assertEquals(InputSpec.Kind.ENERGY, s.inputs.get(2).kind);
        assertNull(s.inputs.get(2).id);
        assertEquals(5000, s.inputs.get(2).amount);
    }

    @Test
    void malformedValuesNeverYieldNullParseError() {
        // Gson throws exceptions without a message for these; they must still be invalid.
        String[] bad = { ",\"ticks\":{}", ",\"ticks\":null", ",\"steps\":[{\"at\":[],\"action\":\"fill\"}]",
            ",\"machine\":{\"structure\":\"x\",\"cells\":{\"C*\":null}}", ",\"inputs\":[{\"energy\":null}]" };
        for (String extra : bad) {
            String json = "[{\"id\":\"a\",\"source\":\"x\",\"expect\":[]" + extra + "}]";
            Scenario s = parse(json).get(0);
            assertNotNull(s.parseError, extra);
        }
    }

    @Test
    void unparseableFileThrows() {
        assertThrows(ScenarioParseException.class, () -> parse("{not json"));
        assertThrows(ScenarioParseException.class, () -> parse("42"));
    }
}
