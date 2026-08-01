package ruiseki.okmodular.structure.pattern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        /** The character this cell was drawn with, kept for error messages. */
        public final char symbol;
        /** {@code modid:name}, {@code modid:name:meta} or {@code modid:name:*}. */
        public final String blockId;

        Cell(int a, int b, int c, char symbol, String blockId) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.symbol = symbol;
            this.blockId = blockId;
        }

        @Override
        public String toString() {
            return "'" + symbol + "'@(" + a + "," + b + "," + c + ")=" + blockId;
        }
    }

    private final String name;
    private final char anchor;
    private final String[][] shape;
    private final Map<Character, String> mappings;

    /** Resolved cells per machine facing. Built on demand; a pattern is usually asked for one. */
    private final Map<String, List<Cell>> resolved = new HashMap<>();

    StructurePattern(String name, char anchor, String[][] shape, Map<Character, String> mappings) {
        this.name = name;
        this.anchor = anchor;
        this.shape = shape;
        this.mappings = mappings;
    }

    public String getName() {
        return name;
    }

    /**
     * The blocks of this pattern, offset from its anchor.
     *
     * @param machineDefaultFacing the {@code defaultFacing} of the structure this pattern is used
     *                             in, or null for a horizontal machine. <b>The machine's, not the
     *                             pattern's.</b>
     * @return an unmodifiable list, shared between calls with the same facing
     */
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
        int[] found = StructureShape.findSymbolOffset(processed, anchor);
        int[] origin = found != null ? found : new int[] { 0, 0, 0 };

        List<Cell> cells = new ArrayList<>();
        for (int layer = 0; layer < processed.length; layer++) {
            for (int row = 0; row < processed[layer].length; row++) {
                String rowStr = processed[layer][row];
                for (int col = 0; col < rowStr.length(); col++) {
                    char symbol = rowStr.charAt(col);
                    String blockId = mappings.get(symbol);
                    if (blockId == null) continue; // space, and an anchor used only as a marker
                    cells.add(new Cell(col - origin[0], layer - origin[1], row - origin[2], symbol, blockId));
                }
            }
        }
        return Collections.unmodifiableList(cells);
    }
}
