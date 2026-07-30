package ruiseki.okmodular.api.structure.io;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonElement;

import ruiseki.okcore.json.ParsingContext;
import ruiseki.okmodular.api.condition.ConditionParserRegistry;
import ruiseki.okmodular.api.condition.ICondition;
import ruiseki.okmodular.api.structure.core.ConditionPolicy;
import ruiseki.okmodular.util.Logger;

/**
 * Reads a structure's own <code>conditions</code> and <code>conditionPolicy</code>.
 * <p>
 * Split out of {@link StructureJsonReader} because that class cannot be exercised off-game
 * - resolving mappings needs real blocks - while this part is pure JSON and worth testing.
 * The reader keeps the rest; this holds only the two keys B8 added.
 * <p>
 * <b>Unreadable conditions are dropped, not carried as nulls.</b>
 * {@link ConditionParserRegistry#parse} answers null for a condition it cannot build, and
 * letting that reach the machine would mean either a null check on a path that runs every
 * tick or a crash there. Load time is where a bad line of JSON should be reported, so it is
 * reported here and the condition is left out.
 * <p>
 * The cost of that choice is worth saying plainly: a misspelled condition <b>weakens the
 * gate rather than closing it</b>, so a machine can end up running with fewer conditions
 * than its author wrote. The warning names the file, which is what an author needs to find
 * it.
 */
public final class MachineConditionsParser {

    private MachineConditionsParser() {}

    /**
     * Reads the condition list.
     * <p>
     * Accepts an array of conditions or, for the common single-condition case, one
     * condition object on its own. Each element goes through
     * {@link ConditionParserRegistry#parse}, so every form it understands - an explicit
     * <code>type</code>, the nested <code>{"offset": {...}}</code> shape, or inference from
     * the properties present - works here too.
     *
     * @param element the value of the <code>conditions</code> key, may be null
     * @return the conditions that could be read, in order; empty rather than null
     */
    public static List<ICondition> parse(JsonElement element) {
        if (element == null || element.isJsonNull()) return Collections.emptyList();

        List<ICondition> conditions = new ArrayList<>();
        if (element.isJsonArray()) {
            for (JsonElement each : element.getAsJsonArray()) {
                addIfReadable(conditions, each);
            }
        } else {
            addIfReadable(conditions, element);
        }
        return conditions;
    }

    private static void addIfReadable(List<ICondition> into, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            Logger.warn(
                "Machine condition is not an object in {}, ignoring it: {}",
                ParsingContext.getCurrentFileName(),
                element);
            return;
        }

        ICondition condition = ConditionParserRegistry.parse(element.getAsJsonObject());
        if (condition == null) {
            // parse() has already said what it could not identify; this adds where it was,
            // which is the part an author needs to fix it.
            Logger.warn(
                "Dropping a machine condition that could not be read in {}: {}",
                ParsingContext.getCurrentFileName(),
                element);
            return;
        }
        into.add(condition);
    }

    /**
     * Reads the policy name, falling back and warning when it is not recognised.
     * <p>
     * Same shape as how <code>durationPolicy</code> is read: a misspelling should leave the
     * machine working on the default rather than stop it.
     *
     * @param element      the value of the <code>conditionPolicy</code> key, may be null
     * @param defaultValue what to use when the key is absent, not a string, or unrecognised
     */
    public static ConditionPolicy parsePolicy(JsonElement element, ConditionPolicy defaultValue) {
        if (element == null || !element.isJsonPrimitive()) return defaultValue;

        String name = element.getAsString();
        ConditionPolicy policy = ConditionPolicy.fromString(name, null);
        if (policy == null) {
            Logger.warn(
                "Unknown conditionPolicy '{}' in {}. Keeping {}.",
                name,
                ParsingContext.getCurrentFileName(),
                defaultValue.name()
                    .toLowerCase());
            return defaultValue;
        }
        return policy;
    }
}
