package ruiseki.okmodular.client.nei.modular.layout;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import ruiseki.okcore.addon.nei.PositionedStackAdv;
import ruiseki.okmodular.api.recipe.io.BlockInput;
import ruiseki.okmodular.api.recipe.io.BlockOutput;
import ruiseki.okmodular.client.nei.modular.renderer.PositionedText;
import ruiseki.okmodular.structure.BlockResolver;

/** A block a recipe reads from or writes to a structure cell, drawn as its item with notes. */
public class LayoutPartBlock extends LayoutPartItem {

    private LayoutPartBlock(ItemStack stack, List<String> notes) {
        super(new PositionedStackAdv(stack, 0, 0, notes));
    }

    public static RecipeLayoutPart<?> of(BlockInput in) {
        List<String> notes = new ArrayList<>();
        notes.add(cellNote(in.getSymbol()));
        notes.add(note(in.isConsume() ? "okmodular.nei.block.consumed" : "okmodular.nei.block.kept"));
        String shown = in.getBlock();
        if (in.getReplace() != null) {
            // The cell holds the replace block; the block field is what it turns into
            notes.add(note("okmodular.nei.block.becomes", in.getBlock()));
            shown = in.getReplace();
        }
        return create(shown, in.getAmount(), notes);
    }

    public static RecipeLayoutPart<?> of(BlockOutput out) {
        List<String> notes = new ArrayList<>();
        notes.add(cellNote(out.getSymbol()));
        if (out.getReplace() != null) notes.add(note("okmodular.nei.block.replaces", out.getReplace()));
        return create(out.getBlock(), out.getAmount(), notes);
    }

    /** Falls back to a line of text when the block has no item to draw, e.g. air or a missing mod. */
    private static RecipeLayoutPart<?> create(String blockId, int amount, List<String> notes) {
        BlockResolver.ResolvedBlock resolved = BlockResolver.resolve(blockId);
        Item item = resolved == null || resolved.block == null ? null : Item.getItemFromBlock(resolved.block);
        if (item == null) {
            String line = blockId + " " + String.join(", ", notes);
            return new LayoutPartRenderer(new PositionedText(line, 0x222222, new Rectangle(0, 0, 166, 10), false));
        }
        ItemStack stack = new ItemStack(item, Math.max(1, amount), resolved.meta);
        return new LayoutPartBlock(stack, notes);
    }

    private static String cellNote(char symbol) {
        return note("okmodular.nei.block.cell", String.valueOf(symbol));
    }

    private static String note(String key, Object... args) {
        return EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(key, args);
    }
}
