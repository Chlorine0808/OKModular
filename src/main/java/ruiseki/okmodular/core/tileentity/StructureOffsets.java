package ruiseki.okmodular.core.tileentity;

/**
 * Picks the offset a structure check starts from.
 * <p>
 * {@link AbstractMBModifierTE#getOffSet()} returns {@code int[][]} - one row per tier, from
 * the fixed multiblocks this hierarchy grew out of. Its only implementation today,
 * {@code StructureAgent}, always returns exactly one row: the structure JSON carries one
 * controller offset, or none and the origin stands in.
 * <p>
 * The caller used to index it as {@code getOffSet()[getTier() - 1]}, and {@code getTier()} on
 * a machine controller is not a row index - it is the tier computed from the blocks the
 * machine was built out of. The two only agreed because no structure definition had a
 * {@code tierMap} yet, so the tier was always 1 and the index always 0. The first tier-3
 * machine took the server down with an {@code ArrayIndexOutOfBoundsException} every tick.
 * <p>
 * Clamping rather than always taking row 0 keeps the {@code int[][]} contract meaningful for
 * an implementation that really does vary by tier, and lands on row 0 for the one that does
 * not - which is what {@code StructureAgent.forceStructureCheck} already did, so the periodic
 * and the forced check now agree.
 */
public final class StructureOffsets {

    private StructureOffsets() {}

    /**
     * @param offsets the table, possibly null, empty, or shorter than the tier
     * @param tier    the machine's tier, 1-based; anything out of range is clamped
     * @return a fresh three-element offset, never null
     */
    public static int[] forTier(int[][] offsets, int tier) {
        if (offsets == null || offsets.length == 0) return origin();

        int row = Math.min(Math.max(tier - 1, 0), offsets.length - 1);
        int[] offset = offsets[row];
        if (offset == null || offset.length < 3) return origin();

        return new int[] { offset[0], offset[1], offset[2] };
    }

    private static int[] origin() {
        return new int[] { 0, 0, 0 };
    }
}
