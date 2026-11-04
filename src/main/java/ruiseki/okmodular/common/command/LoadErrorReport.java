package ruiseki.okmodular.common.command;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import ruiseki.okmodular.Reference;
import ruiseki.okmodular.common.recipe.RecipeLoader;
import ruiseki.okmodular.structure.StructureErrorCollector;
import ruiseki.okmodular.structure.pattern.StructurePatternLoader;

/** Points at the errors.txt each config folder got from the last load. */
public final class LoadErrorReport {

    private LoadErrorReport() {}

    public static int total() {
        return StructureErrorCollector.getInstance()
            .getErrorCount()
            + StructurePatternLoader.getInstance()
                .getErrors()
                .size()
            + RecipeLoader.getInstance()
                .getErrors()
                .size();
    }

    public static void toChat(ICommandSender sender) {
        List<String> lines = lines(
            StructureErrorCollector.getInstance()
                .getErrorCount(),
            StructurePatternLoader.getInstance()
                .getErrors()
                .size(),
            StructurePatternLoader.resolveDir(new File(""))
                .getName(),
            RecipeLoader.getInstance()
                .getErrors()
                .size());
        if (lines.isEmpty()) return;
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.RED + "[OKModular] "
                    + EnumChatFormatting.YELLOW
                    + total()
                    + " load error(s). Check:"));
        for (String line : lines) sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "  " + line));
    }

    static List<String> lines(int structures, int patterns, String patternFolder, int recipes) {
        List<String> out = new ArrayList<>();
        add(out, "structures", structures);
        add(out, patternFolder, patterns);
        add(out, "recipes", recipes);
        return out;
    }

    private static void add(List<String> out, String folder, int count) {
        if (count > 0) out.add("config/" + Reference.MOD_ID + "/" + folder + "/errors.txt (" + count + ")");
    }
}
