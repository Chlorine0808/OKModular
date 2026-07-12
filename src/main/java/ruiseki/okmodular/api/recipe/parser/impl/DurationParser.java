package ruiseki.okmodular.api.recipe.parser.impl;

import com.google.gson.JsonElement;

import ruiseki.okmodular.api.recipe.core.ModularRecipe;
import ruiseki.okmodular.api.recipe.parser.IRecipePropertyParser;

public class DurationParser implements IRecipePropertyParser {

    @Override
    public void parse(ModularRecipe.Builder builder, JsonElement element) {
        if (element.isJsonPrimitive()) {
            builder.duration(element.getAsInt());
        }
    }
}
