package ruiseki.okmodular.structure.pattern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.structure.StructureCellLocator;
import ruiseki.okmodular.structure.StructureShape;

/**
 * A block pattern a recipe can read or write as a single input or output.
 * <p>
 * The pattern is authored with the same {@code layers} / {@code rows} vocabulary as a structure,
 * which means it is <b>not</b> in structure-local (ABC) coordinates as written: it has to go through
 * {@link StructureShape#process} first. That is done here, in {@link #cellsFor}, so no caller can
 * forget -- forgetting rotates the pattern against the machine it sits in and raises nothing.
 * <p>
 * <b>The pattern does not carry a facing of its own.</b> The transform to apply is the one the
 * <i>machine</i> was built with, so {@link #cellsFor} takes it as an argument. A {@code
 * defaultFacing} in the pattern file would be a second place for that value to live, and the day it
 * disagreed with the machine's the pattern would quietly rotate.
 * <p>
 * Cells come back relative to the pattern's anchor, because that is what the caller can act on: the
 * recipe names a structure symbol, the controller reports which cell that block occupies, and the
 * pattern says how far each of its own blocks sits from there.
 */
public final class StructurePattern {

    /** One block of the pattern, offset from the anchor in structure-local (ABC) coordinates. */
    public static final class Cell {

        public final int a;
        public final int b;
        public final int c;
        /**
         * Offset from the anchor in the pattern's own frame, which is what {@code cell_x} /
         * {@code cell_y} / {@code cell_z} read. It turns with the shape, so the same drawn cell
         * has the same offsets whichever way the machine faces.
         */
        public final int cellX;
        public final int cellY;
        public final int cellZ;
        /** The character this cell was drawn with, kept for error messages. */
        public final char symbol;
        /** What goes here. Ask it with {@link CellMapping#blockFor}; it need not be constant. */
        public final CellMapping mapping;

        Cell(int a, int b, int c, int[] cellOffset, char symbol, CellMapping mapping) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.cellX = cellOffset[0];
            this.cellY = cellOffset[1];
            this.cellZ = cellOffset[2];
            this.symbol = symbol;
            this.mapping = mapping;
        }

        @Override
        public String toString() {
            return "'" + symbol + "'@(" + a + "," + b + "," + c + ")=" + mapping;
        }
    }

    private final String name;
    private final char anchor;
    private final String[][] shape;
    private final Map<Character, CellMapping> mappings;

    /**
     * Cells per machine facing. Built on demand; a pattern is usually asked for one.
     * <p>
     * Only the geometry is cached. What each cell contains is asked for at placement time,
     * because a mapping may answer differently per position - see {@link CellMapping}.
     */
    private final Map<String, List<Cell>> resolved = new HashMap<>();

    private final boolean deterministic;

    /**
     * Where the anchor sits, for a shape that was generated rather than drawn; null otherwise.
     * <p>
     * A generated shape holds one character in every cell, so searching for the anchor would find
     * the topmost cell. The index is in the shape as authored, {@code {col, layer, row}}, and may
     * lie outside it when the shape was offset; {@link StructureShape#toProcessed} carries it into
     * the machine's facing.
     */
    private final int[] declaredOrigin;

    StructurePattern(String name, char anchor, String[][] shape, Map<Character, CellMapping> mappings) {
        this(name, anchor, shape, mappings, null);
    }

    StructurePattern(String name, char anchor, String[][] shape, Map<Character, CellMapping> mappings,
        int[] declaredOrigin) {
        this.name = name;
        this.anchor = anchor;
        this.shape = shape;
        this.mappings = mappings;
        this.declaredOrigin = declaredOrigin;
        this.deterministic = allCellsSettled(shape, mappings);
    }

    public String getName() {
        return name;
    }

    /**
     * Whether this pattern is the same arrangement every time it is written.
     * <p>
     * A structure <i>input</i> may only use one that is: matching against blocks that are
     * drawn fresh on each placement has nothing to compare to. Only the characters actually
     * drawn count, so a pattern may declare a mapping it never uses without losing this.
     */
    public boolean isDeterministic() {
        return deterministic;
    }

    private static boolean allCellsSettled(String[][] shape, Map<Character, CellMapping> mappings) {
        for (String[] layer : shape) {
            for (String row : layer) {
                for (int col = 0; col < row.length(); col++) {
                    CellMapping mapping = mappings.get(row.charAt(col));
                    if (mapping != null && !mapping.isDeterministic()) return false;
                }
            }
        }
        return true;
    }

    /**
     * The blocks of this pattern, offset from its anchor.
     *
     * @param machineDefaultFacing the {@code defaultFacing} of the structure this pattern is used
     *                             in, or null for a horizontal machine. <b>The machine's, not the
     *                             pattern's.</b>
     * @return an unmodifiable list, shared between calls with the same facing
     */
    /**
     * Each distinct mapping in {@code cells}, settled once for one placement.
     * <p>
     * Shared by the input and the output side because both lay the same pattern over an anchor and
     * then ask about every cell. Resolving inside the cell loop instead - which the input side did
     * - re-evaluates one mapping's expressions once per cell it appears in.
     */
    public static Map<CellMapping, CellMapping.Resolved> settle(List<Cell> cells, ConditionContext context) {
        Map<CellMapping, CellMapping.Resolved> settled = new IdentityHashMap<>();
        for (Cell cell : cells) {
            if (!settled.containsKey(cell.mapping)) settled.put(cell.mapping, cell.mapping.resolve(context));
        }
        return settled;
    }

    public List<Cell> cellsFor(String machineDefaultFacing) {
        String key = machineDefaultFacing == null ? "" : machineDefaultFacing.toUpperCase();
        List<Cell> cached = resolved.get(key);
        if (cached == null) {
            cached = resolve(machineDefaultFacing);
            resolved.put(key, cached);
        }
        return cached;
    }

    private List<Cell> resolve(String machineDefaultFacing) {
        String[][] processed = StructureShape.process(shape, machineDefaultFacing);

        // A declared anchor is checked for at parse time, against the shape as written, so a miss
        // here normally means the pattern never drew one and is measured from its own first cell.
        // The one other way to get here is a ragged vertical pattern: transformForVertical sizes
        // itself from the first layer, so rows past that length are dropped -- the same thing
        // happens to a machine's own structure, and the fix belongs there rather than here.
        int[] found = declaredOrigin != null
            ? StructureShape
                .toProcessed(shape, machineDefaultFacing, declaredOrigin[0], declaredOrigin[1], declaredOrigin[2])
            : StructureShape.findSymbolOffset(processed, anchor);
        int[] origin = found != null ? found : new int[] { 0, 0, 0 };
        int[] originInFrame = inFrame(machineDefaultFacing, origin[0], origin[1], origin[2]);

        List<Cell> cells = new ArrayList<>();
        for (int layer = 0; layer < processed.length; layer++) {
            for (int row = 0; row < processed[layer].length; row++) {
                String rowStr = processed[layer][row];
                for (int col = 0; col < rowStr.length(); col++) {
                    char symbol = rowStr.charAt(col);
                    CellMapping mapping = mappings.get(symbol);
                    if (mapping == null) continue; // space, and an anchor used only as a marker
                    int[] here = inFrame(machineDefaultFacing, col, layer, row);
                    int[] offset = { here[0] - originInFrame[0], here[1] - originInFrame[1],
                        here[2] - originInFrame[2] };
                    cells.add(new Cell(col - origin[0], layer - origin[1], row - origin[2], offset, symbol, mapping));
                }
            }
        }
        return Collections.unmodifiableList(cells);
    }

    /**
     * A processed cell's position in the pattern's own frame: where it would land, in world
     * axes, if the same pattern sat in a north-facing machine lying down. That is the frame
     * cell_* always had for such a machine, so patterns written for one read the same numbers.
     */
    private int[] inFrame(String machineDefaultFacing, int a, int b, int c) {
        int[] drawn = StructureShape.toAuthored(shape, machineDefaultFacing, a, b, c);
        return frameOf(shape, drawn[0], drawn[1], drawn[2]);
    }

    /** A drawn cell {@code (col, layer, row)} in the pattern's frame; see {@link #inFrame}. */
    private static int[] frameOf(String[][] shape, int col, int layer, int row) {
        int[] lyingDown = StructureShape.toProcessed(shape, null, col, layer, row);
        return StructureCellLocator
            .toWorld(ExtendedFacing.NORTH_NORMAL_NONE, 0, 0, 0, null, lyingDown[0], lyingDown[1], lyingDown[2]);
    }

    /**
     * The drawn index to anchor at so that the drawn cell {@code centre} reads {@code offset} as
     * its cell_*. The frame is the drawn axes up to sign and order, so the step along each frame
     * axis is found by trying one drawn step along each drawn axis.
     */
    static int[] anchorFor(String[][] shape, int[] centre, int[] offset) {
        int[] zero = frameOf(shape, 0, 0, 0);
        int[] anchor = centre.clone();
        for (int drawnAxis = 0; drawnAxis < 3; drawnAxis++) {
            int[] step = new int[3];
            step[drawnAxis] = 1;
            int[] moved = frameOf(shape, step[0], step[1], step[2]);
            for (int frameAxis = 0; frameAxis < 3; frameAxis++) {
                int sign = moved[frameAxis] - zero[frameAxis];
                if (sign != 0) anchor[drawnAxis] -= sign * offset[frameAxis];
            }
        }
        return anchor;
    }
}
