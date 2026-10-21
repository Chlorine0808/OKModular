package ruiseki.okmodular.common.command;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;

import ruiseki.okcore.command.CommandMod;
import ruiseki.okcore.helper.LangHelpers;
import ruiseki.okcore.init.ModBase;

/**
 * Prints the held item's id, meta and NBT, so a recipe's {@code nbt} expressions can be checked.
 * Builds: /&lt;modid&gt; hand
 */
public class CommandModularHand extends CommandMod {

    public static final String NAME = "hand";

    public CommandModularHand(ModBase mod) {
        super(mod, NAME);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public int run(CommandContext<ICommandSender> context) {
        ICommandSender sender = context.getSource();
        if (!(sender instanceof EntityPlayer player)) {
            printErrorToChat(sender, LangHelpers.localize("command.okmodular.hand_player_only"));
            return Command.SINGLE_SUCCESS;
        }

        ItemStack held = player.getCurrentEquippedItem();
        if (held == null) {
            printErrorToChat(sender, LangHelpers.localize("command.okmodular.hand_empty"));
            return Command.SINGLE_SUCCESS;
        }

        String name = Item.itemRegistry.getNameForObject(held.getItem());
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GREEN + "[Modular] "
                    + HeldItemDescription.itemLine(name, held.getItemDamage(), held.stackSize)));

        String nbt = HeldItemDescription.nbtLine(held.getTagCompound());
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GRAY
                    + (nbt != null ? "NBT: " + nbt : LangHelpers.localize("command.okmodular.hand_no_nbt"))));
        return Command.SINGLE_SUCCESS;
    }
}
