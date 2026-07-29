package ruiseki.okmodular.api.recipe.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.recipe.expression.StubMachineContext;

/**
 * 資源種を引数で受け取る容量アクセサ `getCapacity(Kind)` の検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * IMachineState は資源種ごとにメソッドを並べており（49 メソッド）、
 * 資源種を 1 つ足すたびに同じ形のメソッドが約 10 本増える。
 * これを「資源種を引数で受け取る 3 本」に畳むのが B9。
 *
 * その 1 本目が容量。**畳んだ後も各資源種に正しく届いていること**を
 * 資源種ごとに違う値を返すスタブで確かめる
 * （同じ値のスタブでは fluid と gas を繋ぎ違えても通ってしまう）。
 *
 * ============================================
 * Kind の正本は IPortType.Type
 * ============================================
 *
 * 新しい enum を作らない。IPortType.Type は既にポート型の軸として存在し、
 * checkOutputCapacity の戻り値であり、lang キー gui.port_type.* も生えている。
 * 軸を 2 本持つと同期ずれが起きる。
 *
 * 資源として量を持たない BLOCK / NONE を除く述語 isStorable() を持たせ、
 * 手書きの SUPPORTED_TYPES 配列と一致することもここで確認する
 * （一致していれば、後で配列を述語から導出する形に置き換えられる）。
 *
 * ============================================
 */
@DisplayName("資源種で引く容量")
public class MachineStateCapacityTest {

    private final IMachineState state = StubMachineContext.machineState();

    private long capacityOf(IPortType.Type kind) {
        return state.getCapacity(kind);
    }

    @Test
    @DisplayName("エネルギーとマナの容量")
    public void testエネルギーとマナ() {
        assertEquals(StubMachineContext.ENERGY_CAPACITY, capacityOf(IPortType.Type.ENERGY));
        assertEquals(StubMachineContext.MANA_CAPACITY, capacityOf(IPortType.Type.MANA));
    }

    @Test
    @DisplayName("流体とガスの容量")
    public void test流体とガス() {
        assertEquals(StubMachineContext.FLUID_CAPACITY, capacityOf(IPortType.Type.FLUID));
        assertEquals(StubMachineContext.GAS_CAPACITY, capacityOf(IPortType.Type.GAS));
    }

    @Test
    @DisplayName("エッセンチアと vis の容量")
    public void testエッセンチアとvis() {
        assertEquals(StubMachineContext.ESSENTIA_CAPACITY, capacityOf(IPortType.Type.ESSENTIA));
        assertEquals(StubMachineContext.VIS_CAPACITY, capacityOf(IPortType.Type.VIS));
    }

    @Test
    @DisplayName("アイテムの容量はスロット数 × 64")
    public void testアイテム() {
        assertEquals(
            (long) StubMachineContext.ITEM_CAPACITY,
            capacityOf(IPortType.Type.ITEM),
            "式レイヤが item_max で使っている換算と同じであるべき");
    }

    @Test
    @DisplayName("量を持たない種は 0")
    public void test量を持たない種() {
        assertEquals(0L, capacityOf(IPortType.Type.BLOCK));
        assertEquals(0L, capacityOf(IPortType.Type.NONE));
    }

    @Test
    @DisplayName("isStorable は量を持つ種だけ true")
    public void test述語() {
        for (IPortType.Type kind : IPortType.Type.values()) {
            boolean storable = kind != IPortType.Type.BLOCK && kind != IPortType.Type.NONE;
            assertEquals(storable, kind.isStorable(), kind + " の isStorable");
        }

        assertTrue(IPortType.Type.FLUID.isStorable());
        assertFalse(IPortType.Type.NONE.isStorable());
    }

    @Test
    @DisplayName("isStorable は手書きの SUPPORTED_TYPES と一致する")
    public void test述語が既存の配列と一致する() {
        Set<IPortType.Type> declared = Arrays.stream(IPortType.SUPPORTED_TYPES)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(IPortType.Type.class)));

        Set<IPortType.Type> byPredicate = Arrays.stream(IPortType.Type.values())
            .filter(IPortType.Type::isStorable)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(IPortType.Type.class)));

        assertEquals(declared, byPredicate, "一致していれば配列を述語から導出する形に置き換えられる");
    }
}
