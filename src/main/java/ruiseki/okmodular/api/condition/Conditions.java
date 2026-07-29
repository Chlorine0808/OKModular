package ruiseki.okmodular.api.condition;

import ruiseki.okmodular.api.recipe.expression.ExpressionParser;

/**
 * Utility class to register default condition parsers.
 */
public class Conditions {

    public static void registerDefaults() {
        ConditionParserRegistry
            .register("dimension", DimensionCondition::fromJson, json -> json.has("ids") || json.has("dimension"));

        ConditionParserRegistry.register(
            "biome",
            BiomeCondition::fromJson,
            json -> json.has("biomes") || json.has("biome")
                || json.has("tags")
                || json.has("tag")
                || json.has("minTemp")
                || json.has("maxTemp")
                || json.has("minHumid")
                || json.has("maxHumid"));

        ConditionParserRegistry.register(
            "offset",
            OffsetCondition::fromJson,
            json -> (json.has("dx") || json.has("dy") || json.has("dz"))
                && (json.has("condition") || json.has("expression")));

        ConditionParserRegistry.register("pattern", BiomePatternCondition::fromJson, json -> json.has("pattern"));

        ConditionParserRegistry.register("block", BlockCondition::fromJson, json -> json.has("block"));

        ConditionParserRegistry.register("block_below", BlockBelowCondition::fromJson); // Left for backward
                                                                                        // compatibility

        ConditionParserRegistry.register("weather", WeatherCondition::fromJson, json -> json.has("weather"));

        ConditionParserRegistry.register(
            "comparison",
            ComparisonCondition::fromJson,
            json -> json.has("left") && json.has("right") && json.has("operator"));

        ConditionParserRegistry.register(
            "expression",
            json -> ExpressionParser.parseCondition(
                json.get("expression")
                    .getAsString()),
            json -> json.has("expression"));

        // tile_nbt is gone. It read one flat key off the machine's own TileEntity with
        // its own comparison parser, and could not do nested paths, other blocks, or
        // !=. Write it as an expression instead:
        //
        // { "expression": "nbt('energy') >= 1000" }
        // { "expression": "has_nbt('heat') && nbt('heat') <= 100" }
        //
        // The second form is the one that needs care: tile_nbt treated an absent key
        // as failing the condition, whereas nbt() answers 0, which passes a <=
        // comparison. has_nbt() restores that distinction.

        // Logical Operators
        ConditionParserRegistry.register("and", OpAnd::fromJson, json -> json.has("conditions") || json.has("and"));
        ConditionParserRegistry.register("or", OpOr::fromJson, json -> json.has("conditions") || json.has("or"));
        ConditionParserRegistry.register("not", OpNot::fromJson, json -> json.has("condition") || json.has("not"));

        // Their fromJson reads exactly like and/or/xor do; only the detector was
        // missing, which left { "nand": [...] } unresolvable unless "type" was
        // spelled out. "conditions" is not offered here because "and" is registered
        // first and would claim it.
        ConditionParserRegistry.register("nand", OpNand::fromJson, json -> json.has("nand"));
        ConditionParserRegistry.register("nor", OpNor::fromJson, json -> json.has("nor"));
        ConditionParserRegistry.register("xor", OpXor::fromJson, json -> json.has("xor"));
    }
}
