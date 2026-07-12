package ruiseki.okmodular.core.item;

import net.minecraft.block.Block;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlockWithMetadata;
import net.minecraft.item.ItemStack;

import ruiseki.okmodular.core.block.IBlock;
import ruiseki.okmodular.core.block.IBlockRarityProvider;

public class ItemBlockOK extends ItemBlockWithMetadata implements IItem {

    protected IBlockRarityProvider rarityProvider = null;

    private final IBlock blockType;

    public ItemBlockOK(Block block) {
        super(block, block);

        if (block instanceof IBlock) {
            this.blockType = (IBlock) block;
            this.hasSubtypes = this.blockType.isHasSubtypes();
        } else {
            this.blockType = null;
        }

        if (block instanceof IBlockRarityProvider) {
            this.rarityProvider = (IBlockRarityProvider) this.field_150939_a;
        }

    }

    @Override
    public void init() {
        // NO OP
    }

    @Override
    public int getMetadata(final int meta) {
        if (this.hasSubtypes) {
            return meta;
        }
        return 0;
    }

    @Override
    public EnumRarity getRarity(ItemStack itemStack) {
        if (rarityProvider != null) {
            return rarityProvider.getRarity(itemStack);
        }
        return super.getRarity(itemStack);
    }

    @Override
    public Item getItem() {
        return this;
    }
}
