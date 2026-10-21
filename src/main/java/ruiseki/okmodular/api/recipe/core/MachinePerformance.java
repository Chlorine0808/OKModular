package ruiseki.okmodular.api.recipe.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.context.IRecipeContext;

/**
 * The machine's performance as it stood when a recipe started.
 * <p>
 * Completion-time expressions read this instead of the live machine, so rebuilding the
 * structure mid-run cannot raise the payout of work set up under the old one. Stored
 * resources are not part of it.
 */
public final class MachinePerformance {

    private final int tier;
    private final double speedMultiplier;
    private final double energyMultiplier;
    private final Map<String, Integer> componentTiers;

    private MachinePerformance(int tier, double speedMultiplier, double energyMultiplier,
        Map<String, Integer> componentTiers) {
        this.tier = tier;
        this.speedMultiplier = speedMultiplier;
        this.energyMultiplier = energyMultiplier;
        this.componentTiers = Collections.unmodifiableMap(new HashMap<>(componentTiers));
    }

    /**
     * @return the machine's current performance, or null when the context has no machine
     */
    public static MachinePerformance capture(ConditionContext context) {
        IRecipeContext recipeContext = context != null ? context.getRecipeContext() : null;
        IMachineState state = recipeContext != null ? recipeContext.getMachineState() : null;
        if (state == null) return null;

        Map<String, Integer> components = recipeContext instanceof ITieredMachine tiered ? tiered.getComponentTiers()
            : Collections.emptyMap();
        return new MachinePerformance(
            state.getTier(),
            state.getSpeedMultiplier(),
            state.getEnergyMultiplier(),
            components);
    }

    public int getTier() {
        return tier;
    }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public double getEnergyMultiplier() {
        return energyMultiplier;
    }

    public int getComponentTier(String componentName) {
        return componentTiers.getOrDefault(componentName, 0);
    }

    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger("tier", tier);
        nbt.setDouble("speedMultiplier", speedMultiplier);
        nbt.setDouble("energyMultiplier", energyMultiplier);

        NBTTagCompound components = new NBTTagCompound();
        for (Map.Entry<String, Integer> entry : componentTiers.entrySet()) {
            components.setInteger(entry.getKey(), entry.getValue());
        }
        nbt.setTag("componentTiers", components);
    }

    public static MachinePerformance readFromNBT(NBTTagCompound nbt) {
        Map<String, Integer> components = new HashMap<>();
        NBTTagCompound tag = nbt.getCompoundTag("componentTiers");
        for (Object key : tag.func_150296_c()) {
            components.put((String) key, tag.getInteger((String) key));
        }
        return new MachinePerformance(
            nbt.getInteger("tier"),
            nbt.getDouble("speedMultiplier"),
            nbt.getDouble("energyMultiplier"),
            components);
    }
}
