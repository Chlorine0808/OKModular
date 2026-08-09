package ruiseki.okmodular.integration.waila;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.common.event.FMLInterModComms;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;
import ruiseki.okmodular.core.block.BlockOK;
import ruiseki.okmodular.core.tileentity.TileEntityOK;

/**
 * Hands this mod's blocks and tiles to WAILA.
 * <p>
 * <b>This half went missing when the machinery became its own mod.</b> The split brought
 * across {@code IWaila*InfoProvider} and {@code WailaUtils}, and around fifteen blocks
 * implement {@code getWailaInfo} against them - but the class that registers with WAILA and
 * calls those methods stayed behind in the parent. Nothing in this mod or in OKCore ever
 * reached them, so every tooltip this mod meant to draw was simply absent. Not wrong: absent,
 * which is far harder to notice than wrong.
 * <p>
 * OKCore ships a provider of its own, and it does not help: it registers for
 * {@code ruiseki.okcore.addon.waila.IWailaBlockInfoProvider}, a different type from the one
 * these blocks implement, and WAILA matches providers by class. Two interfaces with the same
 * simple name in different packages answer separately - {@code PortWailaOverrideTest} was
 * green the whole time, holding {@code super} calls in code nobody called.
 */
public class BlockProvider implements IWailaDataProvider {

    public static final BlockProvider INSTANCE = new BlockProvider();

    public static void init() {
        String callback = BlockProvider.class.getCanonicalName() + ".load";
        FMLInterModComms.sendMessage("Waila", "register", callback);
    }

    /**
     * Registers against {@link BlockOK} / {@link TileEntityOK} rather than against the
     * interfaces, because WAILA only looks at the class handed over here. Every block in this
     * mod descends from those two, and the dispatch below narrows to the interfaces.
     */
    public static void load(IWailaRegistrar registrar) {
        registrar.registerStackProvider(INSTANCE, BlockOK.class);
        registrar.registerHeadProvider(INSTANCE, BlockOK.class);
        registrar.registerBodyProvider(INSTANCE, BlockOK.class);
        registrar.registerTailProvider(INSTANCE, BlockOK.class);
        registrar.registerNBTProvider(INSTANCE, TileEntityOK.class);
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        Block block = accessor.getBlock();
        TileEntity tile = accessor.getTileEntity();

        if (block instanceof IWailaBlockInfoProvider provider) {
            return provider.getWailaStack(accessor, config);
        }
        if (tile instanceof IWailaTileInfoProvider provider) {
            return provider.getWailaStack(accessor, config);
        }
        return null;
    }

    @Override
    public List<String> getWailaHead(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        TileEntity tile = accessor.getTileEntity();

        if (tile instanceof IWailaTileInfoProvider provider) {
            provider.getWailaHead(itemStack, currenttip, accessor, config);
        }
        return currenttip;
    }

    @Override
    public List<String> getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        Block block = accessor.getBlock();
        TileEntity tile = accessor.getTileEntity();

        if (block instanceof IWailaBlockInfoProvider info) {
            info.getWailaInfo(currenttip, itemStack, accessor, config);
        }
        if (tile instanceof IWailaTileInfoProvider info) {
            info.getWailaInfo(currenttip, itemStack, accessor, config);
        }
        return currenttip;
    }

    @Override
    public List<String> getWailaTail(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        Block block = accessor.getBlock();
        TileEntity tile = accessor.getTileEntity();

        if (block instanceof IWailaBlockInfoProvider provider) {
            provider.getWailaTail(itemStack, currenttip, accessor, config);
        }
        if (tile instanceof IWailaTileInfoProvider provider) {
            provider.getWailaTail(itemStack, currenttip, accessor, config);
        }
        return currenttip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x,
        int y, int z) {
        if (tile instanceof IWailaNBTProvider te) {
            te.getData(tag);
        }
        if (tile instanceof IWailaTileInfoProvider te) {
            te.getWailaNBTData(player, tile, tag, world, x, y, z);
        }
        return tag;
    }
}
