package ruiseki.okmodular.selftest.report;

import java.util.HashMap;
import java.util.Map;

/** Fixed colours for blocks the scenarios use; everything else gets an HSL colour derived from its id. */
final class BlockPalette {

    private static final String[] WOOL = { "#e9ecec", "#f07613", "#bd44b3", "#3aafd9", "#f8c627", "#70b919", "#ed8dac",
        "#3e4447", "#8e8e86", "#158991", "#792aac", "#35399d", "#724728", "#546d1b", "#a12722", "#141519" };

    private static final Map<String, String> FIXED = new HashMap<>();
    static {
        FIXED.put("minecraft:stone", "#7d7d7d");
        FIXED.put("minecraft:cobblestone", "#5f5f5f");
        FIXED.put("minecraft:dirt", "#866043");
        FIXED.put("minecraft:grass", "#5d9b3a");
        FIXED.put("minecraft:iron_ore", "#d8af93");
        FIXED.put("minecraft:gold_block", "#f9d73e");
        FIXED.put("minecraft:glass", "#bfe3f0");
        FIXED.put("minecraft:leaves", "#2f7a1f");
        FIXED.put("minecraft:torch", "#ffb800");
        FIXED.put("minecraft:sand", "#e3d9a6");
        FIXED.put("minecraft:gravel", "#a39a94");
        FIXED.put("minecraft:lapis_block", "#2350b8");
        FIXED.put("minecraft:obsidian", "#1d1430");
        FIXED.put("minecraft:iron_block", "#dcdcdc");
        FIXED.put("minecraft:quartz_block", "#f4efe6");
        FIXED.put("minecraft:emerald_block", "#2fcf6a");
        FIXED.put("minecraft:diamond_block", "#62e0d8");
        FIXED.put("minecraft:gold_ore", "#b39a2e");
        FIXED.put("minecraft:coal_ore", "#2b2b2b");
        FIXED.put("minecraft:glowstone", "#c7924a");
        FIXED.put("minecraft:netherrack", "#7a2f2f");
        FIXED.put("minecraft:planks", "#a8834f");
        FIXED.put("okmodular:casing_plain", "#9aa0a6");
        FIXED.put("okmodular:casing_reinforced", "#4c5560");
        FIXED.put("okmodular:casing_vented", "#b9a37a");
        FIXED.put("okmodular:casing_circuitry", "#2e8b6e");
        FIXED.put("okmodular:modular_machine_controller", "#d33b3b");
        FIXED.put("okmodular:modular_item_input", "#3b78d3");
        FIXED.put("okmodular:modular_item_output", "#e08a2c");
        FIXED.put("okmodular:modular_fluid_input", "#2cc4d8");
        FIXED.put("okmodular:modular_fluid_output", "#8e44ad");
        FIXED.put("okmodular:modular_energy_input", "#e6d12e");
    }

    private BlockPalette() {}

    /** CSS colour for a block id ("modid:name[:meta]"); air is "transparent". */
    static String colourOf(String block) {
        if ("minecraft:air".equals(block)) return "transparent";
        String exact = FIXED.get(block);
        if (exact != null) return exact;
        int c1 = block.indexOf(':');
        int c2 = c1 < 0 ? -1 : block.indexOf(':', c1 + 1);
        String base = c2 < 0 ? block : block.substring(0, c2);
        if ("minecraft:wool".equals(base)) {
            int meta = c2 < 0 ? 0 : parseMeta(block.substring(c2 + 1));
            return WOOL[Math.floorMod(meta, 16)];
        }
        String fixed = FIXED.get(base);
        // a fixed colour covers meta 0 only; other metas hash the full id so they stay apart
        if (fixed != null && (c2 < 0 || parseMeta(block.substring(c2 + 1)) == 0)) return fixed;
        int h = block.hashCode();
        int hue = Math.floorMod(h, 360);
        int sat = 45 + Math.floorMod(h >>> 9, 30);
        int light = 40 + Math.floorMod(h >>> 17, 25);
        return "hsl(" + hue + "," + sat + "%," + light + "%)";
    }

    private static int parseMeta(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
