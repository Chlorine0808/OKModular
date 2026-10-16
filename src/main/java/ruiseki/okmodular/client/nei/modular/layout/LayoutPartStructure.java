package ruiseki.okmodular.client.nei.modular.layout;

import java.awt.Rectangle;

import net.minecraft.util.StatCollector;

import ruiseki.okmodular.api.recipe.io.StructureInput;
import ruiseki.okmodular.api.recipe.io.StructureOutput;
import ruiseki.okmodular.client.nei.modular.renderer.PositionedText;

/** A structure pattern a recipe needs or builds, drawn as a line of text. */
public final class LayoutPartStructure {

    private LayoutPartStructure() {}

    public static RecipeLayoutPart<?> of(StructureInput in) {
        return line("okmodular.nei.structure.input", in.getPatternName(), in.getSymbol(), in.isOptional());
    }

    public static RecipeLayoutPart<?> of(StructureOutput out) {
        return line("okmodular.nei.structure.output", out.getPatternName(), out.getSymbol(), out.isOptional());
    }

    private static RecipeLayoutPart<?> line(String key, String pattern, char symbol, boolean optional) {
        String text = StatCollector.translateToLocalFormatted(key, pattern, String.valueOf(symbol));
        if (optional) text += " " + StatCollector.translateToLocal("okmodular.nei.optional");
        return new LayoutPartRenderer(new PositionedText(text, 0x222222, new Rectangle(0, 0, 166, 10), false));
    }
}
