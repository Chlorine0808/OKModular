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
     * Decides the payout, not the start.
     * <p>
     * It has moved twice, for the same reason each time: a draw only means something where
     * losing it costs something.
     * <p>
     * It began in {@code isConditionMet}, which the engine asks every tick while a recipe
     * runs, so a 60% recipe over 150 ticks had to win 60% a hundred and fifty times and
     * effectively never finished. Moving it to {@code canStart} fixed that and introduced
     * the opposite: <b>a refused start is free.</b> The machine keeps its inputs, comes back
     * on the next tick with a new evaluation seed, and draws again - so a 0.25 recipe was
     * merely a few ticks slower to begin, and then completed. Every time. That is
     * indistinguishable from no chance at all.
     * <p>
     * Here the run happens - inputs consumed, duration served - and the draw decides whether
     * anything comes out of it. That is what a success rate is, and it is the only placement
     * where the number is observable.
     * <p>
     * <b>One draw per run</b>, though not because this method is called once - {@code
     * ProcessAgent.produceOutputs} runs again on every tick the output stays blocked, and
     * this sits at the top of it. The agent asks once and keeps the answer; see
     * {@code ProcessAgent#payoutSucceeds}. A fixed {@code chance} was safe without that,
     * since the evaluation seed does not move inside a run - but an expression-valued one
     * reads a machine that does.
     * <p>
     * The draw comes from that seed rather than a {@link java.util.Random} held by the
     * decorator: that field belonged to the recipe definition, so a re-ask could not return
     * the same answer even in principle.
     */
    @Override
    public boolean producesOutput(ConditionContext context) {
        if (!internal.producesOutput(context)) return false;
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
