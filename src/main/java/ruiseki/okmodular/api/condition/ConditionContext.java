package ruiseki.okmodular.api.condition;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import ruiseki.okmodular.api.recipe.context.IRecipeContext;
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

    // Cache for evaluation results during a single evaluation session
    private final Map<String, EvaluationValue> resultCache = new HashMap<>();
    private NBTTagCompound workingNBT;

    public ConditionContext(World world, int x, int y, int z, IRecipeContext recipeContext, long evaluationSeed) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.recipeContext = recipeContext;
        this.evaluationSeed = evaluationSeed;
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
        return new ConditionContext(world, x, y, z, recipeContext, SeedMixer.forDraw(evaluationSeed, index));
    }

    /**
     * Get a stable seed for deterministic evaluation during this check.
     */
    public long getEvaluationSeed() {
        return evaluationSeed;
    }
}
