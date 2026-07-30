package ruiseki.okmodular.api.structure.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.condition.ICondition;
import ruiseki.okmodular.api.enums.EnumIO;
import ruiseki.okmodular.api.recipe.core.DurationPolicy;
import ruiseki.okmodular.api.structure.io.IStructureRequirement;
import ruiseki.okmodular.api.structure.io.IStructureSerializable;
import ruiseki.okmodular.api.structure.visitor.IStructureVisitor;

/**
 * The root interface for a multiblock structure definition.
 */
public interface IStructureEntry extends IStructureSerializable {

    /**
     * Get the internal unique name.
     */
    String getName();

    /**
     * Get the human-readable display name.
     */
    String getDisplayName();

    /**
     * Get all structure layers.
     */
    List<IStructureLayer> getLayers();

    /**
     * Get the symbol-to-representation mappings.
     */
    Map<Character, ISymbolMapping> getMappings();

    /**
     * Get the required elements for formation.
     */
    List<IStructureRequirement> getRequirements();

    /**
     * Get the recipe group(s) this structure belongs to.
     */
    List<String> getRecipeGroup();

    /**
     * Get the controller offset [x, y, z].
     */
    int[] getControllerOffset();

    /**
     * Get the hex tint color (e.g. #FFFFFF).
     */
    String getTintColor();

    /**
     * Returns whether this structure entry requires dynamic evaluation.
     * If true, the machinery process agent (OK Modular)
     * will re-evaluate performance multipliers every tick.
     *
     * @return true if dynamic evaluation is needed
     */
    boolean isDynamic();

    /**
     * When a machine built from this structure re-resolves a recipe duration that
     * was written as an expression.
     * <p>
     * Independent of {@link #isDynamic()}: that one governs the performance
     * multipliers, this one the recipe's work amount. Defaults to evaluating once at
     * recipe start.
     *
     * @return the policy, never null
     */
    default DurationPolicy getDurationPolicy() {
        return DurationPolicy.ON_START;
    }

    /**
     * Conditions the machine itself must satisfy to run at all.
     * <p>
     * Separate from a recipe's conditions: these gate the machine, the way a redstone
     * signal does, and are checked whatever recipe is up next. Declared at the top level of
     * a structure definition under <code>conditions</code>.
     * <p>
     * Empty, never null - this is read every tick, so the caller should not have to spread
     * null checks around to use it.
     */
    default List<ICondition> getConditions() {
        return Collections.emptyList();
    }

    /**
     * What happens to a running recipe when {@link #getConditions()} stops being met.
     * <p>
     * {@link ConditionPolicy#PAUSE} by default, which keeps the run and loses nothing.
     */
    default ConditionPolicy getConditionPolicy() {
        return ConditionPolicy.PAUSE;
    }

    /**
     * Returns the static base speed multiplier for this structure.
     * This is the non-contextual, predefined value.
     *
     * @return base speed multiplier
     */
    double getSpeedMultiplier();

    /**
     * Evaluates the speed multiplier based on the given context.
     *
     * @param context the context for evaluation
     * @return the evaluated speed multiplier
     */
    double evaluateSpeedMultiplier(ConditionContext context);

    /**
     * Returns the static base energy multiplier for this structure.
     * This is the non-contextual, predefined value.
     *
     * @return base energy multiplier
     */
    double getEnergyMultiplier();

    /**
     * Evaluates the energy multiplier based on the given context.
     *
     * @param context the context for evaluation
     * @return the evaluated energy multiplier
     */
    double evaluateEnergyMultiplier(ConditionContext context);

    /**
     * Returns the static base minimum batch size for this structure.
     *
     * @return base minimum batch size
     */
    int getBatchMin();

    /**
     * Evaluates the minimum batch size based on the given context.
     *
     * @param context the context for evaluation
     * @return the evaluated minimum batch size
     */
    int evaluateBatchMin(ConditionContext context);

    /**
     * Returns the static base maximum batch size for this structure.
     *
     * @return base maximum batch size
     */
    int getBatchMax();

    /**
     * Evaluates the maximum batch size based on the given context.
     *
     * @param context the context for evaluation
     * @return the evaluated maximum batch size
     */
    int evaluateBatchMax(ConditionContext context);

    /**
     * @deprecated Use {@link #evaluateSpeedMultiplier(ConditionContext)}
     */
    @Deprecated
    default double getSpeedMultiplier(ConditionContext context) {
        return evaluateSpeedMultiplier(context);
    }

    /**
     * @deprecated Use {@link #evaluateEnergyMultiplier(ConditionContext)}
     */
    @Deprecated
    default double getEnergyMultiplier(ConditionContext context) {
        return evaluateEnergyMultiplier(context);
    }

    /**
     * @deprecated Use {@link #evaluateBatchMin(ConditionContext)}
     */
    @Deprecated
    default int getBatchMin(ConditionContext context) {
        return evaluateBatchMin(context);
    }

    /**
     * @deprecated Use {@link #evaluateBatchMax(ConditionContext)}
     */
    @Deprecated
    default int getBatchMax(ConditionContext context) {
        return evaluateBatchMax(context);
    }

    /**
     * Get the machine tier.
     */
    int getTier();

    /**
     * Get the default structure facing (e.g. SOUTH).
     */
    String getDefaultFacing();

    /**
     * Get the set of external port symbols.
     */
    Set<Character> getExternalPorts();

    /**
     * Get the map of fixed external port symbols and their IO direction.
     */
    Map<Character, EnumIO> getFixedExternalPorts();

    /**
     * Get names of all tiered components in this structure.
     */
    List<String> getComponentNames();

    /**
     * Get the structures that contribute to this machine's tier.
     */
    List<TierStructureRef> getTierStructures();

    /**
     * Accepts a visitor to perform operations on this structure.
     */
    void accept(IStructureVisitor visitor);
}
