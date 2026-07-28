package ruiseki.okmodular.api.recipe.decorator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.condition.ConditionParserRegistry;
import ruiseki.okmodular.api.condition.ICondition;
import ruiseki.okmodular.api.condition.OffsetCondition;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.expression.ExpressionParser;

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

    /**
     * Reads the extra condition, which may be spelled either way.
     * <p>
     * A recipe script goes in as a string — <code>"condition": "tier.glass >= 1"</code>
     * — and a structured condition as an object. {@link OffsetCondition} already
     * accepts both; this follows it.
     */
    public static IModularRecipe fromJson(IModularRecipe recipe, JsonObject json) {
        JsonElement element = json.get("condition");
        if (element == null) {
            throw new IllegalArgumentException("A requirement decorator needs a \"condition\": " + json);
        }

        ICondition condition = element.isJsonPrimitive() ? ExpressionParser.parseCondition(element.getAsString())
            : ConditionParserRegistry.parse(element.getAsJsonObject());

        if (condition == null) {
            throw new IllegalArgumentException("A requirement decorator could not read its condition: " + element);
        }
        return new RequirementDecorator(recipe, condition);
    }
}
