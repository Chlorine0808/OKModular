package ruiseki.okmodular.common.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `/okmodular hand` が手持ちアイテムをどう書き出すか。
 *
 * レシピ作者が `nbt` 式の結果を確かめるための道具なので、
 * レシピに書く綴り（`modid:name`）とメタ値をそのまま出す。
 */
@DisplayName("手持ちアイテムの書き出し")
public class HeldItemDescriptionTest {

    @Test
    @DisplayName("レシピに書く綴りとメタ値と個数を出す")
    public void testアイテムの行() {
        assertEquals("minecraft:clay_ball:0 x3", HeldItemDescription.itemLine("minecraft:clay_ball", 0, 3));
    }

    @Test
    @DisplayName("NBT は中身をそのまま出す")
    public void testNBTの行() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("tier", 1);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("customData", data);

        assertEquals("{customData:{tier:1,},}", HeldItemDescription.nbtLine(tag));
    }

    @Test
    @DisplayName("NBT が無ければ null")
    public void testNBTが無い() {
        assertNull(HeldItemDescription.nbtLine(null));
        assertNull(HeldItemDescription.nbtLine(new NBTTagCompound()), "空のタグは無いのと同じに扱う");
    }
}
