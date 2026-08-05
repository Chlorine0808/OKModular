package ruiseki.okmodular.api.recipe.decorator;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.WeightedRandom;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.expression.SeedMixer;
import ruiseki.okmodular.api.recipe.io.IModularRecipeOutput;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;
import ruiseki.okmodular.api.recipe.parser.OutputParserRegistry;

/**
 * Decorator that picks outputs randomly from a weighted pool.
 */
public class WeightedRandomDecorator extends RecipeDecorator {

    private final List<WeightedOutputEntry> pool;
    private final int rolls;

    public WeightedRandomDecorator(IModularRecipe internal, List<WeightedOutputEntry> pool, int rolls) {
        super(internal);
        this.pool = pool;
        this.rolls = rolls;
    }

    @Override
    public void produceExtraOutputs(List<IModularPort> outputPorts, ConditionContext context) {
        super.produceExtraOutputs(outputPorts, context);

        int draws = totalRolls(context);
        for (int i = 0; i < draws; i++) {
            WeightedOutputEntry picked = pick(context, i);
            if (picked != null && picked.output instanceof IModularRecipeOutput modularOutput) {
                List<IModularPort> filtered = filterByType(outputPorts, modularOutput.getPortType());
                if (modularOutput.checkCapacity(filtered, 1, context)) {
                    modularOutput.apply(filtered, 1, context);
                }
            }
        }
    }

    /**
     * Picks one entry for draw {@code index}.
     * <p>
     * This was {@code WeightedRandom.getRandomItem} on a {@link java.util.Random} held as a
     * field - see {@link BonusOutputDecorator#rolls} for why a shared field could not stay.
     * Removing it exposes something the shared field had been hiding: the evaluation seed is
     * fixed for the whole run, so {@code rolls: 3} would have picked the <b>same entry three
     * times</b>. Only the field's advancing state was making repeated rolls differ, and that
     * state belonged to whichever machine drew last. {@link SeedMixer#forDraw} moves each
     * draw along a stream that is derived from the seed instead.
     *
     * @param index which draw, from zero
     * @return the chosen entry, or null if the pool is empty or every weight is zero
     */
    /**
     * How many entries this completion picks.
     * <p>
     * {@code rolls} is per run of the recipe, and a batch of n is n runs - see
     * {@link RecipeDecorator#batchSizeOf}. The draws are numbered straight through rather
     * than restarting per run in the batch, so no two land on the same point of the stream.
     */
    int totalRolls(ConditionContext context) {
        return rolls * batchSizeOf(context);
    }

    WeightedOutputEntry pick(ConditionContext context, int index) {
        int total = 0;
        for (WeightedOutputEntry entry : pool) {
            if (entry.itemWeight > 0) total += entry.itemWeight;
        }
        if (total <= 0) return null;

        long seed = context != null ? context.getEvaluationSeed() : 0L;
        int roll = (int) (SeedMixer.toUnitInterval(SeedMixer.forDraw(seed, index), SeedMixer.WEIGHTED_OUTPUT) * total);

        for (WeightedOutputEntry entry : pool) {
            if (entry.itemWeight <= 0) continue;
            roll -= entry.itemWeight;
            if (roll < 0) return entry;
        }
        return null;
    }

    private List<IModularPort> filterByType(List<IModularPort> ports, IPortType.Type type) {
        List<IModularPort> filtered = new ArrayList<>();
        for (IModularPort port : ports) {
            if (port.getPortType() == type) {
                filtered.add(port);
            }
        }
        return filtered;
    }

    /**
     * Entry for the weighted pool.
     */
    public static class WeightedOutputEntry extends WeightedRandom.Item {

        public final IRecipeOutput output;

        public WeightedOutputEntry(IRecipeOutput output, int weight) {
            super(weight);
            this.output = output;
        }
    }

    public List<WeightedOutputEntry> getPool() {
        return pool;
    }

    public int getRolls() {
        return rolls;
    }

    /**
     * Reads a weighted pool in either shape.
     * <p>
     * The documented shape puts the entries under <code>outputs</code> and lets each
     * one carry its weight alongside its own output properties:
     *
     * <pre>
     * { "type": "weighted_random", "outputs": [ { "weight": 70, "item": "minecraft:flint" } ] }
     * </pre>
     *
     * The older shape uses <code>pool</code> with a nested <code>output</code>
     * object, and is still accepted. <code>rolls</code> defaults to one pick per
     * completion.
     */
    public static IModularRecipe fromJson(IModularRecipe recipe, JsonObject json) {
        int rolls = json.has("rolls") ? json.get("rolls")
            .getAsInt() : 1;

        JsonArray arr = json.has("outputs") ? json.getAsJsonArray("outputs") : json.getAsJsonArray("pool");
        if (arr == null) {
            throw new IllegalArgumentException("weighted_random needs an \"outputs\" (or \"pool\") array: " + json);
        }

        List<WeightedOutputEntry> pool = new ArrayList<>();
        for (JsonElement e : arr) {
            JsonObject obj = e.getAsJsonObject();
            int weight = obj.has("weight") ? obj.get("weight")
                .getAsInt() : 1;
            JsonObject outputJson = obj.has("output") ? obj.getAsJsonObject("output") : obj;
            pool.add(new WeightedOutputEntry(OutputParserRegistry.parse(outputJson), weight));
        }
        return new WeightedRandomDecorator(recipe, pool, rolls);
    }
}
