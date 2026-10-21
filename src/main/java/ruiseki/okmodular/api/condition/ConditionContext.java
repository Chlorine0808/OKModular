package ruiseki.okmodular.api.condition;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import ruiseki.okmodular.api.recipe.context.IRecipeContext;
import ruiseki.okmodular.api.recipe.core.MachinePerformance;
import ruiseki.okmodular.api.recipe.expression.EvaluationValue;
import ruiseki.okmodular.api.recipe.expression.SeedMixer;

/**
 * Represents a context in which a condition is checked.
 */
public class ConditionContext {

    private final World world;
    private final int x;
    private final int y;
    private final int z;
    private final IRecipeContext recipeContext;
    private final long evaluationSeed;

    /**
     * The seed with no position mixed into it, so it is the same at every cell of one placement.
     * <p>
     * {@link #evaluationSeed} deliberately differs per cell - that is what makes {@code random()}
     * scatter instead of flipping one coin for a whole pattern. A field that has to be
     * <em>smooth</em> over positions needs the opposite: {@code noise()} hashes lattice corners, and
     * hashing each of them under a different seed leaves white noise. So the unscattered value is
     * kept alongside rather than derived, because {@code forPosition} cannot be undone.
     */
    private final long placementSeed;

    /** Offset from the anchor of the pattern cell being asked about, or null outside a pattern. */
    private final int[] cell;

    /** What the machine's performance was when the recipe started, or null to read it live. */
    private final MachinePerformance performance;

    // Cache for evaluation results during a single evaluation session
    private final Map<String, EvaluationValue> resultCache = new HashMap<>();
    private NBTTagCompound workingNBT;

    public ConditionContext(World world, int x, int y, int z, IRecipeContext recipeContext, long evaluationSeed) {
        this(world, x, y, z, recipeContext, evaluationSeed, evaluationSeed, null, null);
    }

    private ConditionContext(World world, int x, int y, int z, IRecipeContext recipeContext, long evaluationSeed,
        long placementSeed, int[] cell, MachinePerformance performance) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.recipeContext = recipeContext;
        this.evaluationSeed = evaluationSeed;
        this.placementSeed = placementSeed;
        this.cell = cell;
        this.performance = performance;
    }

    /**
     * The same machine, asked about one cell of a pattern it is writing.
     * <p>
     * <b>The machine is an argument rather than six loose values, so it cannot be left behind.</b>
     * Building a bare context here would leave {@code tier} and every other machine property
     * answering zero, with nothing raised - the failure {@code fix-expression} had to clear out of
     * seven call sites.
     * <p>
     * {@code x} / {@code y} / {@code z} become the cell's own world position, which is what
     * {@code OffsetCondition} and {@code BiomePatternCondition} already do: those coordinates mean
     * "the position being asked about", not "the controller". That is what lets a pattern read the
     * depth, the light or the biome at each cell rather than at the machine.
     *
     * @param machine the machine's context, or null where there is none (a load-time check, NEI)
     * @param cellX   offset from the anchor in the pattern's own frame, where {@code cellY} is up
     */
    public static ConditionContext forCell(ConditionContext machine, int worldX, int worldY, int worldZ, int cellX,
        int cellY, int cellZ) {
        World world = machine != null ? machine.world : null;
        IRecipeContext recipeContext = machine != null ? machine.recipeContext : null;
        MachinePerformance performance = machine != null ? machine.performance : null;
        long runSeed = machine != null ? machine.evaluationSeed : 0L;

        // Mixed per position, or random() inside a chance would answer the same at every cell and
        // place all of them or none - the whole-pattern coin flip forPosition exists to prevent.
        long seed = SeedMixer.forPosition(runSeed, worldX, worldY, worldZ);

        return new ConditionContext(
            world,
            worldX,
            worldY,
            worldZ,
            recipeContext,
            seed,
            runSeed,
            new int[] { cellX, cellY, cellZ },
            performance);
    }

    /**
     * The same context, answering the machine's performance from what it was at recipe start.
     * <p>
     * Stored resources are still read live. Passing null reads everything live.
     */
    public ConditionContext withPerformance(MachinePerformance performance) {
        return new ConditionContext(world, x, y, z, recipeContext, evaluationSeed, placementSeed, cell, performance);
    }

    /** The performance captured at recipe start, or null when the machine is read live. */
    public MachinePerformance getPerformance() {
        return performance;
    }

    /** Whether this context is being asked about a particular pattern cell. */
    public boolean hasCell() {
        return cell != null;
    }

    public int getCellX() {
        return cell == null ? 0 : cell[0];
    }

    public int getCellY() {
        return cell == null ? 0 : cell[1];
    }

    public int getCellZ() {
        return cell == null ? 0 : cell[2];
    }
    // ... rest of constructors ...

    public EvaluationValue getCachedValue(String key) {
        return resultCache.get(key);
    }

    public void setCachedValue(String key, EvaluationValue value) {
        resultCache.put(key, value);
    }

    public NBTTagCompound getWorkingNBT() {
        return workingNBT;
    }

    public void setWorkingNBT(NBTTagCompound nbt) {
        this.workingNBT = nbt;
    }

    public void clearCache() {
        resultCache.clear();
    }

    public ConditionContext(World world, int x, int y, int z, IRecipeContext recipeContext) {
        this(world, x, y, z, recipeContext, 0L);
    }

    public ConditionContext(World world, int x, int y, int z) {
        this(world, x, y, z, null, 0L);
    }

    public World getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public IRecipeContext getRecipeContext() {
        return recipeContext;
    }

    /**
     * The same context, drawing from a different point in the random stream.
     * <p>
     * For amounts resolved once per run of a batch: everything about the world and the
     * machine stays put, only {@code random()} and {@code chance()} move. Draw zero is this
     * context itself, so a batch of one behaves exactly as before.
     */
    public ConditionContext forDraw(int index) {
        if (index == 0) return this;
        // The cell is carried over: this says "the same question, a different draw", and dropping
        // it would make cell_y answer zero from the second draw on without raising anything.
        return new ConditionContext(
            world,
            x,
            y,
            z,
            recipeContext,
            SeedMixer.forDraw(evaluationSeed, index),
            placementSeed,
            cell,
            performance);
    }

    /**
     * Get a stable seed for deterministic evaluation during this check.
     */
    public long getEvaluationSeed() {
        return evaluationSeed;
    }

    /**
     * The seed shared by every cell of one placement, for a field that has to be smooth across
     * them. Outside a pattern this is {@link #getEvaluationSeed()}.
     */
    public long getPlacementSeed() {
        return placementSeed;
    }
}
