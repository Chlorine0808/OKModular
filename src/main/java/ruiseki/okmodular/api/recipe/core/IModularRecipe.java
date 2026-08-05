package ruiseki.okmodular.api.recipe.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IPortType;

/**
 * Interface for modular recipes.
 * Extends Generic Comparable to handle sorting of various recipe
 * implementations and decorators.
 */
public interface IModularRecipe extends IRecipe {

    /**
     * @param inputPorts List of input ports
     * @param simulate   If true, only check. If false, consume inputs.
     * @return true if all inputs are satisfied
     */
    boolean processInputs(List<IModularPort> inputPorts, boolean simulate);

    /**
     * @param outputPorts List of output ports
     * @param simulate    If true, only check. If false, produce outputs.
     * @return true if all outputs can be inserted
     */
    boolean processOutputs(List<IModularPort> outputPorts, boolean simulate);

    /**
     * Anything a decorator produces beyond the recipe's own outputs, applied on completion.
     * <p>
     * Decorators used to hang their effects off {@code processOutputs(ports, false)}, and
     * <b>nothing ever called it that way</b>. The engine settles each output's amount when a
     * recipe starts, keeps the resolved copies in {@code ProcessAgent.cachedOutputs}, and
     * hands those over on completion - so the recipe object is never asked to produce
     * anything, and every {@code if (!simulate)} block in every decorator was dead. Six
     * decorator types shipped, were documented, and did nothing.
     * <p>
     * This is a separate call rather than a revival of {@code processOutputs} because that
     * one starts by producing the base outputs, which the cached copies have already handed
     * over: routing completion through it would pay every recipe twice. It also has to stay
     * separate to keep amounts settled at start - a machine whose tier changes mid-run must
     * pay out at the tier it began with.
     * <p>
     * Called after the cached outputs are in, so a decorator can see the port state its
     * recipe just produced.
     *
     * @param context the same context the outputs were applied with; carries the run's seed
     */
    default void produceExtraOutputs(List<IModularPort> outputPorts,
        ruiseki.okmodular.api.condition.ConditionContext context) {}

    boolean matchesInput(List<IModularPort> inputPorts);

    boolean canOutput(List<IModularPort> outputPorts);

    IPortType.Type checkOutputCapacity(List<IModularPort> outputPorts);

    /**
     * Get the required Tier for specific components.
     * key: component name, value: required Tier.
     */
    default Map<String, Integer> getRequiredComponentTiers() {
        return Collections.emptyMap();
    }
}
