package ruiseki.okmodular.api.recipe.decorator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.util.ChunkCoordinates;

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
 * Decorator that randomly selects N positions from all positions with a specific symbol
 * and applies BlockOutput only to selected positions.
 */
public class RandomBlockOutputDecorator extends RecipeDecorator {

    private final IExpression countExpr;
    private final List<BlockOutputSelection> selections;

    public static class BlockOutputSelection {

        public final char symbol;
        public final BlockOutput output;

        public BlockOutputSelection(char symbol, BlockOutput output) {
            this.symbol = symbol;
            this.output = output;
        }
    }

    public RandomBlockOutputDecorator(IModularRecipe internal, IExpression countExpr,
        List<BlockOutputSelection> selections) {
        super(internal);
        this.countExpr = countExpr;
        this.selections = selections;
    }

    @Override
    public void produceExtraOutputs(List<IModularPort> outputPorts, ConditionContext context) {
        super.produceExtraOutputs(outputPorts, context);

        IRecipeContext recipeContext = context != null ? context.getRecipeContext() : null;
        if (recipeContext == null) recipeContext = IRecipeContext.findIn(outputPorts);
        if (recipeContext == null) return;

        for (BlockOutputSelection selection : selections) {
            int selectCount = (int) countExpr.evaluateDouble(context);
            List<ChunkCoordinates> allPositions = recipeContext.getSymbolPositions(selection.symbol);

            for (ChunkCoordinates pos : select(allPositions, selectCount, context)) {
                selection.output.applyAt(recipeContext, pos, context);
            }
        }
    }

    /**
     * The {@code count} positions this draw picks out of {@code positions}.
     * <p>
     * This was {@code Collections.shuffle} on a {@link java.util.Random} held as a field -
     * see {@link BonusOutputDecorator#rolls} for why that could not stay. Shuffling needs a
     * source that moves, and the evaluation seed does not move inside a run, so the shuffle
     * is replaced rather than reseeded: every position draws a ticket from its own
     * coordinates and the lowest {@code count} tickets win.
     * <p>
     * That gives the same set every time it is asked within a run, and a different set on the
     * next run. It also makes the selection <b>grow</b> rather than reshuffle: raising the
     * count keeps the positions the smaller count had already chosen, which is what a
     * structure being filled in over time wants.
     */
    List<ChunkCoordinates> select(List<ChunkCoordinates> positions, int count, ConditionContext context) {
        if (positions == null || positions.isEmpty() || count <= 0) {
            return Collections.emptyList();
        }

        long seed = context != null ? context.getEvaluationSeed() : 0L;
        List<ChunkCoordinates> ordered = new ArrayList<>(positions);
        ordered.sort(Comparator.comparingDouble(pos -> ticket(seed, pos)));

        return new ArrayList<>(ordered.subList(0, Math.min(count, ordered.size())));
    }

    /** A position's place in the queue, in [0, 1). */
    private static double ticket(long seed, ChunkCoordinates pos) {
        return SeedMixer
            .toUnitInterval(SeedMixer.forPosition(seed, pos.posX, pos.posY, pos.posZ), SeedMixer.RANDOM_BLOCK_OUTPUT);
    }

    /**
     * Create decorator from JSON.
     *
     * Expected format:
     * {
     * "type": "random_block_output",
     * "count": { "type": "constant", "value": 2 },
     * "selections": [
     * {
     * "symbol": "L",
     * "output": { "symbol": "L", "block": "modid:blockname:meta" }
     * }
     * ]
     * }
     */
    public static IModularRecipe fromJson(IModularRecipe recipe, JsonObject json) {
        IExpression count = ExpressionsParser.parse(json.get("count"));

        List<BlockOutputSelection> selections = new ArrayList<>();
        JsonArray selectionsArray = json.getAsJsonArray("selections");
        for (JsonElement elem : selectionsArray) {
            JsonObject selObj = elem.getAsJsonObject();
            char symbol = selObj.get("symbol")
                .getAsString()
                .charAt(0);
            BlockOutput output = BlockOutput.fromJson(selObj.getAsJsonObject("output"));
            selections.add(new BlockOutputSelection(symbol, output));
        }

        return new RandomBlockOutputDecorator(recipe, count, selections);
    }
}
