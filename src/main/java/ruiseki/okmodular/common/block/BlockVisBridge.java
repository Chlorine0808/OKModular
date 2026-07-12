package ruiseki.okmodular.common.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ruiseki.okcore.block.legacy.BlockOK;
import ruiseki.okmodular.OKMObjects;
import ruiseki.okmodular.Reference;
import ruiseki.okmodular.common.tile.vis.TileVisBridge;

/**
 * Bridges Vis from Vis Output Port to Thaumcraft Vis network.
 * When placed, it absorbs Vis from the block face that was clicked.
 * TODO: Add texture and model
 */
public class BlockVisBridge extends BlockOK {

    public BlockVisBridge() {
        super(OKMObjects.blockVisBridge.name, Material.iron);
        setHardness(5.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 0);
        // setCreativeTab(LibMisc.MACHINERY_TAB);
        // Use base port texture for now
        setBlockTextureName("modularmachineryOverlay/base_modularports");
    }

    // BlockOK.registerBlockIcons prefixes the parent mod's domain; register under okmodular instead
    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        blockIcon = reg.registerIcon(Reference.PREFIX_MOD + getTextureName());
    }

    public static BlockVisBridge create() {
        return new BlockVisBridge();
    }

    @Override
    protected void registerTileEntity() {
        GameRegistry.registerTileEntity(TileVisBridge.class, name + "_TE");
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileVisBridge();
    }

    @Override
    public int onBlockPlaced(World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ,
        int metadata) {
        // Store the clicked side in metadata temporarily
        return side;
    }

    @Override
    public void onPostBlockPlaced(World world, int x, int y, int z, int metadata) {
        if (world.isRemote) return;

        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileVisBridge) {
            // Use the clicked side (stored in metadata) as absorption direction
            ForgeDirection clickedSide = ForgeDirection.getOrientation(metadata);
            ((TileVisBridge) te).setAbsorptionDirection(clickedSide.getOpposite());
        }
        // Reset metadata to 0
        world.setBlockMetadataWithNotify(x, y, z, 0, 2);
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileVisBridge) {
            ((TileVisBridge) te).removeThisNode();
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    public boolean canCreatureSpawn(EnumCreatureType type, IBlockAccess world, int x, int y, int z) {
        return false;
    }

    @Override
    public boolean canSilkHarvest(World world, EntityPlayer player, int x, int y, int z, int metadata) {
        return false;
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        if (world.isRemote) return;

        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileVisBridge) {
            ((TileVisBridge) te).onNeighborBlockChange(world, x, y, z, neighbor);
        }
    }
}
