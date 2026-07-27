package ruiseki.okmodular.common.command;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import ruiseki.okcore.command.CommandMod;
import ruiseki.okcore.helper.LangHelpers;
import ruiseki.okcore.init.ModBase;
import ruiseki.okcore.structure.StructureConstants;
import ruiseki.okcore.structure.StructureScanner;
import ruiseki.okcore.structure.WandSelectionManager;
import ruiseki.okmodular.MachineryModule;

/**
 * Saves the area selected with the structure wand as a structure JSON.
 * Builds: /&lt;modid&gt; modular wand save &lt;name&gt;
 */
public class CommandModularWandSave extends CommandMod {

    public static final String NAME = "save";

    public CommandModularWandSave(ModBase mod) {
        super(mod, NAME);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public LiteralArgumentBuilder<ICommandSender> make() {
        return super.make().then(
            RequiredArgumentBuilder.<ICommandSender, String>argument("name", StringArgumentType.word())
                .executes(this::save));
    }

    @Override
    public int run(CommandContext<ICommandSender> context) {
        printErrorToChat(context.getSource(), LangHelpers.localize("command.okmodular.wand_usage"));
        return Command.SINGLE_SUCCESS;
    }

    private int save(CommandContext<ICommandSender> context) {
        ICommandSender sender = context.getSource();
        if (!(sender instanceof EntityPlayer player)) {
            printErrorToChat(sender, LangHelpers.localize("command.okmodular.wand_player_only"));
            return Command.SINGLE_SUCCESS;
        }

        WandSelectionManager.PendingScan pending = WandSelectionManager.getInstance()
            .getPendingScan(player.getUniqueID());

        if (pending == null) {
            printErrorToChat(sender, LangHelpers.localize("command.okmodular.wand_no_pending"));
            return Command.SINGLE_SUCCESS;
        }

        if (pending.dimensionId != player.worldObj.provider.dimensionId) {
            printErrorToChat(sender, LangHelpers.localize("command.okmodular.wand_different_dimension"));
            return Command.SINGLE_SUCCESS;
        }

        int blockCount = pending.getBlockCount();
        if (blockCount > StructureConstants.MAX_WAND_SCAN_BLOCKS) {
            printErrorToChat(
                sender,
                LangHelpers.localize(
                    "chat.wand.area_too_large",
                    String.format("%,d", StructureConstants.MAX_WAND_SCAN_BLOCKS),
                    String.format("%,d", blockCount)));
            return Command.SINGLE_SUCCESS;
        }

        String name = StringArgumentType.getString(context, "name");
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.YELLOW + LangHelpers.localize("command.okmodular.wand_scanning", blockCount)));

        StructureScanner.ScanResult result = StructureScanner.scan(
            player.worldObj,
            name,
            pending.pos1.posX,
            pending.pos1.posY,
            pending.pos1.posZ,
            pending.pos2.posX,
            pending.pos2.posY,
            pending.pos2.posZ,
            MachineryModule.getConfigDir());

        if (!result.success) {
            printErrorToChat(sender, LangHelpers.localize("command.okmodular.scan_failed", result.message));
            return Command.SINGLE_SUCCESS;
        }

        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GREEN + LangHelpers.localize("command.okmodular.scan_success", result.message)));
        sender.addChatMessage(
            new ChatComponentText(EnumChatFormatting.GRAY + LangHelpers.localize("command.okmodular.scan_file", name)));

        WandSelectionManager.getInstance()
            .clearPendingScan(player.getUniqueID());
        return Command.SINGLE_SUCCESS;
    }
}
