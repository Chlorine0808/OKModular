package ruiseki.okmodular.api.recipe.io;

import net.minecraft.block.Block;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;

/**
 * How a block written in JSON is matched against a block in the world.
 * <p>
 * Accepted forms: {@code *} for anything, {@code modid:name} for any metadata, and
 * {@code modid:name:meta} where the metadata may itself be {@code *}.
 * <p>
 * This used to be copied privately into {@code BlockInput} and {@code BlockOutput}, and the two
 * copies had already drifted -- only one of them survived a null. Structure IO would have been a
 * third copy, so the rule is stated once here instead.
 */
public final class BlockIdMatcher {

    private BlockIdMatcher() {}

    /**
     * The {@code modid:name:meta} identity of a block in the world.
     *
     * @return {@code minecraft:air:0} for a block the game registry does not know, which is what a
     *         missing block behaves as
     */
    public static String idOf(Block block, int meta) {
        UniqueIdentifier id = block == null ? null : GameRegistry.findUniqueIdentifierFor(block);
        if (id == null) return "minecraft:air:0";
        return id.modId + ":" + id.name + ":" + meta;
    }

    /**
     * @param blockId the world block's identity, as {@link #idOf} produces it
     * @param pattern the block spec written in JSON
     */
    public static boolean matches(String blockId, String pattern) {
        if (pattern == null || blockId == null) return false;
        if (pattern.equals("*")) return true;

        String[] blockParts = blockId.split(":");
        String[] patternParts = pattern.split(":");

        if (patternParts.length == 2) {
            return blockParts.length >= 2 && blockParts[0].equals(patternParts[0])
                && blockParts[1].equals(patternParts[1]);
        }

        if (patternParts.length == 3) {
            if (blockParts.length < 3) return false;
            if (!blockParts[0].equals(patternParts[0]) || !blockParts[1].equals(patternParts[1])) return false;
            return patternParts[2].equals("*") || blockParts[2].equals(patternParts[2]);
        }

        return blockId.equals(pattern);
    }
}
