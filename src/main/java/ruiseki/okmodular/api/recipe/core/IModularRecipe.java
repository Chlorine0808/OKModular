package ruiseki.okmodular.api.recipe.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;

/**
 * Interface for modular recipes.
 * Extends Generic Comparable to handle sorting of various recipe
 * implementations and decorators.
 */
public interface IModularRecipe extends IRecipe {

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
    default void produceExtraOutputs(List<IModularPort> outputPorts, ConditionContext context) {}

    /**
     * Anything that has to happen while the input blocks are still standing.
     * <p>
     * Called from {@code ProcessAgent.startRecipe} immediately before the consuming visitor
     * runs, so a decorator can still see what a {@code BlockInput} is about to take away.
     * There is no equivalent moment later: once the visitor has run, the blocks are gone and
     * nothing records what they were.
     * <p>
     * This is not the place to refuse. The batch size is already chosen and the run is about
     * to begin; a decorator that wants to stop a recipe overrides {@link #canStart}, which is
     * asked before any of this.
     *
     * @return outputs to hold with the run's own and pay out at completion, such as what was
     *         collected from those blocks; never the input ports, which are not where a
     *         product belongs
     */
    default List<IRecipeOutput> beforeInputsConsumed(ConditionContext context) {
        return Collections.emptyList();
    }

    /**
     * Anything that has to happen while the blocks an output will overwrite are still there.
     * <p>
     * Called from {@code ProcessAgent.produceOutputs} between the capacity check and the
     * application of {@code cachedOutputs}. {@link #produceExtraOutputs} is too late for this
     * - it runs after the outputs have landed, by which point a {@code BlockOutput} has
     * already replaced whatever was in the way.
     * <p>
     * Also not a place to refuse: the inputs were consumed at start, so answering no here
     * would take the materials and hand back nothing.
     */
    default void beforeOutputsApplied(List<IModularPort> outputPorts, ConditionContext context) {}

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
