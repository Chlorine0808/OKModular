package ruiseki.okmodular.api.recipe.expression;

import java.util.Collections;
import java.util.List;

import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.recipe.context.IRecipeContext;
import ruiseki.okmodular.api.recipe.core.IMachineState;
import ruiseki.okmodular.api.structure.core.IStructureEntry;

/**
 * 機械プロパティの式を評価するための最小コンテキスト。
 *
 * ============================================
 * なぜ「全部の値が非ゼロで、しかも互いに違う」のか
 * ============================================
 *
 * MachinePropertyExpression は定義が見つからないとき **黙って ZERO を返す**
 * （evaluate の末尾）。つまり「登録されているのに定義が無い」欠落は、
 * 値が 0 になることでしか観測できない。
 *
 * そこで機械側の値をすべて非ゼロにしておく。そうすれば
 * **0 が返ってきたこと自体が「定義が無い」の証拠**になる。
 *
 * 差分から導出されるプロパティ（`*_f` = 容量 - 残量）が偶然 0 に
 * ならないよう、残量と容量には違う値を入れる。
 *
 * ============================================
 * B9 との関係
 * ============================================
 *
 * このスタブが 49 メソッドあるのは IMachineState が資源種ごとに
 * メソッドを並べているから。資源種を Kind に畳む改修（B9）が済めば
 * ここは 3 メソッドになる。**このファイルの行数が B9 の進捗計**。
 *
 * ============================================
 */
public final class StubMachineContext {

    /** 残量。容量と違う値にする（差分が 0 にならないように）。 */
    public static final long STORED = 100L;
    /** 容量。 */
    public static final long CAPACITY = 500L;
    /** 空き。容量 - 残量 と一致させる必要は無い（別経路であることを見たいので）。 */
    public static final long SPACE = 400L;
    /** 毎 tick のエネルギー。 */
    public static final int ENERGY_PER_TICK = 7;

    private StubMachineContext() {}

    /** 機械が繋がっているコンテキストを作る。 */
    public static ConditionContext withMachine() {
        return new ConditionContext(null, 0, 0, 0, new StubRecipeContext(new StubMachineState()));
    }

    /** 機械が繋がっていないコンテキスト（NEI のレシピ描画と同じ状況）。 */
    public static ConditionContext withoutMachine() {
        return new ConditionContext(null, 0, 0, 0);
    }

    /** 式の評価だけに使う最小の IRecipeContext。機械状態以外は使われない。 */
    private static final class StubRecipeContext implements IRecipeContext {

        private final IMachineState state;

        StubRecipeContext(IMachineState state) {
            this.state = state;
        }

        @Override
        public IMachineState getMachineState() {
            return state;
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
            return null;
        }
    }

    /**
     * 全メソッドが非ゼロを返す IMachineState。
     * 値の意味は問わない。「0 でないこと」だけが契約。
     */
    private static final class StubMachineState implements IMachineState {

        // --- energy ---
        @Override
        public long getStoredEnergy() {
            return STORED;
        }

        @Override
        public long getEnergyCapacity() {
            return CAPACITY;
        }

        @Override
        public int getEnergyPerTick() {
            return ENERGY_PER_TICK;
        }

        // --- progress / lifecycle ---
        @Override
        public double getProgressPercent() {
            return 0.5;
        }

        @Override
        public long getProgress() {
            return 300L;
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public boolean isWaitingForOutput() {
            return true;
        }

        @Override
        public int getTier() {
            return 3;
        }

        @Override
        public long getTimePlaced() {
            return 1000L;
        }

        @Override
        public long getTimeContinuous() {
            return 800L;
        }

        @Override
        public int getRecipeProcessedCount() {
            return 5;
        }

        @Override
        public int getRecipeProcessedTypesCount() {
            return 2;
        }

        @Override
        public long getRecipeStartTick() {
            return 900L;
        }

        // --- fluid ---
        @Override
        public long getStoredFluid() {
            return STORED;
        }

        @Override
        public long getFluidCapacity() {
            return CAPACITY;
        }

        @Override
        public long getStoredFluid(String name) {
            return STORED;
        }

        @Override
        public long getTotalFluidInput() {
            return STORED;
        }

        @Override
        public long getTotalFluidOutput() {
            return STORED;
        }

        @Override
        public long getFluidInput(String name) {
            return STORED;
        }

        @Override
        public long getFluidOutput(String name) {
            return STORED;
        }

        @Override
        public long getFluidInputSpace(String name) {
            return SPACE;
        }

        @Override
        public long getFluidOutputSpace(String name) {
            return SPACE;
        }

        @Override
        public long getTotalFluidInputSpace() {
            return SPACE;
        }

        @Override
        public long getTotalFluidOutputSpace() {
            return SPACE;
        }

        // --- mana ---
        @Override
        public long getStoredMana() {
            return STORED;
        }

        @Override
        public long getManaCapacity() {
            return CAPACITY;
        }

        // --- gas ---
        @Override
        public long getStoredGas(String name) {
            return STORED;
        }

        @Override
        public long getTotalStoredGas() {
            return STORED;
        }

        @Override
        public long getGasCapacity() {
            return CAPACITY;
        }

        @Override
        public long getTotalGasInput() {
            return STORED;
        }

        @Override
        public long getTotalGasOutput() {
            return STORED;
        }

        @Override
        public long getGasInput(String name) {
            return STORED;
        }

        @Override
        public long getGasOutput(String name) {
            return STORED;
        }

        @Override
        public long getGasInputSpace(String name) {
            return SPACE;
        }

        @Override
        public long getGasOutputSpace(String name) {
            return SPACE;
        }

        @Override
        public long getTotalGasInputSpace() {
            return SPACE;
        }

        @Override
        public long getTotalGasOutputSpace() {
            return SPACE;
        }

        // --- essentia / vis ---
        @Override
        public long getStoredEssentia(String aspect) {
            return STORED;
        }

        @Override
        public long getTotalStoredEssentia() {
            return STORED;
        }

        @Override
        public long getEssentiaCapacity() {
            return CAPACITY;
        }

        @Override
        public long getStoredVis(String aspect) {
            return STORED;
        }

        @Override
        public long getTotalStoredVis() {
            return STORED;
        }

        @Override
        public long getVisCapacity() {
            return CAPACITY;
        }

        // --- item ---
        @Override
        public long getItemCount(IPortType.Direction direction, String itemName) {
            return STORED;
        }

        @Override
        public long getItemSpace(IPortType.Direction direction, String itemName) {
            return SPACE;
        }

        @Override
        public int getItemSlotCount(IPortType.Direction direction, boolean emptyOnly) {
            return 9;
        }

        // --- recipe modifiers ---
        @Override
        public int getBatchSize() {
            return 4;
        }

        @Override
        public double getSpeedMultiplier() {
            return 1.5;
        }

        @Override
        public double getEnergyMultiplier() {
            return 2.5;
        }
    }
}
