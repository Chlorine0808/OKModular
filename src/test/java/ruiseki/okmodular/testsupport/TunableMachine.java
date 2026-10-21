package ruiseki.okmodular.testsupport;

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

    private final IMachineState state = new State();

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

    /** 性能と energy だけを答える機械状態。残りは 0 / false。 */
    private final class State implements IMachineState {

        @Override
        public long getStoredEnergy() {
            return energy;
        }

        @Override
        public long getEnergyCapacity() {
            return 1000L;
        }

        @Override
        public int getEnergyPerTick() {
            return 0;
        }

        @Override
        public double getProgressPercent() {
            return 0;
        }

        @Override
        public boolean isRunning() {
            return false;
        }

        @Override
        public boolean isWaitingForOutput() {
            return false;
        }

        @Override
        public int getTier() {
            return tier;
        }

        @Override
        public long getTimePlaced() {
            return 0;
        }

        @Override
        public long getTimeContinuous() {
            return 0;
        }

        @Override
        public int getRecipeProcessedCount() {
            return 0;
        }

        @Override
        public int getRecipeProcessedTypesCount() {
            return 0;
        }

        @Override
        public long getStoredFluid() {
            return 0;
        }

        @Override
        public long getFluidCapacity() {
            return 0;
        }

        @Override
        public long getStoredFluid(String name) {
            return 0;
        }

        @Override
        public long getFluidInput(String name) {
            return 0;
        }

        @Override
        public long getFluidOutputSpace(String name) {
            return 0;
        }

        @Override
        public long getStoredMana() {
            return 0;
        }

        @Override
        public long getManaCapacity() {
            return 0;
        }

        @Override
        public long getStoredGas(String name) {
            return 0;
        }

        @Override
        public long getTotalStoredGas() {
            return 0;
        }

        @Override
        public long getGasCapacity() {
            return 0;
        }

        @Override
        public long getStoredEssentia(String aspect) {
            return 0;
        }

        @Override
        public long getEssentiaCapacity() {
            return 0;
        }

        @Override
        public long getStoredVis(String aspect) {
            return 0;
        }

        @Override
        public long getVisCapacity() {
            return 0;
        }

        @Override
        public int getBatchSize() {
            return 1;
        }

        @Override
        public double getSpeedMultiplier() {
            return speed;
        }

        @Override
        public double getEnergyMultiplier() {
            return energyMultiplier;
        }
    }
}
