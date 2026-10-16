package ruiseki.okmodular.structure.pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;

import ruiseki.okmodular.structure.StructureCellLocator;
import ruiseki.okmodular.testsupport.JavaSource;

/**
 * cell_x / cell_y / cell_z are measured in the pattern's own frame, so they turn with the shape.
 *
 * They used to be world offsets while the shape itself turned with the machine. On an upright
 * machine (defaultFacing UP or DOWN) layers become rows, so an expression written against
 * cell_y found its values on a horizontal axis: Pattern Lab's vein, centred with cell_y - 4,
 * lost its centre and placed almost nothing (PATTERN_LAB_TESTING 0-5).
 *
 * The frame is the one a pattern drawn for a north-facing, lying-down machine already had: there
 * cell_* equal the world offsets they always were, so nothing written for that case changes, and
 * every other facing now reads the same numbers for the same drawn cell.
 */
class PatternLocalCellTest {

    /** Three marked cells around an anchor X in the middle of a 3x3x3 block. */
    private static StructurePattern pattern() {
        String json = "{ 'name': 'p', 'anchor': 'X', 'mappings': { 'T': 'minecraft:stone', 'B': 'minecraft:dirt',"
            + " 'R': 'minecraft:sand' }, 'layers': ["
            + " [ '   ', ' T ', '   ' ],"
            + " [ '   ', ' XR', '   ' ],"
            + " [ 'B  ', '   ', '   ' ] ] }";
        return StructurePatternParser.parse(
            new JsonParser().parse(json.replace('\'', '"'))
                .getAsJsonObject(),
            "test.json");
    }

    private static Map<Character, String> localOffsets(String facing) {
        Map<Character, String> out = new TreeMap<>();
        for (StructurePattern.Cell cell : pattern().cellsFor(facing)) {
            out.put(cell.symbol, cell.cellX + "," + cell.cellY + "," + cell.cellZ);
        }
        return out;
    }

    @Test
    void uprightMachinesReadTheSameCellOffsets() {
        assertEquals(localOffsets(null), localOffsets("UP"), "cell_* changed when the machine stood up");
        assertEquals(localOffsets(null), localOffsets("DOWN"), "cell_* changed when the machine stood upside down");
    }

    @Test
    void theLayerAboveIsPositiveY() {
        Map<Character, String> offsets = localOffsets("UP");
        assertEquals("0,1,0", offsets.get('T'), "the cell drawn one layer above the anchor");
    }

    /** Both readers hand the expression the cell's own offsets, not a world difference. */
    @Test
    void ioPassesTheCellOffsets() {
        for (String file : new String[] { "api/recipe/io/StructureOutput.java", "api/recipe/io/StructureInput.java" }) {
            String source = JavaSource.stripComments(JavaSource.read(JavaSource.MAIN.resolve(file)));
            assertTrue(
                source.contains("cell.cellX, cell.cellY, cell.cellZ"),
                file + " does not pass the cell's offsets");
            assertFalse(source.contains("pos[0] - anchorPos[0]"), file + " still measures cell_* in world axes");
        }
    }

    /** For a north-facing machine lying down, the frame is the world, as it always was. */
    @Test
    void northFacingKeepsTheWorldOffsets() {
        ExtendedFacing north = ExtendedFacing.NORTH_NORMAL_NONE;
        int[] anchor = StructureCellLocator.toWorld(north, 0, 64, 0, null, 0, 0, 0);
        for (StructurePattern.Cell cell : pattern().cellsFor(null)) {
            int[] world = StructureCellLocator.toWorld(north, 0, 64, 0, null, cell.a, cell.b, cell.c);
            assertArrayEquals(
                new int[] { world[0] - anchor[0], world[1] - anchor[1], world[2] - anchor[2] },
                new int[] { cell.cellX, cell.cellY, cell.cellZ },
                "cell " + cell.symbol);
        }
    }
}
