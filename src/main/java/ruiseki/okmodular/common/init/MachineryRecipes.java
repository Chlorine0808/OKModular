package ruiseki.okmodular.common.init;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;

import cpw.mods.fml.common.registry.GameRegistry;
import ruiseki.okmodular.util.Logger;

/**
 * Crafting-table recipes for the blocks the Assembler is built from.
 */
public final class MachineryRecipes {

    /** A shaped recipe as plain strings, so it can be checked without a running game. */
    public static final class Shape {

        public final String output;
        public final int meta;
        public final int count;
        public final String[] rows;
        /** Symbol to material: an ore dictionary name, or {@code mod:name[:meta]}. */
        public final Map<Character, String> key;

        Shape(String output, int meta, int count, String[] rows, Object... key) {
            this.output = output;
            this.meta = meta;
            this.count = count;
            this.rows = rows;
            Map<Character, String> map = new LinkedHashMap<>();
            for (int i = 0; i < key.length; i += 2) map.put((Character) key[i], (String) key[i + 1]);
            this.key = Collections.unmodifiableMap(map);
        }
    }

    private static final String CASING = "okmodular:casing_plain:0";

    // spotless:off
    public static final List<Shape> SHAPES = Collections.unmodifiableList(Arrays.asList(
        new Shape("okmodular:modular_machine_controller", 0, 1, new String[] { "IRI", "RTR", "IRI" },
            'I', "ingotIron", 'R', "minecraft:redstone", 'T', "minecraft:crafting_table"),
        new Shape("okmodular:casing_plain", 0, 4, new String[] { "ISI", "S S", "ISI" },
            'I', "ingotIron", 'S', "minecraft:stone"),
        new Shape("okmodular:modular_item_input", 0, 1, new String[] { " H ", " C ", " T " },
            'H', "minecraft:hopper", 'C', CASING, 'T', "minecraft:chest"),
        new Shape("okmodular:modular_item_output", 0, 1, new String[] { " T ", " C ", " H " },
            'H', "minecraft:hopper", 'C', CASING, 'T', "minecraft:chest"),
        new Shape("okmodular:modular_energy_input", 0, 1, new String[] { " R ", "ICI", " R " },
            'R', "minecraft:redstone_block", 'I', "ingotIron", 'C', CASING),
        new Shape("okmodular:wrench", 0, 1, new String[] { "I I", " I ", " I " },
            'I', "ingotIron")));
    // spotless:on

    private MachineryRecipes() {}

    public static void init() {
        for (Shape shape : SHAPES) {
            ItemStack output = stack(shape.output, shape.meta, shape.count);
            if (output == null) {
                Logger.warn("Crafting recipe skipped, no such item: " + shape.output);
                continue;
            }
            List<Object> args = new ArrayList<>(Arrays.asList((Object[]) shape.rows));
            boolean resolved = true;
            for (Map.Entry<Character, String> entry : shape.key.entrySet()) {
                Object ingredient = ingredient(entry.getValue());
                if (ingredient == null) {
                    Logger.warn("Crafting recipe for " + shape.output + " skipped, no such item: " + entry.getValue());
                    resolved = false;
                    break;
                }
                args.add(entry.getKey());
                args.add(ingredient);
            }
            if (resolved) GameRegistry.addRecipe(new ShapedOreRecipe(output, args.toArray()));
        }
    }

    /** An ore name stays a string for ShapedOreRecipe; an item ID becomes a stack. */
    private static Object ingredient(String material) {
        if (!material.contains(":")) return material;
        String[] parts = material.split(":");
        return stack(parts[0] + ":" + parts[1], parts.length > 2 ? Integer.parseInt(parts[2]) : 0, 1);
    }

    private static ItemStack stack(String id, int meta, int count) {
        String[] parts = id.split(":");
        Item item = GameRegistry.findItem(parts[0], parts[1]);
        return item == null ? null : new ItemStack(item, count, meta);
    }
}
