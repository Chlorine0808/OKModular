package ruiseki.okmodular.api.recipe.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.ChunkCoordinates;

import org.junit.jupiter.api.Test;

/**
 * Which cells of a symbol a block input or output acts on: the first ones, in the order the
 * structure recorded them, that the input or output accepts, and no more than its amount.
 *
 * harvest_block used to collect from every cell of the symbol instead, so four stones under a
 * one-block recipe gave four cobblestones while only one stone was removed. Taking the
 * selection out as one function lets consumption, placement and harvesting share it.
 */
class CellSelectionTest {

    private static ChunkCoordinates at(int x) {
        return new ChunkCoordinates(x, 0, 0);
    }

    private final List<ChunkCoordinates> cells = Arrays.asList(at(0), at(1), at(2), at(3));

    @Test
    void stopsAtTheAmount() {
        assertEquals(Arrays.asList(at(0)), CellSelection.firstMatching(cells, p -> true, 1));
    }

    @Test
    void skipsCellsItDoesNotAccept() {
        assertEquals(Arrays.asList(at(1), at(3)), CellSelection.firstMatching(cells, p -> p.posX % 2 == 1, 5));
    }

    @Test
    void keepsTheRecordedOrder() {
        assertEquals(Arrays.asList(at(2), at(3)), CellSelection.firstMatching(cells, p -> p.posX >= 2, 2));
    }

    @Test
    void takesNothingForNoAmount() {
        assertEquals(Collections.emptyList(), CellSelection.firstMatching(cells, p -> true, 0));
    }

    @Test
    void takesNothingFromNoCells() {
        assertEquals(Collections.emptyList(), CellSelection.firstMatching(null, p -> true, 3));
    }
}
