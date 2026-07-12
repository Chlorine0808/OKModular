package ruiseki.okmodular.api.recipe.visitor;

import ruiseki.okmodular.api.recipe.core.IRecipe;
import ruiseki.okmodular.api.recipe.io.BlockInput;
import ruiseki.okmodular.api.recipe.io.BlockOutput;
import ruiseki.okmodular.api.recipe.io.EnergyInput;
import ruiseki.okmodular.api.recipe.io.EnergyOutput;
import ruiseki.okmodular.api.recipe.io.EssentiaInput;
import ruiseki.okmodular.api.recipe.io.EssentiaOutput;
import ruiseki.okmodular.api.recipe.io.FluidInput;
import ruiseki.okmodular.api.recipe.io.FluidOutput;
import ruiseki.okmodular.api.recipe.io.GasInput;
import ruiseki.okmodular.api.recipe.io.GasOutput;
import ruiseki.okmodular.api.recipe.io.IModularRecipeInput;
import ruiseki.okmodular.api.recipe.io.IModularRecipeOutput;
import ruiseki.okmodular.api.recipe.io.IRecipeInput;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;
import ruiseki.okmodular.api.recipe.io.ItemInput;
import ruiseki.okmodular.api.recipe.io.ItemOutput;
import ruiseki.okmodular.api.recipe.io.ManaInput;
import ruiseki.okmodular.api.recipe.io.ManaOutput;
import ruiseki.okmodular.api.recipe.io.VisInput;
import ruiseki.okmodular.api.recipe.io.VisOutput;

/**
 * Interface for visiting recipe elements.
 * Implementations define operations to be performed on recipe components.
 */
public interface IRecipeVisitor {

    /**
     * Visit the entire recipe.
     * Default implementation visits all inputs and outputs.
     */
    default void visit(IRecipe recipe) {
        if (recipe.getInputs() != null) {
            recipe.getInputs()
                .forEach(input -> input.accept(this));
        }
        if (recipe.getOutputs() != null) {
            recipe.getOutputs()
                .forEach(output -> output.accept(this));
        }
    }

    // --- Inputs ---

    default void visit(ItemInput input) {
        visit((IModularRecipeInput) input);
    }

    default void visit(FluidInput input) {
        visit((IModularRecipeInput) input);
    }

    default void visit(EnergyInput input) {
        visit((IModularRecipeInput) input);
    }

    default void visit(EssentiaInput input) {
        visit((IModularRecipeInput) input);
    }

    default void visit(GasInput input) {
        visit((IModularRecipeInput) input);
    }

    default void visit(ManaInput input) {
        visit((IModularRecipeInput) input);
    }

    default void visit(VisInput input) {
        visit((IModularRecipeInput) input);
    }

    default void visit(BlockInput input) {
        visit((IModularRecipeInput) input);
    }

    /**
     * Visit Modular Port-based inputs.
     * Fallback to base input visitor.
     */
    default void visit(IModularRecipeInput input) {
        visit((IRecipeInput) input);
    }

    /**
     * Fallback for unknown input types.
     */
    default void visit(IRecipeInput input) {}

    // --- Outputs ---

    default void visit(ItemOutput output) {
        visit((IModularRecipeOutput) output);
    }

    default void visit(FluidOutput output) {
        visit((IModularRecipeOutput) output);
    }

    default void visit(EnergyOutput output) {
        visit((IModularRecipeOutput) output);
    }

    default void visit(EssentiaOutput output) {
        visit((IModularRecipeOutput) output);
    }

    default void visit(GasOutput output) {
        visit((IModularRecipeOutput) output);
    }

    default void visit(ManaOutput output) {
        visit((IModularRecipeOutput) output);
    }

    default void visit(VisOutput output) {
        visit((IModularRecipeOutput) output);
    }

    default void visit(BlockOutput output) {
        visit((IModularRecipeOutput) output);
    }

    /**
     * Visit Modular Port-based outputs.
     * Fallback to base output visitor.
     */
    default void visit(IModularRecipeOutput output) {
        visit((IRecipeOutput) output);
    }

    /**
     * Fallback for unknown output types.
     */
    default void visit(IRecipeOutput output) {}
}
