package ruiseki.okmodular.core.tileentity;

import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.mojang.authlib.GameProfile;

import lombok.Getter;
import ruiseki.okcore.helper.PlayerHelpers;
import ruiseki.okmodular.core.persist.nbt.NBTPersist;

/**
 * Base class for multiblock modifier tile entities.
 * <p>
 * Handles:
 * <ul>
 * <li>Multiblock structure formation and validation</li>
 * <li>Player ownership via GameProfile</li>
 * </ul>
 */
public abstract class AbstractMBModifierTE extends AbstractTE {

    /** Owner of the multiblock machine. */
    @NBTPersist
    protected GameProfile player;

    /** Flag indicating whether the multiblock structure is fully formed. */
    @Getter
    @NBTPersist
    protected boolean isFormed = false;
    private transient boolean lastIsFormed = false;

    /**
     * Returns the multiblock structure definition used for validation.
     *
     * @return structure definition
     */
    @SuppressWarnings("rawtypes")
    protected abstract IStructureDefinition getStructureDefinition();

    /**
     * Returns offsets for structure validation based on tier.
     *
     * @return 2D array of offsets [tier][x,y,z]
     */
    public abstract int[][] getOffSet();

    /**
     * Returns the name of the structure piece to check.
     *
     * @return structure piece name
     */
    public abstract String getStructurePieceName();

    /**
     * Returns the facing of the multiblock structure.
     *
     * @return ExtendedFacing instance
     */
    protected ExtendedFacing getExtendedFacing() {
        return ExtendedFacing.of(ForgeDirection.getOrientation(2));
    }

    /**
     * Checks if the multiblock structure is valid.
     *
     * @param piece structure piece name
     * @param ox    x offset
     * @param oy    y offset
     * @param oz    z offset
     * @return true if structure is formed
     */
    @SuppressWarnings("unchecked")
    protected boolean structureCheck(String piece, int ox, int oy, int oz) {
        clearStructureParts();

        boolean valid = getStructureDefinition()
            .check(this, piece, worldObj, getExtendedFacing(), xCoord, yCoord, zCoord, ox, oy, oz, false);

        if (valid && !isFormed) {
            isFormed = true;
            onFormed();
            forceRenderUpdate();
        } else if (!valid && isFormed) {
            isFormed = false;
            forceRenderUpdate();
        }

        return isFormed;
    }

    /**
     * Clears stored multiblock structure parts.
     */
    protected abstract void clearStructureParts();

    /**
     * Attempts to add a TileEntity to the machine structure.
     *
     * @param tile tile entity to add
     * @return true if successfully added
     */
    protected boolean addToMachine(TileEntity tile) {
        return false;
    }

    /**
     * Attempts to add a block to the machine structure.
     *
     * @param block block to add
     * @param meta  block metadata
     * @param x     x-coordinate
     * @param y     y-coordinate
     * @param z     z-coordinate
     * @return true if successfully added
     */
    protected boolean addToMachine(Block block, int meta, int x, int y, int z) {
        return false;
    }

    /**
     * Returns the tier of the multiblock machine.
     *
     * @return tier number
     */
    public abstract int getTier();

    /**
     * Updates the tile entity each tick.
     * Checks multiblock structure before processing tasks.
     */
    @Override
    public void updateEntityClient() {
        super.updateEntityClient();
        if (isFormed != lastIsFormed) {
            lastIsFormed = isFormed;
            forceRenderUpdate();
        }
    }

    @Override
    public void doUpdate() {
        super.doUpdate();

        if (worldObj.isRemote) {
            return;
        }

        if (!shouldDoWorkThisTick(5) && isFormed) {
            return;
        }

        // See StructureOffsets for why the tier cannot index this table directly.
        int[] offset = StructureOffsets.forTier(getOffSet(), getTier());
        boolean valid = structureCheck(getStructurePieceName(), offset[0], offset[1], offset[2]);

        if (!valid) return;
    }

    /**
     * Called when the multiblock structure is successfully formed.
     */
    public abstract void onFormed();

    /**
     * Sets the owner of the machine from a player.
     *
     * @param plr EntityPlayer instance
     */
    public void setPlayer(EntityPlayer plr) {
        if (plr != null) {
            this.player = plr.getGameProfile();
        }
    }

    /**
     * Sets the owner of the machine from a UUID.
     *
     * @param plr player UUID
     */
    public void setPlayer(UUID plr) {
        EntityPlayer pl = PlayerHelpers.getPlayerFromWorld(this.worldObj, plr);
        if (pl != null) {
            this.player = pl.getGameProfile();
        }

    }

    /**
     * Returns the UUID of the player who owns the machine.
     *
     * @return player UUID
     */
    public UUID getPlayerID() {
        return this.player.getId();
    }

    /**
     * Returns the GameProfile of the player who owns the machine.
     *
     * @return player GameProfile
     */
    public GameProfile getPlayerProfile() {
        return this.player;
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public boolean processTasks(boolean redstone) {
        return false;
    }

}
