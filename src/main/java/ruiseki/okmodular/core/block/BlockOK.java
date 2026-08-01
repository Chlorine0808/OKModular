package ruiseki.okmodular.core.block;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.Nullable;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lombok.experimental.Delegate;
import ruiseki.okcore.datastructure.BlockPos;
import ruiseki.okcore.helper.InventoryHelpers;
import ruiseki.okcore.helper.TileHelpers;
import ruiseki.okmodular.OKModular;
import ruiseki.okmodular.Reference;
import ruiseki.okmodular.core.block.orientable.IOrientableBlock;
import ruiseki.okmodular.core.block.property.BlockPropertyProviderComponent;
import ruiseki.okmodular.core.block.property.IBlockPropertyProvider;
import ruiseki.okmodular.core.item.ItemBlockOK;
import ruiseki.okmodular.core.tileentity.IOrientable;
import ruiseki.okmodular.core.tileentity.TileEntityNBTStorage;
import ruiseki.okmodular.core.tileentity.TileEntityOK;

public class BlockOK extends Block implements IBlockPropertyProvider, IBlock {

    protected final Class<? extends TileEntityOK> teClass;
    protected final String name;

    @Delegate(types = IBlockPropertyProvider.class)
    private final IBlockPropertyProvider propertyComponent = new BlockPropertyProviderComponent(this);

    protected boolean isOpaque = true;
    protected boolean isFullSize = true;
    public boolean hasSubtypes = false;

    @Override
    public void registerProperties() {
        propertyComponent.registerProperties();
    }

    protected boolean rotatable = false;

    protected BlockOK(String name) {
        this(name, null, Material.iron);
    }

    public BlockOK(String name, Material material) {
        this(name, null, material);
    }

    protected BlockOK(String name, Class<? extends TileEntityOK> teClass) {
        this(name, teClass, Material.iron);
    }

    protected BlockOK(String name, @Nullable Class<? extends TileEntityOK> teClass, Material mat) {
        super(mat);
        this.teClass = teClass;
        this.name = name;
        setHardness(0.5F);
        setBlockName(name);
        setHarvestLevel("pickaxe", 0);
        this.setStepSound(getSoundForMaterial(mat));
        setLightOpacity(SOLID);
    }

    /**
     * The light opacity of a block that stops skylight completely.
     * <p>
     * <b>Every block in this hierarchy is a full solid block, and it has to say so here
     * rather than through {@link #isOpaque}.</b> Two things were wrong with leaving it to
     * the superclass:
     * <ol>
     * <li><b>The superclass could not see the answer.</b> {@code Block(Material)} runs
     * {@code lightOpacity = isOpaqueCube() ? 255 : 0}, and {@link #isOpaqueCube()} reads
     * {@link #isOpaque} - whose initialiser has not run yet, because instance field
     * initialisers run only after the superclass constructor returns. So every block came
     * out at zero: transparent to skylight, and invisible to the chunk's height map, while
     * {@code isOpaqueCube()} answered true from then on.
     * <li><b>The answer is not {@code isOpaque} anyway.</b> The controller and the ports set
     * {@code isOpaque = false} so their translucent overlay pass renders; they are still
     * solid blocks that a player cannot see through. Deriving one from the other would make
     * a rendering choice decide how the world is lit.
     * </ol>
     * A block that really is see-through calls {@code setLightOpacity(0)} in its own
     * constructor - after this one has run, so it wins.
     */
    private static final int SOLID = 255;

    @Override
    public void init() {
        registerBlock();
        registerTileEntity();
        registerBlockColor();
        registerComponent();
    }

    @Override
    public Block getBlock() {
        return this;
    }

    @Override
    public boolean isHasSubtypes() {
        return this.hasSubtypes;
    }

    protected void registerBlock() {
        GameRegistry.registerBlock(this, getItemBlockClass(), name);
    }

    protected Class<? extends ItemBlock> getItemBlockClass() {
        return ItemBlockOK.class;
    }

    protected void registerTileEntity() {
        if (teClass != null) {
            GameRegistry.registerTileEntity(teClass, name + "TileEntity");
        }
    }

    protected void registerBlockColor() {}

    protected void registerComponent() {
        registerProperties();
    }

    @SideOnly(Side.CLIENT)
    public void setRenderStateByMeta(final int itemDamage) {}

    @Override
    public boolean hasTileEntity(int metadata) {
        return teClass != null;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        if (teClass != null) {
            try {
                TileEntityOK tile = teClass.getDeclaredConstructor()
                    .newInstance();
                tile.onLoad();
                tile.setRotatable(isRotatable());
                return tile;
            } catch (Exception e) {
                OKModular.instance
                    .log(Level.ERROR, "Could not create tile entity for block " + name + " for class " + teClass);
            }
            return null;
        }
        return null;
    }

    public BlockOK setTextureName(String texture) {
        this.textureName = texture;
        return this;
    }

    @Override
    public String getTextureName() {
        return textureName == null ? name : textureName;
    }

    @Override
    public void registerBlockIcons(IIconRegister reg) {
        blockIcon = reg.registerIcon(Reference.PREFIX_MOD + getTextureName());
    }

    /* Subclass Helpers */

    @Override
    public final boolean isOpaqueCube() {
        return this.isOpaque;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return this.isFullSize && this.isOpaque;
    }

    @Override
    public final boolean isNormalCube(final IBlockAccess world, final int x, final int y, final int z) {
        return this.isFullSize;
    }

    public boolean isRotatable() {
        return rotatable;
    }

    public void setRotatable(boolean rotatable) {
        this.rotatable = rotatable;
    }

    public SoundType getSoundForMaterial(Material mat) {
        if (mat == Material.glass) return Block.soundTypeGlass;
        if (mat == Material.rock) return Block.soundTypeStone;
        if (mat == Material.wood) return Block.soundTypeWood;
        return Block.soundTypeMetal;
    }

    // Because the vanilla method takes floats...
    public void setBlockBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    // Orientable
    public IOrientable getOrientable(final IBlockAccess world, final int x, final int y, final int z) {
        if (this instanceof IOrientableBlock) {
            return ((IOrientableBlock) this).getOrientable(world, x, y, z);
        }
        return TileHelpers.getSafeTile(world, x, y, z, IOrientable.class);
    }

    // Block Destroy

    /**
     * If the NBT data of this tile entity should be added to the dropped meta.
     *
     * @return If the NBT data should be added.
     */
    public boolean saveNBTToDroppedItem() {
        return true;
    }

    /**
     * @return If the items should be dropped.
     */
    public boolean shouldDropInventory(World world, int x, int y, int z) {
        return true;
    }

    /**
     * Sets a block to air, but also plays the sound and particles and can spawn drops.
     * This includes calls to {@link BlockOK#onPreBlockDestroyed(World, int x, int y, int z, EntityPlayer)}
     * and {@link BlockOK#onPostBlockDestroyed(World, int x, int y, int z)}.
     *
     * @param world     The world.
     * @param x,        y, z The position.
     * @param dropBlock If this should produce item drops.
     * @return If the block was destroyed and not air.
     */
    public boolean destroyBlock(World world, int x, int y, int z, boolean dropBlock) {
        onPreBlockDestroyedPersistence(world, x, y, z);
        boolean result = world.func_147480_a(x, y, z, dropBlock);
        onPostBlockDestroyed(world, x, y, z);
        return result;
    }

    /**
     * Called before the block is broken or destroyed.
     *
     * @param world  The world.
     * @param x,     y, z The position of the to-be-destroyed block.
     * @param player The player destroying the block.
     */
    protected void onPreBlockDestroyed(World world, int x, int y, int z, @Nullable EntityPlayer player) {
        onPreBlockDestroyedPersistence(world, x, y, z);
    }

    /**
     * Called before the block is broken or destroyed when the tile data needs to be persisted.
     *
     * @param world The world.
     * @param x,    y, z The position of the to-be-destroyed block.
     */
    protected void onPreBlockDestroyedPersistence(World world, int x, int y, int z) {
        if (!world.isRemote) {
            preDestroyBlock(world, x, y, z, saveNBTToDroppedItem());
        }
    }

    /**
     * Legacy pre-destroy handling: drop inventory contents and cache the tile
     * NBT so getDrops can attach it to the dropped item.
     */
    private void preDestroyBlock(World world, int x, int y, int z, boolean saveNBT) {
        TileEntity tile = world.getTileEntity(x, y, z);

        if (shouldDropInventory(world, x, y, z) && tile instanceof IInventory inventory && !world.isRemote) {
            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (stack != null && stack.stackSize > 0) {
                    InventoryHelpers.dropItems(world, stack.copy(), new BlockPos(x, y, z));
                    inventory.setInventorySlotContents(i, null);
                }
            }
        }

        if (tile instanceof TileEntityOK teok && saveNBT) {
            TileEntityNBTStorage.TAG = teok.getNBTTagCompound();
            TileEntityNBTStorage.TILE = teok;
            writeAdditionalInfo(tile, TileEntityNBTStorage.TAG);
            teok.destroy();
        } else {
            TileEntityNBTStorage.TAG = null;
            TileEntityNBTStorage.TILE = null;
        }
        TileEntityNBTStorage.NAME = null;
    }

    /**
     * Called before the block is broken or destroyed.
     *
     * @param world The world.
     * @param x,    y, z The position of the to-be-destroyed block.
     */
    protected void onPostBlockDestroyed(World world, int x, int y, int z) {

    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block blockBroken, int meta) {
        onPreBlockDestroyed(world, x, y, z, null);
        super.breakBlock(world, x, y, z, blockBroken, meta);
        onPostBlockDestroyed(world, x, y, z);
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        onPreBlockDestroyed(world, x, y, z, player);
        if (willHarvest) return true;
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    @Override
    public void onBlockExploded(World world, int x, int y, int z, Explosion explosion) {
        onPreBlockDestroyed(world, x, y, z, null);
        super.onBlockExploded(world, x, y, z, explosion);
        onPostBlockDestroyed(world, x, y, z);
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, int x, int y, int z, int meta) {
        super.harvestBlock(world, player, x, y, z, meta);
        world.setBlockToAir(x, y, z);
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack stack) {
        if (entity != null) {
            TileEntityOK tile = (TileEntityOK) world.getTileEntity(x, y, z);
            if (tile != null && stack.getTagCompound() != null) {
                stack.getTagCompound()
                    .setInteger("x", x);
                stack.getTagCompound()
                    .setInteger("y", y);
                stack.getTagCompound()
                    .setInteger("z", z);
                tile.readFromNBT(stack.getTagCompound());
            }

            if (tile instanceof TileEntityOK.ITickingTile) {
                ((TileEntityOK.ITickingTile) tile).update();
            }
        }
        super.onBlockPlacedBy(world, x, y, z, entity, stack);
    }

    /**
     * Write additional info about the tile into the item.
     *
     * @param tile The tile that is being broken.
     * @param tag  The tag that will be added to the dropped item.
     */
    public void writeAdditionalInfo(TileEntity tile, NBTTagCompound tag) {

    }

    /**
     * If this block should drop its block item.
     *
     * @param world   The world.
     * @param x,      y, z The position.
     * @param fortune Fortune level.
     * @return If the item should drop.
     */
    public boolean isDropBlockItem(IBlockAccess world, int x, int y, int z, int fortune) {
        return true;
    }

    @Override
    public final ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<>();

        Item item = getItemDropped(meta, world.rand, fortune);
        if (item != null && isDropBlockItem(world, x, y, z, fortune)) {
            ItemStack itemStack = new ItemStack(item, 1, damageDropped(meta));
            if (TileEntityNBTStorage.TILE != null) {
                itemStack = tileDataToItemStack(TileEntityNBTStorage.TILE, itemStack);
            }
            drops.add(itemStack);
        }
        return drops;
    }

    protected ItemStack tileDataToItemStack(TileEntityOK tile, ItemStack itemStack) {
        if (isKeepNBTOnDrop()) {
            if (TileEntityNBTStorage.TAG != null) {
                itemStack.setTagCompound(TileEntityNBTStorage.TAG);
            }
            if (TileEntityNBTStorage.NAME != null) {
                itemStack.setStackDisplayName(TileEntityNBTStorage.NAME);
            }
        }
        return itemStack;
    }

    /**
     * If the NBT data of this block should be preserved in the item when it
     * is broken into an item.
     *
     * @return If it should keep NBT data.
     */
    public boolean isKeepNBTOnDrop() {
        return true;
    }

    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z,
        @Nullable EntityPlayer player) {
        ItemStack itemStack = super.getPickBlock(target, world, x, y, z, player);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileEntityOK teok && isKeepNBTOnDrop()) {
            itemStack.setTagCompound(teok.getNBTTagCompound());
        }
        return itemStack;
    }
}
