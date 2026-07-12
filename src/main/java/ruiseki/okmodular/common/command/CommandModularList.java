package ruiseki.okmodular.common.command;

import java.util.Set;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;

import ruiseki.okcore.api.structure.core.IStructureEntry;
import ruiseki.okcore.command.CommandMod;
import ruiseki.okcore.init.ModBase;
import ruiseki.okcore.structure.CustomStructureRegistry;
import ruiseki.okcore.structure.StructureManager;

public class CommandModularList extends CommandMod {

    public static final String NAME = "list";

    public CommandModularList(ModBase mod) {
        super(mod, NAME);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public int run(CommandContext<ICommandSender> context) {
        ICommandSender sender = context.getSource();
        Set<String> names = StructureManager.getInstance()
            .getCustomStructureNames();

        if (names.isEmpty()) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.YELLOW + "[Modular] No custom structures registered"));
            return Command.SINGLE_SUCCESS;
        }

        sender.addChatMessage(
            new ChatComponentText(EnumChatFormatting.GREEN + "[Modular] Custom Structures (" + names.size() + "):"));

        for (String name : names) {
            IStructureEntry entry = StructureManager.getInstance()
                .getCustomStructure(name);
            String displayName = (entry != null && entry.getDisplayName() != null) ? entry.getDisplayName() : name;
            String recipeGroupDisplay = "default";
            if (entry != null && entry.getRecipeGroup() != null
                && !entry.getRecipeGroup()
                    .isEmpty()) {
                recipeGroupDisplay = String.join(", ", entry.getRecipeGroup());
            }
            boolean hasStructureDef = CustomStructureRegistry.hasDefinition(name);

            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.WHITE + "  "
                        + name
                        + EnumChatFormatting.GRAY
                        + " ("
                        + displayName
                        + ")"
                        + EnumChatFormatting.AQUA
                        + " -> "
                        + recipeGroupDisplay
                        + (hasStructureDef ? EnumChatFormatting.GREEN + " [OK]" : EnumChatFormatting.RED + " [ERR]")));
        }
        return Command.SINGLE_SUCCESS;
    }
}
