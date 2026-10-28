package ruiseki.okmodular.selftest.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        "okmodular:modular_fluid_output", "okmodular:modular_energy_input", "okmodular:modular_machine_controller" };

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
}
