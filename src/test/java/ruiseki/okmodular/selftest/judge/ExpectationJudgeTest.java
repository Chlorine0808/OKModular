package ruiseki.okmodular.selftest.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ruiseki.okmodular.selftest.grid.BlockGrid;
import ruiseki.okmodular.selftest.scenario.Expectation;

/**
 * Each kind gets at least one passing and one failing case. The judge never throws: bad input (unknown snapshot,
 * missing argument) becomes a failed CheckResult whose actual text names the problem.
 */
class ExpectationJudgeTest {

    private static CheckResult judge(String json, Observation o) {
        JsonObject args = new JsonParser().parse(json)
            .getAsJsonObject();
        return ExpectationJudge.judge(
            new Expectation(
                args.get("kind")
                    .getAsString(),
                args),
            o);
    }

    private static Observation obs() {
        return new Observation();
    }

    private static BlockGrid grid(int size) {
        return new BlockGrid(new int[] { 0, 0, 0 }, new int[] { size - 1, 0, 0 });
    }

    private static BlockGrid cube(int half) {
        return new BlockGrid(new int[] { -half, -half, -half }, new int[] { half, half, half });
    }

    private static Observation withGrid(String name, BlockGrid g) {
        Observation o = obs();
        o.snapshots.put(name, g);
        return o;
    }

    private static Observation withSamples(MachineSample... s) {
        Observation o = obs();
        o.samples.addAll(Arrays.asList(s));
        return o;
    }

    @Test
    void everyKindIsRegistered() {
        assertEquals(
            new HashSet<>(
                Arrays.asList(
                    "formed",
                    "validationError",
                    "componentTier",
                    "recipeStarted",
                    "progressAt",
                    "outputCount",
                    "inputCount",
                    "droppedCount",
                    "blockCount",
                    "blockFraction",
                    "blockAt",
                    "snapshotsDiffer",
                    "sphereCentred",
                    "clustered",
                    "densityFallsOutward",
                    "uniformAlong",
                    "nbtEqual",
                    "outputMatchesPayout",
                    "chunkReloaded",
                    "conditionFailure",
                    "fileContains",
                    "logContains")),
            ExpectationJudge.KINDS);
    }

    @Test
    void formedUsesTheLastSample() {
        Observation o = withSamples(
            new MachineSample(0, false, null, 0, false),
            new MachineSample(1, true, null, 0, false));
        assertTrue(judge("{\"kind\":\"formed\",\"value\":true}", o).passed);
        assertFalse(judge("{\"kind\":\"formed\",\"value\":false}", o).passed);
        assertFalse(judge("{\"kind\":\"formed\",\"value\":true}", obs()).passed);
    }

    @Test
    void validationErrorMatchesSubstring() {
        Observation o = obs();
        o.validationError = "missing casing at 1,2,3";
        assertTrue(judge("{\"kind\":\"validationError\",\"contains\":\"casing\"}", o).passed);
        assertFalse(judge("{\"kind\":\"validationError\",\"contains\":\"glass\"}", o).passed);
        assertFalse(judge("{\"kind\":\"validationError\",\"contains\":\"glass\"}", obs()).passed);
    }

    @Test
    void componentTierComparesTheValue() {
        Observation o = obs();
        o.componentTiers.put("energy", 3);
        assertTrue(judge("{\"kind\":\"componentTier\",\"component\":\"energy\",\"value\":3}", o).passed);
        assertFalse(judge("{\"kind\":\"componentTier\",\"component\":\"energy\",\"value\":2}", o).passed);
        assertFalse(judge("{\"kind\":\"componentTier\",\"component\":\"fluid\",\"value\":2}", o).passed);
    }

    private static Observation startedAt50() {
        Observation o = obs();
        for (int t = 0; t <= 60; t++) o.samples.add(new MachineSample(t, true, t >= 50 ? "r" : null, 0, false));
        return o;
    }

    @Test
    void recipeStartedLooksOnlyInsideTheWindow() {
        Observation o = startedAt50();
        CheckResult early = judge("{\"kind\":\"recipeStarted\",\"value\":true,\"from\":0,\"to\":40}", o);
        assertFalse(early.passed);
        assertEquals("recipe starts within ticks 0..40", early.expected);
        assertEquals("idle for all 41 samples", early.actual);
        assertTrue(judge("{\"kind\":\"recipeStarted\",\"value\":true,\"from\":0,\"to\":60}", o).passed);
        assertTrue(judge("{\"kind\":\"recipeStarted\",\"value\":false,\"from\":0,\"to\":40}", o).passed);
    }

    @Test
    void recipeNotStartedPassesWhenIdleThroughout() {
        Observation o = obs();
        for (int t = 0; t <= 60; t++) o.samples.add(new MachineSample(t, true, null, 0, false));
        CheckResult c = judge("{\"kind\":\"recipeStarted\",\"value\":false}", o);
        assertTrue(c.passed);
        assertEquals("idle for all 61 samples", c.actual);
        assertFalse(judge("{\"kind\":\"recipeStarted\",\"value\":true}", o).passed);
    }

    @Test
    void progressAtChecksTheSampleOfThatTick() {
        Observation o = withSamples(new MachineSample(10, true, "r", 0.5f, false));
        assertTrue(judge("{\"kind\":\"progressAt\",\"at\":10,\"min\":0.4,\"max\":0.6}", o).passed);
        assertFalse(judge("{\"kind\":\"progressAt\",\"at\":10,\"min\":0.6,\"max\":0.9}", o).passed);
        assertFalse(judge("{\"kind\":\"progressAt\",\"at\":11,\"min\":0,\"max\":1}", o).passed);
    }

    @Test
    void itemCountsUseTheirOwnMaps() {
        Observation o = obs();
        o.outputItems.put("a", 5);
        o.inputItems.put("a", 1);
        o.droppedItems.put("a", 0);
        assertTrue(judge("{\"kind\":\"outputCount\",\"item\":\"a\",\"min\":5,\"max\":5}", o).passed);
        assertFalse(judge("{\"kind\":\"outputCount\",\"item\":\"a\",\"min\":6,\"max\":9}", o).passed);
        assertTrue(judge("{\"kind\":\"inputCount\",\"item\":\"a\",\"min\":1,\"max\":2}", o).passed);
        assertFalse(judge("{\"kind\":\"inputCount\",\"item\":\"a\",\"min\":2,\"max\":3}", o).passed);
        assertTrue(judge("{\"kind\":\"droppedCount\",\"item\":\"a\",\"min\":0,\"max\":0}", o).passed);
        assertTrue(judge("{\"kind\":\"droppedCount\",\"item\":\"missing\",\"min\":0,\"max\":0}", o).passed);
    }

    @Test
    void blockCountHonoursTheSubRegion() {
        BlockGrid g = grid(4);
        g.set(0, 0, 0, "m:a");
        g.set(3, 0, 0, "m:a");
        Observation o = withGrid("s", g);
        assertTrue(
            judge("{\"kind\":\"blockCount\",\"snapshot\":\"s\",\"block\":\"m:a\",\"min\":2,\"max\":2}", o).passed);
        assertTrue(
            judge(
                "{\"kind\":\"blockCount\",\"snapshot\":\"s\",\"block\":\"m:a\",\"min\":1,\"max\":1,\"from\":[0,0,0],\"to\":[1,0,0]}",
                o).passed);
        assertFalse(
            judge("{\"kind\":\"blockCount\",\"snapshot\":\"s\",\"block\":\"m:a\",\"min\":3,\"max\":4}", o).passed);
    }

    @Test
    void fractionAcceptsAnExpectedCountAndRejectsAWildOne() {
        BlockGrid g = grid(1000);
        for (int i = 0; i < 500; i++) g.set(i, 0, 0, "m:a");
        Observation o = withGrid("s", g);
        assertTrue(judge("{\"kind\":\"blockFraction\",\"snapshot\":\"s\",\"block\":\"m:a\",\"p\":0.5}", o).passed);
        assertFalse(judge("{\"kind\":\"blockFraction\",\"snapshot\":\"s\",\"block\":\"m:a\",\"p\":0.1}", o).passed);
    }

    @Test
    void fractionRejectsAllOrNothingEvenWhenBoundsAllowIt() {
        BlockGrid all = grid(4);
        for (int i = 0; i < 4; i++) all.set(i, 0, 0, "m:a");
        // n=4, p=0.5: the bounds are [0,4], but "all four" and "none" must still fail
        String json = "{\"kind\":\"blockFraction\",\"snapshot\":\"s\",\"block\":\"m:a\",\"p\":0.5}";
        assertFalse(judge(json, withGrid("s", all)).passed);
        assertFalse(judge(json, withGrid("s", grid(4))).passed);
    }

    @Test
    void fractionWithPOfOneNeedsEveryCell() {
        BlockGrid g = grid(4);
        for (int i = 0; i < 4; i++) g.set(i, 0, 0, "m:a");
        assertTrue(
            judge(
                "{\"kind\":\"blockFraction\",\"snapshot\":\"s\",\"block\":\"m:a\",\"p\":1.0}",
                withGrid("s", g)).passed);
    }

    @Test
    void fractionAmongCountsOnlyListedBlocks() {
        // 2000 cells: 800 A, 200 B, 1000 stone; weights 8:2 over A/B ignoring stone
        BlockGrid g = grid(2000);
        for (int i = 0; i < 800; i++) g.set(i, 0, 0, "m:a");
        for (int i = 800; i < 1000; i++) g.set(i, 0, 0, "m:b");
        for (int i = 1000; i < 2000; i++) g.set(i, 0, 0, "m:stone");
        Observation o = withGrid("s", g);
        assertTrue(
            judge(
                "{\"kind\":\"blockFraction\",\"snapshot\":\"s\",\"block\":\"m:a\",\"p\":0.8,\"among\":[\"m:a\",\"m:b\"]}",
                o).passed);
        // without among the stone shrinks the share to 0.4, so 0.8 must fail
        assertFalse(judge("{\"kind\":\"blockFraction\",\"snapshot\":\"s\",\"block\":\"m:a\",\"p\":0.8}", o).passed);
    }

    @Test
    void blockAtMatchesTheBlock() {
        BlockGrid g = grid(2);
        g.set(1, 0, 0, "m:a");
        Observation o = withGrid("s", g);
        assertTrue(judge("{\"kind\":\"blockAt\",\"snapshot\":\"s\",\"pos\":[1,0,0],\"block\":\"m:a\"}", o).passed);
        CheckResult bad = judge("{\"kind\":\"blockAt\",\"snapshot\":\"s\",\"pos\":[0,0,0],\"block\":\"m:a\"}", o);
        assertFalse(bad.passed);
        assertEquals("minecraft:air", bad.actual);
    }

    @Test
    void snapshotsDifferNeedsEnoughDifferingCells() {
        BlockGrid a = grid(3), b = grid(3);
        b.set(0, 0, 0, "m:x");
        b.set(1, 0, 0, "m:x");
        Observation o = obs();
        o.snapshots.put("a", a);
        o.snapshots.put("b", b);
        assertTrue(judge("{\"kind\":\"snapshotsDiffer\",\"a\":\"a\",\"b\":\"b\",\"min\":2}", o).passed);
        assertFalse(judge("{\"kind\":\"snapshotsDiffer\",\"a\":\"a\",\"b\":\"b\",\"min\":3}", o).passed);
        assertFalse(judge("{\"kind\":\"snapshotsDiffer\",\"a\":\"a\",\"b\":\"a\"}", o).passed);
        assertTrue(judge("{\"kind\":\"snapshotsDiffer\",\"a\":\"a\",\"b\":\"b\"}", o).passed);
    }

    @Test
    void sphereCentredComparesTheCentroidWithTolerance() {
        BlockGrid g = cube(5);
        g.set(1, 0, 0, "m:a");
        g.set(-1, 0, 0, "m:a");
        g.set(0, 2, 0, "m:a");
        g.set(0, -2, 0, "m:a");
        Observation o = withGrid("s", g);
        assertTrue(
            judge("{\"kind\":\"sphereCentred\",\"snapshot\":\"s\",\"block\":\"m:a\",\"centre\":[0,0,0]}", o).passed);
        assertFalse(
            judge(
                "{\"kind\":\"sphereCentred\",\"snapshot\":\"s\",\"block\":\"m:a\",\"centre\":[2,0,0],\"tolerance\":0.5}",
                o).passed);
        assertTrue(
            judge(
                "{\"kind\":\"sphereCentred\",\"snapshot\":\"s\",\"block\":\"m:a\",\"centre\":[2,0,0],\"tolerance\":2}",
                o).passed);
        // an absent block has a NaN centroid and must fail rather than pass
        assertFalse(
            judge("{\"kind\":\"sphereCentred\",\"snapshot\":\"s\",\"block\":\"m:none\",\"centre\":[0,0,0]}", o).passed);
    }

    @Test
    void clusteredSeparatesBlobsFromSaltAndPepper() {
        BlockGrid blob = cube(5);
        for (int x = -5; x <= -1; x++)
            for (int y = -5; y <= 0; y++) for (int z = -5; z <= 0; z++) blob.set(x, y, z, "m:a");
        BlockGrid salt = cube(5);
        for (int x = -5; x <= 5; x++) for (int y = -5; y <= 5; y++)
            for (int z = -5; z <= 5; z++) if ((x + y + z) % 2 == 0) salt.set(x, y, z, "m:a");
        Observation o = obs();
        o.snapshots.put("blob", blob);
        o.snapshots.put("salt", salt);
        assertTrue(judge("{\"kind\":\"clustered\",\"snapshot\":\"blob\",\"block\":\"m:a\",\"value\":true}", o).passed);
        assertFalse(
            judge("{\"kind\":\"clustered\",\"snapshot\":\"blob\",\"block\":\"m:a\",\"value\":false}", o).passed);
        assertTrue(judge("{\"kind\":\"clustered\",\"snapshot\":\"salt\",\"block\":\"m:a\",\"value\":false}", o).passed);
        assertFalse(judge("{\"kind\":\"clustered\",\"snapshot\":\"salt\",\"block\":\"m:a\",\"value\":true}", o).passed);
    }

    /**
     * The margin is 0.15: thresholded noise(6) in the 27^3 box of s3.56-noise-threshold scores
     * 0.416 +- 0.051, so 0.25 sat only 3.2 sd away (a false fail about once in 2300 runs).
     */
    @Test
    void clusteredMarginIsPointFifteen() {
        // "aa....aaaa" along x: same-neighbour ratio 8/10, density 6/10, margin 0.2
        BlockGrid line = new BlockGrid(new int[] { 0, 0, 0 }, new int[] { 9, 0, 0 });
        for (int x : new int[] { 0, 1, 6, 7, 8, 9 }) line.set(x, 0, 0, "m:a");
        // "aaaa..aaaa": ratio 12/14, density 8/10, margin 0.06
        BlockGrid dense = new BlockGrid(new int[] { 0, 0, 0 }, new int[] { 9, 0, 0 });
        for (int x : new int[] { 0, 1, 2, 3, 6, 7, 8, 9 }) dense.set(x, 0, 0, "m:a");
        Observation o = obs();
        o.snapshots.put("line", line);
        o.snapshots.put("dense", dense);
        assertTrue(judge("{\"kind\":\"clustered\",\"snapshot\":\"line\",\"block\":\"m:a\",\"value\":true}", o).passed);
        assertTrue(
            judge("{\"kind\":\"clustered\",\"snapshot\":\"dense\",\"block\":\"m:a\",\"value\":false}", o).passed);
    }

    @Test
    void densityFallsOutwardNeedsAMonotoneProfile() {
        BlockGrid falling = cube(6);
        BlockGrid rising = cube(6);
        for (int x = -6; x <= 6; x++) for (int y = -6; y <= 6; y++) for (int z = -6; z <= 6; z++) {
            double r = Math.sqrt(x * x + y * y + z * z);
            if (r < 2) falling.set(x, y, z, "m:a");
            else if (r < 4 && (x + y + z) % 2 == 0) falling.set(x, y, z, "m:a");
            if (r >= 4 && r < 6) rising.set(x, y, z, "m:a");
        }
        Observation o = obs();
        o.snapshots.put("f", falling);
        o.snapshots.put("r", rising);
        assertTrue(
            judge(
                "{\"kind\":\"densityFallsOutward\",\"snapshot\":\"f\",\"block\":\"m:a\",\"centre\":[0,0,0],\"radius\":6}",
                o).passed);
        assertFalse(
            judge(
                "{\"kind\":\"densityFallsOutward\",\"snapshot\":\"r\",\"block\":\"m:a\",\"centre\":[0,0,0],\"radius\":6}",
                o).passed);
    }

    @Test
    void uniformAlongFollowsTheColumns() {
        BlockGrid g = new BlockGrid(new int[] { 0, 0, 0 }, new int[] { 1, 2, 0 });
        for (int y = 0; y <= 2; y++) g.set(0, y, 0, "m:a");
        Observation o = withGrid("s", g);
        String yes = "{\"kind\":\"uniformAlong\",\"snapshot\":\"s\",\"block\":\"m:a\",\"axis\":\"y\",\"value\":true}";
        String no = "{\"kind\":\"uniformAlong\",\"snapshot\":\"s\",\"block\":\"m:a\",\"axis\":\"y\",\"value\":false}";
        assertTrue(judge(yes, o).passed);
        g.set(1, 1, 0, "m:a");
        assertFalse(judge(yes, o).passed);
        assertTrue(judge(no, o).passed);
    }

    @Test
    void nbtEqualComparesEachKey() {
        Observation o = obs();
        o.nbt.put("before", new HashMap<>(Collections.singletonMap("k", "1")));
        o.nbt.put("after", new HashMap<>(Collections.singletonMap("k", "1")));
        o.nbt.get("after")
            .put("j", "x");
        String json = "{\"kind\":\"nbtEqual\",\"a\":\"before\",\"b\":\"after\",\"keys\":[\"k\"]}";
        assertTrue(judge(json, o).passed);
        o.nbt.get("after")
            .put("k", "2");
        assertFalse(judge(json, o).passed);
        assertFalse(judge("{\"kind\":\"nbtEqual\",\"a\":\"before\",\"b\":\"nope\",\"keys\":[\"k\"]}", o).passed);
    }

    @Test
    void outputMatchesPayoutFollowsTheDraw() {
        String json = "{\"kind\":\"outputMatchesPayout\",\"item\":\"i\",\"label\":\"run\"}";
        Observation o = obs();
        o.nbt.put("run", new HashMap<>(Collections.singletonMap("payoutSucceeded", "false")));
        assertTrue(judge(json, o).passed);
        o.outputItems.put("i", 1);
        assertFalse(judge(json, o).passed);
        o.nbt.get("run")
            .put("payoutSucceeded", "true");
        assertTrue(judge(json, o).passed);
        o.outputItems.clear();
        assertFalse(judge(json, o).passed);
    }

    @Test
    void flagsAndTextKinds() {
        Observation o = obs();
        assertFalse(judge("{\"kind\":\"chunkReloaded\"}", o).passed);
        o.chunkReloaded = true;
        assertTrue(judge("{\"kind\":\"chunkReloaded\"}", o).passed);

        assertFalse(judge("{\"kind\":\"conditionFailure\",\"contains\":\"dark\"}", o).passed);
        o.conditionFailure = "too dark";
        assertTrue(judge("{\"kind\":\"conditionFailure\",\"contains\":\"dark\"}", o).passed);

        o.files.put("a.txt", "hello world");
        assertTrue(judge("{\"kind\":\"fileContains\",\"path\":\"a.txt\",\"text\":\"world\"}", o).passed);
        assertFalse(judge("{\"kind\":\"fileContains\",\"path\":\"a.txt\",\"text\":\"mars\"}", o).passed);
        assertFalse(judge("{\"kind\":\"fileContains\",\"path\":\"b.txt\",\"text\":\"x\"}", o).passed);

        o.logLines.add("ERROR boom");
        assertTrue(judge("{\"kind\":\"logContains\",\"text\":\"boom\"}", o).passed);
        assertFalse(judge("{\"kind\":\"logContains\",\"text\":\"fine\"}", o).passed);
    }

    @Test
    void expectedAndActualAreHumanReadable() {
        CheckResult c = judge("{\"kind\":\"validationError\",\"contains\":\"casing\"}", obs());
        assertEquals("validationError", c.kind);
        assertEquals("validation error containing \"casing\"", c.expected);
        assertEquals("no validation error", c.actual);
    }

    @Test
    void unknownSnapshotFailsWithMessageNotException() {
        CheckResult c = judge(
            "{\"kind\":\"blockCount\",\"snapshot\":\"ghost\",\"block\":\"m:a\",\"min\":0,\"max\":1}",
            obs());
        assertFalse(c.passed);
        assertTrue(c.actual.contains("ghost"), c.actual);
    }

    @Test
    void judgeErrorNamesTheExceptionClassWhenItHasNoMessage() {
        // an empty array makes Gson throw an IllegalStateException without a message
        CheckResult c = judge("{\"kind\":\"componentTier\",\"component\":\"x\",\"value\":[]}", obs());
        assertFalse(c.passed);
        assertFalse(c.actual.contains("null"), c.actual);
        assertTrue(c.actual.contains("IllegalStateException"), c.actual);
    }

    @Test
    void unknownKindAndMissingArgumentsFailWithJudgeError() {
        CheckResult unknown = judge("{\"kind\":\"bogus\"}", obs());
        assertFalse(unknown.passed);
        assertTrue(unknown.actual.startsWith("judge error"), unknown.actual);
        CheckResult missing = judge("{\"kind\":\"validationError\"}", obs());
        assertFalse(missing.passed);
        assertTrue(missing.actual.startsWith("judge error"), missing.actual);
    }
}
