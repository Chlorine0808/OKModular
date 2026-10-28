package ruiseki.okmodular.selftest.scenario;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Reads scenario files (a JSON array, or an object with a "scenarios" array). A bad scenario becomes
 * Scenario.invalid so the others still run.
 */
public final class ScenarioParser {

    private final Set<String> knownKinds;

    public ScenarioParser(Set<String> knownKinds) {
        this.knownKinds = knownKinds;
    }

    public List<Scenario> parseFile(String fileName, Reader json) {
        JsonElement root;
        try {
            root = new JsonParser().parse(json);
        } catch (RuntimeException e) {
            throw new ScenarioParseException(fileName + ": not valid JSON: " + e.getMessage(), e);
        }
        JsonArray array;
        if (root.isJsonArray()) {
            array = root.getAsJsonArray();
        } else if (root.isJsonObject() && root.getAsJsonObject()
            .has("scenarios")
            && root.getAsJsonObject()
                .get("scenarios")
                .isJsonArray()) {
                    array = root.getAsJsonObject()
                        .getAsJsonArray("scenarios");
                } else {
                    throw new ScenarioParseException(fileName + ": expected an array of scenarios");
                }
        List<Scenario> out = new ArrayList<Scenario>();
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < array.size(); i++) {
            Scenario s = parseOne(fileName, i, array.get(i));
            if (s.parseError == null && !seen.add(s.id)) {
                s = Scenario.invalid(s.id, s.section, s.source, "duplicate id '" + s.id + "'");
            }
            out.add(s);
        }
        return out;
    }

    private Scenario parseOne(String fileName, int index, JsonElement el) {
        String fallback = fileName + "#" + index;
        if (!el.isJsonObject()) {
            return Scenario.invalid(fallback, null, fileName, "scenario is not an object");
        }
        JsonObject o = el.getAsJsonObject();
        String id = str(o, "id");
        String section = str(o, "section");
        String source = str(o, "source");
        String shown = id != null ? id : fallback;
        try {
            if (id == null) throw new ScenarioParseException("missing id");
            if (source == null) throw new ScenarioParseException("missing source");
            String title = strOr(o, "title", id);
            int ticks = intOr(o, "ticks", 0);

            MachineSpec machine = o.has("machine") ? machine(o.get("machine")) : null;
            List<InputSpec> inputs = o.has("inputs") ? inputs(o) : new ArrayList<InputSpec>();
            List<Step> steps = new ArrayList<Step>();
            if (o.has("steps")) {
                for (JsonElement e : array(o, "steps")) steps.add(step(e));
            }
            Collections.sort(steps, new Comparator<Step>() {

                @Override
                public int compare(Step a, Step b) {
                    return Integer.compare(a.at, b.at);
                }
            });

            Map<String, Region> snapshots = new LinkedHashMap<String, Region>();
            Map<String, Integer> snapshotTicks = new LinkedHashMap<String, Integer>();
            if (o.has("snapshots")) {
                for (Map.Entry<String, JsonElement> e : obj(o.get("snapshots"), "snapshots").entrySet()) {
                    JsonObject r = obj(e.getValue(), "snapshot '" + e.getKey() + "'");
                    snapshots.put(e.getKey(), new Region(triple(r, "from"), triple(r, "to")));
                    snapshotTicks.put(e.getKey(), intOr(r, "at", ticks));
                }
            }

            List<Expectation> expectations = new ArrayList<Expectation>();
            for (JsonElement e : array(o, "expect")) {
                JsonObject x = obj(e, "expectation");
                String kind = str(x, "kind");
                if (kind == null) throw new ScenarioParseException("expectation without kind");
                if (!knownKinds.contains(kind)) throw new ScenarioParseException("unknown kind '" + kind + "'");
                String snap = str(x, "snapshot");
                if (snap != null && !snapshots.containsKey(snap)) {
                    throw new ScenarioParseException("snapshot '" + snap + "' is not declared");
                }
                expectations.add(new Expectation(kind, x));
            }
            return new Scenario(
                id,
                section,
                title,
                source,
                intOr(o, "dimension", 0),
                intOr(o, "y", 64),
                bool(o, "visual"),
                bool(o, "exclusive"),
                machine,
                inputs,
                steps,
                ticks,
                snapshots,
                snapshotTicks,
                expectations,
                null);
        } catch (ScenarioParseException | ClassCastException | IllegalStateException | NumberFormatException
            | UnsupportedOperationException e) {
            return Scenario.invalid(shown, section, source != null ? source : fileName, describe(e));
        }
    }

    /** Gson exceptions often carry no message; never let that turn into a null parseError. */
    private static String describe(RuntimeException e) {
        String m = e.getMessage();
        return m == null ? e.getClass()
            .getSimpleName()
            : e.getClass()
                .getSimpleName() + ": "
                + m;
    }

    private static MachineSpec machine(JsonElement el) {
        JsonObject m = obj(el, "machine");
        String structure = str(m, "structure");
        if (structure == null) throw new ScenarioParseException("machine without structure");
        Map<String, String> cells = new LinkedHashMap<String, String>();
        if (m.has("cells")) {
            for (Map.Entry<String, JsonElement> e : obj(m.get("cells"), "cells").entrySet()) {
                cells.put(
                    e.getKey(),
                    e.getValue()
                        .getAsString());
            }
        }
        return new MachineSpec(
            structure,
            strOr(m, "facing", "NORTH"),
            strOr(m, "rotation", "NORMAL"),
            strOr(m, "flip", "NONE"),
            cells);
    }

    /** The "inputs" array of an insert step's args, read exactly like a scenario's own inputs. */
    public static List<InputSpec> inputs(JsonObject args) {
        List<InputSpec> out = new ArrayList<InputSpec>();
        for (JsonElement e : array(args, "inputs")) out.add(input(e));
        return out;
    }

    private static InputSpec input(JsonElement el) {
        JsonObject i = obj(el, "input");
        if (i.has("energy")) {
            return new InputSpec(
                InputSpec.Kind.ENERGY,
                null,
                i.get("energy")
                    .getAsInt());
        }
        int amount = intOr(i, "amount", 1);
        if (i.has("item")) return new InputSpec(InputSpec.Kind.ITEM, str(i, "item"), amount);
        if (i.has("fluid")) return new InputSpec(InputSpec.Kind.FLUID, str(i, "fluid"), amount);
        throw new ScenarioParseException("input needs item, fluid or energy");
    }

    private static Step step(JsonElement el) {
        JsonObject s = obj(el, "step");
        String action = str(s, "action");
        if (action == null) throw new ScenarioParseException("step without action");
        if (!Step.ACTIONS.contains(action)) throw new ScenarioParseException("unknown action '" + action + "'");
        JsonObject args = new JsonObject();
        for (Map.Entry<String, JsonElement> e : s.entrySet()) {
            String k = e.getKey();
            if (!k.equals("at") && !k.equals("action")) args.add(k, e.getValue());
        }
        // Read now so a typo fails the file check rather than the in-game run.
        if (action.equals("insert")) inputs(args);
        return new Step(intOr(s, "at", 0), action, args);
    }

    private static JsonArray array(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray()) throw new ScenarioParseException("'" + key + "' must be an array");
        return e.getAsJsonArray();
    }

    private static JsonObject obj(JsonElement e, String what) {
        if (e == null || !e.isJsonObject()) throw new ScenarioParseException(what + " must be an object");
        return e.getAsJsonObject();
    }

    private static int[] triple(JsonObject o, String key) {
        JsonArray a = array(o, key);
        if (a.size() != 3) throw new ScenarioParseException("'" + key + "' must have 3 numbers");
        return new int[] { a.get(0)
            .getAsInt(),
            a.get(1)
                .getAsInt(),
            a.get(2)
                .getAsInt() };
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? null : e.getAsString();
    }

    private static String strOr(JsonObject o, String key, String def) {
        String v = str(o, key);
        return v != null ? v : def;
    }

    private static int intOr(JsonObject o, String key, int def) {
        JsonElement e = o.get(key);
        return e == null ? def : e.getAsInt();
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.getAsBoolean();
    }
}
