package ruiseki.okmodular.selftest;

import java.util.ArrayList;
import java.util.List;

import ruiseki.okmodular.structure.StructureShape;

/** Resolves an authored structure symbol or key such as "C*" or "P#2" to processed ABC cells. */
final class ShapeCells {

    private ShapeCells() {}

    /** Every cell drawn as {@code symbol}, in authored order (layer, row, col), as processed {A, B, C}. */
    static List<int[]> cellsOf(String[][] authoredShape, String defaultFacing, char symbol) {
        List<int[]> cells = new ArrayList<>();
        for (int layer = 0; layer < authoredShape.length; layer++) {
            for (int row = 0; row < authoredShape[layer].length; row++) {
                String rowStr = authoredShape[layer][row];
                for (int col = 0; col < rowStr.length(); col++) {
                    if (rowStr.charAt(col) == symbol) {
                        cells.add(StructureShape.toProcessed(authoredShape, defaultFacing, col, layer, row));
                    }
                }
            }
        }
        return cells;
    }

    /** "C*" selects every cell of the symbol, "P#2" the third one in authored order. */
    static List<int[]> resolve(String[][] authoredShape, String defaultFacing, String key) {
        if (key == null || key.length() < 2) {
            throw new IllegalArgumentException("Bad cell key: " + key);
        }
        char symbol = key.charAt(0);
        String selector = key.substring(1);
        List<int[]> cells = cellsOf(authoredShape, defaultFacing, symbol);
        if (selector.equals("*")) {
            return cells;
        }
        if (!selector.startsWith("#")) {
            throw new IllegalArgumentException("Bad cell key: " + key);
        }
        int index;
        try {
            index = Integer.parseInt(selector.substring(1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bad cell key: " + key, e);
        }
        if (index < 0 || index >= cells.size()) {
            throw new IllegalArgumentException("Cell key out of range: " + key + " (" + cells.size() + " cells)");
        }
        List<int[]> one = new ArrayList<>();
        one.add(cells.get(index));
        return one;
    }
}
