package ruiseki.okmodular.structure.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import ruiseki.okmodular.structure.StructureException;

/**
 * A generated shape can be moved off its anchor with {@code offset} (StructureIO todos, item Y).
 *
 * A generated shape is anchored at its centre, and the centre is the block the recipe names - a
 * block of the machine. StructureOutput refuses any anchor that would write over the machine,
 * so a shape could never be placed as an output: the vein example in the docs did nothing
 * (Pattern Lab C25). {@code offset} says where the shape's centre sits, in cell_* axes, so the
 * centre cell reads exactly the offset and a shape moved clear of the machine can be written.
 */
class PatternShapeOffsetTest {

    private static StructurePattern sphere(String offset) {
        String json = "{ 'name': 'v', 'shape': { 'type': 'sphere', 'radius': 1, 'symbol': 'X'"
            + (offset == null ? "" : ", 'offset': " + offset)
            + " }, 'mappings': { 'X': 'minecraft:stone' } }";
        return StructurePatternParser.parse(
            new JsonParser().parse(json.replace('\'', '"'))
                .getAsJsonObject(),
            "test.json");
    }

    private static Set<String> offsets(StructurePattern pattern, String facing) {
        Set<String> out = new TreeSet<>();
        for (StructurePattern.Cell cell : pattern.cellsFor(facing)) {
            out.add(cell.cellX + "," + cell.cellY + "," + cell.cellZ);
        }
        return out;
    }

    @Test
    void theCentreReadsTheOffset() {
        Set<String> cells = offsets(sphere("[2, 4, -3]"), null);
        assertEquals(7, cells.size());
        assertTrue(cells.contains("2,4,-3"), "the centre cell is not at the offset: " + cells);
        assertTrue(cells.contains("2,5,-3") && cells.contains("2,3,-3"), "the shape did not move as a whole: " + cells);
    }

    @Test
    void anOffsetClearOfTheRadiusLeavesTheAnchorEmpty() {
        assertFalse(offsets(sphere("[0, 4, 0]"), null).contains("0,0,0"), "a cell still sits on the anchor");
    }

    @Test
    void theOffsetTurnsWithTheMachine() {
        StructurePattern pattern = sphere("[0, 4, 0]");
        assertEquals(offsets(pattern, null), offsets(pattern, "UP"));
        assertEquals(offsets(pattern, null), offsets(pattern, "DOWN"));
    }

    @Test
    void noOffsetStaysOnTheCentre() {
        assertTrue(offsets(sphere(null), null).contains("0,0,0"));
    }

    @Test
    void anOffsetMustBeThreeWholeNumbers() {
        assertThrows(StructureException.class, () -> sphere("[0, 4]"));
        assertThrows(StructureException.class, () -> sphere("[0, '4', 0]"));
        assertThrows(StructureException.class, () -> sphere("4"));
    }
}
