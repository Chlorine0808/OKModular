package ruiseki.okmodular.common.block;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.client.model.color.BlockColor;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import ruiseki.okcore.enums.EnumDye;
import ruiseki.okcore.helper.LangHelpers;
import ruiseki.okmodular.Reference;
import ruiseki.okmodular.api.enums.EnumIO;
import ruiseki.okmodular.api.modular.IModularBlock;
import ruiseki.okmodular.api.modular.IModularBlockTint;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IVisitablePort;
import ruiseki.okmodular.api.modular.ModularTier;
import ruiseki.okmodular.api.modular.PortColor;
import ruiseki.okmodular.client.util.IconRegistry;
import ruiseki.okmodular.common.item.AbstractPortItemBlock;
import ruiseki.okmodular.common.item.ItemWrench;
import ruiseki.okmodular.common.tile.StructureTintCache;
import ruiseki.okmodular.common.tile.energy.AbstractEnergyIOPortTE;
import ruiseki.okmodular.common.tile.fluid.AbstractFluidPortTE;
import ruiseki.okmodular.common.tile.gas.AbstractGasPortTE;
import ruiseki.okmodular.common.tile.item.AbstractItemIOPortTE;
import ruiseki.okmodular.config.MachineryConfig;
import ruiseki.okmodular.core.block.AbstractTieredBlock;
import ruiseki.okmodular.core.tileentity.AbstractTE;
import ruiseki.okmodular.core.tileentity.ISidedIO;
import ruiseki.okmodular.integration.waila.WailaUtils;

public abstract class AbstractPortBlock<T extends AbstractTE> extends AbstractTieredBlock<T>
    implements IModularBlock, IModularBlockTint {

    // Render ID for ISBRH, set during client init
    public static int portRendererId = -1;

    public IIcon[] baseIcons; // Tier-based base textures
    public IIcon[] casingIcons; // Tier-based casing textures for disabled sides

    protected final int tierCount;

    @SafeVarargs
    protected AbstractPortBlock(String name, Class<? extends TileEntity>... teClasses) {
        super(name, teClasses);
        // Use actual tier count from ModularTier (16 tiers: 0-15)
        this.tierCount = ModularTier.getTierCount();
        this.baseIcons = new IIcon[tierCount];
        this.casingIcons = new IIcon[tierCount];
        this.useNeighborBrightness = true;
        isFullSize = isOpaque = false;
    }

    /**
     * The tint a port at this position renders with.
     *
     * A colour a player painted on wins; failing that the machine's structure tint,
     * and failing that the configured default. Both the block colour handler and the
     * port renderer go through here so a painted port cannot end up visible in one and
     * not the other.
     */
    public static int tintAt(IBlockAccess world, int x, int y, int z) {
        Integer structureColor = StructureTintCache.get(world, x, y, z);
        int fallback = structureColor != null ? structureColor : MachineryConfig.getDefaultTintColorInt();

        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof IModularPort port) {
            return port.getPortColor()
                .tintOr(fallback);
        }
        return fallback;
    }

    @Override
    protected void registerBlockColor() {
        BlockColor.registerBlockColors(new IModularBlockTint() {

            @Override
            public int colorMultiplier(IBlockAccess world, int x, int y, int z, int tintIndex) {
                if (tintIndex == 0) {
                    return tintAt(world, x, y, z);
                }
                return 0xFFFFFFFF; // White for non-tinted layers
            }

            @Override
            public int colorMultiplier(ItemStack stack, int tintIndex) {
                if (tintIndex == 0) {
                    // Items always use config color (no structure context)
                    return MachineryConfig.getDefaultTintColorInt();
                }
                return 0xFFFFFFFF; // White for non-tinted layers
            }
        }, this);
    }

    @Override
    public int getRenderType() {
        return portRendererId;
    }

    @Override
    public boolean isSideSolid(IBlockAccess world, int x, int y, int z, ForgeDirection side) {
        return false;
    }

    /**
     * Paints this port, which is what divides a machine's ports into groups that run
     * recipes independently.
     *
     * <p>
     * This is the Forge hook other mods' painting tools reach for. AE2's Color
     * Applicator tries its own colour interface first and then falls back to here
     * with {@code AEColor.ordinal()}, which is the same numbering {@link PortColor}
     * declares its constants in, so the argument is an index straight into it.
     *
     * <p>
     * Answers false when the colour is already what was asked for. Tools spend paint
     * or power on a true, and repainting a port the same colour should not cost the
     * player anything.
     *
     * @param colour vanilla wool metadata: 0 white through 15 black, 16 to strip
     */
    @Override
    public boolean recolourBlock(World world, int x, int y, int z, ForgeDirection side, int colour) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof AbstractTE port)) return false;

        PortColor wanted = PortColor.fromColorIndex(colour);
        if (port.getPortColor() == wanted) return false;

        // The client is told a colour change happened so the tool behaves, but only
        // the server writes it - the colour reaches the client in the tile entity's
        // own update packet.
        if (world.isRemote) return true;

        port.setPortColor(wanted);
        port.markDirty();
        world.markBlockForUpdate(x, y, z);
        return true;
    }

    /**
     * A dye in hand paints the port; a water bucket washes the colour off.
     *
     * This is the route that needs no other mod installed. Anything else falls through
     * to the normal click, so opening a port's GUI is unaffected.
     *
     * <p>
     * A painting item consumes the click even when the colour did not change - a
     * player holding a dye is reaching to paint, not to open a screen. The dye itself
     * is only spent on an actual change, and the water bucket is never spent: washing
     * paint off is a configuration step, not a use of the water.
     */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        ItemStack held = player.getHeldItem();
        PortColor wanted = colorAppliedBy(held);
        if (wanted == null) {
            return super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ);
        }

        boolean changed = recolourBlock(world, x, y, z, ForgeDirection.getOrientation(side), wanted.toColorIndex());

        if (changed && !world.isRemote) {
            if (wanted.isColored() && !player.capabilities.isCreativeMode) {
                consumeOne(player, held);
            }
            player.addChatMessage(
                wanted.isColored()
                    ? new ChatComponentTranslation(
                        "chat.okmodular.port_color_set",
                        LangHelpers.localize(wanted.getUnlocalizedName()))
                    : new ChatComponentTranslation("chat.okmodular.port_color_cleared"));
        }
        return true;
    }

    /**
     * The colour this item would apply, or null if it is not something that paints.
     *
     * A water bucket answers {@link PortColor#NONE}, which is how a colour is taken
     * back off.
     */
    private static PortColor colorAppliedBy(ItemStack held) {
        if (held == null) return null;
        if (held.getItem() == Items.water_bucket) return PortColor.NONE;

        EnumDye dye = EnumDye.getColorFromDye(held);
        return dye == null ? null : PortColor.fromDye(dye);
    }

    private static void consumeOne(EntityPlayer player, ItemStack held) {
        held.stackSize--;
        if (held.stackSize <= 0) {
            player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
        }
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        // Return tier-based base texture
        if (baseIcons != null && meta >= 0 && meta < baseIcons.length && baseIcons[meta] != null) {
            return baseIcons[meta];
        }
        // Fallback to tier 0
        if (baseIcons != null && baseIcons.length > 0 && baseIcons[0] != null) {
            return baseIcons[0];
        }
        return null;
    }

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        int tier = 0;

        // Get tier from TileEntity
        if (te instanceof IModularPort port) {
            tier = port.getTier();
        }

        // Check if side should show casing (IO disabled)
        if (te instanceof ISidedIO io) {
            ForgeDirection dir = ForgeDirection.VALID_DIRECTIONS[side];
            if (io.getSideIO(dir) == EnumIO.NONE) {
                // Use tier-based casing texture
                if (tier >= 0 && tier < casingIcons.length && casingIcons[tier] != null) {
                    return casingIcons[tier];
                }
                // Fallback to tier 0 casing
                if (casingIcons.length > 0 && casingIcons[0] != null) {
                    return casingIcons[0];
                }
            }
        }

        // Return tier-based base texture
        if (baseIcons != null && tier >= 0 && tier < baseIcons.length && baseIcons[tier] != null) {
            return baseIcons[tier];
        }
        // Fallback to tier 0
        if (baseIcons != null && baseIcons.length > 0 && baseIcons[0] != null) {
            return baseIcons[0];
        }
        return null;
    }

    @Override
    public void registerBlockIcons(IIconRegister reg) {
        // Use the same tier textures as Casing blocks
        for (int i = 0; i < tierCount; i++) {
            // Format: tier_0_base, tier_1_base, etc. (same as BlockMachineCasing)
            baseIcons[i] = reg.registerIcon(Reference.PREFIX_MOD + "modular/tier_" + i + "_base");
            // Casing icons also tier-based
            casingIcons[i] = reg.registerIcon(Reference.PREFIX_MOD + "modular/tier_" + i + "_base");
        }
        registerPortOverlays(reg);
    }

    public void registerPortOverlays(IIconRegister reg) {
        String prefix = getOverlayPrefix();
        for (int i = 0; i < tierCount; i++) {
            int tier = i + 1;
            IconRegistry.addIcon(
                prefix + tier,
                reg.registerIcon(Reference.PREFIX_MOD + "modularmachineryOverlay/" + prefix + tier));
        }
        IconRegistry
            .addIcon("overlay_port_disabled", reg.registerIcon(Reference.PREFIX_MOD + "modular_machine_casing"));
    }

    public abstract String getOverlayPrefix();

    protected abstract Class<? extends AbstractPortItemBlock> getItemBlockClass();

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase player, ItemStack stack) {
        ForgeDirection facing = ForgeDirection.NORTH;

        if (Math.abs(player.rotationPitch) > 50) {
            facing = player.rotationPitch > 0 ? ForgeDirection.DOWN : ForgeDirection.UP;
        } else {
            int rotation = MathHelper.floor_double((double) (player.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
            switch (rotation) {
                case 0 -> facing = ForgeDirection.SOUTH;
                case 1 -> facing = ForgeDirection.WEST;
                case 2 -> facing = ForgeDirection.NORTH;
                case 3 -> facing = ForgeDirection.EAST;
            }
        }

        // Set block metadata to match tier from ItemStack
        int tier = stack.getItemDamage();
        world.setBlockMetadataWithNotify(x, y, z, tier, 2);

        TileEntity te = world.getTileEntity(x, y, z);

        // Bring back the colour the port was painted, and only the colour. Breaking a
        // port attaches its whole tile entity NBT to the dropped item, but nothing ever
        // read that back and restoring all of it would undo the side-IO reset above.
        if (te instanceof AbstractTE port && stack.hasTagCompound()) {
            port.setPortColor(AbstractTE.readPortColor(stack.getTagCompound()));
        }

        if (te instanceof IVisitablePort port) {
            // Set tier on TileEntity
            port.setTier(tier);

            // Determine IO limit based on type
            EnumIO ioLimit = EnumIO.NONE;
            if (te instanceof AbstractFluidPortTE portTE) ioLimit = portTE.getIOLimit();
            else if (te instanceof AbstractEnergyIOPortTE portTE) ioLimit = portTE.getIOLimit();
            else if (te instanceof AbstractItemIOPortTE portTE) ioLimit = portTE.getIOLimit();
            else if (te instanceof AbstractGasPortTE portTE) ioLimit = portTE.getIOLimit();

            if (ioLimit != EnumIO.NONE) {
                // Reset all to NONE first
                for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                    port.setSideIO(dir, EnumIO.NONE);
                }
                // Set facing side
                port.setSideIO(facing.getOpposite(), ioLimit);
            }
        }
    }

    @Override
    public void getSubBlocks(Item itemIn, CreativeTabs tab, List<ItemStack> list) {
        for (int i = 0; i < tierCount; i++) {
            list.add(new ItemStack(itemIn, 1, i));
        }
    }

    @Override
    public void getWailaInfo(List<String> tooltip, ItemStack itemStack, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        TileEntity te = accessor.getTileEntity();

        // Which group this port belongs to is not obvious from the tint alone once a
        // machine has its own colour scheme, and it decides what the port can be used
        // for. Silent when unpainted, which is the ordinary case.
        if (te instanceof IModularPort port) {
            PortColor color = port.getPortColor();
            if (color.isColored()) {
                tooltip.add(
                    LangHelpers
                        .localize("tooltip.okmodular.port_color", LangHelpers.localize(color.getUnlocalizedName())));
            }
        }

        if (te instanceof ISidedIO io) {
            Vec3 hit = WailaUtils.getLocalHit(accessor);
            if (hit == null) return;
            ForgeDirection side = ItemWrench
                .getClickedSide(accessor.getSide(), (float) hit.xCoord, (float) hit.yCoord, (float) hit.zCoord);
            tooltip.add(WailaUtils.getSideIOTooltip(io, side));
        }
    }

    public void addTooltip(List<String> list, int tier) {
        addCapacityTooltip(list, tier);
        addTransferTooltip(list, tier);
    }

    protected void addCapacityTooltip(List<String> list, int tier) {}

    protected void addTransferTooltip(List<String> list, int tier) {}

    /**
     * Get base icon for the specified tier.
     * Provides safe access to baseIcons array with fallback.
     */
    public IIcon getBaseIcon(int tier) {
        if (baseIcons != null && tier >= 0 && tier < baseIcons.length && baseIcons[tier] != null) {
            return baseIcons[tier];
        }
        // Fallback to tier 0 if available
        if (baseIcons != null && baseIcons.length > 0 && baseIcons[0] != null) {
            return baseIcons[0];
        }
        return null;
    }
}
