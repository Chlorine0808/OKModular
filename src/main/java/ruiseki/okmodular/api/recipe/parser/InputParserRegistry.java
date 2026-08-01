package ruiseki.okmodular.api.recipe.parser;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.recipe.io.BlockInput;
import ruiseki.okmodular.api.recipe.io.EnergyInput;
import ruiseki.okmodular.api.recipe.io.EssentiaInput;
import ruiseki.okmodular.api.recipe.io.FluidInput;
import ruiseki.okmodular.api.recipe.io.GasInput;
import ruiseki.okmodular.api.recipe.io.IRecipeInput;
import ruiseki.okmodular.api.recipe.io.ItemInput;
import ruiseki.okmodular.api.recipe.io.ManaInput;
import ruiseki.okmodular.api.recipe.io.StructureInput;
import ruiseki.okmodular.api.recipe.io.VisInput;

public class InputParserRegistry {

    private static final Map<String, Function<JsonObject, IRecipeInput>> parsers = new HashMap<>();

    static {
        register("item", ItemInput::fromJson);
        register("ore", ItemInput::fromJson);
        register("fluid", FluidInput::fromJson);
        register("energy", EnergyInput::fromJson);
        register("mana", ManaInput::fromJson);
        register("gas", GasInput::fromJson);
        register("essentia", EssentiaInput::fromJson);
        register("vis", VisInput::fromJson);
        register("symbol", BlockInput::fromJson);
        // Registered under the same word it writes as its "type", so the round trip does not
        // depend on the identifying-key fallback below the way "block" currently does.
        register(StructureInput.TYPE, StructureInput::fromJson);
    }

    /**
     * Register a parser for a specific JSON key.
     * 
     * @param key    The JSON key that identifies this input type (e.g., "item",
     *               "fluid")
     * @param parser Function that creates IRecipeInput from JsonObject
     */
    public static void register(String key, Function<JsonObject, IRecipeInput> parser) {
        parsers.put(key, parser);
    }

    /**
     * Parse a JsonObject into an IRecipeInput.
     * Determines type by checking which key is present.
     */
    public static IRecipeInput parse(JsonObject json) {
        if (json.has("type")) {
            String type = json.get("type")
                .getAsString();
            if (parsers.containsKey(type)) {
                return parsers.get(type)
                    .apply(json);
            } else if (type.equals("block") && parsers.containsKey("symbol")) {
                return parsers.get("symbol")
                    .apply(json);
            }
        }

        for (Map.Entry<String, Function<JsonObject, IRecipeInput>> entry : parsers.entrySet()) {
            if (json.has(entry.getKey())) {
                return entry.getValue()
                    .apply(json);
            }
        }
        throw new IllegalArgumentException("Unknown input type in JSON: " + json);
    }
}
