package ruiseki.okmodular.api.recipe.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.testsupport.TunableMachine;

/**
 * 開始時の性能の写し。
 *
 * ============================================
 * なぜ写すのか
 * ============================================
 *
 * 完成時に評価する式（デコレータの chance、出力の nbt など）は完成時の機械を読んでいた。
 * 長いレシピを低 Tier で始め、完成直前に構造体を高 Tier へ組み替えると、
 * 低 Tier の入力で高 Tier の払い出しが得られる。
 *
 * 写すのは**性能**（tier・速度係数・エネルギー係数・部品 Tier）だけ。
 * 残量は後から足しても入れた分が消えるだけなので、読んだ時点の値でよい。
 */
@DisplayName("開始時の性能の写し")
public class MachinePerformanceTest {

    @Test
    @DisplayName("機械の性能をそのまま写す")
    public void test写す() {
        TunableMachine machine = new TunableMachine();
        machine.tier = 3;
        machine.speed = 1.5;
        machine.energyMultiplier = 2.5;
        machine.components.put("casing", 4);

        MachinePerformance taken = MachinePerformance.capture(machine.context());

        assertEquals(3, taken.getTier());
        assertEquals(1.5, taken.getSpeedMultiplier());
        assertEquals(2.5, taken.getEnergyMultiplier());
        assertEquals(4, taken.getComponentTier("casing"));
    }

    @Test
    @DisplayName("写した後に機械が変わっても写しは変わらない")
    public void test写しは動かない() {
        TunableMachine machine = new TunableMachine();
        machine.tier = 1;
        machine.components.put("casing", 1);

        MachinePerformance taken = MachinePerformance.capture(machine.context());
        machine.tier = 5;
        machine.components.put("casing", 5);

        assertEquals(1, taken.getTier());
        assertEquals(1, taken.getComponentTier("casing"), "部品 Tier の表を参照のまま持っている");
    }

    @Test
    @DisplayName("無い部品の Tier は 0")
    public void test無い部品() {
        MachinePerformance taken = MachinePerformance.capture(new TunableMachine().context());

        assertEquals(0, taken.getComponentTier("casing"));
    }

    @Test
    @DisplayName("機械の無い文脈からは何も写さない")
    public void test機械が無い() {
        assertNull(MachinePerformance.capture(new ConditionContext(null, 0, 0, 0)));
        assertNull(MachinePerformance.capture(null));
    }

    @Test
    @DisplayName("NBT を往復しても同じ")
    public void testNBT往復() {
        TunableMachine machine = new TunableMachine();
        machine.tier = 3;
        machine.speed = 1.5;
        machine.energyMultiplier = 2.5;
        machine.components.put("casing", 4);
        machine.components.put("coil", 2);

        NBTTagCompound tag = new NBTTagCompound();
        MachinePerformance.capture(machine.context())
            .writeToNBT(tag);
        MachinePerformance back = MachinePerformance.readFromNBT(tag);

        assertEquals(3, back.getTier());
        assertEquals(1.5, back.getSpeedMultiplier());
        assertEquals(2.5, back.getEnergyMultiplier());
        assertEquals(4, back.getComponentTier("casing"));
        assertEquals(2, back.getComponentTier("coil"));
    }
}
