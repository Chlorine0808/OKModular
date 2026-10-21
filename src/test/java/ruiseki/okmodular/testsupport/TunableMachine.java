package ruiseki.okmodular.testsupport;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.context.IRecipeContext;
import ruiseki.okmodular.api.recipe.core.IMachineState;
import ruiseki.okmodular.api.recipe.core.ITieredMachine;
import ruiseki.okmodular.api.structure.core.IStructureEntry;

/**
 * 稼働中に性能を差し替えられる機械。
 *
 * 「途中で構造体を組み替える」をテストで再現するための道具。
 * 性能（tier・係数・部品 Tier）と、比べ役の残量（energy）だけを持ち、
 * それ以外の機械状態は 0 / false を返す。
 */
public final class TunableMachine implements IRecipeContext, ITieredMachine {

    public int tier = 1;
    public double speed = 1.0;
    public double energyMultiplier = 1.0;
    public long energy = 0;
    public final Map<String, Integer> components = new HashMap<>();

    private final IMachineState state = (IMachineState) Proxy
        .newProxyInstance(IMachineState.class.getClassLoader(), new Class<?>[] { IMachineState.class }, (p, m, a) -> {
            switch (m.getName()) {
                case "getTier":
                    return tier;
                case "getSpeedMultiplier":
                    return speed;
                case "getEnergyMultiplier":
                    return energyMultiplier;
                case "getStoredEnergy":
                    return energy;
                case "getEnergyCapacity":
                    return 1000L;
                case "getBatchSize":
                    return 1;
                default:
                    return zeroOf(m.getReturnType());
            }
        });

    private static Object zeroOf(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        return null;
    }

    /** この機械に繋がった文脈。 */
    public ConditionContext context() {
        return new ConditionContext(null, 0, 0, 0, this, 0L);
    }

    @Override
    public IMachineState getMachineState() {
        return state;
    }

    @Override
    public int getComponentTier(String componentName) {
        return components.getOrDefault(componentName, 0);
    }

    @Override
    public Map<String, Integer> getComponentTiers() {
        return components;
    }

    @Override
    public double getSpeedMultiplier() {
        return speed;
    }

    @Override
    public double getEnergyMultiplier() {
        return energyMultiplier;
    }

    @Override
    public int getBatchMin() {
        return 1;
    }

    @Override
    public int getBatchMax() {
        return 1;
    }

    @Override
    public IStructureEntry getStructureEntry() {
        return null;
    }

    @Override
    public World getWorld() {
        return null;
    }

    @Override
    public ChunkCoordinates getControllerPos() {
        return null;
    }

    @Override
    public IStructureEntry getCurrentStructure() {
        return null;
    }

    @Override
    public ForgeDirection getFacing() {
        return ForgeDirection.NORTH;
    }

    @Override
    public List<ChunkCoordinates> getSymbolPositions(char symbol) {
        return Collections.emptyList();
    }

    @Override
    public ConditionContext getConditionContext() {
        return context();
    }
}
