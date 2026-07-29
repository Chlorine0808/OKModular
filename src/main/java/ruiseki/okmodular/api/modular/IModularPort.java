package ruiseki.okmodular.api.modular;

import net.minecraft.tileentity.TileEntity;

/**
 * Base marker interface for modular machinery IO ports.
 * Implemented by TileEntities that can be part of a modular machine structure.
 * Recipe visitation is a machinery-side concern; implementations that take part
 * in recipe execution additionally implement the owning mod's visitable-port
 * interface.
 */
public interface IModularPort extends IPortType {

    /**
     * Get the tier level of this port (0-15).
     *
     * @return The tier level
     */
    int getTier();

    /**
     * Set the tier level of this port (0-15).
     *
     * @param tier The tier level to set
     */
    void setTier(int tier);

    /**
     * Check if this port is currently valid and usable.
     * Template Method Pattern: each implementation defines its own validation logic.
     *
     * Default implementation checks if this port is a TileEntity and whether it's invalid.
     * Subclasses (especially proxies) should override this to provide custom validation.
     *
     * @return true if this port is valid and can be used, false if it should be filtered out
     */
    default boolean isPortValid() {
        // Default implementation: check TileEntity validity
        if (this instanceof TileEntity) {
            return !((TileEntity) this).isInvalid();
        }
        return true;
    }

    /**
     * Get the index assigned to this port during structure formation.
     * 
     * @return The assigned index, or -1 if none.
     */
    default int getAssignedIndex() {
        return -1;
    }

    /**
     * Set the index assigned to this port during structure formation.
     *
     * @param index The index to assign.
     */
    default void setAssignedIndex(int index) {}

    /**
     * The colour a player has painted this port, dividing a machine's ports into
     * groups that run recipes independently.
     *
     * <p>
     * Never null. An unpainted port answers {@link PortColor#NONE}, and an
     * unpainted port takes part in <em>every</em> colour group - so a single
     * unpainted energy hatch can feed all of them.
     *
     * @return the colour, or {@link PortColor#NONE} if unpainted
     */
    default PortColor getPortColor() {
        return PortColor.NONE;
    }

    /**
     * Paint this port.
     *
     * Implementations with nowhere to store a colour ignore the call rather than
     * refusing it: this arrives from other mods' painting tools, which have no way
     * to ask first.
     *
     * @param color the colour to apply, or {@link PortColor#NONE} to strip it
     */
    default void setPortColor(PortColor color) {}
}
