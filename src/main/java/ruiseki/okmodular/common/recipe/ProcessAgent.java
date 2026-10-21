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
import ruiseki.okmodular.api.recipe.core.MachinePerformance;
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

    /**
     * The plan for the tick being processed: the speed multiplier, and the stretch of work
     * the tick covers. {@code NaN} means no tick is in flight.
     * <p>
     * {@link #tick} decides whether the per-tick resources are there and {@link #executeTick}
     * takes them, and <b>the two must arrive at the same count</b> or the machine draws what
     * it never confirmed. The multiplier is an expression evaluated against the machine and
     * nothing caches it, so resolving it twice in one tick is an invitation for the halves to
     * disagree; the work amount can move mid-run too, under {@code DurationPolicy.PER_TICK}.
     * Both are settled once, by {@link #planTick}, and read from here by both halves.
     */
    private double plannedSpeed = Double.NaN;
    private long plannedFrom;
    private long plannedTo;

    /** Whether the last condition check refused, and which condition said so. */
    private boolean conditionBlocked;
    private String conditionFailure;

    /** This run's success draw: whether it has been made, and what it said. See {@link #payoutSucceeds}. */
    private boolean payoutDecided;
    private boolean payoutSucceeded;

    /** The machine's performance when this run started. The payout reads it; see {@link #produceOutputs}. */
    private MachinePerformance startedWith;

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
        if (!conditionsHold(recipe, context, true)) return false;

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
        this.startedWith = MachinePerformance.capture(context);

        // Initialize state via base start logic
        super.start(recipe, inputPorts);

        // start() takes the static duration because it has no context. Re-resolve it
        // now that we do, so an expression-valued duration takes effect.
        setMaxProgress(recipe.getDuration(context));

        this.workProgress = this.progress;

        // Last moment the input blocks are still standing. harvest_block collects them here;
        // after the visitor below runs, a consuming BlockInput has removed them and nothing
        // remembers what they were. What it collects is held until the cache below is built.
        List<IRecipeOutput> collected = recipe.beforeInputsConsumed(context);

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
        for (IRecipeOutput output : collected) {
            addCachedOutput(output);
        }

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

        // A fresh tick, so the previous tick's plan is void. Nothing else clears it: the
        // paths out of executeTick include an early return, and a plan left behind there
        // would bill the next tick at the speed the machine used to have.
        this.plannedSpeed = Double.NaN;

        // Before anything is drawn for this tick. A paused recipe must not go on paying for
        // itself, and an aborted one must not pay for the tick that killed it - which is
        // what happened while this check sat below the per-tick consumption in executeTick.
        if (currentRecipe != null && !conditionsHold(currentRecipe, context, false)) {
            if (currentRecipe.getConditionPolicy() == ConditionPolicy.ABORT) {
                abort();
                return TickResult.IDLE;
            }
            return TickResult.PAUSED;
        }

        // Generalized resource check for per-tick inputs/outputs. The count is how many
        // intervals this tick's work crosses, which is exactly what executeTick will draw.
        for (IModularRecipeInput input : perTickInputs) {
            int due = dueThisTick(input.getInterval());
            if (due > 0 && !input.process(inputPorts, due, true, context)) {
                return mapResult(input.getFailureResult(true));
            }
        }
        for (IModularRecipeOutput output : perTickOutputs) {
            int due = dueThisTick(output.getInterval());
            if (due > 0 && !output.checkCapacity(outputPorts, due, context)) {
                return mapResult(output.getFailureResult(true));
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

        // The same plan tick() checked against, not a second reading of it.
        planTick();
        double speedMultiplier = plannedSpeed;

        if (this.context instanceof ITieredMachine tiered) {
            IStructureEntry entry = tiered.getStructureEntry();
            if (entry != null && entry.isDynamic()) {
                // A dynamic structure's amounts can change while it runs, so the
                // reported totals are re-summed rather than left at their start value.
                recomputePerTickTotals(context);
            }

            // Machines that opted in re-resolve the work amount every tick. See
            // DurationPolicy for what moving the denominator does to the progress bar.
            // This lands after planTick, so a work amount that moves takes effect on the
            // next tick rather than splitting this one's check from its consumption.
            if (tiered.getDurationPolicy() == DurationPolicy.PER_TICK) {
                setMaxProgress(currentRecipe.getDuration(context));
            }
        }

        // 1. Tick-based resource consumption
        if (!consumePerTickResources(inputPorts)) {
            onResourceMissing();
            return;
        }

        // Process generalized per-tick inputs
        for (IModularRecipeInput input : perTickInputs) {
            int due = dueThisTick(input.getInterval());
            if (due > 0) input.process(inputPorts, due, false, context);
        }

        // Process generalized per-tick outputs
        for (IModularRecipeOutput output : perTickOutputs) {
            int due = dueThisTick(output.getInterval());
            if (due > 0) output.apply(outputPorts, due, context);
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
     * Whether this run hands over its outputs, asked once and remembered.
     *
     * <h2>The draw sits inside the method that retries</h2>
     *
     * {@code produceOutputs} both decides the payout and tries to place it, and a full output
     * port makes it answer false and <b>run again on the next tick</b>. So the draw was made
     * again on every tick the machine spent blocked. Nobody designed that; it is where the
     * call happens to sit.
     * <p>
     * A fixed {@code chance} survived it, because the evaluation seed does not move inside a
     * run and the same question gives the same answer. <b>An expression-valued chance did
     * not.</b> {@code "chance": "energy_p"} is re-evaluated against a machine whose energy is
     * still moving while it waits, so a run that won on the tick it finished could lose on
     * the next one - and losing means completing with nothing, after the inputs are already
     * gone. The longer the output stayed blocked, the likelier it was.
     * <p>
     * Deciding once and keeping the answer is what the draw meant in the first place. The
     * decision is persisted because a machine can be saved while it waits, and re-drawing
     * after a reload is the same bug through a different door.
     */
    private boolean payoutSucceeds(ConditionContext context) {
        if (!payoutDecided) {
            payoutSucceeded = currentRecipe.producesOutput(context);
            payoutDecided = true;
        }
        return payoutSucceeded;
    }

    /**
     * Settle what this tick will do, once, for both halves of it to read.
     * <p>
     * The stretch of work is clamped to the recipe's work amount because <b>progress past
     * completion is thrown away</b> - {@code workProgress} resets on completion - so billing
     * for it would charge for work the recipe never used. With the clamp the draw count over
     * a whole recipe is {@code duration / interval} at any speed, which is what lets a recipe
     * state its own cost.
     */
    private void planTick() {
        if (!Double.isNaN(plannedSpeed)) return;

        plannedSpeed = this.context instanceof ITieredMachine tiered ? tiered.getSpeedMultiplier() : 1.0;
        plannedFrom = (long) workProgress;

        double reached = workProgress + plannedSpeed;
        if (maxProgress > 0 && reached > maxProgress) reached = maxProgress;
        plannedTo = (long) reached;
    }

    /** How many times a resource with this interval comes due during this tick. */
    private int dueThisTick(int interval) {
        planTick();
        return PerTickSchedule.dueCount(plannedFrom, plannedTo, interval);
    }

    /**
     * Diagnose why the agent is idle.
     */
    public TickResult diagnoseIdle(List<IModularPort> inputPorts) {
        if (running || waitingForOutput) return TickResult.CONTINUE; // Not idle

        return TickResult.IDLE;
    }

    @Override
    protected boolean produceOutputs(List<IModularPort> outputPorts, ConditionContext live) {
        // Everything below - the success draw, the outputs, the decorators - reads the
        // machine's performance as it stood at the start. Read live, a structure rebuilt to a
        // higher tier just before completion would pay out at that tier for inputs taken at
        // the lower one.
        ConditionContext context = live != null ? live.withPerformance(startedWith) : null;

        // A lost success draw ends the run with nothing to hand over. Reporting success is
        // right: the run did finish, and answering false would leave the machine waiting for
        // output space it does not need, forever. See IRecipe#producesOutput.
        if (currentRecipe != null && !payoutSucceeds(context)) {
            return true;
        }

        // 1. Check capacity for all
        for (IRecipeOutput output : cachedOutputs) {
            if (output instanceof IModularRecipeOutput o) {
                if (!o.checkCapacity(outputPorts, 1, context)) {
                    return false;
                }
            }
        }

        // 2. Last moment the blocks an output is about to overwrite are still there.
        // produceExtraOutputs below is too late: by then a BlockOutput has replaced them.
        if (currentRecipe != null) {
            currentRecipe.beforeOutputsApplied(outputPorts, context);
        }

        // 3. Apply outputs
        for (IRecipeOutput output : cachedOutputs) {
            if (output instanceof IModularRecipeOutput o) {
                o.apply(outputPorts, 1, context);
            }
        }

        // 3. Then whatever the decorators add on top. Without this call the decorators are
        // inert: their effects sit behind processOutputs(ports, false), which nothing invokes
        // because completion pays out the cached copies rather than asking the recipe.
        if (currentRecipe != null) {
            currentRecipe.produceExtraOutputs(outputPorts, context);
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
        this.plannedSpeed = Double.NaN;
        this.payoutDecided = false;
        this.payoutSucceeded = false;
        this.startedWith = null;
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
     * Null when a decorator refused rather than a named condition - {@code requirement}
     * carries a condition of its own that is not in {@link IModularRecipe#getConditions()}.
     */
    public String getConditionFailure() {
        return conditionFailure;
    }

    /**
     * Whether the recipe's conditions hold, remembering which one said no.
     * <p>
     * {@link IModularRecipe#isConditionMet} stays the authority: a decorator can refuse on
     * top of the named conditions - {@code requirement} is one - and asking the list
     * directly would skip that. The list is only walked afterwards, on the path where the
     * machine has already stopped, to find something to show. That second pass re-evaluates,
     * so a condition built on {@code chance()} may name itself or not; the message is a
     * hint, not a record.
     */
    private boolean conditionsHold(IModularRecipe recipe, ConditionContext context, boolean starting) {
        if (starting ? recipe.canStart(context) : recipe.isConditionMet(context)) {
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
            nbt.setBoolean("payoutDecided", payoutDecided);
            nbt.setBoolean("payoutSucceeded", payoutSucceeded);
            if (startedWith != null) {
                NBTTagCompound tag = new NBTTagCompound();
                startedWith.writeToNBT(tag);
                nbt.setTag("startedWith", tag);
            }

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
            payoutDecided = nbt.getBoolean("payoutDecided");
            payoutSucceeded = nbt.getBoolean("payoutSucceeded");
            startedWith = nbt.hasKey("startedWith") ? MachinePerformance.readFromNBT(nbt.getCompoundTag("startedWith"))
                : null;

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
