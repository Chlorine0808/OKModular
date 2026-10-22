package ruiseki.okmodular.common.tile.agent;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import ruiseki.okcore.helper.LangHelpers;
import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.PortColor;
import ruiseki.okmodular.api.modular.PortColorGrouping;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.error.ErrorReason;
import ruiseki.okmodular.api.recipe.io.IRecipeInput;
import ruiseki.okmodular.common.recipe.ColoredRecipeSearch;
import ruiseki.okmodular.common.recipe.ProcessAgent;
import ruiseki.okmodular.common.recipe.RecipeLoader;
import ruiseki.okmodular.common.tile.ProcessErrorState;

/**
 * Drives a machine's recipes tick by tick: output retries, progress, look-ahead and the
 * error reason the GUI shows.
 * <p>
 * The persisted state (the active colour and the start tick) stays on the host, where
 * {@code @NBTPersist} can see it.
 */
public class RecipeRunner {

    /** What the runner needs from the machine it drives. */
    public interface Host {

        /** A fresh context; one per path, see {@link RecipeRunner#process()}. */
        ConditionContext newConditionContext();

        List<IModularPort> getContextualInputPorts();

        List<IModularPort> getContextualOutputPorts();

        /** The output ports without the controller itself, as the waiting path has always used. */
        List<IModularPort> getOutputPorts();

        List<String> getRecipeGroups();

        PortColor getActiveRecipeColor();

        /**
         * Records the colour and the start tick of a recipe about to start. Called before the
         * start's context is built, because that context's evaluation seed reads the tick.
         */
        void onRecipeStarting(PortColor color);

        void recordRecipeCompletion(IModularRecipe recipe);

        void onWorkingChanged();
    }

    /** Where recipes are looked up. */
    public interface RecipeFinder {

        IModularRecipe findMatch(String[] groups, List<IModularPort> inputs, Predicate<IModularRecipe> canStart);

        int getRecipeVersion();

        RecipeFinder LOADER = new RecipeFinder() {

            @Override
            public IModularRecipe findMatch(String[] groups, List<IModularPort> inputs,
                Predicate<IModularRecipe> canStart) {
                return RecipeLoader.getInstance()
                    .findMatch(groups, inputs, canStart);
            }

            @Override
            public int getRecipeVersion() {
                return RecipeLoader.getInstance()
                    .getRecipeVersion();
            }
        };
    }

    private final Host host;
    private final ProcessAgent processAgent;
    private final ProcessErrorState errors;
    private final RecipeFinder finder;

    // Look-ahead: next recipe cached during current processing
    private IModularRecipe nextRecipe = null;
    // Recipe version at the time nextRecipe was cached (for invalidation on reload)
    private int cachedRecipeVersion = -1;
    // Whether this run has looked ahead. A miss is not retried: completion searches anyway.
    private boolean lookedAhead = false;
    // Previous working state, for the render update
    private boolean wasWorking = false;

    public RecipeRunner(Host host, ProcessAgent processAgent, ProcessErrorState errors, RecipeFinder finder) {
        this.host = Objects.requireNonNull(host, "host");
        this.processAgent = Objects.requireNonNull(processAgent, "processAgent");
        this.errors = Objects.requireNonNull(errors, "errors");
        this.finder = Objects.requireNonNull(finder, "finder");
    }

    /** Drops the look-ahead result, which was searched against ports that may have changed. */
    public void forgetLookAhead() {
        nextRecipe = null;
        lookedAhead = false;
    }

    /**
     * Process recipe using ProcessAgent.
     * Searches for the next recipe during current recipe processing,
     * enabling zero-tick delay between recipe completions.
     * <p>
     * Both paths below build their context with {@link Host#newConditionContext()}, the same way
     * {@link #startNextRecipe()} always has. A bare context answers zero for every machine
     * property and carries no evaluation seed, so with one of those an {@code "amount"} of
     * {@code "1 + tier"} produced 1 and {@code chance()} returned the same answer on every
     * machine forever. One instance per path, reused for the whole of it: its result cache
     * is what makes the machine's state a single snapshot rather than something that shifts
     * between the per-tick inputs and the outputs that follow them.
     */
    public void process() {
        // Try to output if waiting
        if (processAgent.isWaitingForOutput()) {
            ConditionContext context = host.newConditionContext();
            // Same colour the recipe started in. This path passes getOutputPorts()
            // rather than the contextual list, as it always has.
            List<IModularPort> waitingOutputs = PortColorGrouping
                .select(host.getOutputPorts(), host.getActiveRecipeColor());
            if (processAgent.diagnoseBlockOutputFull(waitingOutputs, context)) {
                errors.set(ErrorReason.BLOCK_OUTPUT_FULL);
            } else {
                errors.set(ErrorReason.WAITING_OUTPUT);
            }
            if (processAgent.tryOutput(waitingOutputs, context)) {
                errors.set(ErrorReason.NONE);
                host.recordRecipeCompletion(processAgent.getCurrentRecipe());
                // Output succeeded, try to start next recipe immediately
                startNextRecipe();
            }
            return;
        }

        // If running, tick and look-ahead search for next recipe
        if (processAgent.isRunning()) {
            ConditionContext context = host.newConditionContext();
            ProcessAgent.TickResult result = processAgent.tick(activeInputPorts(), activeOutputPorts(), context);

            if (result == ProcessAgent.TickResult.NO_ENERGY) {
                errors.set(ErrorReason.NO_ENERGY);
            } else if (result == ProcessAgent.TickResult.NO_MANA) {
                errors.set(ErrorReason.NO_MANA);
            } else if (result == ProcessAgent.TickResult.NO_INPUT) {
                errors.set(ErrorReason.INPUT_MISSING);
                if (isOnlyNonConsumingRecipe(processAgent.getCurrentRecipe())) {
                    processAgent.abort();
                }
            } else if (result == ProcessAgent.TickResult.PAUSED) {
                // Only a recipe condition reaches this now, so name the one that stopped it.
                errors.set(ErrorReason.PAUSED, processAgent.getConditionFailure());
            } else if (result == ProcessAgent.TickResult.OUTPUT_FULL) {
                errors.set(ErrorReason.OUTPUT_FULL);
            } else if (result == ProcessAgent.TickResult.BLOCK_MISSING) {
                errors.set(ErrorReason.BLOCK_MISSING);
                if (isOnlyNonConsumingRecipe(processAgent.getCurrentRecipe())) {
                    processAgent.abort();
                }
            } else {
                errors.set(ErrorReason.NONE);
            }

            // Look-ahead: search for next recipe while processing (only once)
            if (!lookedAhead) {
                lookedAhead = true;
                ConditionContext lookAheadContext = host.newConditionContext();
                nextRecipe = finder.findMatch(
                    host.getRecipeGroups()
                        .toArray(new String[0]),
                    host.getContextualInputPorts(),
                    r -> r.canStart(lookAheadContext));
                cachedRecipeVersion = finder.getRecipeVersion();
            }

            // If complete, immediately try to output and start next
            if (result == ProcessAgent.TickResult.READY_OUTPUT) {
                if (processAgent.tryOutput(activeOutputPorts(), context)) {
                    errors.set(ErrorReason.NONE);
                    host.recordRecipeCompletion(processAgent.getCurrentRecipe());
                    startNextRecipe();
                }
            }
            return;
        }

        // IDLE: Try to start new recipe
        startNextRecipe();

        // Update render if working state changed
        boolean currentlyWorking = processAgent.isRunning();
        if (currentlyWorking != wasWorking) {
            wasWorking = currentlyWorking;
            host.onWorkingChanged();
        }
    }

    private void startNextRecipe() {
        // Invalidate cache if recipes were reloaded
        if (cachedRecipeVersion != finder.getRecipeVersion()) {
            nextRecipe = null;
        }

        final IModularRecipe lookAhead = nextRecipe;
        forgetLookAhead();
        final String[] groupNames = host.getRecipeGroups()
            .toArray(new String[0]);
        final ConditionContext searchContext = host.newConditionContext();

        // Each colour group is offered the look-ahead result first, since checking one
        // recipe against a port list is far cheaper than searching every candidate.
        // The look-ahead searched the unfiltered port list, so it may well not apply
        // to the group being considered.
        ColoredRecipeSearch.Selection selection = ColoredRecipeSearch.search(
            PortColorGrouping.group(host.getContextualInputPorts(), host.getContextualOutputPorts()),
            inputs -> {
                if (lookAhead != null && lookAhead.matchesInput(inputs) && lookAhead.canStart(searchContext)) {
                    return lookAhead;
                }
                return finder.findMatch(groupNames, inputs, r -> r.canStart(searchContext));
            });

        if (selection.isRunnable()) {
            host.onRecipeStarting(selection.getColor());
            ConditionContext context = host.newConditionContext();
            if (processAgent
                .startRecipe(selection.getRecipe(), selection.getInputs(), selection.getOutputs(), context)) {
                errors.set(ErrorReason.NONE);
            } else if (processAgent.wasBlockedByCondition()) {
                // Not a missing input. Saying so would send the player to the wrong hatch.
                errors.set(ErrorReason.CONDITION_NOT_MET, processAgent.getConditionFailure());
            } else {
                errors.set(ErrorReason.NO_INPUT);
            }
            return;
        }

        // A recipe was found but no group could run it. The one reported is from the
        // highest-priority colour that got that far.
        if (selection.getRecipe() != null) {
            if (selection.getInsufficientType() != null) {
                errors.set(
                    ErrorReason.OUTPUT_CAPACITY_INSUFFICIENT,
                    LangHelpers.localize(
                        "gui.port_type." + selection.getInsufficientType()
                            .name()));
            } else {
                errors.set(ErrorReason.OUTPUT_FULL);
            }
            return;
        }

        errors.set(ErrorReason.NO_MATCHING_RECIPE);

        // Check if it's actually NO_INPUT
        if (processAgent.diagnoseIdle(host.getContextualInputPorts()) == ProcessAgent.TickResult.NO_INPUT) {
            errors.set(ErrorReason.INPUT_MISSING);
        }
    }

    /**
     * The input ports the running recipe is allowed to draw from.
     *
     * Filtered by the colour the recipe started in, so a painted port only feeds the
     * group it belongs to. A machine with nothing painted gets the whole list back
     * without a copy, which matters because this runs every tick.
     */
    private List<IModularPort> activeInputPorts() {
        return PortColorGrouping.select(host.getContextualInputPorts(), host.getActiveRecipeColor());
    }

    /** The output ports the running recipe is allowed to put results into. */
    private List<IModularPort> activeOutputPorts() {
        return PortColorGrouping.select(host.getContextualOutputPorts(), host.getActiveRecipeColor());
    }

    private static boolean isOnlyNonConsumingRecipe(IModularRecipe recipe) {
        if (recipe == null) return false;
        List<IRecipeInput> inputs = recipe.getInputs();
        if (inputs.isEmpty()) return false;
        for (IRecipeInput input : inputs) {
            if (input.isConsume()) return false;
        }
        return true;
    }
}
