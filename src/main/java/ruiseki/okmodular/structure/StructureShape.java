package ruiseki.okmodular.structure;

/**
 * Turns the {@code layers} / {@code rows} written in JSON into the orientation StructureLib
 * expects.
 * <p>
 * The authored shape is not used as-is: rows are reversed within each layer, or, for a machine that
 * builds upright, layers and rows swap roles. Only afterwards does
 * {@code StructureUtility.transpose} run and the result reach {@code addShape}.
 * <p>
 * <b>In the processed shape, {@code (col, layer, row)} is {@code (A, B, C)}.</b>
 * {@link #findControllerOffset} returns that triple, and it is handed straight to
 * {@code IStructureDefinition.check} as {@code basePositionA/B/C}.
 * <p>
 * This lives apart from {@code CustomStructureRegistry} because structure IO patterns reuse the same
 * authoring vocabulary and have to land in the same orientation. Copying the arithmetic into a
 * second place would let the two drift apart, silently rotating patterns relative to the machine
 * they sit in.
 */
public final class StructureShape {

    private StructureShape() {}

    /**
     * Applies whichever transform the machine's facing calls for.
     *
     * @param defaultFacing the structure's {@code defaultFacing}, or null for a horizontal machine
     */
    public static String[][] process(String[][] shape, String defaultFacing) {
        if (defaultFacing != null && ("UP".equalsIgnoreCase(defaultFacing) || "DOWN".equalsIgnoreCase(defaultFacing))) {
            return transformForVertical(shape, defaultFacing);
        }
        return rotate180(shape);
    }

    /** Reverses the row order within each layer. Returns a new array; the input is untouched. */
    public static String[][] rotate180(String[][] shape) {
        String[][] rotated = new String[shape.length][];
        for (int layer = 0; layer < shape.length; layer++) {
            int numRows = shape[layer].length;
            rotated[layer] = new String[numRows];
            for (int row = 0; row < numRows; row++) {
                rotated[layer][row] = shape[layer][numRows - 1 - row];
            }
        }
        return rotated;
    }

    /**
     * Moves layers (Y) into rows (Z) so the structure builds upright.
     * <p>
     * {@code DOWN} additionally reverses the resulting rows, so the shape reads the same way from
     * either side.
     */
    public static String[][] transformForVertical(String[][] shape, String facing) {
        int originalLayers = shape.length;
        if (originalLayers == 0) return shape;

        int originalRows = shape[0].length;
        boolean isDown = "DOWN".equalsIgnoreCase(facing);

        String[][] transformed = new String[originalRows][originalLayers];

        for (int originalZ = 0; originalZ < originalRows; originalZ++) {
            for (int originalY = 0; originalY < originalLayers; originalY++) {
                int targetRowIndex = isDown ? (originalLayers - 1 - originalY) : originalY;

                transformed[originalZ][targetRowIndex] = shape[originalY].length > originalZ
                    ? shape[originalY][originalZ]
                    : "";
            }
        }
        return transformed;
    }

    /**
     * Locates the controller symbol {@code 'Q'} in an already-processed shape.
     *
     * @return {@code {col, layer, row}} = {@code {A, B, C}}, or the origin when there is no
     *         controller in the shape
     */
    public static int[] findControllerOffset(String[][] shape) {
        for (int layer = 0; layer < shape.length; layer++) {
            for (int row = 0; row < shape[layer].length; row++) {
                String rowStr = shape[layer][row];
                for (int col = 0; col < rowStr.length(); col++) {
                    if (rowStr.charAt(col) == 'Q') {
                        return new int[] { col, layer, row };
                    }
                }
            }
        }
        return new int[] { 0, 0, 0 };
    }
}
