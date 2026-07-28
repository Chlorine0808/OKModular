package ruiseki.okmodular.api.modular;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;

/**
 * Marker interface for external port proxies (like chests, tanks, energy cells)
 * that are linked to a controller and act as a modular port. Extends
 * {@link IVisitablePort} so proxies carry the sided IO/texture and recipe
 * visitor concerns that machinery ports need.
 */
public interface IExternalPortProxy extends IVisitablePort {

    /**
     * @return The controller this proxy is linked to.
     */
    IMachineController getController();

    /**
     * @return The coordinates of the external block this proxy represents.
     */
    ChunkCoordinates getTargetPosition();

    /**
     * @return The actual TileEntity this proxy wraps (if currently loaded).
     */
    TileEntity getTargetTileEntity();

    /**
     * Set the actual TileEntity this proxy wraps.
     */
    void setTargetTileEntity(TileEntity tileEntity);

    /**
     * Proxy validation: check if the target TileEntity is valid.
     * Overrides the default IModularPort implementation to validate the proxy target.
     *
     * @return true if the target TileEntity exists and is valid
     */
    @Override
    default boolean isPortValid() {
        TileEntity target = getTargetTileEntity();
        return target != null && !target.isInvalid();
    }
}
