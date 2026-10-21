package ruiseki.okmodular.structure;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * toProcessed / toAuthored say, cell by cell, what StructureShape.process does to a whole shape.
 *
 * A pattern needs both answers for one cell: where it lands once the shape is turned for the
 * machine (to place it), and where it was drawn (so cell_x / cell_y / cell_z and a generated
 * shape's offset turn with the pattern instead of staying on the world's axes). Every character
 * of the grid below is distinct, so each cell can be found again after the transform.
 */
class ShapeIndexMappingTest {

    private static final String[][] GRID = { { "ab", "cd", "ef", "gh" }, { "ij", "kl", "mn", "op" },
        { "qr", "st", "uv", "wx" } };

    private static void roundTrips(String facing) {
        String[][] processed = StructureShape.process(GRID, facing);
        for (int layer = 0; layer < GRID.length; layer++) {
            for (int row = 0; row < GRID[layer].length; row++) {
                for (int col = 0; col < GRID[layer][row].length(); col++) {
                    int[] p = StructureShape.toProcessed(GRID, facing, col, layer, row);
                    assertEquals(
                        GRID[layer][row].charAt(col),
                        processed[p[1]][p[2]].charAt(p[0]),
                        facing + ": cell " + col + "," + layer + "," + row + " is not where process put it");
                    assertArrayEquals(
                        new int[] { col, layer, row },
                        StructureShape.toAuthored(GRID, facing, p[0], p[1], p[2]),
                        facing + ": toAuthored does not undo toProcessed");
                }
            }
        }
    }

    @Test
    void horizontal() {
        roundTrips(null);
    }

    @Test
    void up() {
        roundTrips("UP");
    }

    @Test
    void down() {
        roundTrips("DOWN");
    }

    /** An offset anchor can sit outside the drawn cells; the mapping is arithmetic, not lookup. */
    @Test
    void indicesOutsideTheShapeStillMapBack() {
        for (String facing : new String[] { null, "UP", "DOWN" }) {
            int[] p = StructureShape.toProcessed(GRID, facing, 1, 7, -2);
            assertArrayEquals(
                new int[] { 1, 7, -2 },
                StructureShape.toAuthored(GRID, facing, p[0], p[1], p[2]),
                facing);
        }
    }
}
