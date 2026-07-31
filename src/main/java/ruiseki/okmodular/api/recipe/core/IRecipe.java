package ruiseki.okmodular.api.recipe.core;

import java.util.List;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.condition.ICondition;
import ruiseki.okmodular.api.recipe.expression.IExpression;
import ruiseki.okmodular.api.recipe.io.IModularRecipeInput;
import ruiseki.okmodular.api.recipe.io.IRecipeInput;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;
import ruiseki.okmodular.api.recipe.io.ItemInput;
import ruiseki.okmodular.api.recipe.visitor.IRecipeVisitor;
import ruiseki.okmodular.api.structure.core.ConditionPolicy;

/**
 * Base interface for all modular recipes.
 * Extends Generic Comparable to handle sorting of various recipe
 * implementations and decorators.
 */
public interface IRecipe extends Comparable<IRecipe> {

    String getRegistryName();

    String getRecipeGroup();

    String getName();

    int getDuration();

    /**
     * The duration to use for a machine in the given context.
     * <p>
     * Note that duration is a work amount, not a wall-clock time: the engine
     * advances a recipe by the machine's speed multiplier each tick, so the actual
     * time taken is this divided by that multiplier. Writing the speed multiplier
     * into the duration would apply it twice.
     *
     * @param context The context to evaluate against, or null when there is no
     *                machine — NEI, validation, tooling
     * @return The evaluated duration, or the static one when there is no expression
     *         or no context
     */
    default int getDuration(ConditionContext context) {
        return getDuration();
    }

    /**
     * The expression behind the duration, if it was written as one.
     * <p>
     * NEI needs this because it renders recipes with no machine to evaluate
     * against, so for a machine-dependent duration there is no number it can
     * honestly show.
     *
     * @return The expression, or null if the duration is a plain number
     */
    default IExpression getDurationExpression() {
        return null;
    }

    int getPriority();

    /**
     * Get the maximum Tier required by this recipe across all component
     * requirements.
     * 
     * @return the max tier required, defaults to 0.
     */
    default int getMaxTierRequired() {
        return 0;
    }

    List<IRecipeInput> getInputs();

    List<IRecipeOutput> getOutputs();

    List<ICondition> getConditions();

    boolean isConditionMet(ConditionContext context);

    /**
     * What becomes of this recipe when its own conditions stop holding mid-run.
     * <p>
     * Recipe conditions used to be checked only while running, and a failure always threw
     * the recipe away along with everything it had already eaten. Structures could choose
     * between pausing and aborting; recipes could not. They can now, and the default is the
     * same {@link ConditionPolicy#PAUSE} for the same reason.
     */
    default ConditionPolicy getConditionPolicy() {
        return ConditionPolicy.PAUSE;
    }

    @Override
    default int compareTo(IRecipe other) {
        // 1. Higher Tier comes first
        int tierCompare = Integer.compare(other.getMaxTierRequired(), this.getMaxTierRequired());
        if (tierCompare != 0) return tierCompare;

        // 2. Higher priority comes first
        int priorityCompare = Integer.compare(other.getPriority(), this.getPriority());
        if (priorityCompare != 0) return priorityCompare;

        // 2. More input types comes first
        int thisInputTypes = (int) this.getInputs()
            .stream()
            .filter(i -> i instanceof IModularRecipeInput)
            .map(i -> ((IModularRecipeInput) i).getPortType())
            .distinct()
            .count();
        int otherInputTypes = (int) other.getInputs()
            .stream()
            .filter(i -> i instanceof IModularRecipeInput)
            .map(i -> ((IModularRecipeInput) i).getPortType())
            .distinct()
            .count();
        int inputTypeCompare = Integer.compare(otherInputTypes, thisInputTypes);
        if (inputTypeCompare != 0) return inputTypeCompare;

        int stackCompare = Integer.compare(other.getTotalItemInputCount(), this.getTotalItemInputCount());
        if (stackCompare != 0) return stackCompare;

        // 4. Registry name alphabetical order
        if (this.getRegistryName() != null && other.getRegistryName() != null) {
            return this.getRegistryName()
                .compareTo(other.getRegistryName());
        }
        return 0;
    }

    default int getTotalItemInputCount() {
        return getInputs().stream()
            .filter(i -> i instanceof ItemInput)
            .mapToInt(i -> (int) ((ItemInput) i).getRequiredAmount())
            .sum();
    }

    /**
     * Called every tick while the recipe is being processed.
     * 
     * @param context The context of the machine processing this recipe.
     */
    void onTick(ConditionContext context);

    /**
     * Accept a visitor to perform operations on this recipe.
     * 
     * @param visitor The visitor to accept.
     */
    void accept(IRecipeVisitor visitor);
}
