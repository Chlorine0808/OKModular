package ruiseki.okmodular.common.item;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

import ruiseki.okcore.helper.LangHelpers;
import ruiseki.okmodular.api.modular.PortColor;
import ruiseki.okmodular.client.util.IconRegistry;
import ruiseki.okmodular.common.block.AbstractPortBlock;
import ruiseki.okmodular.core.item.ItemBlockOK;
import ruiseki.okmodular.core.tileentity.AbstractTE;

public abstract class AbstractPortItemBlock extends ItemBlockOK {

    public AbstractPortItemBlock(Block block) {
        super(block);
        hasSubtypes = true;
    }

    public IIcon getOverlayIcon(int tier) {
        if (field_150939_a instanceof AbstractPortBlock) {
            return IconRegistry.getIcon(((AbstractPortBlock<?>) field_150939_a).getOverlayPrefix() + tier);
        }
        return null;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName();
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        int tier = stack.getItemDamage() + 1;
        String unlocalizedName = getUnlocalizedName(stack) + ".name";
        String localizedFormat = StatCollector.translateToLocal(unlocalizedName);

        // Always try to format with tier number
        try {
            return String.format(localizedFormat, tier);
        } catch (Exception e) {
            // Fallback: return the localized string as-is if formatting fails
            return localizedFormat;
        }
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> list, boolean flag) {
        if (field_150939_a instanceof AbstractPortBlock) {
            ((AbstractPortBlock<?>) field_150939_a).addTooltip(list, stack.getItemDamage() + 1);
        }
        addPortColorTooltip(list, stack);
    }

    /**
     * Shows the colour a broken port is carrying.
     *
     * Without this the colour would be invisible until the port was placed back down,
     * and a stack of ports with different colours would look identical. Says nothing
     * for an unpainted port, which is the ordinary case.
     */
    public static void addPortColorTooltip(List<String> list, ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return;

        PortColor color = AbstractTE.readPortColor(stack.getTagCompound());
        if (!color.isColored()) return;

        list.add(
            LangHelpers.localize("tooltip.okmodular.port_color", LangHelpers.localize(color.getUnlocalizedName())));
    }
}
