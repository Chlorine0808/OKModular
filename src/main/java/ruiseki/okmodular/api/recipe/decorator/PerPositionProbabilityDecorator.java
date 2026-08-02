package ruiseki.okmodular.api.recipe.decorator;

import java.util.List;

import net.minecraft.util.ChunkCoordinates;

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
 * Decorator that applies probability check independently for each block position.
 * Realizes "transform each position of all L mappings with X probability".
 */
public class PerPositionProbabilityDecorator extends RecipeDecorator {

    private final IExpression chanceExpr;
    private final char symbol;
    private final BlockOutput output;

    public PerPositionProbabilityDecorator(IModularRecipe internal, IExpression chanceExpr, char symbol,
        BlockOutput output) {
        super(internal);
        this.chanceExpr = chanceExpr;
        this.symbol = symbol;
        this.output = output;
    }

    @Override
    public boolean processOutputs(List<IModularPort> outputPorts, boolean simulate) {
        // 1. Process base recipe outputs
        if (!internal.processOutputs(outputPorts, simulate)) {
            return false;
        }

        // 2. Apply probability check for each position
        if (!simulate) {
            IRecipeContext context = IRecipeContext.findIn(outputPorts);

            if (context != null) {
                ConditionContext condContext = context.getConditionContext();
                List<ChunkCoordinates> positions = context.getSymbolPositions(symbol);

                // Check each position independently
                for (ChunkCoordinates pos : positions) {
                    if (rollsAt(condContext, pos)) {
                        output.applyAt(context, pos, condContext);
                    }
                }
            }
        }

        return true;
    }

    /**
     * Whether this one position is transformed.
     * <p>
     * See {@link BonusOutputDecorator#rolls} for why the shared {@link java.util.Random}
     * field had to go. Here it cannot simply be swapped for the evaluation seed: that seed is
     * fixed for the whole run, so every position would draw the same number and the decorator
     * would degenerate into placing all of them or none of them - worse than the bug it is
     * replacing, which at least varied. {@link SeedMixer#forPosition} mixes the coordinates
     * in, so neighbours disagree while the run stays reproducible.
     */
    boolean rollsAt(ConditionContext context, ChunkCoordinates pos) {
        double chance = chanceExpr.evaluateDouble(context);
        long seed = context != null ? context.getEvaluationSeed() : 0L;
        long positioned = SeedMixer.forPosition(seed, pos.posX, pos.posY, pos.posZ);
        return SeedMixer.toUnitInterval(positioned, SeedMixer.PER_POSITION) < chance;
    }

    /**
     * Create decorator from JSON.
     *
     * Expected format:
     * {
     * "type": "per_position_probability",
     * "chance": { "type": "constant", "value": 0.3 },
     * "symbol": "L",
     * "output": { "symbol": "L", "block": "modid:blockname:meta" }
     * }
     */
    public static IModularRecipe fromJson(IModularRecipe recipe, JsonObject json) {
        IExpression chance = ExpressionsParser.parse(json.get("chance"));
        char symbol = json.get("symbol")
            .getAsString()
            .charAt(0);
        BlockOutput output = BlockOutput.fromJson(json.getAsJsonObject("output"));

        return new PerPositionProbabilityDecorator(recipe, chance, symbol, output);
    }
}
