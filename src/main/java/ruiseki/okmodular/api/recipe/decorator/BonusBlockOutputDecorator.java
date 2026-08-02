package ruiseki.okmodular.api.recipe.decorator;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.recipe.context.IRecipeContext;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.expression.ExpressionsParser;
import ruiseki.okmodular.api.recipe.expression.IExpression;
import ruiseki.okmodular.api.recipe.expression.SeedMixer;
import ruiseki.okmodular.api.recipe.io.BlockOutput;

/**
 * BlockOutput-specific decorator for applying outputs with probability.
 * Similar to BonusOutputDecorator but uses IRecipeContext instead of IModularPort.
 */
public class BonusBlockOutputDecorator extends RecipeDecorator {

    private final IExpression chanceExpr;
    private final List<BlockOutput> bonusOutputs;

    public BonusBlockOutputDecorator(IModularRecipe internal, IExpression chanceExpr, List<BlockOutput> bonusOutputs) {
        super(internal);
        this.chanceExpr = chanceExpr;
        this.bonusOutputs = bonusOutputs;
    }

    @Override
    public boolean processOutputs(List<IModularPort> outputPorts, boolean simulate) {
        // 1. Process base recipe outputs
        if (!internal.processOutputs(outputPorts, simulate)) {
            return false;
        }

        // 2. Apply probability check and bonus BlockOutputs
        if (!simulate) {
            // Find IRecipeContext to get ConditionContext for evaluation
            IRecipeContext context = IRecipeContext.findIn(outputPorts);
            ConditionContext condContext = context != null ? context.getConditionContext() : null;

            int times = timesFiring(condContext);
            if (context != null) {
                for (int fired = 0; fired < times; fired++) {
                    // Apply all bonus BlockOutputs
                    for (BlockOutput output : bonusOutputs) {
                        output.apply(outputPorts, 1, condContext);
                    }
                }
            }
        }

        return true;
    }

    /**
     * Whether the bonus fires.
     * <p>
     * See {@link BonusOutputDecorator#rolls} for why a shared {@link java.util.Random} field
     * had to go. This draws from its own stream rather than that one's: a recipe may carry
     * both decorators, and sharing a stream would make them fire and skip in lockstep.
     */
    boolean rolls(ConditionContext context) {
        double chance = chanceExpr.evaluateDouble(context);
        long seed = context != null ? context.getEvaluationSeed() : 0L;
        return SeedMixer.toUnitInterval(seed, SeedMixer.BONUS_BLOCK_OUTPUT) < chance;
    }

    /** How many times the bonus fires. See {@link BonusOutputDecorator#timesFiring}. */
    int timesFiring(ConditionContext context) {
        return timesFiring(context, batchSizeOf(context));
    }

    /** The same, with the batch size supplied rather than read from the machine. */
    int timesFiring(ConditionContext context, int batch) {
        int fired = 0;
        for (int draw = 0; draw < batch; draw++) {
            if (rolls(context != null ? context.forDraw(draw) : null)) fired++;
        }
        return fired;
    }

    /**
     * Create decorator from JSON.
     *
     * Expected format:
     * {
     * "type": "bonus_block_output",
     * "chance": { "type": "constant", "value": 0.5 },
     * "outputs": [
     * { "type": "block", "symbol": "L", "block": "modid:blockname:meta", "count": 4 }
     * ]
     * }
     */
    public static IModularRecipe fromJson(IModularRecipe recipe, JsonObject json) {
        IExpression chance = ExpressionsParser.parse(json.get("chance"));

        List<BlockOutput> outputs = new ArrayList<>();
        JsonArray outputsArray = json.getAsJsonArray("outputs");
        for (JsonElement elem : outputsArray) {
            JsonObject outputObj = elem.getAsJsonObject();
            // Only accept block type outputs
            if ("block".equals(
                outputObj.get("type")
                    .getAsString())) {
                outputs.add(BlockOutput.fromJson(outputObj));
            }
        }

        return new BonusBlockOutputDecorator(recipe, chance, outputs);
    }
}
