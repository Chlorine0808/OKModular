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
 * なぜ値が「すべて非ゼロで、すべて相異なる」のか
 * ============================================
 *
 * 理由が 3 つあり、どれも「間違った繋ぎ方をしたときに失敗すること」を狙っている。
 *
 * **1. 定義の欠落を検出するため。**
 * MachinePropertyExpression は定義が見つからないとき **黙って ZERO を返す**。
 * 機械側の値をすべて非ゼロにしておけば、**0 が返ったこと自体が「定義が無い」の証拠**になる。
 *
 * **2. 資源種の取り違えを検出するため。**
 * 全資源種が同じ値だと、fluid を読むつもりで gas を読んでいても通ってしまう。
 *
 * **3. 方向と名前指定の取り違えを検出するため。**
 * 資源種を引数で受ける汎用アクセサ（B9）は
 * (資源種 × 方向 × 名前あり/なし) を switch で振り分ける。
 * 「合計を返すメソッド」と「名前で引くメソッド」が同じ値だと、
 * どちらを呼んでいても一致してしまう。
 *
 * ============================================
 * 値の付け方
 * ============================================
 *
 * 資源種ごとに基数を決め（energy 100 / mana 200 / fluid 300 / gas 400 /
 * essentia 500 / vis 600 / item 700）、その中で下 1 桁を用途に割り当てる:
 *
 * <pre>
 * +0 合計          +1 名前で引いた量
 * +2 入力側の合計   +3 入力側を名前で引いた量
 * +4 出力側の合計   +5 出力側を名前で引いた量
 * </pre>
 *
 * 空き容量は基数 +10 から同じ並びで置く。容量は基数 × 10。
 * どの値が返ったかで「どのメソッドが呼ばれたか」が一意に分かる。
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

    // --- energy: 方向も名前指定も持たない ---
    public static final long ENERGY_STORED = 100L;
    public static final long ENERGY_CAPACITY = 1000L;
    public static final int ENERGY_PER_TICK = 7;

    // --- mana: 方向も名前指定も持たない ---
    public static final long MANA_STORED = 200L;
    public static final long MANA_CAPACITY = 2000L;

    // --- fluid: 方向も名前指定も持つ ---
    public static final long FLUID_STORED = 300L;
    public static final long FLUID_STORED_NAMED = 301L;
    public static final long FLUID_IN = 302L;
    public static final long FLUID_IN_NAMED = 303L;
    public static final long FLUID_OUT = 304L;
    public static final long FLUID_OUT_NAMED = 305L;
    public static final long FLUID_IN_SPACE = 310L;
    public static final long FLUID_IN_SPACE_NAMED = 311L;
    public static final long FLUID_OUT_SPACE = 312L;
    public static final long FLUID_OUT_SPACE_NAMED = 313L;
    public static final long FLUID_CAPACITY = 3000L;

    // --- gas: 方向も名前指定も持つ ---
    public static final long GAS_STORED = 400L;
    public static final long GAS_STORED_NAMED = 401L;
    public static final long GAS_IN = 402L;
    public static final long GAS_IN_NAMED = 403L;
    public static final long GAS_OUT = 404L;
    public static final long GAS_OUT_NAMED = 405L;
    public static final long GAS_IN_SPACE = 410L;
    public static final long GAS_IN_SPACE_NAMED = 411L;
    public static final long GAS_OUT_SPACE = 412L;
    public static final long GAS_OUT_SPACE_NAMED = 413L;
    public static final long GAS_CAPACITY = 4000L;

    // --- essentia: 名前指定は持つが方向は持たない ---
    public static final long ESSENTIA_STORED = 500L;
    public static final long ESSENTIA_STORED_NAMED = 501L;
    public static final long ESSENTIA_CAPACITY = 5000L;

    // --- vis: 名前指定は持つが方向は持たない ---
    public static final long VIS_STORED = 600L;
    public static final long VIS_STORED_NAMED = 601L;
    public static final long VIS_CAPACITY = 6000L;

    // --- item: 方向も名前指定も持つ。容量はスロット数から導かれる ---
    public static final long ITEM_COUNT = 700L;
    public static final long ITEM_COUNT_NAMED = 701L;
    public static final long ITEM_IN = 702L;
    public static final long ITEM_IN_NAMED = 703L;
    public static final long ITEM_OUT = 704L;
    public static final long ITEM_OUT_NAMED = 705L;
    public static final long ITEM_SPACE = 710L;
    public static final long ITEM_SPACE_NAMED = 711L;
    public static final long ITEM_IN_SPACE = 712L;
    public static final long ITEM_IN_SPACE_NAMED = 713L;
    public static final long ITEM_OUT_SPACE = 714L;
    public static final long ITEM_OUT_SPACE_NAMED = 715L;
    public static final int ITEM_SLOTS = 20;

    /** アイテムの容量は「スロット数 × 64」で表される（式レイヤの既存の扱い）。 */
    public static final double ITEM_CAPACITY = ITEM_SLOTS * (double) IMachineState.ITEMS_PER_SLOT;

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

    private static boolean named(String name) {
        return name != null && !name.isEmpty();
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
     * どのメソッドが呼ばれたかが返り値で一意に分かる IMachineState。
     * 値の意味は問わない。区別できることだけが契約。
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
        public long getStoredFluid(String name) {
            return FLUID_STORED_NAMED;
        }

        @Override
        public long getFluidCapacity() {
            return FLUID_CAPACITY;
        }

        @Override
        public long getTotalFluidInput() {
            return FLUID_IN;
        }

        @Override
        public long getTotalFluidOutput() {
            return FLUID_OUT;
        }

        @Override
        public long getFluidInput(String name) {
            return FLUID_IN_NAMED;
        }

        @Override
        public long getFluidOutput(String name) {
            return FLUID_OUT_NAMED;
        }

        @Override
        public long getTotalFluidInputSpace() {
            return FLUID_IN_SPACE;
        }

        @Override
        public long getTotalFluidOutputSpace() {
            return FLUID_OUT_SPACE;
        }

        @Override
        public long getFluidInputSpace(String name) {
            return FLUID_IN_SPACE_NAMED;
        }

        @Override
        public long getFluidOutputSpace(String name) {
            return FLUID_OUT_SPACE_NAMED;
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
        public long getTotalStoredGas() {
            return GAS_STORED;
        }

        @Override
        public long getStoredGas(String name) {
            return GAS_STORED_NAMED;
        }

        @Override
        public long getGasCapacity() {
            return GAS_CAPACITY;
        }

        @Override
        public long getTotalGasInput() {
            return GAS_IN;
        }

        @Override
        public long getTotalGasOutput() {
            return GAS_OUT;
        }

        @Override
        public long getGasInput(String name) {
            return GAS_IN_NAMED;
        }

        @Override
        public long getGasOutput(String name) {
            return GAS_OUT_NAMED;
        }

        @Override
        public long getTotalGasInputSpace() {
            return GAS_IN_SPACE;
        }

        @Override
        public long getTotalGasOutputSpace() {
            return GAS_OUT_SPACE;
        }

        @Override
        public long getGasInputSpace(String name) {
            return GAS_IN_SPACE_NAMED;
        }

        @Override
        public long getGasOutputSpace(String name) {
            return GAS_OUT_SPACE_NAMED;
        }

        // --- essentia / vis ---
        @Override
        public long getTotalStoredEssentia() {
            return ESSENTIA_STORED;
        }

        @Override
        public long getStoredEssentia(String aspect) {
            return ESSENTIA_STORED_NAMED;
        }

        @Override
        public long getEssentiaCapacity() {
            return ESSENTIA_CAPACITY;
        }

        @Override
        public long getTotalStoredVis() {
            return VIS_STORED;
        }

        @Override
        public long getStoredVis(String aspect) {
            return VIS_STORED_NAMED;
        }

        @Override
        public long getVisCapacity() {
            return VIS_CAPACITY;
        }

        // --- item ---
        @Override
        public long getItemCount(IPortType.Direction direction, String itemName) {
            boolean byName = named(itemName);
            switch (direction) {
                case INPUT:
                    return byName ? ITEM_IN_NAMED : ITEM_IN;
                case OUTPUT:
                    return byName ? ITEM_OUT_NAMED : ITEM_OUT;
                default:
                    return byName ? ITEM_COUNT_NAMED : ITEM_COUNT;
            }
        }

        @Override
        public long getItemSpace(IPortType.Direction direction, String itemName) {
            boolean byName = named(itemName);
            switch (direction) {
                case INPUT:
                    return byName ? ITEM_IN_SPACE_NAMED : ITEM_IN_SPACE;
                case OUTPUT:
                    return byName ? ITEM_OUT_SPACE_NAMED : ITEM_OUT_SPACE;
                default:
                    return byName ? ITEM_SPACE_NAMED : ITEM_SPACE;
            }
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
