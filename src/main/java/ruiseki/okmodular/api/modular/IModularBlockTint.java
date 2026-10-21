package ruiseki.okmodular.api.modular;

import com.gtnewhorizon.gtnhlib.client.model.color.IBlockColor;

/**
 * Extended interface for modular blocks that support dynamic color tinting.
 * Tinting priority:
 * 1. Structure color (if formed and defined) - highest priority
 * 2. Config default color - fallback
 * <p>
 * The structure colour itself is asked of the controller's
 * {@code StructureAgent.getStructureTintColor()}, not of the block. This interface used to declare a
 * {@code getStructureTintColor(world, x, y, z)} of its own that returned null and that no
 * block ever overrode, so anyone reaching for it through the block got "not part of a formed
 * structure" for every block in the mod. Two spellings of one question, and the reachable one
 * always lied. Removed rather than implemented, because {@code colorMultiplier} - the method
 * the renderer actually calls - already goes to the tile.
 */
public interface IModularBlockTint extends IBlockColor {
}
