package ruiseki.okmodular.api.condition;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ruiseki.okmodular.util.Logger;

/**
 * Registry for parsing ICondition from JSON objects.
 */
public class ConditionParserRegistry {

    private static final Map<String, ConditionEntry> registry = new HashMap<>();
    private static final List<ConditionEntry> entries = new ArrayList<>();

    /**
     * Register a parser for a specific condition type with an inference detector.
     * 
     * @param type     The condition type key (e.g., "dimension", "biome")
     * @param parser   Function that creates ICondition from JsonObject
     * @param detector Predicate to check if a JsonObject matches this condition
     *                 type
     */
    public static void register(String type, Function<JsonObject, ICondition> parser, Predicate<JsonObject> detector) {
        ConditionEntry entry = new ConditionEntry(type, parser, detector);
        registry.put(type, entry);
        entries.add(entry);
    }

    /**
     * Register a parser for a specific condition type (without automatic
     * inference).
     * 
     * @param type   The condition type key
     * @param parser Function that creates ICondition from JsonObject
     */
    public static void register(String type, Function<JsonObject, ICondition> parser) {
        register(type, parser, json -> false);
    }

    /**
     * Parse a JsonObject into an ICondition.
     * 
     * @param json The JsonObject representing the condition.
     * @return The parsed ICondition, or null if parsing failed.
     */
    public static ICondition parse(JsonObject json) {
        // 1. Explicit "type" field
        if (json.has("type")) {
            ConditionEntry target = registry.get(
                json.get("type")
                    .getAsString());
            if (target != null) {
                return apply(target, json);
            }
            // Fall through: an unknown type name should not hide a condition the
            // remaining properties already identify.
        }

        // 2. Nested form — the key names the type, its value holds the properties:
        // { "offset": { "dx": 0, "dy": -1, "dz": 0, "condition": {...} } }
        Map.Entry<ConditionEntry, JsonObject> nested = findNested(json);
        if (nested != null) {
            return apply(nested.getKey(), nested.getValue());
        }

        // 3. Infer from the properties present
        for (ConditionEntry entry : entries) {
            if (entry.detector.test(json)) {
                return apply(entry, json);
            }
        }

        Logger.warn("Unknown or non-inferable condition type: {}", json);
        return null;
    }

    private static ICondition apply(ConditionEntry entry, JsonObject json) {
        try {
            return entry.parser.apply(json);
        } catch (Exception e) {
            Logger.error("Failed to parse condition of type '{}': {}", entry.type, e.getMessage());
            return null;
        }
    }

    /**
     * Detects the nested form <code>{ "&lt;type&gt;": { ...properties } }</code>.
     * <p>
     * The inner object must satisfy the target's own detector, which is what keeps
     * this from swallowing conditions that merely happen to hold one object. The
     * logical operators are unaffected: their value is an array, so they are left to
     * property inference, where their own key identifies them.
     *
     * @return the matched entry paired with the inner object, or null if this is not the nested form
     */
    private static Map.Entry<ConditionEntry, JsonObject> findNested(JsonObject json) {
        if (json.entrySet()
            .size() != 1) return null;

        Map.Entry<String, JsonElement> only = json.entrySet()
            .iterator()
            .next();
        if (!only.getValue()
            .isJsonObject()) return null;

        ConditionEntry entry = registry.get(only.getKey());
        if (entry == null) return null;

        JsonObject body = only.getValue()
            .getAsJsonObject();
        if (!entry.detector.test(body)) return null;

        return new AbstractMap.SimpleEntry<>(entry, body);
    }

    private static class ConditionEntry {

        final String type;
        final Function<JsonObject, ICondition> parser;
        final Predicate<JsonObject> detector;

        ConditionEntry(String type, Function<JsonObject, ICondition> parser, Predicate<JsonObject> detector) {
            this.type = type;
            this.parser = parser;
            this.detector = detector;
        }
    }
}
