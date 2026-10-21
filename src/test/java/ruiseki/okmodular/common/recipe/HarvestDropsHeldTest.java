package ruiseki.okmodular.common.recipe;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.core.ModularRecipe;
import ruiseki.okmodular.api.recipe.decorator.RecipeDecorator;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;
import ruiseki.okmodular.api.recipe.visitor.IRecipeVisitor;

/**
 * What harvest_block collects from the input blocks is held with the run's outputs and paid
 * out at completion.
 *
 * The hook was handed the input ports and the decorator put the drops there. The Ore
 * Transformer's coal ore recipe filled its input hatch with coal. Confirmed in game. The input
 * blocks have to be read at start, before the consuming visitor removes them, so the drops
 * cannot simply be produced at the end; they ride on the cached outputs, which are already
 * saved with the machine and paid out through the capacity check.
 */
class HarvestDropsHeldTest {

    private static final List<IModularPort> NO_PORTS = Collections.emptyList();

    /** Stands in for a harvested drop. Only its identity matters here. */
    private static final class Drop implements IRecipeOutput {

        @Override
        public int getInterval() {
            return 0;
        }

        @Override
        public IRecipeOutput copy() {
            return this;
        }

        @Override
        public IRecipeOutput copy(int multiplier) {
            return this;
        }

        @Override
        public void writeToNBT(NBTTagCompound nbt) {}

        @Override
        public void readFromNBT(NBTTagCompound nbt) {}

        @Override
        public long getRequiredAmount() {
            return 1;
        }

        @Override
        public void accept(IRecipeVisitor visitor) {}

        @Override
        public void read(JsonObject json) {}

        @Override
        public void write(JsonObject json) {}
    }

    private static final class Harvesting extends RecipeDecorator {

        final Drop drop = new Drop();

        Harvesting(IModularRecipe internal) {
            super(internal);
        }

        @Override
        public List<IRecipeOutput> beforeInputsConsumed(ConditionContext context) {
            return Collections.singletonList(drop);
        }
    }

    @Test
    void dropsFromTheInputBlocksWaitWithTheOutputs() {
        Harvesting recipe = new Harvesting(
            ModularRecipe.builder()
                .registryName("test")
                .recipeGroup("test")
                .name("test")
                .duration(100)
                .build());
        ProcessAgent agent = new ProcessAgent(null);

        assertTrue(agent.startRecipe(recipe, NO_PORTS, NO_PORTS, new ConditionContext(null, 0, 0, 0)));

        assertTrue(
            agent.getCachedOutputs()
                .contains(recipe.drop),
            "the drop must be paid out at completion with the other outputs");
    }
}
