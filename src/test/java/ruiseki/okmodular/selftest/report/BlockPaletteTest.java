package ruiseki.okmodular.selftest.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BlockPaletteTest {

    private static final String[] COMMON = { "minecraft:stone", "minecraft:cobblestone", "minecraft:dirt",
        "minecraft:grass", "minecraft:iron_ore", "minecraft:gold_block", "minecraft:glass", "minecraft:leaves",
        "minecraft:torch", "minecraft:wool:0", "minecraft:wool:14", "okmodular:casing_plain",
        "okmodular:casing_reinforced", "okmodular:casing_vented", "okmodular:casing_circuitry",
        "okmodular:modular_item_input", "okmodular:modular_item_output", "okmodular:modular_fluid_input",
        "okmodular:modular_fluid_output", "okmodular:modular_energy_input", "okmodular:modular_machine_controller",
        "minecraft:sand", "minecraft:gravel", "minecraft:lapis_block", "minecraft:obsidian", "minecraft:iron_block",
        "minecraft:quartz_block", "minecraft:emerald_block", "minecraft:diamond_block", "minecraft:gold_ore",
        "minecraft:coal_ore", "minecraft:glowstone", "minecraft:netherrack", "minecraft:planks" };

    @Test
    @DisplayName("同じブロックは同じ色")
    void sameBlockSameColour() {
        assertEquals(BlockPalette.colourOf("somemod:thing:3"), BlockPalette.colourOf("somemod:thing:3"));
    }

    @Test
    @DisplayName("よく使うブロックは互いに違う色")
    void distinctCommonBlocksHaveDistinctColours() {
        Set<String> seen = new HashSet<>();
        for (String b : COMMON) assertTrue(seen.add(BlockPalette.colourOf(b)), b);
    }

    @Test
    @DisplayName("シナリオで使うバニラのブロックには固定の色がある")
    void scenarioBlocksHaveFixedColours() {
        for (String b : COMMON) {
            if (!b.startsWith("minecraft:wool")) assertFalse(
                BlockPalette.colourOf(b)
                    .startsWith("hsl("),
                b);
        }
    }

    @Test
    @DisplayName("空気は透明")
    void airIsTransparent() {
        assertEquals("transparent", BlockPalette.colourOf("minecraft:air"));
    }

    @Test
    @DisplayName("未知のブロックは ID から決まる HSL 色")
    void unknownBlocksGetHslFromHash() {
        String c = BlockPalette.colourOf("somemod:thing");
        assertTrue(c.startsWith("hsl("), c);
        assertNotEquals(c, BlockPalette.colourOf("somemod:other"));
    }

    @Test
    @DisplayName("meta 違いの羊毛は別の色")
    void woolMetasDiffer() {
        assertNotEquals(BlockPalette.colourOf("minecraft:wool:0"), BlockPalette.colourOf("minecraft:wool:1"));
    }

    @Test
    @DisplayName("固定色のブロックでも meta が違えば別の色")
    void fixedBlockMetasDiffer() {
        assertEquals(BlockPalette.colourOf("minecraft:stone"), BlockPalette.colourOf("minecraft:stone:0"));
        assertNotEquals(BlockPalette.colourOf("minecraft:stone"), BlockPalette.colourOf("minecraft:stone:1"));
        assertNotEquals(
            BlockPalette.colourOf("okmodular:casing_plain:1"),
            BlockPalette.colourOf("okmodular:casing_plain:2"));
    }
}
