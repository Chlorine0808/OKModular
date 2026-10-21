package ruiseki.okmodular.api.recipe.decorator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * random_block_output and per_position_probability place their output only on cells that hold
 * its {@code replace}.
 *
 * Both picked from every cell of the symbol and called BlockOutput.applyAt, which places
 * without looking at replace. The Ore Transformer's sand-to-glass example, with
 * "replace": "minecraft:glass", grew stained glass on cells that had held air. Confirmed in
 * game.
 *
 * Which block a cell holds can only be read from a World, which tests cannot build, so this
 * pins that the cell test lives in one method and that targets(...) and both decorators ask it.
 */
class DecoratorReplaceTest {

    private static String body(String file, String signature) {
        return JavaSource
            .methodBody(JavaSource.stripComments(JavaSource.read(JavaSource.MAIN.resolve(file))), signature);
    }

    @Test
    void targetsUsesTheSharedCellTest() {
        String targets = body("api/recipe/io/BlockOutput.java", "public List<ChunkCoordinates> targets(");
        assertTrue(targets.contains("acceptsAt("), "targets must choose cells through acceptsAt");
    }

    @Test
    void randomBlockOutputPicksOnlyAcceptedCells() {
        String produce = body(
            "api/recipe/decorator/RandomBlockOutputDecorator.java",
            "public void produceExtraOutputs(");
        assertTrue(produce.contains(".acceptsAt("), "cells that do not hold replace must not be candidates");
    }

    @Test
    void perPositionProbabilityAsksEachCell() {
        String produce = body(
            "api/recipe/decorator/PerPositionProbabilityDecorator.java",
            "public void produceExtraOutputs(");
        assertTrue(produce.contains(".acceptsAt("), "cells that do not hold replace must be left alone");
    }
}
