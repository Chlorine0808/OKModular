package ruiseki.okmodular.api.recipe.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.modular.IPortType.Direction;
import ruiseki.okmodular.api.modular.IPortType.Type;
import ruiseki.okmodular.api.recipe.expression.StubMachineContext;

/**
 * 資源種を引数で受け取る量アクセサ `getAmount(Kind, Direction, name)` の検証。
 *
 * ============================================
 * 何を畳んでいるか
 * ============================================
 *
 * 量を読むメソッドが資源種 × 方向 × 名前あり/なし で手書きされている:
 *
 * <pre>
 * getStoredFluid()      getStoredFluid(name)
 * getTotalFluidInput()  getFluidInput(name)
 * getTotalFluidOutput() getFluidOutput(name)
 * </pre>
 *
 * gas も同じ形で 6 本、essentia / vis は方向が無いので 2 本ずつ、
 * item だけは既に `getItemCount(Direction, name)` という**畳んだ形**を持っている。
 * つまり目標形は既に in-tree にあり、他の資源種をそこへ寄せるのが B9。
 *
 * ============================================
 * 引数の意味
 * ============================================
 *
 * - `Direction.BOTH` = 方向を問わない（機械全体）
 * - `name == null` または空文字 = 種類を問わない（その資源種の合計）
 *
 * 方向を持たない資源種（energy / mana / essentia / vis）に INPUT / OUTPUT を
 * 渡した場合は**合計に落ちる**。「方向別の値が無い」ことと「0」は違うので、
 * 0 を返してはいけない。
 *
 * ============================================
 * なぜ値を 1 つずつ突き合わせるのか
 * ============================================
 *
 * switch で 7 種 × 3 方向 × 2 通りを振り分けるので、**繋ぎ間違いが起きやすい**。
 * スタブは呼ばれたメソッドごとに違う値を返すので、
 * 期待値との一致がそのまま「正しいメソッドに届いた」証明になる。
 *
 * ============================================
 */
@DisplayName("資源種で引く量")
public class MachineStateAmountTest {

    private final IMachineState state = StubMachineContext.machineState();

    private long amount(Type kind, Direction dir, String name) {
        return state.getAmount(kind, dir, name);
    }

    private long total(Type kind) {
        return amount(kind, Direction.BOTH, null);
    }

    @Test
    @DisplayName("エネルギーは方向も名前も無視して残量を返す")
    public void testエネルギー() {
        assertEquals(StubMachineContext.ENERGY_STORED, total(Type.ENERGY));
        assertEquals(StubMachineContext.ENERGY_STORED, amount(Type.ENERGY, Direction.INPUT, null));
        assertEquals(StubMachineContext.ENERGY_STORED, amount(Type.ENERGY, Direction.OUTPUT, "何か"));
    }

    @Test
    @DisplayName("マナも方向も名前も無視して残量を返す")
    public void testマナ() {
        assertEquals(StubMachineContext.MANA_STORED, total(Type.MANA));
        assertEquals(StubMachineContext.MANA_STORED, amount(Type.MANA, Direction.INPUT, "何か"));
    }

    @Test
    @DisplayName("流体は方向と名前で 6 通りに分かれる")
    public void test流体() {
        assertEquals(StubMachineContext.FLUID_STORED, total(Type.FLUID));
        assertEquals(StubMachineContext.FLUID_STORED_NAMED, amount(Type.FLUID, Direction.BOTH, "water"));
        assertEquals(StubMachineContext.FLUID_IN, amount(Type.FLUID, Direction.INPUT, null));
        assertEquals(StubMachineContext.FLUID_IN_NAMED, amount(Type.FLUID, Direction.INPUT, "water"));
        assertEquals(StubMachineContext.FLUID_OUT, amount(Type.FLUID, Direction.OUTPUT, null));
        assertEquals(StubMachineContext.FLUID_OUT_NAMED, amount(Type.FLUID, Direction.OUTPUT, "water"));
    }

    @Test
    @DisplayName("ガスは方向と名前で 6 通りに分かれる")
    public void testガス() {
        assertEquals(StubMachineContext.GAS_STORED, total(Type.GAS));
        assertEquals(StubMachineContext.GAS_STORED_NAMED, amount(Type.GAS, Direction.BOTH, "hydrogen"));
        assertEquals(StubMachineContext.GAS_IN, amount(Type.GAS, Direction.INPUT, null));
        assertEquals(StubMachineContext.GAS_IN_NAMED, amount(Type.GAS, Direction.INPUT, "hydrogen"));
        assertEquals(StubMachineContext.GAS_OUT, amount(Type.GAS, Direction.OUTPUT, null));
        assertEquals(StubMachineContext.GAS_OUT_NAMED, amount(Type.GAS, Direction.OUTPUT, "hydrogen"));
    }

    @Test
    @DisplayName("アイテムは方向と名前で 6 通りに分かれる")
    public void testアイテム() {
        assertEquals(StubMachineContext.ITEM_COUNT, total(Type.ITEM));
        assertEquals(StubMachineContext.ITEM_COUNT_NAMED, amount(Type.ITEM, Direction.BOTH, "minecraft:stone"));
        assertEquals(StubMachineContext.ITEM_IN, amount(Type.ITEM, Direction.INPUT, null));
        assertEquals(StubMachineContext.ITEM_IN_NAMED, amount(Type.ITEM, Direction.INPUT, "minecraft:stone"));
        assertEquals(StubMachineContext.ITEM_OUT, amount(Type.ITEM, Direction.OUTPUT, null));
        assertEquals(StubMachineContext.ITEM_OUT_NAMED, amount(Type.ITEM, Direction.OUTPUT, "minecraft:stone"));
    }

    @Test
    @DisplayName("エッセンチアと vis は名前で分かれるが方向は持たない")
    public void testエッセンチアとvis() {
        assertEquals(StubMachineContext.ESSENTIA_STORED, total(Type.ESSENTIA));
        assertEquals(StubMachineContext.ESSENTIA_STORED_NAMED, amount(Type.ESSENTIA, Direction.BOTH, "ignis"));
        assertEquals(
            StubMachineContext.ESSENTIA_STORED,
            amount(Type.ESSENTIA, Direction.INPUT, null),
            "方向別の値を持たない資源種は合計に落ちる。0 ではない");

        assertEquals(StubMachineContext.VIS_STORED, total(Type.VIS));
        assertEquals(StubMachineContext.VIS_STORED_NAMED, amount(Type.VIS, Direction.BOTH, "ignis"));
        assertEquals(StubMachineContext.VIS_STORED, amount(Type.VIS, Direction.OUTPUT, null));
    }

    @Test
    @DisplayName("空文字の名前は「種類を問わない」と同じ扱い")
    public void test空文字は名前なし扱い() {
        assertEquals(
            StubMachineContext.FLUID_STORED,
            amount(Type.FLUID, Direction.BOTH, ""),
            "式の引数が空文字で届くことがあるので、null と同じに扱う");
    }

    @Test
    @DisplayName("量を持たない種は 0")
    public void test量を持たない種() {
        assertEquals(0L, total(Type.BLOCK));
        assertEquals(0L, total(Type.NONE));
    }
}
