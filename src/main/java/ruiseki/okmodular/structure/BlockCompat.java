package ruiseki.okmodular.structure;

import java.util.HashMap;
import java.util.Map;

/**
 * Remap table for block IDs that were renamed or removed, applied when reading structure and
 * recipe JSON so files written against an older build still resolve.
 *
 * The table is empty: the entries this class shipped with targeted the parent mod's block names
 * and never matched anything once the machinery split moved the class out of it. Add entries here
 * when a block this mod owns is renamed or removed.
 */
public class BlockCompat {

    /** Maps an old block name (without domain or meta) to its replacement name. */
    private static final Map<String, String> REMOVED_BLOCK_REMAPS = new HashMap<>();

    /**
     * Remaps a removed or renamed block ID to its replacement, preserving the domain and the meta
     * suffix. Returns {@code null} when no remap applies.
     *
     * The domain is carried over rather than matched against this mod's ID, so a table entry works
     * for whichever mod owns the block being remapped.
     *
     * Example, given an entry {@code old_casing -> casing_plain}:
     * "somemod:old_casing:0" -> "somemod:casing_plain:0"
     *
     * @param id the full block ID, optionally with a meta suffix
     * @return the remapped ID, or {@code null} if no remap exists for it
     */
    public static String remapRemovedBlocks(String id) {
        if (id == null) return null;

        String[] parts = id.split(":", 3);
        if (parts.length < 2) return null;

        String newName = REMOVED_BLOCK_REMAPS.get(parts[1]);
        if (newName == null) return null;

        StringBuilder sb = new StringBuilder(parts[0]).append(":")
            .append(newName);
        if (parts.length > 2) sb.append(":")
            .append(parts[2]);
        return sb.toString();
    }
}
