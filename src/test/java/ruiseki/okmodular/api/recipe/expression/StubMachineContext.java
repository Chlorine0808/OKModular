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
 * 理由が 2 つある。
 *
 * **1. 定義の欠落を検出するため。**
 * MachinePropertyExpression は定義が見つからないとき **黙って ZERO を返す**。
 * つまり「登録されているのに定義が無い」欠落は、値が 0 になることでしか
 * 観測できない。機械側の値をすべて非ゼロにしておけば、
 * **0 が返ってきたこと自体が「定義が無い」の証拠**になる。
 *
 * **2. 資源種の対応付けを検証するため。**
 * 全資源種が同じ値を返すスタブだと、fluid を読むつもりのコードが
 * 誤って gas を読んでいても値が一致してテストが通ってしまう。
 * **資源種ごとに違う値**を返せば、繋ぎ間違いがそのまま失敗として出る。
 * Kind でまとめた汎用アクセサ（B9）が正しい資源種に届いているかは、
 * これが無いと確かめられない。
 *
 * 残量と容量も違う値にする（`*_f` = 容量 - 残量 が偶然 0 にならないように）。
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

    // 資源種ごとに桁で見分けられる値を置く。
    // 残量 = N00、容量 = N000、方向別 = 残量に近い別値、空き = 容量 - 残量 とは別値。

    public static final long ENERGY_STORED = 100L;
    public static final long ENERGY_CAPACITY = 1000L;
    public static final int ENERGY_PER_TICK = 7;

    public static final long MANA_STORED = 200L;
    public static final long MANA_CAPACITY = 2000L;

    public static final long FLUID_STORED = 300L;
    public static final long FLUID_CAPACITY = 3000L;
    public static final long FLUID_INPUT = 310L;
    public static final long FLUID_OUTPUT = 320L;
    public static final long FLUID_INPUT_SPACE = 330L;
    public static final long FLUID_OUTPUT_SPACE = 340L;

    public static final long GAS_STORED = 400L;
    public static final long GAS_CAPACITY = 4000L;
    public static final long GAS_INPUT = 410L;
    public static final long GAS_OUTPUT = 420L;
    public static final long GAS_INPUT_SPACE = 430L;
    public static final long GAS_OUTPUT_SPACE = 440L;

    public static final long ESSENTIA_STORED = 500L;
    public static final long ESSENTIA_CAPACITY = 5000L;

    public static final long VIS_STORED = 600L;
    public static final long VIS_CAPACITY = 6000L;

    public static final long ITEM_COUNT = 700L;
    public static final long ITEM_SPACE = 750L;
    public static final int ITEM_SLOTS = 9;

    /** アイテムの容量は「スロット数 × 64」で表される（式レイヤの既存の扱い）。 */
    public static final double ITEM_CAPACITY = ITEM_SLOTS * 64.0;

    private StubMachineContext() {}

    /** 機械が繋がっているコンテキストを作る。 */
    public static ConditionContext withMachine() {
        return new ConditionContext(null, 0, 0, 0, new StubRecipeContext(new StubMachineState()));
    }

    /** 機械が繋がっていないコンテキスト（NEI のレシピ描画と同じ状況）。 */
    public static ConditionContext withoutMachine() {
        return new ConditionContext(null, 0, 0, 0);
    }

    /** 機械状態そのもの。汎用アクセサを直接叩くテスト用。 */
    public static IMachineState machineState() {
        return new StubMachineState();
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
     * 全メソッドが非ゼロを返し、資源種ごとに値が違う IMachineState。
     * 値の意味は問わない。「0 でないこと」と「種を混同すれば違う値になること」が契約。
     */
    private static final class StubMachineState implements IMachineState {

        // --- energy ---
        @Override
        public long getStoredEnergy() {
            return ENERGY_STORED;
        }

        @Override
        public long getEnergyCapacity() {
            return ENERGY_CAPACITY;
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
            return FLUID_STORED;
        }

        @Override
        public long getFluidCapacity() {
            return FLUID_CAPACITY;
        }

        @Override
        public long getStoredFluid(String name) {
            return FLUID_STORED;
        }

        @Override
        public long getTotalFluidInput() {
            return FLUID_INPUT;
        }

        @Override
        public long getTotalFluidOutput() {
            return FLUID_OUTPUT;
        }

        @Override
        public long getFluidInput(String name) {
            return FLUID_INPUT;
        }

        @Override
        public long getFluidOutput(String name) {
            return FLUID_OUTPUT;
        }

        @Override
        public long getFluidInputSpace(String name) {
            return FLUID_INPUT_SPACE;
        }

        @Override
        public long getFluidOutputSpace(String name) {
            return FLUID_OUTPUT_SPACE;
        }

        @Override
        public long getTotalFluidInputSpace() {
            return FLUID_INPUT_SPACE;
        }

        @Override
        public long getTotalFluidOutputSpace() {
            return FLUID_OUTPUT_SPACE;
        }

        // --- mana ---
        @Override
        public long getStoredMana() {
            return MANA_STORED;
        }

        @Override
        public long getManaCapacity() {
            return MANA_CAPACITY;
        }

        // --- gas ---
        @Override
        public long getStoredGas(String name) {
            return GAS_STORED;
        }

        @Override
        public long getTotalStoredGas() {
            return GAS_STORED;
        }

        @Override
        public long getGasCapacity() {
            return GAS_CAPACITY;
        }

        @Override
        public long getTotalGasInput() {
            return GAS_INPUT;
        }

        @Override
        public long getTotalGasOutput() {
            return GAS_OUTPUT;
        }

        @Override
        public long getGasInput(String name) {
            return GAS_INPUT;
        }

        @Override
        public long getGasOutput(String name) {
            return GAS_OUTPUT;
        }

        @Override
        public long getGasInputSpace(String name) {
            return GAS_INPUT_SPACE;
        }

        @Override
        public long getGasOutputSpace(String name) {
            return GAS_OUTPUT_SPACE;
        }

        @Override
        public long getTotalGasInputSpace() {
            return GAS_INPUT_SPACE;
        }

        @Override
        public long getTotalGasOutputSpace() {
            return GAS_OUTPUT_SPACE;
        }

        // --- essentia / vis ---
        @Override
        public long getStoredEssentia(String aspect) {
            return ESSENTIA_STORED;
        }

        @Override
        public long getTotalStoredEssentia() {
            return ESSENTIA_STORED;
        }

        @Override
        public long getEssentiaCapacity() {
            return ESSENTIA_CAPACITY;
        }

        @Override
        public long getStoredVis(String aspect) {
            return VIS_STORED;
        }

        @Override
        public long getTotalStoredVis() {
            return VIS_STORED;
        }

        @Override
        public long getVisCapacity() {
            return VIS_CAPACITY;
        }

        // --- item ---
        @Override
        public long getItemCount(IPortType.Direction direction, String itemName) {
            return ITEM_COUNT;
        }

        @Override
        public long getItemSpace(IPortType.Direction direction, String itemName) {
            return ITEM_SPACE;
        }

        @Override
        public int getItemSlotCount(IPortType.Direction direction, boolean emptyOnly) {
            return ITEM_SLOTS;
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
