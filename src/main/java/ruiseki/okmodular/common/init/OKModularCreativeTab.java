package ruiseki.okmodular.common.init;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ruiseki.okmodular.Reference;

/** The creative tab every block and item of this mod is listed in. */
public class OKModularCreativeTab extends CreativeTabs {

    public static final OKModularCreativeTab TAB = new OKModularCreativeTab();

    private OKModularCreativeTab() {
        super(Reference.MOD_ID);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Item getTabIconItem() {
        return Item.getItemFromBlock(MachineryBlocks.MACHINE_CONTROLLER.getBlock());
    }
}
