package ruiseki.okmodular.selftest.report;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Hand-written notes for the report (selftest/annotations.json): cause guesses per scenario and mod fixes. */
public final class Annotations {

    private static final Set<String> CAUSES = new HashSet<>(
        Arrays.asList("mod-bug", "expectation-doubt", "harness-bug"));

    /** A mod fix made because a scenario failed. */
    public static final class Fix {

        public final String commit, scenario, summary;

        Fix(String commit, String scenario, String summary) {
            this.commit = commit;
            this.scenario = scenario;
            this.summary = summary;
        }
    }

    private final Map<String, String> causes;
    private final Map<String, String> notes;
    private final List<Fix> fixes;

    private Annotations(Map<String, String> causes, Map<String, String> notes, List<Fix> fixes) {
        this.causes = causes;
        this.notes = notes;
        this.fixes = Collections.unmodifiableList(fixes);
    }

    public static Annotations empty() {
        return new Annotations(new HashMap<String, String>(), new HashMap<String, String>(), new ArrayList<Fix>());
    }

    /** Format: {"scenarios": {id: {"cause": ..., "note": ...}}, "fixes": [{commit, scenario, summary}]}. */
    public static Annotations parse(Reader json) {
        JsonObject root = new JsonParser().parse(json)
            .getAsJsonObject();
        Map<String, String> causes = new HashMap<>();
        Map<String, String> notes = new HashMap<>();
        List<Fix> fixes = new ArrayList<>();
        if (root.has("scenarios")) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("scenarios")
                .entrySet()) {
                JsonObject o = e.getValue()
                    .getAsJsonObject();
                if (o.has("cause")) {
                    String cause = o.get("cause")
                        .getAsString();
                    if (!CAUSES.contains(cause)) {
                        throw new IllegalArgumentException(
                            "unknown cause '" + cause + "' for " + e.getKey() + " (allowed: " + CAUSES + ")");
                    }
                    causes.put(e.getKey(), cause);
                }
                if (o.has("note")) {
                    notes.put(
                        e.getKey(),
                        o.get("note")
                            .getAsString());
                }
            }
        }
        if (root.has("fixes")) {
            JsonArray arr = root.getAsJsonArray("fixes");
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                fixes.add(new Fix(str(o, "commit"), str(o, "scenario"), str(o, "summary")));
            }
        }
        return new Annotations(causes, notes, fixes);
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) ? o.get(key)
            .getAsString() : "";
    }

    /** "mod-bug", "expectation-doubt", "harness-bug" or null. */
    public String causeOf(String id) {
        return causes.get(id);
    }

    public String noteOf(String id) {
        return notes.get(id);
    }

    public List<Fix> fixes() {
        return fixes;
    }
}
