package ruiseki.okmodular.api.recipe.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.modular.IPortType.Direction;
import ruiseki.okmodular.api.modular.IPortType.Type;
import ruiseki.okmodular.api.recipe.expression.StubMachineContext;

/**
 * 資源種を引数で受け取る空き容量アクセサ `getSpace(Kind, Direction, name)` の検証。
 *
 * ============================================
 * 空きは資源種によって「別の量」である
 * ============================================
 *
 * 既存の式定義を読むと、`*_f` の計算は 2 通りに分かれている:
 *
 * - energy / mana / fluid / gas / essentia / vis → **容量 - 残量**
 * - item → **専用メソッド `getItemSpace`**
 *
 * アイテムだけ違うのは偶然ではない。アイテムはスタック上限が種類ごとに違うので、
 * 「スロット数 × 64 - 個数」は実際に入る数と一致しない。
 * だから容量から引くのではなく、入る数を直接数える必要がある。
 *
 * **この違いは畳んだ後も保たなければならない。** 統一の名の下に
 * item を「容量 - 残量」にすると、レシピの空き判定が実態とずれる。
 *
 * ============================================
 * 方向を指定したときは専用メソッドを使う
 * ============================================
 *
 * fluid / gas は方向別の空きを持つ（`fluid_f_in` = getTotalFluidInputSpace）。
 * 入力タンクと出力タンクは別物なので、方向を指定したら容量から引いてはいけない。
 *
 * ============================================
 * 埋めた穴（BOTH かつ種類指定）
 * ============================================
 *
 * 「機械全体で、この流体があと何入るか」は既存の式に無かった組み合わせ
 * （`fluid_f_in("water")` と `fluid_f_out("water")` はあるが `fluid_f("water")` は無い）。
 * 入力側と出力側は別タンクなので **両方向の和** と定める。
 *
 * 一方 BOTH かつ種類指定なしは既存の `fluid_f` があるので、
 * そちらの計算（容量 - 残量）をそのまま残す。同じ資源種の中で式が変わるが、
 * **既存の値を動かさないことを優先する**。
 *
 * ============================================
 */
@DisplayName("資源種で引く空き容量")
public class MachineStateSpaceTest {

    private final IMachineState state = StubMachineContext.machineState();

    private long space(Type kind, Direction dir, String name) {
        return state.getSpace(kind, dir, name);
    }

    private long freeOf(Type kind) {
        return space(kind, Direction.BOTH, null);
    }

    @Test
    @DisplayName("エネルギーとマナは容量 - 残量")
    public void testエネルギーとマナ() {
        assertEquals(StubMachineContext.ENERGY_CAPACITY - StubMachineContext.ENERGY_STORED, freeOf(Type.ENERGY));
        assertEquals(StubMachineContext.MANA_CAPACITY - StubMachineContext.MANA_STORED, freeOf(Type.MANA));
    }

    @Test
    @DisplayName("流体とガスの全体の空きは容量 - 残量（既存の fluid_f / gas_f と同じ）")
    public void test流体とガスの全体() {
        assertEquals(StubMachineContext.FLUID_CAPACITY - StubMachineContext.FLUID_STORED, freeOf(Type.FLUID));
        assertEquals(StubMachineContext.GAS_CAPACITY - StubMachineContext.GAS_STORED, freeOf(Type.GAS));
    }

    @Test
    @DisplayName("流体の方向別の空きは専用メソッドから来る")
    public void test流体の方向別() {
        assertEquals(StubMachineContext.FLUID_IN_SPACE, space(Type.FLUID, Direction.INPUT, null));
        assertEquals(StubMachineContext.FLUID_IN_SPACE_NAMED, space(Type.FLUID, Direction.INPUT, "water"));
        assertEquals(StubMachineContext.FLUID_OUT_SPACE, space(Type.FLUID, Direction.OUTPUT, null));
        assertEquals(StubMachineContext.FLUID_OUT_SPACE_NAMED, space(Type.FLUID, Direction.OUTPUT, "water"));
    }

    @Test
    @DisplayName("ガスの方向別の空きは専用メソッドから来る")
    public void testガスの方向別() {
        assertEquals(StubMachineContext.GAS_IN_SPACE, space(Type.GAS, Direction.INPUT, null));
        assertEquals(StubMachineContext.GAS_IN_SPACE_NAMED, space(Type.GAS, Direction.INPUT, "hydrogen"));
        assertEquals(StubMachineContext.GAS_OUT_SPACE, space(Type.GAS, Direction.OUTPUT, null));
        assertEquals(StubMachineContext.GAS_OUT_SPACE_NAMED, space(Type.GAS, Direction.OUTPUT, "hydrogen"));
    }

    @Test
    @DisplayName("種類を指定した全体の空きは入力側と出力側の和")
    public void test種類指定の全体は両方向の和() {
        assertEquals(
            StubMachineContext.FLUID_IN_SPACE_NAMED + StubMachineContext.FLUID_OUT_SPACE_NAMED,
            space(Type.FLUID, Direction.BOTH, "water"),
            "入力タンクと出力タンクは別物なので足す");

        assertEquals(
            StubMachineContext.GAS_IN_SPACE_NAMED + StubMachineContext.GAS_OUT_SPACE_NAMED,
            space(Type.GAS, Direction.BOTH, "hydrogen"));
    }

    @Test
    @DisplayName("アイテムは容量から引かず専用メソッドを使う")
    public void testアイテム() {
        assertEquals(StubMachineContext.ITEM_SPACE, freeOf(Type.ITEM), "スタック上限が種類ごとに違うので、スロット数 × 64 から引いた値では実態と合わない");
        assertEquals(StubMachineContext.ITEM_SPACE_NAMED, space(Type.ITEM, Direction.BOTH, "minecraft:stone"));
        assertEquals(StubMachineContext.ITEM_IN_SPACE, space(Type.ITEM, Direction.INPUT, null));
        assertEquals(StubMachineContext.ITEM_IN_SPACE_NAMED, space(Type.ITEM, Direction.INPUT, "minecraft:stone"));
        assertEquals(StubMachineContext.ITEM_OUT_SPACE, space(Type.ITEM, Direction.OUTPUT, null));
        assertEquals(StubMachineContext.ITEM_OUT_SPACE_NAMED, space(Type.ITEM, Direction.OUTPUT, "minecraft:stone"));
    }

    @Test
    @DisplayName("エッセンチアと vis は方向別の空きを持たないので容量から引く")
    public void testエッセンチアとvis() {
        assertEquals(StubMachineContext.ESSENTIA_CAPACITY - StubMachineContext.ESSENTIA_STORED, freeOf(Type.ESSENTIA));
        assertEquals(
            StubMachineContext.ESSENTIA_CAPACITY - StubMachineContext.ESSENTIA_STORED_NAMED,
            space(Type.ESSENTIA, Direction.BOTH, "ignis"));
        assertEquals(StubMachineContext.VIS_CAPACITY - StubMachineContext.VIS_STORED, freeOf(Type.VIS));
    }

    @Test
    @DisplayName("量を持たない種は 0")
    public void test量を持たない種() {
        assertEquals(0L, freeOf(Type.BLOCK));
        assertEquals(0L, freeOf(Type.NONE));
    }
}
