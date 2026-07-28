package ruiseki.okmodular.api.recipe.parser;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.decorator.BonusBlockOutputDecorator;
import ruiseki.okmodular.api.recipe.decorator.BonusOutputDecorator;
import ruiseki.okmodular.api.recipe.decorator.ChanceRecipeDecorator;
import ruiseki.okmodular.api.recipe.decorator.HarvestBlockDecorator;
import ruiseki.okmodular.api.recipe.decorator.PerPositionProbabilityDecorator;
import ruiseki.okmodular.api.recipe.decorator.RandomBlockOutputDecorator;
import ruiseki.okmodular.api.recipe.decorator.RequirementDecorator;
import ruiseki.okmodular.api.recipe.decorator.WeightedRandomDecorator;

public class DecoratorParser {

    private static final Map<String, DecoratorEntry> registry = new HashMap<>();
    private static final List<DecoratorEntry> entries = new ArrayList<>();

    static {
        register("requirement", RequirementDecorator::fromJson, json -> json.has("condition"));

        register(
            "weighted_random",
            WeightedRandomDecorator::fromJson,
            json -> json.has("rolls") || json.has("pool")
                || (json.has("outputs") && isFirstOutputWeighted(json.getAsJsonArray("outputs"))));

        register(
            "random_block_output",
            RandomBlockOutputDecorator::fromJson,
            json -> json.has("count") || json.has("selections"));

        register(
            "harvest_block",
            HarvestBlockDecorator::fromJson,
            json -> json.has("fortune") || json.has("silkTouch") || json.has("shear") || json.has("harvestLevel"));

        register(
            "per_position_probability",
            PerPositionProbabilityDecorator::fromJson,
            json -> json.has("chance") && json.has("symbol") && json.has("output"));

        register(
            "bonus_block_output",
            BonusBlockOutputDecorator::fromJson,
            json -> json.has("chance") && json.has("outputs") && isFirstOutputBlock(json.getAsJsonArray("outputs")));

        register("bonus", BonusOutputDecorator::fromJson, json -> json.has("chance") && json.has("outputs"));

        register("chance", ChanceRecipeDecorator::fromJson, json -> json.has("chance"));

        // The camelCase names this registry used before. Recipe packs written
        // against them stay resolvable.
        alias("randomBlockOutput", "random_block_output");
        alias("harvest", "harvest_block");
        alias("perPositionProbability", "per_position_probability");
        alias("bonusBlockOutput", "bonus_block_output");
    }

    public static void register(String type, BiFunction<IModularRecipe, JsonObject, IModularRecipe> parser,
        Predicate<JsonObject> detector) {
        DecoratorEntry entry = new DecoratorEntry(type, parser, detector);
        registry.put(type, entry);
        entries.add(entry);
    }

    /**
     * Registers an additional name for an already registered decorator type.
     * <p>
     * Aliases resolve by name only — they are not added to the inference list, so
     * no detector is evaluated twice.
     *
     * @param alias  The additional name to accept
     * @param target The type name passed to {@link #register}
     */
    public static void alias(String alias, String target) {
        DecoratorEntry entry = registry.get(target);
        if (entry != null) {
            registry.put(alias, entry);
        }
    }

    public static IModularRecipe parse(IModularRecipe recipe, JsonElement element) {
        if (element == null || element.isJsonNull()) return recipe;

        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            IModularRecipe current = recipe;
            for (JsonElement e : arr) {
                current = parseSingle(current, e.getAsJsonObject());
            }
            return current;
        }

        return parseSingle(recipe, element.getAsJsonObject());
    }

    private static IModularRecipe parseSingle(IModularRecipe recipe, JsonObject json) {
        // 1. Explicit "type" field
        if (json.has("type")) {
            DecoratorEntry target = registry.get(
                json.get("type")
                    .getAsString());
            if (target != null) {
                return target.parser.apply(recipe, json);
            }
            // Fall through: an unknown type name should not hide a decorator the
            // remaining properties already identify.
        }

        // 2. Nested form — the key names the type, its value holds the properties:
        // { "bonus": { "chance": 0.3, "outputs": [...] } }
        Map.Entry<DecoratorEntry, JsonObject> nested = findNested(json);
        if (nested != null) {
            return nested.getKey().parser.apply(recipe, nested.getValue());
        }

        // 3. Infer from the properties present
        for (DecoratorEntry entry : entries) {
            if (entry.detector.test(json)) {
                return entry.parser.apply(recipe, json);
            }
        }

        if (json.has("type")) {
            throw new IllegalArgumentException(
                "Unknown decorator type '" + json.get("type")
                    .getAsString() + "', and its properties match no known decorator: " + json.entrySet());
        }
        throw new IllegalArgumentException("Could not infer decorator type from properties: " + json.entrySet());
    }

    /**
     * Detects the nested form <code>{ "&lt;type&gt;": { ...properties } }</code>.
     * <p>
     * The inner object must satisfy the target's own detector. Without that check,
     * <code>{ "chance": { "type": "map_range", ... } }</code> — a chance decorator
     * whose probability is an expression object — would be mistaken for a nested
     * declaration and lose its expression.
     *
     * @return the matched entry paired with the inner object, or null if this is not the nested form
     */
    private static Map.Entry<DecoratorEntry, JsonObject> findNested(JsonObject json) {
        if (json.entrySet()
            .size() != 1) return null;

        Map.Entry<String, JsonElement> only = json.entrySet()
            .iterator()
            .next();
        if (!only.getValue()
            .isJsonObject()) return null;

        DecoratorEntry entry = registry.get(only.getKey());
        if (entry == null) return null;

        JsonObject body = only.getValue()
            .getAsJsonObject();
        if (!entry.detector.test(body)) return null;

        return new AbstractMap.SimpleEntry<>(entry, body);
    }

    /**
     * Tells a weighted pool from a plain bonus list: only the former's entries carry
     * a weight. Both spell their entries out under "outputs", and weighted_random is
     * offered the object before bonus is.
     */
    private static boolean isFirstOutputWeighted(JsonArray outputs) {
        if (outputs == null || outputs.size() == 0) return false;
        JsonElement first = outputs.get(0);
        return first.isJsonObject() && first.getAsJsonObject()
            .has("weight");
    }

    private static boolean isFirstOutputBlock(JsonArray outputs) {
        if (outputs.size() == 0) return false;
        JsonElement first = outputs.get(0);
        if (!first.isJsonObject()) return false;
        JsonObject obj = first.getAsJsonObject();
        return obj.has("type") && "block".equals(
            obj.get("type")
                .getAsString());
    }

    private static class DecoratorEntry {

        final String type;
        final BiFunction<IModularRecipe, JsonObject, IModularRecipe> parser;
        final Predicate<JsonObject> detector;

        DecoratorEntry(String type, BiFunction<IModularRecipe, JsonObject, IModularRecipe> parser,
            Predicate<JsonObject> detector) {
            this.type = type;
            this.parser = parser;
            this.detector = detector;
        }
    }
}
