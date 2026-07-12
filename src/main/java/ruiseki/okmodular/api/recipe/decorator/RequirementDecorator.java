package ruiseki.okmodular.api.recipe.decorator;

import com.google.gson.JsonObject;

import ruiseki.okcore.api.condition.ConditionContext;
import ruiseki.okcore.api.condition.ConditionParserRegistry;
import ruiseki.okcore.api.condition.ICondition;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;

/**
 * Decorator that adds an additional condition to a recipe.
 */
public class RequirementDecorator extends RecipeDecorator {

    private final ICondition extraCondition;

    public RequirementDecorator(IModularRecipe internal, ICondition extraCondition) {
        super(internal);
        this.extraCondition = extraCondition;
    }

    @Override
    public boolean isConditionMet(ConditionContext context) {
        // Both the original conditions and this extra condition must be met
        return internal.isConditionMet(context) && extraCondition.isMet(context);
    }

    public ICondition getExtraCondition() {
        return extraCondition;
    }

    public static IModularRecipe fromJson(IModularRecipe recipe, JsonObject json) {
        ICondition condition = ConditionParserRegistry.parse(json.getAsJsonObject("condition"));
        return new RequirementDecorator(recipe, condition);
    }
}
