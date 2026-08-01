package ruiseki.okmodular.structure;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;

/**
 * Recovers which pattern cell a world position belongs to.
 * <p>
 * The scan calls {@code IStructureElement.check(t, world, x, y, z)} with world coordinates only.
 * StructureLib does compute the structure-local (ABC) coordinate and hands it to
 * {@code IStructureWalker.visit}, but the {@code IStructureDefinition.check} path builds its walker
 * internally, so that value never reaches us. This undoes the transform instead.
 * <p>
 * {@code StructureUtility.iterateV2} builds world coordinates as
 * {@code getWorldOffset(abc) + controllerPosition}, after negating the controller offset it was
 * given. Inverting that gives
 *
 * <pre>
 * cell = getOffsetABC(world - controller) + controllerOffset
 * </pre>
 *
 * The two transforms are exact inverses of each other -- the axis swap is a signed permutation
 * matrix, so its transpose is its inverse, and the arithmetic is integer. {@code
 * ExtendedFacingRoundTripTest} pins that, because StructureLib does not.
 */
public final class StructureCellLocator {

    private StructureCellLocator() {}

    /**
     * @param facing           the machine's orientation, as passed to the structure check
     * @param controllerX      the controller's world X, the anchor the scan was given
     * @param controllerY      the controller's world Y
     * @param controllerZ      the controller's world Z
     * @param controllerOffset the controller's own cell in the pattern, or null for the origin. Not
     *                         modified.
     * @param worldX           world X of the position to locate
     * @param worldY           world Y of the position to locate
     * @param worldZ           world Z of the position to locate
     * @return a new {@code {a, b, c}} naming the pattern cell
     */
    public static int[] locate(ExtendedFacing facing, int controllerX, int controllerY, int controllerZ,
        int[] controllerOffset, int worldX, int worldY, int worldZ) {
        int[] delta = { worldX - controllerX, worldY - controllerY, worldZ - controllerZ };
        int[] cell = new int[3];
        facing.getOffsetABC(delta, cell);

        if (controllerOffset != null) {
            cell[0] += controllerOffset[0];
            cell[1] += controllerOffset[1];
            cell[2] += controllerOffset[2];
        }
        return cell;
    }
}
