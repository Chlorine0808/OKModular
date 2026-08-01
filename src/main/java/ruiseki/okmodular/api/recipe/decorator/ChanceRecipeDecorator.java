package ruiseki.okmodular.api.recipe.decorator;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.expression.ExpressionsParser;
import ruiseki.okmodular.api.recipe.expression.IExpression;
import ruiseki.okmodular.api.recipe.expression.SeedMixer;

/**
 * Decorator that adds a random chance for the recipe to meet its conditions.
 */
public class ChanceRecipeDecorator extends RecipeDecorator {

    private final IExpression chanceExpr;

    public ChanceRecipeDecorator(IModularRecipe internal, IExpression chanceExpr) {
        super(internal);
        this.chanceExpr = chanceExpr;
    }

    /**
     * Rolled once, when the recipe begins.
     * <p>
     * This used to live in {@code isConditionMet}, which the engine asks every tick while a
     * recipe runs - so a 60% recipe over 150 ticks had to win 60% a hundred and fifty times
     * and effectively never finished. The docs call this a success rate, meaning per run.
     * <p>
     * The draw comes from the context's evaluation seed rather than a {@link java.util.Random}
     * held by the decorator: that field belonged to the recipe definition, so every machine
     * in the world running this recipe was pulling from one shared sequence.
     */
    @Override
    public boolean canStart(ConditionContext context) {
        if (!internal.canStart(context)) return false;
        double chance = chanceExpr.evaluateDouble(context);
        return SeedMixer.toUnitInterval(context.getEvaluationSeed(), SeedMixer.RECIPE_CHANCE) < chance;
    }

    public IExpression getChanceExpression() {
        return chanceExpr;
    }

    public static IModularRecipe fromJson(IModularRecipe recipe, JsonObject json) {
        IExpression chance = ExpressionsParser.parse(json.get("chance"));
        return new ChanceRecipeDecorator(recipe, chance);
    }
}
