package ruiseki.okmodular.api.recipe.decorator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.util.WeightedRandom;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.recipe.context.IRecipeContext;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.io.IModularRecipeOutput;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;
import ruiseki.okmodular.api.recipe.parser.OutputParserRegistry;

/**
 * Decorator that picks outputs randomly from a weighted pool.
 */
public class WeightedRandomDecorator extends RecipeDecorator {

    private final List<WeightedOutputEntry> pool;
    private final int rolls;
    private final Random rand = new Random();

    public WeightedRandomDecorator(IModularRecipe internal, List<WeightedOutputEntry> pool, int rolls) {
        super(internal);
        this.pool = pool;
        this.rolls = rolls;
    }

    @Override
    public boolean processOutputs(List<IModularPort> outputPorts, boolean simulate) {
        // Process original outputs first
        if (!internal.processOutputs(outputPorts, simulate)) {
            return false;
        }

        if (!simulate) {
            IRecipeContext context = findRecipeContext(outputPorts);
            ConditionContext condContext = context != null ? context.getConditionContext() : null;

            for (int i = 0; i < rolls; i++) {
                WeightedOutputEntry picked = (WeightedOutputEntry) WeightedRandom.getRandomItem(rand, pool);
                if (picked != null && picked.output instanceof IModularRecipeOutput modularOutput) {
                    List<IModularPort> filtered = filterByType(outputPorts, modularOutput.getPortType());
                    if (modularOutput.checkCapacity(filtered, 1, condContext)) {
                        modularOutput.apply(filtered, 1, condContext);
                    }
                }
            }
        }

        return true;
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

    private IRecipeContext findRecipeContext(List<IModularPort> outputPorts) {
        for (IModularPort port : outputPorts) {
            if (port instanceof IRecipeContext context) {
                return context;
            }
        }
        return null;
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
