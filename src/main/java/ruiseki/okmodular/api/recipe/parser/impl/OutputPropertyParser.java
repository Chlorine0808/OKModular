package ruiseki.okmodular.api.recipe.parser.impl;

import com.google.gson.JsonElement;

import ruiseki.okmodular.api.recipe.core.ModularRecipe;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;
import ruiseki.okmodular.api.recipe.parser.IRecipePropertyParser;
import ruiseki.okmodular.api.recipe.parser.OutputParserRegistry;

public class OutputPropertyParser implements IRecipePropertyParser {

    @Override
    public void parse(ModularRecipe.Builder builder, JsonElement element) {
        if (element.isJsonArray()) {
            for (JsonElement e : element.getAsJsonArray()) {
                if (e.isJsonObject()) {
                    IRecipeOutput output = OutputParserRegistry.parse(e.getAsJsonObject());
                    if (output != null) builder.addOutput(output);
                }
            }
        } else if (element.isJsonObject()) {
            IRecipeOutput output = OutputParserRegistry.parse(element.getAsJsonObject());
            if (output != null) builder.addOutput(output);
        }
    }
}
