package ruiseki.okmodular.common.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import com.gtnewhorizon.gtnhlib.client.model.color.BlockColor;
import com.gtnewhorizon.gtnhlib.client.model.color.IBlockColor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ruiseki.okcore.helper.LangHelpers;
import ruiseki.okcore.helper.MinecraftHelpers;
import ruiseki.okmodular.Reference;
import ruiseki.okmodular.api.modular.IModularBlockTint;
import ruiseki.okmodular.api.modular.ModularTier;
import ruiseki.okmodular.common.tier.TierConfigLoader;
import ruiseki.okmodular.common.tier.TierManager;
import ruiseki.okmodular.common.tile.StructureTintCache;
import ruiseki.okmodular.config.MachineryConfig;
import ruiseki.okmodular.core.block.BlockOK;
import ruiseki.okmodular.core.item.ItemBlockOK;

/**
 * Machine Casing block.
 * Block ID corresponds to Design (Plain, Reinforced, etc.)
 * Metadata corresponds to Tier (0: Basic, 1: Advanced, 2: Elite, 3: Ultimate)
 * TODO: Add crafting recipe
 */
public class BlockMachineCasing extends BlockOK implements IModularBlockTint, IBlockColor {

    private static final int TIERS = 16;

    private String designName = "";
    private final IIcon[] tierIcons = new IIcon[TIERS];

    protected BlockMachineCasing(String designName) {
        super("casing_" + designName);
        this.designName = designName;
        setHardness(5.0F);
        setResistance(10.0F);
        this.hasSubtypes = true;
    }

    public static BlockMachineCasing create(String designName) {
        return new BlockMachineCasing(designName);
    }

    public String getDesignName() {
        return designName;
    }

    @Override
    protected void registerBlockColor() {
        BlockColor.registerBlockColors(this, this);
    }

    @Override
    public int getRenderType() {
        return AbstractPortBlock.portRendererId;
    }

    @Override
    public int colorMultiplier(IBlockAccess world, int x, int y, int z) {
        Integer structureColor = StructureTintCache.get(world, x, y, z);
        if (structureColor != null) {
            return structureColor;
        }
        return MachineryConfig.getDefaultTintColorInt();
    }

    @Override
    public int colorMultiplier(IBlockAccess world, int x, int y, int z, int tintIndex) {
        if (tintIndex == 0) {
            Integer structureColor = StructureTintCache.get(world, x, y, z);
            if (structureColor != null) {
                return structureColor & 0xFFFFFF;
            }
            return MachineryConfig.getDefaultTintColorInt() & 0xFFFFFF;
        }
        return 0xFFFFFF;
    }

    @Override
    public int colorMultiplier(ItemStack stack, int tintIndex) {
        if (tintIndex == 0) {
            return MachineryConfig.getDefaultTintColorInt() & 0xFFFFFF;
        }
        return 0xFFFFFF;
    }

    @Override
    public int getRenderColor(int meta) {
        return MachineryConfig.getDefaultTintColorInt();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        // The icon is picked per tier from metadata rather than per design, so the single
        // blockIcon that super registers is unusable here. getIcon() indexes this array.
        // NOTE: only tier_0_base .. tier_5_base exist; higher tiers log a missing texture.
        for (int i = 0; i < TIERS; i++) {
            tierIcons[i] = reg.registerIcon(Reference.PREFIX_MOD + "modular/tier_" + i + "_base");
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        if (meta < 0 || meta >= tierIcons.length || tierIcons[meta] == null) {
            return null;
        }
        return tierIcons[meta];
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    protected Class<? extends ItemBlock> getItemBlockClass() {
        return ItemBlockMachineCasing.class;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubBlocks(Item item, CreativeTabs tab, List<ItemStack> list) {
        // Only show enabled tiers in creative tab (default: 6)
        int enabledTiers = TierManager.getEnabledTierCount();
        for (int i = 0; i < enabledTiers && i < TIERS; i++) {
            list.add(new ItemStack(item, 1, i));
        }
    }

    public static class ItemBlockMachineCasing extends ItemBlockOK {

        public ItemBlockMachineCasing(Block block) {
            super(block);
        }

        @Override
        public String getItemStackDisplayName(ItemStack stack) {
            BlockMachineCasing block = (BlockMachineCasing) field_150939_a;
            int meta = stack.getItemDamage();
            ModularTier modularTier = ModularTier.fromMeta(meta);

            // Format: %s (Tier) %s (Design)
            // e.g. "Revolutionary" "Machine Casing"
            String locale = MinecraftHelpers.isClientSide() ? Minecraft.getMinecraft().gameSettings.language : "en_US";
            String tier = TierConfigLoader.INSTANCE.getTierName(modularTier.getMeta(), locale);

            String design = LangHelpers.localize("machinery.design." + block.designName);
            return LangHelpers.localize("machinery.casing.format", tier, design);
        }

        @Override
        public String getUnlocalizedName(ItemStack stack) {
            return super.getUnlocalizedName() + ".tier_" + stack.getItemDamage();
        }
    }
}
