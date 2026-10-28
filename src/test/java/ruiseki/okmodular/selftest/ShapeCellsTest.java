package ruiseki.okmodular.selftest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.structure.StructureShape;

// The selftest overrides cells such as the default machines' "P" (port-or-casing) after
// StructureLib built the first option; the ABC must be the one the structure scan uses, so the
// controller cell is cross-checked against findControllerOffset(process(...)).
class ShapeCellsTest {

    /** assembler.json layers: 3 layers of 3 rows; the middle layer holds 7 'P' and the 'Q'. */
    private static final String[][] ASSEMBLER = { { "CCC", "CCC", "CCC" }, { "PPP", "P P", "PQP" },
        { "CCC", "CCC", "CCC" } };

    /** Uneven layers, so a vertical facing swaps layers and rows non-trivially. */
    private static final String[][] TALL = { { "AB", "CD" }, { "EQ", "PG" }, { "HI", "JK" } };

    @Test
    @DisplayName("コントローラのセルはレジストリのオフセットと一致する")
    void controllerCellMatchesTheRegistryOffset() {
        for (String facing : new String[] { null, "UP", "DOWN" }) {
            String[][] shape = "UP".equals(facing) || "DOWN".equals(facing) ? TALL : ASSEMBLER;
            int[] expected = StructureShape.findControllerOffset(StructureShape.process(shape, facing));
            List<int[]> q = ShapeCells.cellsOf(shape, facing, 'Q');
            assertEquals(1, q.size(), String.valueOf(facing));
            assertArrayEquals(expected, q.get(0), String.valueOf(facing));
        }
    }

    @Test
    @DisplayName("P#n は記述順の n 番目を選ぶ")
    void hashPicksTheNthInAuthoredOrder() {
        // authored (col, layer, row): P#0 = (0,1,0), P#3 = (0,1,1), P#6 = (2,1,2)
        assertArrayEquals(
            StructureShape.toProcessed(ASSEMBLER, null, 0, 1, 0),
            ShapeCells.resolve(ASSEMBLER, null, "P#0")
                .get(0));
        assertArrayEquals(
            StructureShape.toProcessed(ASSEMBLER, null, 0, 1, 1),
            ShapeCells.resolve(ASSEMBLER, null, "P#3")
                .get(0));
        assertArrayEquals(
            StructureShape.toProcessed(ASSEMBLER, null, 2, 1, 2),
            ShapeCells.resolve(ASSEMBLER, null, "P#6")
                .get(0));
    }

    @Test
    @DisplayName("P* は assembler.json の P の数だけ返す")
    void starPicksAll() {
        assertEquals(
            7,
            ShapeCells.resolve(ASSEMBLER, null, "P*")
                .size());
        assertEquals(
            18,
            ShapeCells.resolve(ASSEMBLER, null, "C*")
                .size());
    }

    @Test
    @DisplayName("縦向きの構造でも走査と同じ ABC になる")
    void verticalFacingMatchesTheProcessedShape() {
        for (String facing : new String[] { "UP", "DOWN" }) {
            String[][] processed = StructureShape.process(TALL, facing);
            for (char symbol : "ABCDEQPGHIJK".toCharArray()) {
                int[] expected = StructureShape.findSymbolOffset(processed, symbol);
                List<int[]> cells = ShapeCells.cellsOf(TALL, facing, symbol);
                assertEquals(1, cells.size());
                // findSymbolOffset reports {col, layer, row} of the processed shape = {A, B, C}
                assertArrayEquals(expected, cells.get(0), facing + " " + symbol);
            }
        }
    }

    @Test
    @DisplayName("未知の記号は空")
    void unknownSymbolIsEmpty() {
        assertTrue(
            ShapeCells.cellsOf(ASSEMBLER, null, 'Z')
                .isEmpty());
        assertTrue(
            ShapeCells.resolve(ASSEMBLER, null, "Z*")
                .isEmpty());
    }

    @Test
    @DisplayName("不正なキーは IllegalArgumentException")
    void malformedKeyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ShapeCells.resolve(ASSEMBLER, null, "P#x"));
        assertThrows(IllegalArgumentException.class, () -> ShapeCells.resolve(ASSEMBLER, null, "P"));
        assertThrows(IllegalArgumentException.class, () -> ShapeCells.resolve(ASSEMBLER, null, "P#7"));
    }
}
