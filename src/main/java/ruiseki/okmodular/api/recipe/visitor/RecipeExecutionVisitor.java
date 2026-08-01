package ruiseki.okmodular.api.recipe.visitor;

import java.util.List;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.recipe.core.AbstractRecipeProcess;
import ruiseki.okmodular.api.recipe.io.BlockOutput;
import ruiseki.okmodular.api.recipe.io.IModularRecipeInput;
import ruiseki.okmodular.api.recipe.io.IModularRecipeOutput;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;

/**
 * Visitor that handles the actual execution of a recipe.
 * Dispatches logic for checking, consuming, and caching outputs based on the
 * mode.
 */
public class RecipeExecutionVisitor implements IRecipeVisitor {

    public enum Mode {
        CHECK, // Check if inputs are available
        CONSUME, // Consume inputs and setup per-tick processing
        CACHE // Cache outputs for later production
    }

    private final Mode mode;
    private final List<IModularPort> ports;
    private final AbstractRecipeProcess agent;
    private final ConditionContext context;
    private int batchSize = 1;
    private boolean satisfied = true;

    public RecipeExecutionVisitor(Mode mode, List<IModularPort> ports, AbstractRecipeProcess agent,
        ConditionContext context) {
        this.mode = mode;
        this.ports = ports;
        this.agent = agent;
        this.context = context;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public AbstractRecipeProcess getAgent() {
        return agent;
    }

    public boolean isSatisfied() {
        return satisfied;
    }

    // --- Modular Inputs ---

    @Override
    public void visit(IModularRecipeInput input) {
        if (mode == Mode.CHECK) {
            // Check even if per-tick, to ensure initial availability
            if (!input.process(ports, batchSize, true, context)) satisfied = false;
        } else if (mode == Mode.CONSUME) {
            if (input.isPerTick()) {
                agent.addPerTickInput((IModularRecipeInput) input.copy(batchSize));
            } else {
                input.process(ports, batchSize, false, context);
            }
        }
    }

    // --- Modular Outputs ---

    /**
     * The cached copy is the recipe's promise of what it will hand back, so its amount is
     * settled here rather than left as an expression to be evaluated on completion - a
     * machine whose tier changes mid-run would otherwise pay out at the new tier for work
     * set up under the old one. A {@code perTick} output says every tick on its face, so it
     * keeps its expression.
     */
    @Override
    public void visit(IModularRecipeOutput output) {
        if (mode == Mode.CACHE) {
            if (output.isPerTick()) {
                agent.addPerTickOutput((IModularRecipeOutput) output.copy(batchSize));
            } else {
                // copy(1) rather than copy(batchSize): the batch is applied by resolving the
                // amount once per run, not by scaling one result.
                IRecipeOutput cached = output.copy(1);
                cached.resolveAmount(context, batchSize);
                agent.addCachedOutput(cached);
            }
        }
    }

    /**
     * BlockOutput has special capacity checking during CACHE mode.
     */
    @Override
    public void visit(BlockOutput output) {
        if (mode == Mode.CACHE) {
            // BlockOutput acts as a placement check during CACHE mode (checkOutputCapacity)
            if (!output.checkCapacity(ports, batchSize, context)) {
                satisfied = false;
            }
            if (output.isPerTick()) {
                agent.addPerTickOutput((IModularRecipeOutput) output.copy(batchSize));
            } else {
                // copy(1) rather than copy(batchSize): the batch is applied by resolving the
                // amount once per run, not by scaling one result.
                IRecipeOutput cached = output.copy(1);
                cached.resolveAmount(context, batchSize);
                agent.addCachedOutput(cached);
            }
        }
    }
}
