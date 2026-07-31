package ruiseki.okmodular.api.recipe.io;

import net.minecraft.nbt.NBTTagCompound;

import ruiseki.okcore.json.IJsonMaterial;
import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.core.RecipeTickResult;
import ruiseki.okmodular.api.recipe.visitor.IRecipeVisitor;

/**
 * Base interface for recipe output requirements.
 * This interface is independent of the Modular Port system.
 * For Modular Port-specific functionality, see IModularRecipeOutput.
 */
public interface IRecipeOutput extends IJsonMaterial {

    /**
     * Get the interval (in ticks) for per-tick processing.
     * 0 means not per-tick.
     */
    int getInterval();

    /**
     * Whether this output should be processed per tick.
     */
    default boolean isPerTick() {
        return getInterval() > 0;
    }

    /**
     * Create a deep copy of this output.
     */
    IRecipeOutput copy();

    /**
     * Create a deep copy of this output with a multi-batch quantity.
     *
     * @param multiplier The batch size multiplier
     */
    IRecipeOutput copy(int multiplier);

    /**
     * Write this output state to NBT.
     */
    void writeToNBT(NBTTagCompound nbt);

    /**
     * Read this output state from NBT.
     */
    void readFromNBT(NBTTagCompound nbt);

    /**
     * Get the amount produced by this output.
     */
    long getRequiredAmount();

    /**
     * Get the produced amount based on the provided condition context.
     *
     * @param context The condition context
     * @return The produced amount
     */
    default long getRequiredAmount(ConditionContext context) {
        return getRequiredAmount();
    }

    /**
     * Fix the amount to what the given context says, so it no longer depends on one.
     * <p>
     * Called on the copies a recipe caches when it starts. Those copies already decide
     * <em>what</em> gets produced; this makes them decide <em>how much</em> as well, so that
     * changing the machine mid-run cannot change the payout of work that was set up under
     * the old state. An output with no expression has nothing to do here, which is why the
     * default does nothing.
     */
    default void resolveAmount(ConditionContext context) {}

    /**
     * Accept a visitor to perform operations on this output.
     */
    void accept(IRecipeVisitor visitor);

    /**
     * Get the TickResult to return if this output fails during processing.
     * 
     * @param perTick Whether this is a per-tick check
     */
    default RecipeTickResult getFailureResult(boolean perTick) {
        return RecipeTickResult.OUTPUT_FULL;
    }

    /**
     * Cast this output to IModularRecipeOutput if possible.
     */
    default IModularRecipeOutput asModular() {
        return this instanceof IModularRecipeOutput ? (IModularRecipeOutput) this : null;
    }
}
