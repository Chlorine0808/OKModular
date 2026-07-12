package ruiseki.okmodular.api.recipe.parser.impl;

import com.google.gson.JsonElement;

import ruiseki.okmodular.api.recipe.core.ModularRecipe;
import ruiseki.okmodular.api.recipe.io.IRecipeInput;
import ruiseki.okmodular.api.recipe.parser.IRecipePropertyParser;
import ruiseki.okmodular.api.recipe.parser.InputParserRegistry;

public class InputPropertyParser implements IRecipePropertyParser {

    @Override
    public void parse(ModularRecipe.Builder builder, JsonElement element) {
        if (element.isJsonArray()) {
            for (JsonElement e : element.getAsJsonArray()) {
                if (e.isJsonObject()) {
                    IRecipeInput input = InputParserRegistry.parse(e.getAsJsonObject());
                    if (input != null) builder.addInput(input);
                }
            }
        } else if (element.isJsonObject()) {
            IRecipeInput input = InputParserRegistry.parse(element.getAsJsonObject());
            if (input != null) builder.addInput(input);
        }
    }
}
