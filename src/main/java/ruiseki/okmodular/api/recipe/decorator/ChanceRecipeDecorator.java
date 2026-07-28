package ruiseki.okmodular.api.recipe.decorator;

import java.util.Random;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.expression.ExpressionsParser;
import ruiseki.okmodular.api.recipe.expression.IExpression;

/**
 * Decorator that adds a random chance for the recipe to meet its conditions.
 */
public class ChanceRecipeDecorator extends RecipeDecorator {

    private final IExpression chanceExpr;
    private final Random rand = new Random();

    public ChanceRecipeDecorator(IModularRecipe internal, IExpression chanceExpr) {
        super(internal);
        this.chanceExpr = chanceExpr;
    }

    @Override
    public boolean isConditionMet(ConditionContext context) {
        // First check internal conditions, then roll for chance
        double chance = chanceExpr.evaluateDouble(context);
        return internal.isConditionMet(context) && rand.nextFloat() < chance;
    }

    public IExpression getChanceExpression() {
        return chanceExpr;
    }

    public static IModularRecipe fromJson(IModularRecipe recipe, JsonObject json) {
        IExpression chance = ExpressionsParser.parse(json.get("chance"));
        return new ChanceRecipeDecorator(recipe, chance);
    }
}
