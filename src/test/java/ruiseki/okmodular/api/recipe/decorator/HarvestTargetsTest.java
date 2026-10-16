package ruiseki.okmodular.api.recipe.decorator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * harvest_block collects from exactly the cells the block input removes and the block output
 * overwrites (StructureIO todos, item Z).
 *
 * It used to read every cell of the symbol: getSymbolPositions on the input side and
 * BlockOutput.getPositions on the output side. Neither looks at amount or at which block is
 * there, so a symbol of four stones under a one-block recipe dropped four cobblestones and
 * removed one stone - items from nothing. Confirmed in game (Pattern Lab H1/H2/H4/H6/H9).
 *
 * The cells an input or output acts on can only be chosen against a World, which tests cannot
 * build. So the choice lives in one method per side, targets(...), the selection itself is the
 * pure CellSelection, and this test pins that consuming, placing and harvesting all go through
 * targets(...) rather than each choosing for itself.
 */
class HarvestTargetsTest {

    private static String body(String file, String signature) {
        return JavaSource
            .methodBody(JavaSource.stripComments(JavaSource.read(JavaSource.MAIN.resolve(file))), signature);
    }

    @Test
    void harvestReadsTheCellsTheIoActsOn() {
        String positions = body(
            "api/recipe/decorator/HarvestBlockDecorator.java",
            "private List<ChunkCoordinates> positionsFrom(");
        assertTrue(positions.contains(".targets("), "harvest must ask the block IO which cells it acts on");
        assertFalse(positions.contains("getSymbolPositions("), "every cell of the symbol is not what is removed");
        assertFalse(positions.contains(".getPositions("), "every cell of the symbol is not what is overwritten");
    }

    @Test
    void consumingUsesTheSameSelection() {
        String check = body("api/recipe/io/BlockInput.java", "public boolean check(");
        assertTrue(check.contains("targets("), "BlockInput.check must act on targets(...)");
    }

    @Test
    void placingUsesTheSameSelection() {
        String apply = body("api/recipe/io/BlockOutput.java", "public void apply(IRecipeContext context");
        assertTrue(apply.contains("targets("), "BlockOutput.apply must act on targets(...)");
    }
}
