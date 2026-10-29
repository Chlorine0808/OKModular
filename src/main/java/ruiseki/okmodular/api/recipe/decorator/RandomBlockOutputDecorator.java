package ruiseki.okmodular.api.recipe.decorator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.ChunkCoordinates;

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
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;
import ruiseki.okmodular.api.structure.core.StructureSymbols;
import ruiseki.okmodular.util.Logger;

/**
 * Picks N cells of one symbol and puts one of several weighted outputs on each, drawn per cell.
 * Without a count, every candidate cell is used.
 */
public class RandomBlockOutputDecorator extends RecipeDecorator {

    private final char symbol;
    private final IExpression countExpr;
    private final List<WeightedBlockOutput> outputs;

    /** One output of a {@code random_block_output} draw and its weight. */
    public static final class WeightedBlockOutput {

        public final BlockOutput output;
        public final int weight;

        public WeightedBlockOutput(BlockOutput output, int weight) {
            this.output = output;
            this.weight = weight;
        }
    }

    /**
     * Picks one of {@code accepting} for {@code cell}, in proportion to weight.
     * Entries with weight 0 or less never win; null when nothing has positive weight.
     */
    static WeightedBlockOutput pickFor(List<WeightedBlockOutput> accepting, long seed, ChunkCoordinates cell) {
        long total = 0;
        for (WeightedBlockOutput entry : accepting) {
            if (entry.weight > 0) total += entry.weight;
        }
        if (total <= 0) return null;

        double roll = SeedMixer
            .toUnitInterval(SeedMixer.forPosition(seed, cell.posX, cell.posY, cell.posZ), SeedMixer.RANDOM_BLOCK_PICK)
            * total;
        double upTo = 0;
        WeightedBlockOutput last = null;
        for (WeightedBlockOutput entry : accepting) {
            if (entry.weight <= 0) continue;
            upTo += entry.weight;
            last = entry;
            if (roll < upTo) return entry;
        }
        return last;
    }

    public RandomBlockOutputDecorator(IModularRecipe internal, char symbol, IExpression countExpr,
        List<WeightedBlockOutput> outputs) {
        super(internal);
        this.symbol = symbol;
        this.countExpr = countExpr;
        this.outputs = outputs;
    }

    public char getSymbol() {
        return symbol;
    }

    public List<WeightedBlockOutput> getWeightedOutputs() {
        return outputs;
    }

    @Override
    public void produceExtraOutputs(List<IModularPort> outputPorts, ConditionContext context) {
        super.produceExtraOutputs(outputPorts, context);

        IRecipeContext recipeContext = context != null ? context.getRecipeContext() : null;
        if (recipeContext == null) recipeContext = IRecipeContext.findIn(outputPorts);
        if (recipeContext == null) return;

        // A cell is a candidate when some entry may write it, or a cell holding air would take
        // a replacement meant for another block
        Map<ChunkCoordinates, List<WeightedBlockOutput>> accepting = new HashMap<>();
        List<ChunkCoordinates> candidates = new ArrayList<>();
        List<ChunkCoordinates> allPositions = recipeContext.getSymbolPositions(symbol);
        if (allPositions != null) {
            for (ChunkCoordinates pos : allPositions) {
                List<WeightedBlockOutput> forCell = new ArrayList<>();
                for (WeightedBlockOutput entry : outputs) {
                    if (entry.output.acceptsAt(recipeContext, pos)) forCell.add(entry);
                }
                if (forCell.isEmpty()) continue;
                accepting.put(pos, forCell);
                candidates.add(pos);
            }
        }

        ConditionContext own = ownDraw(context);
        long seed = own != null ? own.getEvaluationSeed() : 0L;
        for (ChunkCoordinates pos : select(candidates, totalCount(own), own)) {
            WeightedBlockOutput picked = pickFor(accepting.get(pos), seed, pos);
            if (picked != null) picked.output.applyAt(recipeContext, pos, context);
        }
    }

    /**
     * How many cells this completion replaces.
     * <p>
     * {@code count} is per run of the recipe and a batch of n is n runs, so a batch replaces n
     * times as many cells - see {@link RecipeDecorator#batchSizeOf}. This is the one place the
     * question was open: {@code per_position_probability} deliberately does <b>not</b> scale,
     * because it asks each cell its own question and asking twice rewrites the same block.
     * This one names a number of cells, and n runs of "replace 5 cells" is 5n cells.
     * <p>
     * The count is drawn once per run rather than drawn once and multiplied, which is how
     * {@code resolveAmount} pays out a batched output, so a count written with
     * {@code random()} can total something other than a multiple of n. Draw zero is the
     * context itself, so a batch of one is bit-for-bit what it was before batching.
     * <p>
     * Nothing clamps the total here. {@link #select} takes as many cells as exist and stops,
     * so a count past the end of the structure fills it rather than failing.
     * <p>
     * An omitted {@code count} means every candidate cell, which is {@link Integer#MAX_VALUE}.
     */
    int totalCount(ConditionContext context) {
        if (countExpr == null) return Integer.MAX_VALUE;
        int batch = batchSizeOf(context);
        int total = 0;

        for (int draw = 0; draw < batch; draw++) {
            total += (int) countExpr.evaluateDouble(context != null ? context.forDraw(draw) : null);
        }
        return total;
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

    @Override
    public List<DecoratorDescription> describe() {
        StringBuilder weights = new StringBuilder();
        for (WeightedBlockOutput entry : outputs) {
            if (weights.length() > 0) weights.append(" / ");
            weights.append(entry.weight);
        }
        String sym = String.valueOf(symbol);
        if (countExpr == null) {
            return Collections.singletonList(
                new DecoratorDescription("okmodular.nei.decorator.random_block_output.all", sym, weights.toString()));
        }
        return Collections.singletonList(
            new DecoratorDescription(
                "okmodular.nei.decorator.random_block_output",
                sym,
                number(countExpr),
                weights.toString()));
    }

    @Override
    public List<IRecipeOutput> getPossibleOutputs() {
        List<IRecipeOutput> result = new ArrayList<>();
        for (WeightedBlockOutput entry : outputs) result.add(entry.output);
        return result;
    }

    /**
     * Create decorator from JSON.
     *
     * Expected format ({@code count} may be omitted to use every candidate cell):
     * {
     * "type": "random_block_output",
     * "symbol": "L",
     * "count": 2,
     * "outputs": [ { "weight": 3, "block": "modid:blockname:meta" }, { "block": "modid:other" } ]
     * }
     *
     * The earlier {@code selections} shape is read as one decorator per selection, chained.
     */
    public static IModularRecipe fromJson(IModularRecipe recipe, JsonObject json) {
        IExpression count = json.has("count") ? ExpressionsParser.parse(json.get("count")) : null;

        if (json.has("selections")) {
            Logger.warn(
                "random_block_output \"selections\" is renamed: write one decorator per symbol with \"symbol\" and \"outputs\". Read as such.");
            IModularRecipe chain = recipe;
            for (JsonElement elem : json.getAsJsonArray("selections")) {
                JsonObject selObj = elem.getAsJsonObject();
                char selSymbol = symbolOf(selObj.get("symbol"));
                List<WeightedBlockOutput> single = new ArrayList<>();
                single.add(parseEntry(selObj.getAsJsonObject("output"), selSymbol));
                chain = new RandomBlockOutputDecorator(chain, selSymbol, count, single);
            }
            return chain;
        }

        char symbol = symbolOf(json.get("symbol"));
        List<WeightedBlockOutput> entries = new ArrayList<>();
        for (JsonElement elem : json.getAsJsonArray("outputs")) {
            entries.add(parseEntry(elem.getAsJsonObject(), symbol));
        }
        return new RandomBlockOutputDecorator(recipe, symbol, count, entries);
    }

    private static char symbolOf(JsonElement element) {
        return StructureSymbols.requireAddressable(
            element.getAsString()
                .charAt(0),
            "random_block_output");
    }

    /** One entry: its weight (default 1) and a block output that takes the decorator's symbol. */
    private static WeightedBlockOutput parseEntry(JsonObject entryJson, char symbol) {
        // Shallow copy: the symbol is injected without touching the caller's JSON
        JsonObject copy = new JsonObject();
        for (Map.Entry<String, JsonElement> field : entryJson.entrySet()) copy.add(field.getKey(), field.getValue());
        if (copy.has("symbol")) {
            char own = symbolOf(copy.get("symbol"));
            if (own != symbol) {
                throw new IllegalArgumentException(
                    "random_block_output draws cells of '" + symbol
                        + "' but an output names symbol '"
                        + own
                        + "'. Drop the output's symbol or make them match.");
            }
        } else {
            copy.addProperty("symbol", String.valueOf(symbol));
        }
        int weight = copy.has("weight") ? copy.get("weight")
            .getAsInt() : 1;
        return new WeightedBlockOutput(BlockOutput.fromJson(copy), weight);
    }
}
