package ruiseki.okmodular.api.recipe.decorator;

import java.util.List;
import java.util.Map;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.condition.ICondition;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.expression.IExpression;
import ruiseki.okmodular.api.recipe.io.IRecipeInput;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;
import ruiseki.okmodular.api.recipe.visitor.IRecipeVisitor;
import ruiseki.okmodular.api.structure.core.ConditionPolicy;

/**
 * Basic decorator for IModularRecipe.
 * Delegates all calls to the internal recipe instance.
 */
public abstract class RecipeDecorator implements IModularRecipe {

    protected final IModularRecipe internal;

    protected RecipeDecorator(IModularRecipe internal) {
        this.internal = internal;
    }

    @Override
    public String getRegistryName() {
        return internal.getRegistryName();
    }

    @Override
    public String getRecipeGroup() {
        return internal.getRecipeGroup();
    }

    @Override
    public String getName() {
        return internal.getName();
    }

    @Override
    public int getDuration() {
        return internal.getDuration();
    }

    @Override
    public int getDuration(ConditionContext context) {
        return internal.getDuration(context);
    }

    @Override
    public IExpression getDurationExpression() {
        return internal.getDurationExpression();
    }

    @Override
    public int getPriority() {
        return internal.getPriority();
    }

    @Override
    public List<IRecipeInput> getInputs() {
        return internal.getInputs();
    }

    @Override
    public List<IRecipeOutput> getOutputs() {
        return internal.getOutputs();
    }

    @Override
    public List<ICondition> getConditions() {
        return internal.getConditions();
    }

    @Override
    public boolean isConditionMet(ConditionContext context) {
        return internal.isConditionMet(context);
    }

    @Override
    public boolean canStart(ConditionContext context) {
        return internal.canStart(context);
    }

    @Override
    public ConditionPolicy getConditionPolicy() {
        return internal.getConditionPolicy();
    }

    @Override
    public boolean processInputs(List<IModularPort> inputPorts, boolean simulate) {
        return internal.processInputs(inputPorts, simulate);
    }

    @Override
    public boolean processOutputs(List<IModularPort> outputPorts, boolean simulate) {
        return internal.processOutputs(outputPorts, simulate);
    }

    @Override
    public boolean matchesInput(List<IModularPort> inputPorts) {
        return internal.matchesInput(inputPorts);
    }

    @Override
    public boolean canOutput(List<IModularPort> outputPorts) {
        return internal.canOutput(outputPorts);
    }

    @Override
    public IPortType.Type checkOutputCapacity(List<IModularPort> outputPorts) {
        return internal.checkOutputCapacity(outputPorts);
    }

    @Override
    public void onTick(ConditionContext context) {
        internal.onTick(context);
    }

    @Override
    public int getMaxTierRequired() {
        return internal.getMaxTierRequired();
    }

    @Override
    public Map<String, Integer> getRequiredComponentTiers() {
        return internal.getRequiredComponentTiers();
    }

    @Override
    public int getTotalItemInputCount() {
        return internal.getTotalItemInputCount();
    }

    /**
     * Presents this decorator to the visitor, not the recipe underneath it.
     * <p>
     * {@link IRecipeVisitor#visit(ruiseki.okmodular.api.recipe.core.IRecipe)} walks
     * whatever {@link #getInputs()} and {@link #getOutputs()} return, so handing over
     * the wrapped recipe would hide anything a decorator contributes — which is how
     * the engine sees a recipe's inputs at all. Decorators that leave both lists alone
     * are unaffected, since those calls delegate.
     */
    @Override
    public void accept(IRecipeVisitor visitor) {
        visitor.visit(this);
    }
}
