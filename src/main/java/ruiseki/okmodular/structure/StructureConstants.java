package ruiseki.okmodular.structure;

/**
 * Constants for the Structure system.
 */
public final class StructureConstants {

    // Scan limits
    public static final int MAX_WAND_SCAN_BLOCKS = 1_000_000;
    public static final int MAX_COMMAND_SCAN_BLOCKS = 10_000;

    // How far the wand looks for a block when previewing the pos1 -> cursor box.
    // Matches the parent mod's ItemConfigs.wandPreviewReach default.
    public static final double WAND_PREVIEW_REACH = 16.0D;

    private StructureConstants() {}
}
