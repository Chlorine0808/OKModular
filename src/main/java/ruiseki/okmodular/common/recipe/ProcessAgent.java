package ruiseki.okmodular.common.recipe;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.recipe.context.IRecipeContext;
import ruiseki.okmodular.api.recipe.core.AbstractRecipeProcess;
import ruiseki.okmodular.api.recipe.core.DurationPolicy;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.core.ITieredMachine;
import ruiseki.okmodular.api.recipe.core.RecipeTickResult;
import ruiseki.okmodular.api.recipe.io.BlockInput;
import ruiseki.okmodular.api.recipe.io.IModularRecipeInput;
import ruiseki.okmodular.api.recipe.io.IModularRecipeOutput;
import ruiseki.okmodular.api.recipe.io.IRecipeInput;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;
import ruiseki.okmodular.api.recipe.parser.InputNBTRegistry;
import ruiseki.okmodular.api.recipe.parser.OutputNBTRegistry;
import ruiseki.okmodular.api.recipe.visitor.RecipeExecutionVisitor;
import ruiseki.okmodular.api.structure.core.ConditionPolicy;
import ruiseki.okmodular.api.structure.core.IStructureEntry;
import ruiseki.okmodular.common.tile.MachineConditionGate;

public class ProcessAgent extends AbstractRecipeProcess {

    private final IRecipeContext context;
    private int currentBatchSize = 1;
    private double workProgress;

    /** Whether the last condition check refused, and which condition said so. */
    private boolean conditionBlocked;
    private String conditionFailure;

    public ProcessAgent(IRecipeContext context) {
        this.context = context;
        reset();
    }

    public IRecipeContext getContext() {
        return context;
    }

    @Override
    protected void onStart(IModularRecipe recipe, List<IModularPort> inputPorts) {
        // 1. Check inputs (This is still needed here or in start override)
    }

    // Re-implement start to return boolean and handle validation
    public boolean startRecipe(IModularRecipe recipe, List<IModularPort> inputPorts, List<IModularPort> outputPorts,
        ConditionContext context) {
        if (isRunning()) return false;

        // Nothing checked the recipe's own conditions before it started. The search matches
        // on inputs and the visitors below walk inputs and outputs, so a recipe whose
        // conditions did not hold would start, eat its inputs, fail the check on the next
        // tick and be thrown away - then start again. A machine sat there dissolving its
        // input stack an item every other tick. Refusing here is before anything is consumed.
        if (!conditionsHold(recipe, context)) return false;

        // Calculate maximum possible batch size
        int batchMin = 1;
        int batchMax = 1;

        if (this.context instanceof ITieredMachine tiered) {
            batchMin = Math.max(1, tiered.getBatchMin());
            batchMax = Math.max(batchMin, tiered.getBatchMax());
        }

        int selectedBatch = -1;
        for (int b = batchMax; b >= batchMin; b--) {
            // Check inputs for this batch size
            RecipeExecutionVisitor checker = new RecipeExecutionVisitor(
                RecipeExecutionVisitor.Mode.CHECK,
                inputPorts,
                this,
                context);
            checker.setBatchSize(b);
            recipe.accept(checker);

            if (checker.isSatisfied()) {
                // Check if output ports have capacity for this batch size
                RecipeExecutionVisitor outChecker = new RecipeExecutionVisitor(
                    RecipeExecutionVisitor.Mode.CACHE,
                    outputPorts,
                    this,
                    context);
                outChecker.setBatchSize(b);
                recipe.accept(outChecker);

                if (outChecker.isSatisfied()) {
                    selectedBatch = b;
                    break;
                }
            }
            // Clear current process state if check failed (energyPerTick etc might have
            // been modified by visitors)
            this.reset();
        }

        if (selectedBatch == -1) return false;

        this.currentBatchSize = selectedBatch;

        // Initialize state via base start logic
        super.start(recipe, inputPorts);

        // start() takes the static duration because it has no context. Re-resolve it
        // now that we do, so an expression-valued duration takes effect.
        setMaxProgress(recipe.getDuration(context));

        this.workProgress = this.progress;

        // Consume and setup state (Specific to ProcessAgent)
        RecipeExecutionVisitor consumeVisitor = new RecipeExecutionVisitor(
            RecipeExecutionVisitor.Mode.CONSUME,
            inputPorts,
            this,
            context);
        consumeVisitor.setBatchSize(currentBatchSize);
        recipe.accept(consumeVisitor);

        clearCaches();

        // Cache outputs
        RecipeExecutionVisitor cacheVisitor = new RecipeExecutionVisitor(
            RecipeExecutionVisitor.Mode.CACHE,
            outputPorts,
            this,
            context);
        cacheVisitor.setBatchSize(currentBatchSize);
        recipe.accept(cacheVisitor);

        // Both visitors have run, so the per-tick lists are complete and can be
        // totalled for reporting.
        recomputePerTickTotals(context);

        return true;
    }

    /**
     * Totals the per-tick resource amounts so they can be reported.
     * <p>
     * Nothing ever set these fields, so {@link #getEnergyPerTick()} answered zero for
     * every running machine — and with it {@link IMachineState} and the
     * <code>energy_per_tick</code> recipe variable. The consumption itself was never
     * affected: that is driven by the per-tick inputs, which is exactly what this
     * sums.
     * <p>
     * These are amounts actually drawn, so no multiplier is applied on top. The
     * engine has no route that scales a resource input by the machine's energy
     * multiplier — a recipe applies it by writing <code>energy_multi</code> into the
     * amount, which is already reflected in what we sum here.
     *
     * @param context The context to evaluate expression-valued amounts against
     */
    // Package-private so the totalling can be tested without a TileEntity.
    void recomputePerTickTotals(ConditionContext context) {
        int energyIn = 0;
        int manaIn = 0;
        for (IModularRecipeInput input : perTickInputs) {
            int amount = (int) input.getRequiredAmount(context);
            switch (input.getPortType()) {
                case ENERGY:
                    energyIn += amount;
                    break;
                case MANA:
                    manaIn += amount;
                    break;
                default:
                    break;
            }
        }

        int energyOut = 0;
        int manaOut = 0;
        for (IModularRecipeOutput output : perTickOutputs) {
            int amount = (int) output.getRequiredAmount(context);
            switch (output.getPortType()) {
                case ENERGY:
                    energyOut += amount;
                    break;
                case MANA:
                    manaOut += amount;
                    break;
                default:
                    break;
            }
        }

        this.energyPerTick = energyIn;
        this.manaPerTick = manaIn;
        this.energyOutputPerTick = energyOut;
        this.manaOutputPerTick = manaOut;
    }

    private void clearCaches() {
        cachedOutputs.clear();
        perTickOutputs.clear();
    }

    @Override
    protected boolean consumePerTickResources(List<IModularPort> inputPorts) {
        return true;
    }

    @Override
    protected void onResourceMissing() {
        // Handled by TickResult in TEMachineController.
        // We could set a status here if needed.
    }

    @Override
    protected void onCompleted() {
        // Transition to waitingForOutput is handled by base
    }

    // Adapt tick to return TickResult for TEMachineController compatibility
    public TickResult tick(List<IModularPort> inputPorts, List<IModularPort> outputPorts, ConditionContext context) {
        if (!isRunning()) return TickResult.IDLE;
        if (isWaitingForOutput()) return TickResult.WAITING_OUTPUT;

        // Before anything is drawn for this tick. A paused recipe must not go on paying for
        // itself, and an aborted one must not pay for the tick that killed it - which is
        // what happened while this check sat below the per-tick consumption in executeTick.
        if (currentRecipe != null && !conditionsHold(currentRecipe, context)) {
            if (currentRecipe.getConditionPolicy() == ConditionPolicy.ABORT) {
                abort();
                return TickResult.IDLE;
            }
            return TickResult.PAUSED;
        }

        // Generalized resource check for per-tick inputs/outputs
        for (IModularRecipeInput input : perTickInputs) {
            if (input.getInterval() > 0 && progress % input.getInterval() == 0) {
                if (!input.process(inputPorts, 1, true, context)) {
                    return mapResult(input.getFailureResult(true));
                }
            }
        }
        for (IModularRecipeOutput output : perTickOutputs) {
            if (output.getInterval() > 0 && progress % output.getInterval() == 0) {
                if (!output.checkCapacity(outputPorts, 1, context)) {
                    return mapResult(output.getFailureResult(true));
                }
            }
        }

        // Continuous condition check for non-consuming inputs
        if (currentRecipe != null) {
            RecipeExecutionVisitor checker = new RecipeExecutionVisitor(
                RecipeExecutionVisitor.Mode.CHECK,
                inputPorts,
                this,
                context);
            checker.setBatchSize(currentBatchSize);
            for (IRecipeInput input : currentRecipe.getInputs()) {
                // Skip check if the input is meant to be consumed (already consumed at start)
                // Also skip BlockInput if it involves a replacement (handled at start)
                if (input.isConsume()) continue;
                if (input instanceof BlockInput i && i.getReplace() != null) continue;

                input.accept(checker);
                if (!checker.isSatisfied()) {
                    return mapResult(input.getFailureResult(false));
                }
            }
        }

        // Execute actual tick logic
        executeTick(inputPorts, outputPorts, context);

        if (isWaitingForOutput()) return TickResult.READY_OUTPUT;
        if (!isRunning()) return TickResult.IDLE;

        return TickResult.CONTINUE;
    }

    // Execute base tick (handles conditions, progress, and actual consumption)
    @Override
    public void executeTick(List<IModularPort> inputPorts, List<IModularPort> outputPorts, ConditionContext context) {
        if (!isRunning() || isWaitingForOutput()) return;

        double speedMultiplier = 1.0;
        if (this.context instanceof ITieredMachine tiered) {
            speedMultiplier = tiered.getSpeedMultiplier();

            IStructureEntry entry = tiered.getStructureEntry();
            if (entry != null && entry.isDynamic()) {
                // A dynamic structure's amounts can change while it runs, so the
                // reported totals are re-summed rather than left at their start value.
                recomputePerTickTotals(context);
            }
        }

        // Machines that opted in re-resolve the work amount every tick. See
        // DurationPolicy for what moving the denominator does to the progress bar.
        if (this.context instanceof ITieredMachine tiered && tiered.getDurationPolicy() == DurationPolicy.PER_TICK) {
            setMaxProgress(currentRecipe.getDuration(context));
        }

        // 1. Tick-based resource consumption
        if (!consumePerTickResources(inputPorts)) {
            onResourceMissing();
            return;
        }

        // Process generalized per-tick inputs
        for (IModularRecipeInput input : perTickInputs) {
            if (input.getInterval() > 0 && progress % input.getInterval() == 0) {
                input.process(inputPorts, 1, false, context);
            }
        }

        // Process generalized per-tick outputs
        for (IModularRecipeOutput output : perTickOutputs) {
            if (output.getInterval() > 0 && progress % output.getInterval() == 0) {
                output.apply(outputPorts, 1, context);
            }
        }

        // The conditions were checked in tick(), before any of the above was paid for.

        // 3. Per-tick recipe logic
        currentRecipe.onTick(context);

        // 4. Progress update (Work-amount based)
        workProgress += speedMultiplier;
        this.progress = (long) workProgress;
        onProgressUpdate(progress, maxProgress);

        // 5. Completion check
        if (workProgress >= maxProgress) {
            handleCompletion();
        }
    }

    /**
     * Diagnose why the agent is idle.
     */
    public TickResult diagnoseIdle(List<IModularPort> inputPorts) {
        if (running || waitingForOutput) return TickResult.CONTINUE; // Not idle

        return TickResult.IDLE;
    }

    @Override
    protected boolean produceOutputs(List<IModularPort> outputPorts, ConditionContext context) {
        // 1. Check capacity for all
        for (IRecipeOutput output : cachedOutputs) {
            if (output instanceof IModularRecipeOutput o) {
                if (!o.checkCapacity(outputPorts, 1, context)) {
                    return false;
                }
            }
        }

        // 2. Apply outputs
        for (IRecipeOutput output : cachedOutputs) {
            if (output instanceof IModularRecipeOutput o) {
                o.apply(outputPorts, 1, context);
            }
        }

        return true;
    }

    @Override
    protected void reset() {
        super.reset();
        this.currentBatchSize = 1;
        this.energyPerTick = 0;
        this.energyOutputPerTick = 0;
        this.manaPerTick = 0;
        this.manaOutputPerTick = 0;
        this.workProgress = 0;
        clearCaches();
    }

    public void addCachedOutput(IRecipeOutput output) {
        this.cachedOutputs.add(output);
    }

    /**
     * The energy drawn per tick by the running recipe.
     * <p>
     * Summed from the per-tick inputs by {@link #recomputePerTickTotals}. The setters
     * below exist for a caller that needs to override what gets reported; they are not
     * how the value is normally arrived at.
     */
    public int getEnergyPerTick() {
        return energyPerTick;
    }

    public void setEnergyPerTick(int amount) {
        this.energyPerTick = amount;
    }

    public int getBatchSize() {
        return currentBatchSize;
    }

    /**
     * Whether the last condition check refused.
     * <p>
     * A refused start otherwise looks exactly like a missing input to the caller, and
     * telling a player their inputs are missing when the machine is short of energy sends
     * them looking in the wrong place.
     */
    public boolean wasBlockedByCondition() {
        return conditionBlocked;
    }

    /**
     * The description of the condition that refused, or null.
     * <p>
     * The GUI has always had a slot for this - "conditions not met: %s" - and the machine's
     * own conditions filled it. A recipe's conditions left it empty, so all a player saw was
     * that something was wrong.
     * <p>
     * Null when a decorator refused rather than a named condition, since a {@code chance}
     * roll has nothing to name.
     */
    public String getConditionFailure() {
        return conditionFailure;
    }

    /**
     * Whether the recipe's conditions hold, remembering which one said no.
     * <p>
     * {@link IModularRecipe#isConditionMet} stays the authority: a decorator can refuse on
     * top of the named conditions - {@code chance} is one - and asking the list directly
     * would skip that. The list is only walked afterwards, on the path where the machine has
     * already stopped, to find something to show. That second pass re-evaluates, so a
     * condition built on {@code chance()} may name itself or not; the message is a hint, not
     * a record.
     */
    private boolean conditionsHold(IModularRecipe recipe, ConditionContext context) {
        if (recipe.isConditionMet(context)) {
            conditionBlocked = false;
            conditionFailure = null;
            return true;
        }

        conditionBlocked = true;
        MachineConditionGate.Verdict verdict = MachineConditionGate.evaluate(recipe.getConditions(), () -> context);
        conditionFailure = verdict.isMet() ? null : verdict.getFailedDescription();
        return false;
    }

    public int getEnergyOutputPerTick() {
        return energyOutputPerTick;
    }

    public void setEnergyOutputPerTick(int amount) {
        this.energyOutputPerTick = amount;
    }

    public int getManaPerTick() {
        return manaPerTick;
    }

    public void setManaPerTick(int amount) {
        this.manaPerTick = amount;
    }

    public int getManaOutputPerTick() {
        return manaOutputPerTick;
    }

    public void setManaOutputPerTick(int amount) {
        this.manaOutputPerTick = amount;
    }

    public void setCurrentRecipeName(String name) {
        this.currentRecipeName = name;
    }

    public String getCurrentRecipeName() {
        return currentRecipeName;
    }

    /**
     * Get a list of output types that are currently cached
     */
    public Set<IPortType.Type> getCachedOutputTypes() {
        Set<IPortType.Type> types = new HashSet<>();
        for (IRecipeOutput output : cachedOutputs) {
            if (output instanceof IModularRecipeOutput o) {
                types.add(o.getPortType());
            }
        }
        for (IModularRecipeOutput output : perTickOutputs) {
            types.add(output.getPortType());
        }
        return types;
    }

    public float getProgressPercent() {
        if (maxProgress == 0) return 0;
        return (float) progress / maxProgress;
    }

    public String getStatusMessage(List<IModularPort> outputPorts, ConditionContext context) {
        if (isRunning() && !isWaitingForOutput()) {
            if (maxProgress <= 0) return "Processing " + currentBatchSize + "x 0 %";
            return "Processing " + currentBatchSize + "x " + (int) ((float) progress / maxProgress * 100) + " %";
        }
        if (isWaitingForOutput()) {
            String blocked = diagnoseBlockedOutputs(outputPorts, context);
            return (blocked != null && !blocked.isEmpty() ? blocked + " " : "") + "Output is full";
        }
        return "Idle";
    }

    private String diagnoseBlockedOutputs(List<IModularPort> outputPorts, ConditionContext context) {
        if (currentRecipe != null) {
            StringBuilder blocked = new StringBuilder();
            for (IRecipeOutput output : currentRecipe.getOutputs()) {
                if (output instanceof IModularRecipeOutput o) {
                    if (!o.checkCapacity(outputPorts, currentBatchSize, context)) {
                        if (blocked.length() > 0) blocked.append(", ");
                        blocked.append(
                            o.getPortType()
                                .name());
                    }
                }
            }
            if (blocked.length() > 0) return blocked.toString();
        }
        Set<IPortType.Type> cachedTypes = getCachedOutputTypes();
        if (!cachedTypes.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (IPortType.Type type : cachedTypes) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(type.name());
            }
            return sb.toString();
        }
        return "Unknown";
    }

    public boolean diagnoseBlockOutputFull(List<IModularPort> outputPorts, ConditionContext context) {
        if (currentRecipe != null) {
            for (IRecipeOutput output : currentRecipe.getOutputs()) {
                if (output instanceof IModularRecipeOutput o) {
                    if (o.getPortType() == IPortType.Type.BLOCK && !o.process(outputPorts, 1, true, context)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setLong("progress", progress);
        nbt.setInteger("maxProgress", maxProgress);
        nbt.setInteger("energyPerTick", energyPerTick);
        nbt.setInteger("energyOutputPerTick", energyOutputPerTick);
        nbt.setInteger("manaPerTick", manaPerTick);
        nbt.setInteger("manaOutputPerTick", manaOutputPerTick);
        nbt.setInteger("batchSize", currentBatchSize);
        nbt.setBoolean("running", running);
        nbt.setBoolean("waitingForOutput", waitingForOutput);
        if (currentRecipeName != null) nbt.setString("recipeName", currentRecipeName);

        if (running || waitingForOutput) {
            nbt.setDouble("workProgress", workProgress);

            NBTTagList outputList = new NBTTagList();
            for (IRecipeOutput output : cachedOutputs) {
                NBTTagCompound tag = new NBTTagCompound();
                output.writeToNBT(tag);
                outputList.appendTag(tag);
            }
            nbt.setTag("cachedOutputs", outputList);

            NBTTagList ptInputList = new NBTTagList();
            for (IModularRecipeInput input : perTickInputs) {
                NBTTagCompound tag = new NBTTagCompound();
                input.writeToNBT(tag);
                ptInputList.appendTag(tag);
            }
            nbt.setTag("perTickInputs", ptInputList);

            NBTTagList ptOutputList = new NBTTagList();
            for (IModularRecipeOutput output : perTickOutputs) {
                NBTTagCompound tag = new NBTTagCompound();
                output.writeToNBT(tag);
                ptOutputList.appendTag(tag);
            }
            nbt.setTag("perTickOutputs", ptOutputList);
        }
    }

    public void readFromNBT(NBTTagCompound nbt) {
        progress = nbt.getLong("progress");
        maxProgress = nbt.getInteger("maxProgress");
        energyPerTick = nbt.getInteger("energyPerTick");
        energyOutputPerTick = nbt.getInteger("energyOutputPerTick");
        manaPerTick = nbt.getInteger("manaPerTick");
        manaOutputPerTick = nbt.getInteger("manaOutputPerTick");
        currentBatchSize = nbt.hasKey("batchSize") ? nbt.getInteger("batchSize") : 1;
        running = nbt.getBoolean("running");
        waitingForOutput = nbt.getBoolean("waitingForOutput");
        currentRecipeName = nbt.hasKey("recipeName") ? nbt.getString("recipeName") : null;

        if (running || waitingForOutput) {
            workProgress = nbt.getDouble("workProgress");

            if (currentRecipeName != null && !currentRecipeName.isEmpty()) {
                this.currentRecipe = RecipeLoader.getInstance()
                    .getRecipeByRegistryName(currentRecipeName);
            }
            if (this.currentRecipe == null) {
                this.running = false;
                this.waitingForOutput = false;
                this.currentRecipeName = null;
            }
        }

        clearCaches();

        if (running || waitingForOutput) {
            NBTTagList outputList = nbt.getTagList("cachedOutputs", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < outputList.tagCount(); i++) {
                NBTTagCompound tag = outputList.getCompoundTagAt(i);
                IRecipeOutput output = OutputNBTRegistry.read(tag);
                if (output != null) {
                    cachedOutputs.add(output);
                }
            }

            NBTTagList ptInputList = nbt.getTagList("perTickInputs", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < ptInputList.tagCount(); i++) {
                NBTTagCompound tag = ptInputList.getCompoundTagAt(i);
                IRecipeInput input = InputNBTRegistry.read(tag);
                if (input != null && input instanceof IModularRecipeInput ii) {
                    perTickInputs.add(ii);
                }
            }

            NBTTagList ptOutputList = nbt.getTagList("perTickOutputs", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < ptOutputList.tagCount(); i++) {
                NBTTagCompound tag = ptOutputList.getCompoundTagAt(i);
                IRecipeOutput output = OutputNBTRegistry.read(tag);
                if (output != null && output instanceof IModularRecipeOutput o) {
                    perTickOutputs.add(o);
                }
            }
        }
    }

    private TickResult mapResult(RecipeTickResult result) {
        try {
            return TickResult.valueOf(result.name());
        } catch (IllegalArgumentException e) {
            return TickResult.NO_INPUT; // Fallback
        }
    }

    public enum TickResult {
        IDLE,
        CONTINUE,
        NO_ENERGY,
        READY_OUTPUT,
        WAITING_OUTPUT,
        NO_INPUT,
        NO_MATCHING_RECIPE,
        OUTPUT_FULL,
        PAUSED,
        NO_MANA,
        BLOCK_MISSING,
        BLOCK_OUTPUT_FULL
    }
}
