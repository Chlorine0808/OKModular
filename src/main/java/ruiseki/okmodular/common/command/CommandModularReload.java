package ruiseki.okmodular.common.command;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;

import ruiseki.okcore.command.CommandMod;
import ruiseki.okcore.init.ModBase;
import ruiseki.okcore.json.JsonErrorCollector;
import ruiseki.okmodular.MachineryModule;

public class CommandModularReload extends CommandMod {

    public static final String NAME = "reload";

    public CommandModularReload(ModBase mod) {
        super(mod, NAME);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public int run(CommandContext<ICommandSender> context) {
        ICommandSender sender = context.getSource();
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "[OKModular] Reloading modular..."));

        MachineryModule machineryModule = getMod().getModuleManager()
            .getModuleByType(MachineryModule.class);
        if (machineryModule == null || !machineryModule.isEnable()) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[Modular] Module is disabled."));
            return Command.SINGLE_SUCCESS;
        }

        try {
            machineryModule.reload(sender);
        } catch (Exception e) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "[Modular] Reload failed: " + e.getMessage()));
            return Command.SINGLE_SUCCESS;
        }

        if (!JsonErrorCollector.getInstance()
            .hasErrors()) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.GREEN + "[OKModular] Modular reload completed!"));
        }
        return Command.SINGLE_SUCCESS;
    }
}
