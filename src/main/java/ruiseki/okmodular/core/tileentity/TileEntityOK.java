package ruiseki.okmodular.core.tileentity;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.capability.CapabilityProvider;

import lombok.experimental.Delegate;
import ruiseki.okcore.datastructure.BlockPos;
import ruiseki.okcore.datastructure.DimPos;
import ruiseki.okmodular.core.persist.nbt.INBTProvider;
import ruiseki.okmodular.core.persist.nbt.NBTPersist;
import ruiseki.okmodular.core.persist.nbt.NBTProviderComponent;

public abstract class TileEntityOK extends TileEntity implements ITile, INBTProvider, CapabilityProvider, IOrientable {

    @Delegate
    private final INBTProvider nbtProvider = new NBTProviderComponent(this);

    @NBTPersist
    private Boolean rotatable = false;

    @NBTPersist
    private ForgeDirection forward = ForgeDirection.NORTH;

    @NBTPersist
    private ForgeDirection up = ForgeDirection.UP;

    private final boolean ticking;

    private BlockPos cachedPos;
    private final int randomOffset = (int) (Math.random() * 20);

    public TileEntityOK() {
        this.ticking = this instanceof ITickingTile;
    }

    @Override
    public final boolean canUpdate() {
        return true;
    }

    @Override
    public boolean shouldRefresh(Block oldBlock, Block newBlock, int oldMeta, int newMeta, World world, int x, int y,
        int z) {
        return (oldBlock != newBlock);
    }

    protected boolean isTicking() {
        return ticking;
    }

    /**
     * for dormant chunk cache.
     */
    public void onChunkLoad() {
        if (this.isInvalid()) {
            this.validate();
        }
    }

    /**
     * Set whether or not the blockState that has this tile entity can be rotated.
     *
     * @param rotatable If it can be rotated.
     */
    public void setRotatable(boolean rotatable) {
        this.rotatable = rotatable;
    }

    @Override
    public boolean canBeRotated() {
        return rotatable;
    }

    @Override
    public ForgeDirection getForward() {
        return this.forward;
    }

    @Override
    public ForgeDirection getUp() {
        return this.up;
    }

    @Override
    public void setOrientation(final ForgeDirection inForward, final ForgeDirection inUp) {
        this.forward = inForward;
        this.up = inUp;
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
        worldObj.notifyBlockOfNeighborChange(xCoord, yCoord, zCoord, Blocks.air);
    }

    @Override
    public final void updateEntity() {
        if (isTicking()) {
            ((ITickingTile) this).update();
        }
    }

    /**
     * Do not override this method (you won't even be able to do so).
     * Use updateTileEntity() instead.
     */
    private void updateTicking() {
        doUpdate();
    }

    /**
     * Override this method instead of {@link TileEntityOK#updateEntity()}.
     * This method is called each tick.
     */
    protected void doUpdate() {}

    @Override
    public Packet getDescriptionPacket() {
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, getNBTTagCompound());
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        super.onDataPacket(net, packet);
        NBTTagCompound tag = packet.func_148857_g();
        readFromNBT(tag);
    }

    @Override
    public final void writeToNBT(NBTTagCompound root) {
        super.writeToNBT(root);
        writeCommon(root);
    }

    @Override
    public final void readFromNBT(NBTTagCompound root) {
        super.readFromNBT(root);
        readCommon(root);
    }

    public void writeCommon(NBTTagCompound tag) {
        writeGeneratedFieldsToNBT(tag);
    }

    public void readCommon(NBTTagCompound tag) {
        if (tag == null) return;
        readGeneratedFieldsFromNBT(tag);
        onLoad();
    }

    /**
     * Called when the block of this tile entity is destroyed.
     */
    public void destroy() {
        invalidate();
    }

    /**
     * If this entity is interactable with a player.
     *
     * @param player The player that is checked.
     * @return If the given player can interact.
     */
    public boolean canInteractWith(EntityPlayer player) {
        return !isInvalid() && player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= 64D;
    }

    /**
     * When the tile is loaded or created.
     */
    public void onLoad() {}

    /**
     * Get the NBT tag for this tile entity.
     *
     * @return The NBT tag that is created with the
     *         {@link TileEntityOK#writeToNBT(net.minecraft.nbt.NBTTagCompound)} method.
     */
    public NBTTagCompound getNBTTagCompound() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return tag;
    }

    @Override
    public <T> @Nullable T getCapability(@NotNull Class<T> capability, @NotNull ForgeDirection side) {
        return null;
    }

    @Override
    public BlockPos getPos() {
        if (cachedPos == null || cachedPos.getX() != xCoord
            || cachedPos.getY() != yCoord
            || cachedPos.getZ() != zCoord) {

            cachedPos = new BlockPos(this);
        }
        return cachedPos;
    }

    @Override
    public DimPos getDimPos() {
        return DimPos.of(worldObj, getPos());
    }

    @Override
    public int getX() {
        return xCoord;
    }

    @Override
    public int getY() {
        return yCoord;
    }

    @Override
    public int getZ() {
        return zCoord;
    }

    @Override
    public World getWorld() {
        return worldObj;
    }

    @Override
    public int getWorldID() {
        return worldObj.provider.dimensionId;
    }

    @Override
    public Block getBlock() {
        return getBlockType();
    }

    @Override
    public int getMeta() {
        return getBlockMetadata();
    }

    @Override
    public TileEntity getTile() {
        return this;
    }

    @Override
    public void mark() {
        markDirty();
    }

    @Override
    public void updateTEState() {
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public void updateTELight() {
        worldObj.updateLightByType(EnumSkyBlock.Sky, xCoord, yCoord, zCoord);
        worldObj.updateLightByType(EnumSkyBlock.Block, xCoord, yCoord, zCoord);
    }

    protected boolean shouldDoWorkThisTick(int interval) {
        return (worldObj.getTotalWorldTime() + randomOffset) % interval == 0;
    }

    public interface ITickingTile {

        void update();
    }

    public static class TickingTileComponent implements ITickingTile {

        private final TileEntityOK tile;

        public TickingTileComponent(TileEntityOK tile) {
            this.tile = tile;
        }

        @Override
        public final void update() {
            tile.updateTicking();
        }
    }

}
