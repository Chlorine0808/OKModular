package ruiseki.okmodular.api.recipe.context;

import java.util.List;

import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.core.IMachineState;
import ruiseki.okmodular.api.structure.core.IStructureEntry;

/**
 * Context interface for recipes that interact with structure blocks.
 * Provides access to world, structure information, and block positions.
 */
public interface IRecipeContext {

    /**
     * Get the world where the structure exists.
     */
    World getWorld();

    /**
     * Get the controller's position.
     */
    ChunkCoordinates getControllerPos();

    /**
     * Get the current structure definition.
     */
    IStructureEntry getCurrentStructure();

    /**
     * Get the facing direction of the structure.
     */
    ForgeDirection getFacing();

    /**
     * Get all block positions for a given symbol in the structure.
     * Positions are in world coordinates.
     *
     * @param symbol The mapping symbol (e.g., 'L' for lens)
     * @return List of block positions with that symbol, in world coordinates
     */
    List<ChunkCoordinates> getSymbolPositions(char symbol);

    /**
     * Which pattern cell a world position occupies, in structure-local (ABC) coordinates.
     * <p>
     * Only positions the last structure check visited are known; anything else is null. This is
     * the anchor half of structure IO: the recipe names a symbol, and the block found there says
     * where in the pattern frame it sits.
     *
     * @return a new {@code {a, b, c}}, or null when the position is not part of the formed
     *         structure -- including on a context that tracks no structure at all
     */
    default int[] getSymbolCell(int x, int y, int z) {
        return null;
    }

    /**
     * The other direction: where a pattern cell lands in the world.
     * <p>
     * <b>Not limited to cells the structure check visited.</b> A structure IO region may extend
     * past the blocks the formation check covers, and those cells resolve too.
     *
     * @return a new {@code {x, y, z}}, or null on a context with no structure to measure against
     */
    default int[] getCellPosition(int a, int b, int c) {
        return null;
    }

    /**
     * Get a condition context for expression evaluation.
     */
    ConditionContext getConditionContext();

    /**
     * Get the world tick when the current recipe started.
     */
    default long getRecipeStartTick() {
        return 0;
    }

    /**
     * Get the current redstone level at the controller (0-15).
     */
    default int getRedstoneLevel() {
        return 0;
    }

    /**
     * Get the machine state for this context, if applicable.
     */
    default IMachineState getMachineState() {
        return null;
    }
}
