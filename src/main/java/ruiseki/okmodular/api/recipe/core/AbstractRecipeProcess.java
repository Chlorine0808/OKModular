package ruiseki.okmodular.api.recipe.core;

import java.util.ArrayList;
import java.util.List;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.recipe.io.IModularRecipeInput;
import ruiseki.okmodular.api.recipe.io.IModularRecipeOutput;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;

/**
 * Abstract base class for recipe processing logic.
 * Implements the Template Method pattern for the recipe execution flow.
 */
public abstract class AbstractRecipeProcess {

    protected IModularRecipe currentRecipe;
    protected String currentRecipeName;
    protected long progress;
    protected int maxProgress;
    protected boolean running;
    protected boolean waitingForOutput;

    // Process state
    protected int energyPerTick;
    protected int energyOutputPerTick;
    protected int manaPerTick;
    protected int manaOutputPerTick;

    // Cached outputs to be produced upon completion
    protected final List<IRecipeOutput> cachedOutputs = new ArrayList<>();

    protected List<IModularRecipeInput> perTickInputs = new ArrayList<>();
    protected List<IModularRecipeOutput> perTickOutputs = new ArrayList<>();

    public AbstractRecipeProcess() {}

    public void start(IModularRecipe recipe, List<IModularPort> inputPorts) {
        this.currentRecipe = recipe;
        this.currentRecipeName = recipe.getRegistryName();
        this.maxProgress = recipe.getDuration();
        this.progress = 0;
        this.running = true;
        this.waitingForOutput = false;

        onStart(recipe, inputPorts);
    }

    /**
     * Hook called when a recipe starts. Subclasses can use this to initialize state
     * or cache results.
     */
    protected abstract void onStart(IModularRecipe recipe, List<IModularPort> inputPorts);

    /**
     * Advance the recipe by one tick: draw the per-tick resources, run the recipe's own tick
     * logic, move progress and complete when it is done.
     * <p>
     * This used to carry a default body, and nothing has ever run it - {@code ProcessAgent}
     * is the only subclass and overrides it. The body was a stale earlier design that
     * advanced progress by one per tick with no speed multiplier, and it carried its own
     * spelling of the per-tick schedule that had already drifted from the live one. A dead
     * function on what looks like the main path is worse than no function, so the step is
     * declared here and implemented there.
     */
    public abstract void executeTick(List<IModularPort> inputPorts, List<IModularPort> outputPorts,
        ConditionContext context);

    protected abstract boolean consumePerTickResources(List<IModularPort> inputPorts);

    protected abstract void onResourceMissing();

    protected void onProgressUpdate(long current, int max) {
        // Can be overridden for syncing or particles
    }

    protected void handleCompletion() {
        this.running = false;
        this.waitingForOutput = true;
        onCompleted();
    }

    protected abstract void onCompleted();

    public boolean tryOutput(List<IModularPort> outputPorts, ConditionContext context) {
        if (!waitingForOutput) return false;

        if (produceOutputs(outputPorts, context)) {
            this.waitingForOutput = false;
            reset();
            return true;
        }
        return false;
    }

    protected abstract boolean produceOutputs(List<IModularPort> outputPorts, ConditionContext context);

    public void abort() {
        reset();
    }

    protected void reset() {
        currentRecipe = null;
        currentRecipeName = null;
        progress = 0;
        maxProgress = 0;
        running = false;
        waitingForOutput = false;
        energyPerTick = 0;
        energyOutputPerTick = 0;
        manaPerTick = 0;
        manaOutputPerTick = 0;
        cachedOutputs.clear();
        perTickInputs.clear();
        perTickOutputs.clear();
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isWaitingForOutput() {
        return waitingForOutput;
    }

    public long getProgress() {
        return progress;
    }

    public int getMaxProgress() {
        return maxProgress;
    }

    public void setProgress(long progress) {
        this.progress = Math.max(0, progress);
    }

    public void setMaxProgress(int maxProgress) {
        this.maxProgress = Math.max(0, maxProgress);
    }

    public IModularRecipe getCurrentRecipe() {
        return currentRecipe;
    }

    public void addCachedOutput(IRecipeOutput output) {
        if (output != null) {
            this.cachedOutputs.add(output);
        }
    }

    public List<IRecipeOutput> getCachedOutputs() {
        return cachedOutputs;
    }

    public void addPerTickInput(IModularRecipeInput input) {
        if (input != null) {
            this.perTickInputs.add(input);
        }
    }

    public void addPerTickOutput(IModularRecipeOutput output) {
        if (output != null) {
            this.perTickOutputs.add(output);
        }
    }

    public List<IModularRecipeInput> getPerTickInputs() {
        return perTickInputs;
    }

    public List<IModularRecipeOutput> getPerTickOutputs() {
        return perTickOutputs;
    }
}
