package ruiseki.okmodular.selftest.scenario;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonObject;

/** A scheduled action; all keys except "at" and "action" stay in args. */
public final class Step {

    public static final Set<String> ACTIONS = new HashSet<String>(
        Arrays.asList(
            "setBlock",
            "fill",
            "weather",
            "time",
            "biome",
            "insert",
            "clearOutputs",
            "fillOutputs",
            "unloadReload",
            "recordNbt",
            "snapshot",
            "putOutput",
            "recipeCapture",
            "recipeAmountOf",
            "recipeSave"));

    public final int at;
    public final String action;
    public final JsonObject args;

    public Step(int at, String action, JsonObject args) {
        this.at = at;
        this.action = action;
        this.args = args;
    }
}
