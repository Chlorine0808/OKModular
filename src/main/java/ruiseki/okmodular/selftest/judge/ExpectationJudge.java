package ruiseki.okmodular.selftest.judge;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ruiseki.okmodular.selftest.grid.BlockGrid;
import ruiseki.okmodular.selftest.grid.ShapeMetrics;
import ruiseki.okmodular.selftest.scenario.Expectation;

/** Turns one Expectation plus an Observation into a CheckResult; never throws. */
public final class ExpectationJudge {

    public static final Set<String> KINDS = Collections.unmodifiableSet(
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
                "logContains")));

    /** A cluster this much denser in same-block neighbours than chance counts as clustered. */
    private static final double CLUSTER_MARGIN = 0.15;

    private ExpectationJudge() {}

    public static CheckResult judge(Expectation e, Observation o) {
        try {
            return dispatch(e, o);
        } catch (RuntimeException ex) {
            return new CheckResult(
                e.kind,
                false,
                "valid " + e.kind + " check",
                "judge error: " + (ex.getMessage() != null ? ex.getMessage()
                    : ex.getClass()
                        .getSimpleName()));
        }
    }

    private static CheckResult dispatch(Expectation e, Observation o) {
        JsonObject a = e.args;
        switch (e.kind) {
            case "formed":
                return formed(e, o);
            case "validationError":
                return containing(
                    e.kind,
                    "validation error",
                    "no validation error",
                    o.validationError,
                    str(a, "contains"));
            case "componentTier":
                return componentTier(e, o);
            case "recipeStarted":
                return recipeStarted(e, o);
            case "progressAt":
                return progressAt(e, o);
            case "outputCount":
                return count(e, "output", o.outputItems);
            case "inputCount":
                return count(e, "input", o.inputItems);
            case "droppedCount":
                return count(e, "dropped", o.droppedItems);
            case "blockCount":
                return blockCount(e, o);
            case "blockFraction":
                return blockFraction(e, o);
            case "blockAt":
                return blockAt(e, o);
            case "snapshotsDiffer":
                return snapshotsDiffer(e, o);
            case "sphereCentred":
                return sphereCentred(e, o);
            case "clustered":
                return clustered(e, o);
            case "densityFallsOutward":
                return densityFallsOutward(e, o);
            case "uniformAlong":
                return uniformAlong(e, o);
            case "nbtEqual":
                return nbtEqual(e, o);
            case "outputMatchesPayout":
                return outputMatchesPayout(e, o);
            case "chunkReloaded":
                return new CheckResult(
                    e.kind,
                    o.chunkReloaded,
                    "chunk reloaded",
                    o.chunkReloaded ? "chunk reloaded" : "no reload");
            case "conditionFailure":
                return containing(
                    e.kind,
                    "condition failure",
                    "no condition failure",
                    o.conditionFailure,
                    str(a, "contains"));
            case "fileContains":
                return fileContains(e, o);
            case "logContains":
                return logContains(e, o);
            default:
                throw new IllegalArgumentException("unknown kind " + e.kind);
        }
    }

    // ---- argument helpers (throw IllegalArgumentException, turned into judge errors) ----

    private static JsonElement need(JsonObject a, String key) {
        JsonElement v = a.get(key);
        if (v == null || v.isJsonNull()) throw new IllegalArgumentException("missing argument \"" + key + "\"");
        return v;
    }

    private static String str(JsonObject a, String key) {
        return need(a, key).getAsString();
    }

    private static double num(JsonObject a, String key) {
        return need(a, key).getAsDouble();
    }

    private static double numOr(JsonObject a, String key, double def) {
        return a.has(key) ? num(a, key) : def;
    }

    private static boolean bool(JsonObject a, String key) {
        return need(a, key).getAsBoolean();
    }

    private static double[] triple(JsonObject a, String key) {
        JsonArray arr = need(a, key).getAsJsonArray();
        if (arr.size() != 3) throw new IllegalArgumentException("\"" + key + "\" needs 3 numbers");
        return new double[] { arr.get(0)
            .getAsDouble(),
            arr.get(1)
                .getAsDouble(),
            arr.get(2)
                .getAsDouble() };
    }

    private static int[] intTriple(JsonObject a, String key) {
        double[] d = triple(a, key);
        return new int[] { (int) d[0], (int) d[1], (int) d[2] };
    }

    private static BlockGrid snapshot(Observation o, String name) {
        BlockGrid g = o.snapshots.get(name);
        if (g == null) throw new IllegalArgumentException("unknown snapshot \"" + name + "\"");
        return g;
    }

    /** The named snapshot, narrowed to from..to when given. */
    private static BlockGrid region(JsonObject a, Observation o) {
        BlockGrid g = snapshot(o, str(a, "snapshot"));
        if (a.has("from") || a.has("to")) {
            return g.sub(a.has("from") ? intTriple(a, "from") : g.from(), a.has("to") ? intTriple(a, "to") : g.to());
        }
        return g;
    }

    private static String range(double min, double max) {
        return fmt(min) + ".." + fmt(max);
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }

    private static boolean inRange(double v, double min, double max) {
        return v >= min && v <= max;
    }

    // ---- kinds ----

    private static MachineSample last(Observation o) {
        return o.samples.isEmpty() ? null : o.samples.get(o.samples.size() - 1);
    }

    private static CheckResult formed(Expectation e, Observation o) {
        boolean want = bool(e.args, "value");
        MachineSample s = last(o);
        String actual = s == null ? "no samples" : s.formed ? "formed" : "not formed";
        return new CheckResult(e.kind, s != null && s.formed == want, want ? "formed" : "not formed", actual);
    }

    private static CheckResult containing(String kind, String what, String none, String value, String needle) {
        boolean ok = value != null && value.contains(needle);
        return new CheckResult(
            kind,
            ok,
            what + " containing \"" + needle + "\"",
            value == null ? none : "\"" + value + "\"");
    }

    private static CheckResult componentTier(Expectation e, Observation o) {
        String c = str(e.args, "component");
        int want = (int) num(e.args, "value");
        Integer got = o.componentTiers.get(c);
        return new CheckResult(
            e.kind,
            got != null && got == want,
            c + " tier " + want,
            got == null ? c + " not observed" : c + " tier " + got);
    }

    private static CheckResult recipeStarted(Expectation e, Observation o) {
        boolean want = bool(e.args, "value");
        double from = numOr(e.args, "from", Double.NEGATIVE_INFINITY);
        double to = numOr(e.args, "to", Double.POSITIVE_INFINITY);
        int inWindow = 0;
        MachineSample first = null;
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (MachineSample s : o.samples) {
            if (s.tick < from || s.tick > to) continue;
            inWindow++;
            lo = Math.min(lo, s.tick);
            hi = Math.max(hi, s.tick);
            if (first == null && s.recipe != null) first = s;
        }
        String window = (Double.isInfinite(from) ? lo : fmt(from)) + ".." + (Double.isInfinite(to) ? hi : fmt(to));
        if (inWindow == 0) window = Double.isInfinite(from) && Double.isInfinite(to) ? "any" : window;
        String expected = want ? "recipe starts within ticks " + window : "no recipe within ticks " + window;
        String actual = first != null ? "recipe " + first.recipe + " started at tick " + first.tick
            : "idle for all " + inWindow + " samples";
        return new CheckResult(e.kind, (first != null) == want, expected, actual);
    }

    private static CheckResult progressAt(Expectation e, Observation o) {
        int at = (int) num(e.args, "at");
        double min = num(e.args, "min"), max = num(e.args, "max");
        String expected = "progress at tick " + at + " within " + range(min, max);
        for (MachineSample s : o.samples) {
            if (s.tick == at) {
                return new CheckResult(e.kind, inRange(s.progress, min, max), expected, "progress " + s.progress);
            }
        }
        return new CheckResult(e.kind, false, expected, "no sample at tick " + at);
    }

    private static CheckResult count(Expectation e, String what, Map<String, Integer> items) {
        String item = str(e.args, "item");
        double min = num(e.args, "min"), max = num(e.args, "max");
        int got = items.getOrDefault(item, 0);
        return new CheckResult(
            e.kind,
            inRange(got, min, max),
            what + " " + item + " count within " + range(min, max),
            Integer.toString(got));
    }

    private static CheckResult blockCount(Expectation e, Observation o) {
        BlockGrid g = region(e.args, o);
        String block = str(e.args, "block");
        double min = num(e.args, "min"), max = num(e.args, "max");
        int got = g.count(block);
        return new CheckResult(
            e.kind,
            inRange(got, min, max),
            block + " count within " + range(min, max),
            Integer.toString(got));
    }

    private static CheckResult blockFraction(Expectation e, Observation o) {
        BlockGrid g = region(e.args, o);
        String block = str(e.args, "block");
        double p = num(e.args, "p");
        int n;
        String of;
        if (e.args.has("among")) {
            Set<String> among = new LinkedHashSet<>();
            for (JsonElement x : e.args.getAsJsonArray("among")) among.add(x.getAsString());
            n = 0;
            for (String b : among) n += g.count(b);
            of = "among " + among;
        } else {
            n = g.cells();
            of = "of " + n + " cells";
        }
        int got = g.count(block);
        int[] b = BinomialBounds.of(n, p, BinomialBounds.Z);
        boolean ok = got >= b[0] && got <= b[1];
        if (p > 0.0 && p < 1.0 && (got == 0 || got == n)) ok = false;
        return new CheckResult(
            e.kind,
            ok,
            block + " count within " + b[0] + ".." + b[1] + " (p=" + fmt(p) + ", n=" + n + ", " + of + ")",
            Integer.toString(got));
    }

    private static CheckResult blockAt(Expectation e, Observation o) {
        BlockGrid g = snapshot(o, str(e.args, "snapshot"));
        int[] pos = intTriple(e.args, "pos");
        String want = str(e.args, "block");
        String got = g.contains(pos[0], pos[1], pos[2]) ? g.get(pos[0], pos[1], pos[2]) : "outside the snapshot";
        return new CheckResult(e.kind, want.equals(got), want + " at " + Arrays.toString(pos), got);
    }

    private static CheckResult snapshotsDiffer(Expectation e, Observation o) {
        BlockGrid a = snapshot(o, str(e.args, "a")), b = snapshot(o, str(e.args, "b"));
        double min = numOr(e.args, "min", 1);
        int diff = ShapeMetrics.differingCells(a, b);
        return new CheckResult(
            e.kind,
            diff >= min,
            "at least " + fmt(min) + " differing cells",
            diff + " differing cells");
    }

    private static CheckResult sphereCentred(Expectation e, Observation o) {
        BlockGrid g = region(e.args, o);
        String block = str(e.args, "block");
        double[] centre = triple(e.args, "centre");
        double tol = numOr(e.args, "tolerance", 0.5);
        double[] c = ShapeMetrics.centroid(g, block);
        boolean ok = true;
        for (int i = 0; i < 3; i++) ok &= Math.abs(c[i] - centre[i]) <= tol; // NaN compares false
        return new CheckResult(
            e.kind,
            ok,
            block + " centroid within " + fmt(tol) + " of " + Arrays.toString(centre),
            Double.isNaN(c[0]) ? block + " absent" : String.format("(%.2f, %.2f, %.2f)", c[0], c[1], c[2]));
    }

    private static CheckResult clustered(Expectation e, Observation o) {
        BlockGrid g = region(e.args, o);
        String block = str(e.args, "block");
        boolean want = bool(e.args, "value");
        double density = ShapeMetrics.density(g, block);
        double ratio = ShapeMetrics.sameNeighbourRatio(g, block);
        boolean clustered = ratio - density >= CLUSTER_MARGIN;
        return new CheckResult(
            e.kind,
            clustered == want,
            block + (want ? " clustered" : " scattered")
                + " (same-neighbour ratio - density "
                + (want ? ">= " : "< ")
                + CLUSTER_MARGIN
                + ")",
            String.format("ratio %.2f - density %.2f = %.2f", ratio, density, ratio - density));
    }

    private static CheckResult densityFallsOutward(Expectation e, Observation o) {
        BlockGrid g = region(e.args, o);
        String block = str(e.args, "block");
        double[] d = ShapeMetrics.radialDensity(g, block, triple(e.args, "centre"), 3, num(e.args, "radius"));
        boolean ok = d[0] >= d[1] && d[1] >= d[2] && d[0] > d[2];
        return new CheckResult(
            e.kind,
            ok,
            block + " density falls from the inner to the outer band",
            String.format("bands %.2f, %.2f, %.2f", d[0], d[1], d[2]));
    }

    private static CheckResult uniformAlong(Expectation e, Observation o) {
        BlockGrid g = region(e.args, o);
        String block = str(e.args, "block");
        String axis = str(e.args, "axis");
        if (axis.length() != 1) throw new IllegalArgumentException("axis must be x, y or z");
        boolean want = bool(e.args, "value");
        boolean got = ShapeMetrics.uniformAlong(g, block, axis.charAt(0));
        return new CheckResult(
            e.kind,
            got == want,
            block + (want ? " uniform" : " not uniform") + " along " + axis,
            got ? "uniform" : "not uniform");
    }

    private static CheckResult nbtEqual(Expectation e, Observation o) {
        String la = str(e.args, "a"), lb = str(e.args, "b");
        Map<String, String> a = o.nbt.get(la), b = o.nbt.get(lb);
        String expected = "NBT keys " + e.args.get("keys") + " equal in " + la + " and " + lb;
        if (a == null || b == null) {
            return new CheckResult(e.kind, false, expected, "no NBT recorded for " + (a == null ? la : lb));
        }
        for (JsonElement k : need(e.args, "keys").getAsJsonArray()) {
            String key = k.getAsString();
            String va = a.get(key), vb = b.get(key);
            if (va == null || !va.equals(vb)) {
                return new CheckResult(e.kind, false, expected, key + ": " + la + "=" + va + ", " + lb + "=" + vb);
            }
        }
        return new CheckResult(e.kind, true, expected, "equal");
    }

    private static CheckResult outputMatchesPayout(Expectation e, Observation o) {
        String item = str(e.args, "item"), label = str(e.args, "label");
        Map<String, String> n = o.nbt.get(label);
        String flag = n == null ? null : n.get("payoutSucceeded");
        if (flag == null) {
            return new CheckResult(e.kind, false, "payoutSucceeded recorded under " + label, "not recorded");
        }
        boolean paid = Boolean.parseBoolean(flag);
        int got = o.outputItems.getOrDefault(item, 0);
        return new CheckResult(
            e.kind,
            paid ? got >= 1 : got == 0,
            paid ? "payout succeeded, so output " + item + " >= 1" : "payout failed, so output " + item + " = 0",
            "output " + item + " = " + got);
    }

    private static CheckResult fileContains(Expectation e, Observation o) {
        String path = str(e.args, "path"), text = str(e.args, "text");
        String content = o.files.get(path);
        return new CheckResult(
            e.kind,
            content != null && content.contains(text),
            path + " containing \"" + text + "\"",
            content == null ? "file not found" : "file lacks the text");
    }

    private static CheckResult logContains(Expectation e, Observation o) {
        String text = str(e.args, "text");
        int lines = 0;
        for (String l : o.logLines) if (l.contains(text)) lines++;
        if (e.args.has("count")) {
            int want = (int) num(e.args, "count");
            return new CheckResult(
                e.kind,
                lines == want,
                want + " log lines containing \"" + text + "\"",
                lines + " lines");
        }
        boolean found = lines > 0;
        return new CheckResult(
            e.kind,
            found,
            "a log line containing \"" + text + "\"",
            found ? "found" : "none of " + o.logLines.size() + " lines");
    }
}
