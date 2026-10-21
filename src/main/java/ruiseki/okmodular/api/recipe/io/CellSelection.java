package ruiseki.okmodular.api.recipe.io;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.util.ChunkCoordinates;

/**
 * Picks the cells of a symbol that a block input or output acts on.
 */
public final class CellSelection {

    private CellSelection() {}

    /**
     * @return the first {@code limit} cells, in recorded order, that {@code accepts} passes
     */
    public static List<ChunkCoordinates> firstMatching(List<ChunkCoordinates> cells,
        Predicate<ChunkCoordinates> accepts, int limit) {
        if (cells == null || limit <= 0) return Collections.emptyList();
        List<ChunkCoordinates> chosen = new ArrayList<>();
        for (ChunkCoordinates cell : cells) {
            if (chosen.size() >= limit) break;
            if (accepts.test(cell)) chosen.add(cell);
        }
        return chosen;
    }
}
